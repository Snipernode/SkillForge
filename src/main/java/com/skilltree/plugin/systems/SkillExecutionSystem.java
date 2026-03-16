package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class SkillExecutionSystem implements Listener {

    private final SkillForgePlugin plugin;
    private final SkillRegistry skillRegistry;
    private final Random random = new Random();
    private static final String PROJECTILE_BONUS_META = "sf_projectile_bonus";

    public SkillExecutionSystem(SkillForgePlugin plugin, SkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.skillRegistry = skillRegistry;
        startPassiveRefreshTask();
    }

    /* ==============================
       PASSIVE APPLICATION LOOP
       ============================== */

    private void startPassiveRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    applyAllSkills(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void applyAllSkills(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        double maxStaminaConfig = plugin.getConfig().getDouble("stamina.max-stamina",
                plugin.getConfig().getDouble("stamina.max_stamina", 1000.0));
        double maxThirstConfig = plugin.getConfig().getDouble("thirst.max-thirst",
                plugin.getConfig().getDouble("thirst.max_thirst", 1000.0));
        data.recalculateStaminaAndThirst(maxStaminaConfig, maxThirstConfig);
        applyManaPool(player, data, maxStaminaConfig);

        applyCombatEndurance(player, data);
        applyAgilitySpeed(player, data);
        applyAgilityJump(player, data);
        applyCombatRegeneration(player, data);
        applyMiningHaste(player, data);
        applyFishingUnderwater(player, data);
        applyIntellectPassives(player, data);
        applyBrickExplorerMobility(player, data);
        applyBrickSupportAura(player, data);
        pulseFarmingGrowth(player, data);
    }

    private void applyManaPool(Player player, PlayerData data, double maxStaminaConfig) {
        int level = data.getSkillLevel("magic_manapool");
        if (level <= 0) return;
        double bonus = Math.min(400.0, level * 2.5);
        data.setMaxStamina(Math.min(maxStaminaConfig, data.getMaxStamina() + bonus));
    }

    private void applyCombatEndurance(Player player, PlayerData data) {
        int level = data.getSkillLevel("combat_endurance");
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;

        double targetMaxHealth = 20.0 + Math.min(60.0, level * 0.6);
        attribute.setBaseValue(targetMaxHealth);
        if (player.getHealth() > targetMaxHealth) {
            player.setHealth(targetMaxHealth);
        }
    }

    private void applyAgilitySpeed(Player player, PlayerData data) {
        int level = data.getSkillLevel("agility_speed");
        float speed = (float) Math.min(0.70, 0.20 + (level * 0.004));
        player.setWalkSpeed(speed);
    }

    private void applyAgilityJump(Player player, PlayerData data) {
        int level = data.getSkillLevel("agility_jump");
        if (level <= 0) return;
        int amplifier = Math.min(3, level / 25);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, amplifier, true, false));
    }

    private void applyCombatRegeneration(Player player, PlayerData data) {
        int level = data.getSkillLevel("combat_regeneration");
        if (level <= 0) return;
        int amplifier = Math.min(2, level / 34);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, amplifier, true, false));
    }

    private void applyMiningHaste(Player player, PlayerData data) {
        int total = data.getSkillLevel("mining_efficiency")
                + data.getSkillLevel("mining_stoneworking")
                + data.getSkillLevel("mining_excavation");
        if (total <= 0) return;
        int amplifier = Math.min(3, total / 60);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, amplifier, true, false));
    }

    private void applyFishingUnderwater(Player player, PlayerData data) {
        int level = data.getSkillLevel("fishing_underwater");
        if (level <= 0) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 120, 0, true, false));
    }

    private void applyIntellectPassives(Player player, PlayerData data) {
        int arcana = data.getSkillLevel("intellect_arcana");
        if (arcana > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, true, false));
        }

        int enchanting = data.getSkillLevel("intellect_enchanting");
        if (enchanting > 0) {
            int amp = Math.min(1, enchanting / 60);
            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 120, amp, true, false));
        }
    }

    private void applyBrickExplorerMobility(Player player, PlayerData data) {
        int explorer = data.getSkillLevel("mastery_brick_explorer");
        if (explorer <= 0) return;
        int speedAmp = Math.min(1, explorer / 60);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, speedAmp, true, false));
        if (player.isSwimming() || player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false));
        }
    }

    private void applyBrickSupportAura(Player player, PlayerData data) {
        int support = data.getSkillLevel("mastery_brick_support");
        if (support <= 0) return;
        if (player.getTicksLived() % 40 != 0) return;

        double radius = 4.0 + Math.min(6.0, support * 0.05);
        int regenTicks = 60 + Math.min(80, support * 2);
        int regenAmp = Math.min(1, support / 70);

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player ally)) continue;
            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, regenAmp, true, false));
        }
    }

    private void pulseFarmingGrowth(Player player, PlayerData data) {
        int growth = data.getSkillLevel("farming_growth");
        if (growth <= 0) return;

        int irrigation = data.getSkillLevel("farming_irrigation");
        int radius = 2 + Math.min(2, irrigation / 50);
        double chance = Math.min(0.20, 0.005 + (growth * 0.0025));
        int step = 1 + Math.min(2, growth / 60);

        Location base = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = base.getBlock().getRelative(x, y, z);
                    if (!(block.getBlockData() instanceof Ageable age)) continue;
                    if (age.getAge() >= age.getMaximumAge()) continue;
                    if (random.nextDouble() > chance) continue;
                    age.setAge(Math.min(age.getMaximumAge(), age.getAge() + step));
                    block.setBlockData(age, false);
                }
            }
        }
    }

    /* ==============================
       ACTIVE SKILL TRIGGERS (BOUND)
       ============================== */

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return; // Sneak handling stays in AbilityExecutionSystem

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int slot = player.getInventory().getHeldItemSlot() + 1;
        String skillId = data.getAbilityAtSlot(slot);
        if (skillId == null || skillId.isBlank()) return;

        // Preserve normal right-click block interactions unless hand is empty.
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (action == Action.RIGHT_CLICK_BLOCK && hand != null && hand.getType() != Material.AIR) return;

        event.setCancelled(true);
        if (plugin.getAbilityExecutionSystem() != null) {
            plugin.getAbilityExecutionSystem().triggerAbility(player, skillId, false);
        } else if (skillRegistry != null) {
            skillRegistry.execute(skillId, player, Math.max(1, data.getSkillLevel(skillId)));
        }
    }

    /* ==============================
       PASSIVE RUNTIME EVENTS
       ============================== */

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        Player attacker = null;
        boolean projectileHit = false;
        Projectile projectile = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile p && p.getShooter() instanceof Player shooter) {
            attacker = shooter;
            projectileHit = true;
            projectile = p;
        }

        if (attacker != null) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(attacker);

            int strength = data.getSkillLevel("combat_strength");
            int precision = data.getSkillLevel("combat_precision");
            int critical = data.getSkillLevel("combat_critical");
            int duelistBrick = data.getSkillLevel("mastery_brick_duelist");
            int brawlerBrick = data.getSkillLevel("mastery_brick_brawler");
            int rangerBrick = data.getSkillLevel("mastery_brick_ranger");
            int controllerBrick = data.getSkillLevel("mastery_brick_controller");
            int supportBrick = data.getSkillLevel("mastery_brick_support");

            double multiplier = 1.0 + Math.min(2.0, strength * 0.02) + Math.min(1.0, precision * 0.01);

            if (!projectileHit) {
                ItemStack hand = attacker.getInventory().getItemInMainHand();
                Material weapon = hand == null ? Material.AIR : hand.getType();
                String weaponName = weapon.name();

                int masterySwords = data.getSkillLevel("mastery_swords");
                int masteryAxes = data.getSkillLevel("mastery_axes");
                int masteryFlail = data.getSkillLevel("mastery_flail");

                if (weaponName.endsWith("_SWORD")) {
                    multiplier += Math.min(1.2, masterySwords * 0.012);
                } else if (weaponName.endsWith("_AXE")) {
                    multiplier += Math.min(1.0, masteryAxes * 0.011);
                } else if (weapon == Material.MACE || weaponName.contains("FLAIL")) {
                    multiplier += Math.min(1.0, masteryFlail * 0.012);
                }

                multiplier += Math.min(0.90, brawlerBrick * 0.009);
            } else {
                int masteryBows = data.getSkillLevel("mastery_bows");
                int masteryFirearms = data.getSkillLevel("mastery_firearms");
                double projectileBonus = Math.min(1.2, masteryBows * 0.012) + Math.min(1.0, masteryFirearms * 0.011);
                projectileBonus += Math.min(0.9, rangerBrick * 0.009);
                multiplier += projectileBonus;

                if (projectile != null && projectile.hasMetadata(PROJECTILE_BONUS_META)) {
                    try {
                        double preBaked = projectile.getMetadata(PROJECTILE_BONUS_META).get(0).asDouble();
                        multiplier *= Math.max(0.2, preBaked);
                    } catch (Throwable ignored) {}
                }
            }

            event.setDamage(event.getDamage() * multiplier);

            double critChance = Math.min(0.40, critical * 0.005) + Math.min(0.20, duelistBrick * 0.0025);
            if (critChance > 0 && random.nextDouble() < critChance) {
                double critMultiplier = 1.25 + Math.min(1.0, critical * 0.005) + Math.min(0.45, duelistBrick * 0.003);
                event.setDamage(event.getDamage() * critMultiplier);
                attacker.getWorld().spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 18, 0.35, 0.35, 0.35, 0.05);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.1f);
            }

            if (controllerBrick > 0 && event.getEntity() instanceof org.bukkit.entity.LivingEntity target
                    && random.nextDouble() < Math.min(0.35, controllerBrick * 0.0035)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + Math.min(80, controllerBrick), 0, true, false));
            }

            if (supportBrick > 0) {
                double heal = Math.min(2.0, event.getFinalDamage() * (0.01 + supportBrick * 0.0005));
                if (heal > 0) {
                    double max = attacker.getAttribute(Attribute.MAX_HEALTH) != null
                            ? attacker.getAttribute(Attribute.MAX_HEALTH).getValue()
                            : 20.0;
                    attacker.setHealth(Math.min(max, attacker.getHealth() + heal));
                }
            }
        }

        if (!(event.getEntity() instanceof Player defender)) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(defender);

        int parry = data.getSkillLevel("combat_parry");
        double parryChance = Math.min(0.30, parry * 0.0035);
        if (parry > 0 && random.nextDouble() < parryChance) {
            event.setCancelled(true);
            defender.playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            defender.getWorld().spawnParticle(Particle.CRIT, defender.getLocation().add(0, 1, 0), 10, 0.25, 0.25, 0.25, 0.05);
            return;
        }

        int dodge = data.getSkillLevel("agility_dodge");
        double dodgeChance = Math.min(0.25, dodge * 0.003);
        if (dodge > 0 && random.nextDouble() < dodgeChance) {
            event.setCancelled(true);
            Vector away = defender.getLocation().toVector().subtract(event.getDamager().getLocation().toVector()).normalize().multiply(0.35);
            away.setY(0.15);
            defender.setVelocity(away);
            defender.getWorld().spawnParticle(Particle.CLOUD, defender.getLocation().add(0, 1, 0), 12, 0.2, 0.3, 0.2, 0.03);
        }
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(shooter);

        int masteryBows = data.getSkillLevel("mastery_bows");
        int masteryFirearms = data.getSkillLevel("mastery_firearms");
        int rangerBrick = data.getSkillLevel("mastery_brick_ranger");

        double bonus = 1.0
                + Math.min(1.2, masteryBows * 0.012)
                + Math.min(1.0, masteryFirearms * 0.010)
                + Math.min(0.9, rangerBrick * 0.009);

        projectile.setMetadata(PROJECTILE_BONUS_META, new FixedMetadataValue(plugin, bonus));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int defense = data.getSkillLevel("combat_defense");
        if (defense > 0) {
            double reduction = Math.min(0.75, defense * 0.02);
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }

        int bulwarkBrick = data.getSkillLevel("mastery_brick_bulwark");
        if (bulwarkBrick > 0) {
            double reduction = Math.min(0.40, bulwarkBrick * 0.004);
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            int acrobatics = data.getSkillLevel("agility_acrobatics");
            if (acrobatics > 0) {
                double reduction = Math.min(0.85, acrobatics * 0.02);
                event.setDamage(event.getDamage() * (1.0 - reduction));
            }
        }

        int berserker = data.getSkillLevel("beserker");
        if (berserker > 0) {
            double projectedHealth = player.getHealth() - event.getFinalDamage();
            if (projectedHealth <= 6.0) {
                int amp = Math.min(3, berserker / 30);
                int duration = 80 + Math.min(200, berserker * 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, amp, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amp, true, false));
            }
        }

        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Monster
                && hasNearbyCrops(player, 4)) {
            int pestControl = data.getSkillLevel("farming_pestcontrol");
            if (pestControl > 0) {
                double reduction = Math.min(0.40, pestControl * 0.01);
                event.setDamage(event.getDamage() * (1.0 - reduction));
            }
        }
    }

    @EventHandler
    public void onPlayerExpGain(PlayerExpChangeEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        int wisdom = data.getSkillLevel("intellect_wisdom");
        int scholarship = data.getSkillLevel("intellect_scholarship");
        if (wisdom <= 0 && scholarship <= 0) return;
        if (event.getAmount() <= 0) return;

        double bonusPct = Math.min(2.0, wisdom * 0.03 + scholarship * 0.02);
        int bonus = (int) Math.floor(event.getAmount() * bonusPct);
        if (bonus > 0) {
            event.setAmount(event.getAmount() + bonus);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        Block block = event.getBlock();
        Material type = block.getType();
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        if (isOre(type)) {
            int fortune = data.getSkillLevel("mining_fortune");
            if (fortune > 0 && random.nextDouble() < Math.min(0.75, fortune * 0.02)) {
                PlayerInventory inv = player.getInventory();
                ItemStack tool = inv.getItemInMainHand();
                Collection<ItemStack> drops = block.getDrops(tool, player);
                for (ItemStack drop : drops) {
                    ItemStack extra = drop.clone();
                    extra.setAmount(Math.max(1, extra.getAmount()));
                    block.getWorld().dropItemNaturally(dropLoc, extra);
                }
            }

            int prospector = data.getSkillLevel("mining_prospector");
            if (prospector > 0 && random.nextDouble() < Math.min(0.25, prospector * 0.01)) {
                Material[] gems = new Material[] {Material.EMERALD, Material.DIAMOND, Material.LAPIS_LAZULI, Material.REDSTONE};
                Material reward = gems[random.nextInt(gems.length)];
                block.getWorld().dropItemNaturally(dropLoc, new ItemStack(reward, 1));
            }

            int minerBrick = data.getSkillLevel("mastery_brick_miner");
            if (minerBrick > 0 && random.nextDouble() < Math.min(0.35, minerBrick * 0.0035)) {
                block.getWorld().dropItemNaturally(dropLoc, new ItemStack(type, 1));
            }
        }

        int harvest = data.getSkillLevel("farming_harvest");
        if (harvest > 0 && isMatureCrop(block) && random.nextDouble() < Math.min(0.70, harvest * 0.02)) {
            Material produce = produceForCrop(type);
            if (produce != null) {
                block.getWorld().dropItemNaturally(dropLoc, new ItemStack(produce, 1));
            }
        }

        int harvesterBrick = data.getSkillLevel("mastery_brick_harvester");
        if (harvesterBrick > 0 && isMatureCrop(block) && random.nextDouble() < Math.min(0.35, harvesterBrick * 0.004)) {
            Material produce = produceForCrop(type);
            if (produce != null) {
                block.getWorld().dropItemNaturally(dropLoc, new ItemStack(produce, 1));
            }
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int durability = data.getSkillLevel("mining_durability");
        if (durability <= 0) return;

        ItemStack tool = event.getItem();
        if (tool == null) return;
        String type = tool.getType().name();
        if (!type.contains("PICKAXE") && !type.contains("SHOVEL") && !type.contains("AXE")) return;

        double ignoreChance = Math.min(0.85, durability * 0.02);
        if (random.nextDouble() < ignoreChance) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        int speed = data.getSkillLevel("fishing_speed");
        int luck = data.getSkillLevel("fishing_luck");
        int rare = data.getSkillLevel("fishing_rare");

        if (speed > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60 + Math.min(120, speed * 2), 0, true, false));
            if (random.nextDouble() < Math.min(0.60, speed * 0.015)) {
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.COD, 1));
            }
        }

        if (luck > 0 && random.nextDouble() < Math.min(0.50, luck * 0.0125)) {
            Material[] fish = new Material[] {Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(fish[random.nextInt(fish.length)], 1));
        }

        if (rare > 0 && random.nextDouble() < Math.min(0.35, rare * 0.01)) {
            Material[] treasure = new Material[] {
                    Material.NAUTILUS_SHELL, Material.PRISMARINE_SHARD, Material.NAME_TAG, Material.HEART_OF_THE_SEA
            };
            Material reward = treasure[random.nextInt(treasure.length)];
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(reward, 1));
            player.sendMessage("§bRare catch: " + reward.name());
        }

        Entity caught = event.getCaught();
        if (caught instanceof Item item) {
            ItemStack stack = item.getItemStack();
            if (stack != null && stack.getType() == Material.PUFFERFISH && rare > 0 && random.nextDouble() < 0.25) {
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.TROPICAL_FISH, 1));
            }
        }
    }

    @EventHandler
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.POTION) return;
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        int brewing = data.getSkillLevel("intellect_brewing");
        int alchemy = data.getSkillLevel("intellect_alchemy");

        if (alchemy > 0) {
            data.regenStamina(Math.min(80.0, alchemy * 1.5));
            data.restoreThirst(Math.min(80.0, alchemy * 1.2));
        }

        if (brewing <= 0) return;
        int extendTicks = Math.min(20 * 60, brewing * 6);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                int newDuration = Math.min(20 * 60 * 15, effect.getDuration() + extendTicks);
                player.addPotionEffect(new PotionEffect(effect.getType(), newDuration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles()));
            }
        });
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int enchanting = data.getSkillLevel("intellect_enchanting");
        if (enchanting <= 0) return;

        int currentCost = event.getExpLevelCost();
        int reduction = (int) Math.floor(currentCost * Math.min(0.75, enchanting * 0.02));
        event.setExpLevelCost(Math.max(1, currentCost - reduction));
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int breeding = data.getSkillLevel("farming_breeding");
        if (breeding <= 0) return;
        int bonusXp = Math.max(1, breeding / 8);
        event.setExperience(event.getExperience() + bonusXp);
    }

    @EventHandler
    public void onClimbAssist(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int climb = data.getSkillLevel("agility_climbing");
        int parkour = data.getSkillLevel("agility_parkour");
        int total = climb + parkour;
        if (total <= 0) return;

        if (!player.isSneaking()) return;
        if (player.isFlying() || player.isGliding()) return;
        if (player.getVelocity().getY() > 0.18) return;

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        Block front = loc.clone().add(dir.multiply(0.6)).getBlock();
        if (!front.getType().isSolid()) return;

        double climbBoost = 0.08 + Math.min(0.18, total * 0.0025);
        Vector vel = player.getVelocity();
        player.setVelocity(new Vector(vel.getX(), Math.max(vel.getY(), climbBoost), vel.getZ()));
    }

    /* ==============================
       LIFECYCLE
       ============================== */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAllSkills(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAllSkills(event.getPlayer()), 1L);
    }

    /* ==============================
       HELPERS
       ============================== */

    private boolean isOre(Material material) {
        return material == Material.COAL_ORE
                || material == Material.DEEPSLATE_COAL_ORE
                || material == Material.IRON_ORE
                || material == Material.DEEPSLATE_IRON_ORE
                || material == Material.GOLD_ORE
                || material == Material.DEEPSLATE_GOLD_ORE
                || material == Material.COPPER_ORE
                || material == Material.DEEPSLATE_COPPER_ORE
                || material == Material.DIAMOND_ORE
                || material == Material.DEEPSLATE_DIAMOND_ORE
                || material == Material.REDSTONE_ORE
                || material == Material.DEEPSLATE_REDSTONE_ORE
                || material == Material.LAPIS_ORE
                || material == Material.DEEPSLATE_LAPIS_ORE
                || material == Material.EMERALD_ORE
                || material == Material.DEEPSLATE_EMERALD_ORE
                || material == Material.NETHER_QUARTZ_ORE
                || material == Material.NETHER_GOLD_ORE
                || material == Material.ANCIENT_DEBRIS;
    }

    private boolean isMatureCrop(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private Material produceForCrop(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    private boolean hasNearbyCrops(Player player, int radius) {
        Location base = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = base.getBlock().getRelative(x, y, z);
                    if (block.getBlockData() instanceof Ageable) return true;
                }
            }
        }
        return false;
    }
}
