package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.utils.ProtectionUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;

public class AbilityExecutor implements Listener {
    
    private final SkillForgePlugin plugin;
    private final SkillRegistry skillRegistry;

    public AbilityExecutor(SkillForgePlugin plugin, StaminaSystem staminaSystem) {
        this.plugin = plugin;
        this.skillRegistry = new SkillRegistry(staminaSystem);
        registerDefaultSkills();
    }

    // ==========================================
    // EVENT LISTENERS (From AbilityExecutionSystem)
    // ==========================================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (!data.hasSelectedGamemode()) return;
        
        int slot = player.getInventory().getHeldItemSlot() + 1;
        String abilityId = data.getAbilityAtSlot(slot);
        
        if (abilityId == null) return;
        
        boolean isLeftClick = event.getAction().toString().contains("LEFT");
        triggerAbility(player, abilityId, isLeftClick);
        event.setCancelled(true);
    }
    
    public void triggerAbility(Player player, String abilityId, boolean isLeftClick) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        if (!data.hasSelectedGamemode()) {
            player.sendMessage(ChatColor.RED + "You haven't selected a class/gamemode yet!");
            return;
        }
        
        // Check if learned
        if (data.getSkillLevel(abilityId) <= 0) {
            player.sendMessage(ChatColor.RED + "Skill not learned");
            return;
        }

        // Check cooldown
        if (data.isAbilityOnCooldown(abilityId)) {
            long remaining = data.getAbilityCooldownRemaining(abilityId);
            player.sendMessage(ChatColor.RED + "Ability on cooldown for " + (remaining / 1000) + "s");
            return;
        }
        
        // Execute logic
        executeAbility(player, data, abilityId, isLeftClick);
    }
    
    private void executeAbility(Player player, PlayerData data, String abilityId, boolean isLeftClick) {
        if (skillRegistry.hasSkill(abilityId)) {
            skillRegistry.execute(abilityId, player, data.getSkillLevel(abilityId));
        } else {
            player.sendMessage(ChatColor.RED + "This ability is not yet implemented");
        }
    }
    
    // ==========================================
    // SKILL REGISTRATION
    // ==========================================

    private void registerDefaultSkills() {
        
        // ==========================================
        // COMBAT (Warrior)
        // ==========================================
        
        skillRegistry.register("combat_whirlwind", 10.0, null, (player, level) -> {
            double radius = 4.0 + level;
            double damage = 5.0 + (level * 0.5);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue; 
                    }
                    ((LivingEntity) entity).damage(damage, player);
                }
            }
            player.sendMessage(ChatColor.RED + "⚔ Whirlwind!");
        });

        skillRegistry.register("combat_laststand", 0.0, null, (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1 + (level / 5), false, false));
            player.sendMessage(ChatColor.RED + "🛡 Last Stand!");
        });

        skillRegistry.register("combat_execute", 15.0, null, (player, level) -> {
            double damage = 15.0 + (level * 2);
            double radius = 5.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
                    if (target.getHealth() < maxHealth * 0.4) {
                        target.damage(damage, player);
                    }
                }
            }
            player.sendMessage(ChatColor.RED + "⚡ Execute!");
        });

        skillRegistry.register("combat_shieldbash", 15.0, "combat_laststand", (player, level) -> {
            double damage = 4.0 + level;
            double radius = 3.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    target.damage(damage, player);
                    Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    target.setVelocity(dir.multiply(2.0));
                }
            }
            player.sendMessage(ChatColor.GOLD + "Shield Bash!");
        });

        skillRegistry.register("combat_battlecry", 20.0, "combat_whirlwind", (player, level) -> {
            int duration = 200 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            player.sendMessage(ChatColor.BOLD + "BATTLE CRY!");
        });

        skillRegistry.register("combat_charge", 25.0, null, (player, level) -> {
            Vector dir = player.getLocation().getDirection();
            player.setVelocity(dir.multiply(1.5 + (level * 0.2)).setY(0.2));
            
            new BukkitRunnable() {
                int ticks = 0;
                public void run() {
                    ticks++;
                    if (ticks > 10 || player.isOnGround()) {
                        this.cancel();
                        return;
                    }
                    for (Entity entity : player.getNearbyEntities(1.5, 2, 1.5)) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            // PvP Check
                            if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                                continue;
                            }
                            ((LivingEntity) entity).damage(5.0 + level, player);
                            this.cancel();
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            player.sendMessage(ChatColor.GRAY + "Charge!");
        });

        // ==========================================
        // AGILITY (Assassin)
        // ==========================================

        skillRegistry.register("agility_shadowstep", 20.0, null, (player, level) -> {
            Entity target = null;
            double range = 10 + level;
            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    target = entity;
                    break;
                }
            }
            if (target != null) {
                Vector dir = target.getLocation().getDirection().setY(0).normalize().multiply(-1);
                Location loc = target.getLocation().add(dir);
                // Check if destination is protected
                if (ProtectionUtils.isProtectedArea(player, loc)) {
                    player.sendMessage(ChatColor.RED + "You cannot teleport there!");
                    return;
                }
                player.teleport(loc);
                player.sendMessage(ChatColor.DARK_PURPLE + "👤 Shadowstep!");
            } else {
                player.sendMessage(ChatColor.RED + "No target!");
            }
        });

        skillRegistry.register("agility_timeshift", 25.0, null, (player, level) -> {
            int duration = 20 + (level * 5);
            double radius = 5.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 10, false, false));
                    mob.setAI(false);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (mob.isValid()) mob.setAI(true);
                    }, duration);
                }
            }
            player.sendMessage(ChatColor.AQUA + "⏱ Timeshift activated!");
        });

        skillRegistry.register("agility_blink", 10.0, null, (player, level) -> {
            Vector dir = player.getLocation().getDirection();
            Location loc = player.getLocation().add(dir.multiply(5 + level));
            loc.setPitch(player.getLocation().getPitch());
            loc.setYaw(player.getLocation().getYaw());
            
            // Check if destination is protected
            if (ProtectionUtils.isProtectedArea(player, loc)) {
                player.sendMessage(ChatColor.RED + "You cannot blink there!");
                return;
            }
            
            player.teleport(loc);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Blink!");
        });

        skillRegistry.register("agility_dash", 15.0, "agility_blink", (player, level) -> {
            Vector dir = player.getLocation().getDirection().setY(0);
            player.setVelocity(dir.multiply(1.5 + (level * 0.2)));
            player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 10);
            player.sendMessage(ChatColor.WHITE + "Dash!");
        });

        skillRegistry.register("agility_evasion", 30.0, "agility_timeshift", (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
            player.sendMessage(ChatColor.GRAY + "Evasion Mode!");
        });

        skillRegistry.register("agility_smokebomb", 25.0, "agility_shadowstep", (player, level) -> {
            int radius = 5 + level;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = player.getLocation().add(x, 0, z);
                    player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 1);
                }
            }
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                }
            }
            player.sendMessage(ChatColor.DARK_GRAY + "Smoke Bomb!");
        });

        // ==========================================
        // INTELLECT (Mage)
        // ==========================================

        skillRegistry.register("intellect_mindblast", 15.0, null, (player, level) -> {
            double damage = 8.0 + (level * 1.5);
            double radius = 6.0 + level;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    target.damage(damage, player);
                    Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                    target.setVelocity(knockback);
                }
            }
            player.sendMessage(ChatColor.BLUE + "🧠 Mindblast!");
        });

        skillRegistry.register("intellect_mindshield", 5.0, null, (player, level) -> {
            int duration = 80 + (level * 15);
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, level / 5, false, false));
            player.sendMessage(ChatColor.BLUE + "🛡 Mindshield!");
        });

        skillRegistry.register("intellect_amplify", 5.0, null, (player, level) -> {
            int duration = 60 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, level / 10, false, false));
            player.sendMessage(ChatColor.BLUE + "⚡ Amplify!");
        });

        skillRegistry.register("intellect_missiles", 20.0, "intellect_mindblast", (player, level) -> {
            Entity target = getTargetEntity(player, (int) (20 + level));
            if (target != null && target instanceof LivingEntity) {
                int count = 3 + level;
                new BukkitRunnable() {
                    int shot = 0;
                    public void run() {
                        if (shot >= count || !target.isValid()) {
                            this.cancel();
                            return;
                        }
                        Snowball missile = player.launchProjectile(Snowball.class);
                        missile.setVelocity(target.getLocation().subtract(player.getLocation()).toVector().normalize().multiply(2.0));
                        shot++;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Arcane Missiles!");
            }
        });

        skillRegistry.register("intellect_frostanova", 25.0, "magic_frostbolt", (player, level) -> {
            double radius = 4.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity) {
                    LivingEntity le = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + (level * 10), 5));
                    le.damage(2.0 + level, player);
                }
            }
            player.sendMessage(ChatColor.AQUA + "Frost Nova!");
        });

        // ==========================================
        // MINING
        // ==========================================

        skillRegistry.register("mining_explosive", 20.0, null, (player, level) -> {
            // CHECK: Is the player in a protected area?
            if (ProtectionUtils.isProtectedArea(player, player.getLocation())) {
                player.sendMessage(ChatColor.RED + "You cannot use explosives in a protected area!");
                return;
            }

            float power = 2.0f + (level * 0.1f);
            player.getWorld().createExplosion(player.getLocation(), power, false, false);
            player.sendMessage(ChatColor.RED + "💥 Explosive!");
        });

        skillRegistry.register("mining_superbreaker", 30.0, "mining_explosive", (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 2 + (level / 5)));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 1));
            player.sendMessage(ChatColor.GOLD + "Super Breaker Activated!");
        });

        skillRegistry.register("mining_xray", 40.0, "mining_superbreaker", (player, level) -> {
            int duration = 200 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
            player.sendMessage(ChatColor.GREEN + "Senses heightened...");
            
            new BukkitRunnable() {
                int ticks = 0;
                public void run() {
                    if (ticks > duration || !player.isOnline()) {
                        this.cancel();
                        return;
                    }
                    int radius = 10 + level;
                    Location center = player.getLocation();
                    for (int x = -radius; x <= radius; x++) {
                        for (int y = -radius; y <= radius; y++) {
                            for (int z = -radius; z <= radius; z++) {
                                Block b = center.getBlock().getRelative(x, y, z);
                                if (isOre(b.getType())) {
                                    player.spawnParticle(Particle.BLOCK_CRUMBLE,b.getLocation().add(0.5, 0.5, 0.5), 1, b.getBlockData());
                                }
                            }
                        }
                    }
                    ticks += 10;
                }
            }.runTaskTimer(plugin, 0L, 10L);
        });

        // ==========================================
        // ARCHERY
        // ==========================================

        skillRegistry.register("archery_multishot", 20.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) {
                player.sendMessage(ChatColor.RED + "Must be holding a bow!");
                return;
            }
            Vector dir = player.getLocation().getDirection();
            Arrow arrow1 = player.launchProjectile(Arrow.class);
            arrow1.setVelocity(dir.clone().rotateAroundY(Math.toRadians(10)).multiply(2.0));
            
            Arrow arrow2 = player.launchProjectile(Arrow.class);
            arrow2.setVelocity(dir.clone().rotateAroundY(Math.toRadians(-10)).multiply(2.0));
            
            player.sendMessage(ChatColor.GOLD + "Multishot!");
        });

        skillRegistry.register("archery_explosive", 25.0, "archery_multishot", (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) {
                player.sendMessage(ChatColor.RED + "Must be holding a bow!");
                return;
            }
            Arrow arrow = player.launchProjectile(Arrow.class);
            // Store metadata so the ProjectileHitEvent knows it's explosive
            arrow.setMetadata("explosive_arrow", new FixedMetadataValue(plugin, level));
            player.sendMessage(ChatColor.RED + "Explosive Arrow Ready!");
        });

        // ==========================================
        // NATURE
        // ==========================================

        skillRegistry.register("nature_root", 20.0, null, (player, level) -> {
            double radius = 5.0 + level;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity le = (LivingEntity) entity;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + (level * 10), 10));
                    player.getWorld().playEffect(le.getLocation(), Effect.STEP_SOUND, Material.OAK_LOG);
                }
            }
            player.sendMessage(ChatColor.GREEN + "Entangling Roots!");
        });

        skillRegistry.register("nature_regrowth", 30.0, "nature_root", (player, level) -> {
            double healAmount = 4.0 + (level * 2);
            player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + healAmount));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation(), 10);
            player.sendMessage(ChatColor.DARK_GREEN + "Nature's Grasp Heals You!");
        });

        // ==========================================
        // INNATE SKILLS
        // ==========================================

        skillRegistry.register("innate_vitality", 0.0, null, (player, level) -> {
            player.sendMessage(ChatColor.GREEN + "Vitality increases your constitution.");
        });

        skillRegistry.register("innate_swiftfeet", 0.0, null, (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, true, false));
            player.sendMessage(ChatColor.AQUA + "You feel lighter on your feet.");
        });

        skillRegistry.register("innate_nightvision", 0.0, null, (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 999999, 0, true, false));
        });

        skillRegistry.register("innate_clearmind", 0.0, null, (player, level) -> {
            player.sendMessage(ChatColor.BLUE + "Your mind is clear, regenerating focus faster.");
        });

        // ==========================================
        // BERSERKER (Warrior)
        // ==========================================

        skillRegistry.register("berserker_bloodrage", 10.0, "combat_execute", (player, level) -> {
            double healthCost = 2.0 + level;
            if (player.getHealth() > healthCost) {
                player.damage(healthCost);
                int duration = 200 + (level * 20);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1 + (level/5)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.0f, 0.5f);
                player.sendMessage(ChatColor.RED + "BLOODRAGE!");
            } else {
                player.sendMessage(ChatColor.RED + "Not enough health to sacrifice!");
            }
        });

        skillRegistry.register("berserker_rage", 0.0, "berserker_bloodrage", (player, level) -> {
            player.sendMessage(ChatColor.DARK_RED + "Your rage builds as your health drops.");
        });

        skillRegistry.register("berserker_shout", 25.0, "combat_battlecry", (player, level) -> {
            double radius = 8.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    mob.setTarget(null);
                    Vector dir = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(2.0);
                    mob.setVelocity(dir);
                }
            }
            player.sendMessage(ChatColor.BOLD + "RAAAH!");
        });

        // ==========================================
        // PALADIN (Warrior)
        // ==========================================

        skillRegistry.register("paladin_holylight", 30.0, "combat_laststand", (player, level) -> {
            double healAmount = 6.0 + (level * 2);
            double radius = 5.0 + level;
            
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 50);

            player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + healAmount));
            
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player) {
                    Player ally = (Player) entity;
                    ally.setHealth(Math.min(ally.getAttribute(Attribute.MAX_HEALTH).getValue(), ally.getHealth() + healAmount));
                    ally.sendMessage(ChatColor.GOLD + "You were healed by " + player.getName());
                }
            }
            player.sendMessage(ChatColor.GOLD + "Holy Light!");
        });

        skillRegistry.register("paladin_divineshield", 50.0, "paladin_holylight", (player, level) -> {
            int duration = 40 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 10));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 5));
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            player.sendMessage(ChatColor.YELLOW + "DIVINE SHIELD!");
        });

        skillRegistry.register("paladin_hammer", 20.0, "combat_shieldbash", (player, level) -> {
            Entity target = getTargetEntity(player, 15);
            if (target instanceof LivingEntity && !(target instanceof Player)) {
                LivingEntity le = (LivingEntity) target;
                le.damage(5.0 + level, player);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + (level * 10), 10));
                le.getWorld().spawnParticle(Particle.FLASH, le.getLocation(), 1);
                player.sendMessage(ChatColor.GOLD + "Hammer of Justice!");
            } else {
                player.sendMessage(ChatColor.RED + "No valid target!");
            }
        });

        // ==========================================
        // RANGER (Archery)
        // ==========================================

        skillRegistry.register("ranger_volley", 40.0, "archery_multishot", (player, level) -> {
            Location loc = player.getTargetBlock(null, 50).getLocation().add(0, 10, 0);
            int arrows = 10 + (level * 2);
            
            player.sendMessage(ChatColor.GREEN + "Volley!");
            
            new BukkitRunnable() {
                int shot = 0;
                public void run() {
                    if (shot >= arrows) {
                        this.cancel();
                        return;
                    }
                    Arrow arrow = (Arrow) player.getWorld().spawnEntity(loc, EntityType.ARROW);
                    arrow.setShooter(player);
                    arrow.setDamage(2.0 + level);
                    arrow.setVelocity(new Vector(0, -1.5, 0));
                    arrow.setVelocity(arrow.getVelocity().add(new Vector((Math.random()-0.5), 0, (Math.random()-0.5))));
                    shot++;
                }
            }.runTaskTimer(plugin, 0L, 2L);
        });

        skillRegistry.register("ranger_poison", 15.0, "nature_root", (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) {
                player.sendMessage(ChatColor.RED + "Must be holding a bow!");
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, level));
            player.sendMessage(ChatColor.DARK_GREEN + "Arrow tips poisoned!");
        });

        // ==========================================
        // SUMMONER (Beast Master)
        // ==========================================

        skillRegistry.register("summon_wolf", 40.0, null, (player, level) -> {
            Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
            wolf.setTamed(true);
            wolf.setOwner(player);
            wolf.setCustomName(ChatColor.GREEN + player.getName() + "'s Wolf");
            wolf.setCustomNameVisible(true);
            wolf.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0 + (level * 10));
            wolf.setHealth(wolf.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.sendMessage(ChatColor.GREEN + "Howl!");
        });

        skillRegistry.register("summon_beastrage", 20.0, "summon_wolf", (player, level) -> {
            for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                if (entity instanceof Tameable && ((Tameable) entity).getOwner().equals(player)) {
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
                }
            }
            player.sendMessage(ChatColor.GREEN + "Beast Rage!");
        });

        // ==========================================
        // UTILITY
        // ==========================================

        skillRegistry.register("util_recall", 50.0, null, (player, level) -> {
            Location bed = player.getBedSpawnLocation();
            if (bed != null) {
                // Check if destination is protected
                if (ProtectionUtils.isProtectedArea(player, bed)) {
                    player.sendMessage(ChatColor.RED + "You cannot recall to a protected area!");
                    return;
                }
                player.teleport(bed);
                player.getWorld().playSound(bed, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendMessage(ChatColor.WHITE + "Recall!");
            } else {
                player.sendMessage(ChatColor.RED + "No bed spawn set!");
            }
        });

        skillRegistry.register("util_safefall", 10.0, "agility_blink", (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100 + (level * 20), 0));
            player.sendMessage(ChatColor.GRAY + "Safe Fall activated.");
        });
        
        // ==========================================
        // RUNE MAGIC (Magic)
        // ==========================================

        skillRegistry.register("rune_fire", 30.0, "magic_fireball", (player, level) -> {
            Block block = player.getTargetBlockExact(5);
            if (block != null && block.getType().isSolid()) {
                // CHECK: Is the target block in a protected area?
                if (ProtectionUtils.isProtectedArea(player, block.getLocation())) {
                    player.sendMessage(ChatColor.RED + "You cannot place runes in a protected area!");
                    return;
                }

                Location loc = block.getLocation().add(0, 1, 0);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    loc.getWorld().createExplosion(loc, 2.0f + level, true, false);
                }, 60);
                player.sendMessage(ChatColor.RED + "Fire Rune placed.");
            }
        });

        skillRegistry.register("rune_ice", 30.0, "magic_frostbolt", (player, level) -> {
            Block block = player.getTargetBlockExact(5);
            if (block != null && block.getType().isSolid()) {
                // CHECK: Is the target block in a protected area?
                if (ProtectionUtils.isProtectedArea(player, block.getLocation())) {
                    player.sendMessage(ChatColor.RED + "You cannot place runes in a protected area!");
                    return;
                }

                Location loc = block.getLocation().add(0, 1, 0);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 20);
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                        if (entity instanceof LivingEntity) {
                            ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5));
                        }
                    }
                }, 60);
                player.sendMessage(ChatColor.AQUA + "Ice Rune placed.");
            }
        });

        // ==========================================
        // BARD
        // ==========================================

        skillRegistry.register("bard_speed", 0.0, null, (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Speed.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        });

        skillRegistry.register("bard_healing", 0.0, "bard_speed", (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Healing.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        });

        skillRegistry.register("bard_resistance", 0.0, "bard_healing", (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Resistance.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
        });

        skillRegistry.register("bard_discord", 0.0, "bard_resistance", (player, level) -> {
            player.sendMessage(ChatColor.DARK_PURPLE + "Playing Discord.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        });

        // ==========================================
        // ALCHEMY
        // ==========================================

        skillRegistry.register("alchemy_transmute", 10.0, "mining_explosive", (player, level) -> {
            Block target = player.getTargetBlockExact(5);
            if (target != null && target.getType() == Material.COBBLESTONE) {
                // CHECK: Is the target block in a protected area?
                if (ProtectionUtils.isProtectedArea(player, target.getLocation())) {
                    player.sendMessage(ChatColor.RED + "You cannot transmute in a protected area!");
                    return;
                }

                double chance = 0.1 + (level * 0.05);
                if (Math.random() < chance) {
                    target.setType(Material.IRON_ORE);
                    player.sendMessage(ChatColor.GREEN + "Transmutation successful!");
                } else {
                    player.sendMessage(ChatColor.GRAY + "The stone crumbles...");
                    target.setType(Material.AIR);
                }
            }
        });

        skillRegistry.register("alchemy_brew", 20.0, "alchemy_transmute", (player, level) -> {
            PotionEffectType[] effects = {PotionEffectType.SPEED, PotionEffectType.REGENERATION, PotionEffectType.STRENGTH, PotionEffectType.JUMP_BOOST};
            PotionEffectType type = effects[new Random().nextInt(effects.length)];
            int duration = 200 + (level * 20);
            
            player.addPotionEffect(new PotionEffect(type, duration, 1));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Brewed " + type.getName() + "!");
        });

        skillRegistry.register("alchemy_acid", 25.0, "alchemy_brew", (player, level) -> {
            Snowball acid = player.launchProjectile(Snowball.class);
            acid.setCustomName("Acid");
            player.sendMessage(ChatColor.GREEN + "Acid Splash!");
        });

        // ==========================================
        // DEFENDER (Warrior)
        // ==========================================

        skillRegistry.register("defender_taunt", 15.0, "combat_shieldbash", (player, level) -> {
            double radius = 10.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);
            
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) {
                    ((Mob) entity).setTarget(player);
                }
            }
            player.sendMessage(ChatColor.RED + "TAUNT!");
        });

        skillRegistry.register("defender_shieldwall", 30.0, "defender_taunt", (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 4));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 4));
            player.sendMessage(ChatColor.GRAY + "Shield Wall!");
        });

        skillRegistry.register("defender_lastgasp", 0.0, "combat_laststand", (player, level) -> {
            player.sendMessage(ChatColor.GOLD + "Your determination to protect others strengthens.");
        });

        // ==========================================
        // CHEF
        // ==========================================

        skillRegistry.register("chef_feast", 30.0, null, (player, level) -> {
            double radius = 10.0;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
            
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player) {
                    Player p = (Player) entity;
                    p.setFoodLevel(20);
                    p.setSaturation(20);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                    p.sendMessage(ChatColor.GOLD + "Delicious!");
                }
            }
            player.sendMessage(ChatColor.GOLD + "Feast Served!");
        });

        skillRegistry.register("chef_spice", 10.0, "chef_feast", (player, level) -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isEdible()) {
                player.setMetadata("spiced_food", new FixedMetadataValue(plugin, level));
                player.sendMessage(ChatColor.GOLD + "Food Spiced! Next meal gives buffs.");
            }
        });

        // ==========================================
        // BUILDER
        // ==========================================

        skillRegistry.register("builder_scaffold", 5.0, null, (player, level) -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() > 0) {
                Location loc = player.getTargetBlockExact(5).getRelative(player.getFacing()).getLocation();
                
                // CHECK: Is the target block in a protected area?
                if (ProtectionUtils.isProtectedArea(player, loc)) {
                    player.sendMessage(ChatColor.RED + "You cannot build here!");
                    return;
                }

                loc.getBlock().setType(hand.getType());
                hand.setAmount(hand.getAmount() - 1);
            }
        });

        skillRegistry.register("builder_wall", 20.0, "builder_scaffold", (player, level) -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() > 0) {
                Location center = player.getTargetBlockExact(10).getLocation();
                
                // CHECK: Is the center block in a protected area?
                if (ProtectionUtils.isProtectedArea(player, center)) {
                    player.sendMessage(ChatColor.RED + "You cannot build here!");
                    return;
                }

                Vector dir = player.getLocation().getDirection().setY(0).normalize();
                Vector side = new Vector(-dir.getZ(), 0, dir.getX());
                
                for (int i = -2; i <= 2; i++) {
                    for (int y = 0; y < 3 + level; y++) {
                        Location loc = center.clone().add(side.clone().multiply(i)).add(0, y, 0);
                        if (hand.getAmount() > 0) {
                            loc.getBlock().setType(hand.getType());
                            hand.setAmount(hand.getAmount() - 1);
                        }
                    }
                }
            }
        });

        // ==========================================
        // FISHERMAN
        // ==========================================

        skillRegistry.register("fish_grapple", 15.0, null, (player, level) -> {
            Block block = player.getTargetBlockExact(30);
            if (block != null) {
                Vector dir = block.getLocation().subtract(player.getLocation()).toVector().normalize();
                player.setVelocity(dir.multiply(1.5 + (level * 0.1)));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
            }
        });

        skillRegistry.register("fish_harpoon", 20.0, "fish_grapple", (player, level) -> {
            Entity target = getTargetEntity(player, 20);
            if (target instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) target;
                le.damage(5.0 + level, player);
                Vector pull = player.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(0.5);
                le.setVelocity(pull);
                player.sendMessage(ChatColor.BLUE + "Harpooned!");
            }
        });

        // ==========================================
        // DUELIST (Warrior)
        // ==========================================

        skillRegistry.register("duelist_parry", 10.0, null, (player, level) -> {
            player.setMetadata("parry_active", new FixedMetadataValue(plugin, System.currentTimeMillis()));
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            player.sendMessage(ChatColor.GRAY + "Parry Stance!");
        });

        skillRegistry.register("duelist_riposte", 15.0, "duelist_parry", (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2));
            player.sendMessage(ChatColor.GRAY + "Riposte!");
        });

        skillRegistry.register("duelist_precision", 0.0, "duelist_riposte", (player, level) -> {
            player.setMetadata("precision_active", new FixedMetadataValue(plugin, level));
            player.sendMessage(ChatColor.GRAY + "Precision Activated!");
        });

        // ==========================================
        // SPECIFIC EXECUTIONS (From ExecutionSystem)
        // ==========================================

        skillRegistry.register("magic_fireball", 0.0, null, (player, level) -> {
            double damage = 5.0 + (level * 2.0);
            Location center = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
            
            player.getWorld().spawnParticle(Particle.FLAME, center, 50, 2, 2, 2, 0.1);
            player.getWorld().spawnParticle(Particle.LAVA, center, 30, 2, 2, 2, 0.05);
            player.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
            
            for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    target.damage(damage);
                    target.setFireTicks(100);
                    double dx = target.getLocation().getX() - player.getLocation().getX();
                    double dz = target.getLocation().getZ() - player.getLocation().getZ();
                    double dist = Math.sqrt(dx*dx + dz*dz);
                    if (dist > 0) {
                        target.setVelocity(new Vector(dx/dist * 0.5, 0.3, dz/dist * 0.5));
                    }
                    player.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 20, 1, 1, 1, 0.1);
                }
            }
            
            player.sendMessage(ChatColor.RED + "Fireball! " + ChatColor.YELLOW + "Damage: " + damage);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_fireball", 3000);
        });

        skillRegistry.register("magic_frostbolt", 0.0, null, (player, level) -> {
            int slowTicks = 100 + (level * 10);
            Location center = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2));
            
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 40, 1.5, 1.5, 1.5, 0.05);
            player.getWorld().spawnParticle(Particle.CLOUD, center, 25, 1.5, 1.5, 1.5, 0.1);
            player.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 2.0f);
            
            for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, true, false));
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation(), 30, 1, 1, 1, 0.1);
                }
            }
            
            player.sendMessage(ChatColor.AQUA + "Frostbolt! " + ChatColor.YELLOW + "Slowing enemies...");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_frostbolt", 2000);
        });

        skillRegistry.register("magic_manashield", 0.0, null, (player, level) -> {
            int resistTicks = 200 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistTicks, 0, true, false));
            
            Location playerLoc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.ENCHANT, playerLoc, 50, 1, 1.5, 1, 0.1);
            player.getWorld().spawnParticle(Particle.END_ROD, playerLoc, 30, 1, 1.5, 1, 0.05);
            player.playSound(playerLoc, Sound.ITEM_SHIELD_BLOCK, 1.5f, 1.0f);
            
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Mana Shield! " + ChatColor.YELLOW + "Protected");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_manashield", 4000);
        });

        skillRegistry.register("magic_invisibility", 0.0, null, (player, level) -> {
            int invisTicks = 200 + (level * 5);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisTicks, 0, true, false));
            
            Location playerLoc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.SMOKE, playerLoc, 60, 1, 1, 1, 0.2);
            player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 40, 1, 1, 1, 0.15);
            player.playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
            
            player.sendMessage(ChatColor.DARK_GRAY + "Shadow Cloak! " + ChatColor.YELLOW + "Becoming invisible...");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_invisibility", 12000);
        });

        skillRegistry.register("agility_dodge", 0.0, null, (player, level) -> {
            Vector velocity = player.getVelocity().multiply(2.0).add(new Vector(0, 0.5, 0));
            player.setVelocity(velocity);
            
            Location playerLoc = player.getLocation();
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, playerLoc, 40, 1, 1, 1, 0.1);
            player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 25, 0.5, 0.5, 0.5, 0.2);
            player.playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            
            player.sendMessage(ChatColor.GREEN + "Dodge!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_dodge", 1500);
        });

        skillRegistry.register("combat_strength", 0.0, null, (player, level) -> {
            int strengthTicks = 150 + (level * 10);
            int amplifier = Math.max(0, (level - 1) / 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, strengthTicks, amplifier, true, false));
            
            Location playerLoc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, playerLoc, 35, 1, 1.5, 1, 0.1);
            player.getWorld().spawnParticle(Particle.EXPLOSION, playerLoc, 10, 0.5, 0.5, 0.5, 0.05);
            player.playSound(playerLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            
            player.sendMessage(ChatColor.DARK_RED + "Strength Surge!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("combat_strength", 2500);
        });
        
        skillRegistry.register("MAX_HEALTH", 0.0, null, (player, level) -> {
            double healthBoost = 2.0 * level; 
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20 + healthBoost);
            player.sendMessage(ChatColor.RED + "Max Health increased!");
        });

        skillRegistry.register("magic_meteor", 0.0, null, (player, level) -> {
            player.getWorld().strikeLightning(player.getLocation().add(0, 50, 0));
            player.getWorld().createExplosion(player.getLocation(), 5.0f + level);
            player.sendMessage(ChatColor.DARK_RED + "☄ Meteor!");
        });

        skillRegistry.register("magic_chain_lightning", 0.0, null, (player, level) -> {
            double damage = 10.0 + (level * 2);
            for (Entity entity : player.getNearbyEntities(10.0 + level, 10.0 + level, 10.0 + level)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    // PvP Check
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) {
                        continue;
                    }
                    target.damage(damage, player);
                }
            }
            player.sendMessage(ChatColor.YELLOW + "⚡ Chain Lightning!");
        });
    }

    private boolean isOre(Material m) {
        return m == Material.COAL_ORE || m == Material.IRON_ORE || m == Material.GOLD_ORE || 
               m == Material.DIAMOND_ORE || m == Material.REDSTONE_ORE || m == Material.LAPIS_ORE;
    }
    
    // Helper for 1.21+ versions
    private Entity getTargetEntity(Player player, int range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize().multiply(range);
        RayTraceResult ray = player.getWorld().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER);
        
        if (ray != null && ray.getHitEntity() != null && ray.getHitEntity() != player) {
            return ray.getHitEntity();
        }
        return null;
    }

    // --- Inner Registry Class ---
    private class SkillRegistry {
        private final StaminaSystem staminaSystem;
        private final Map<String, SkillData> skills = new HashMap<>();

        public SkillRegistry(StaminaSystem staminaSystem) {
            this.staminaSystem = staminaSystem;
        }

        public void register(String id, double staminaCost, String requirement, BiConsumer<Player, Integer> action) {
            skills.put(id, new SkillData(staminaCost, requirement, action));
        }

        public boolean hasSkill(String id) {
            return skills.containsKey(id);
        }

        public void execute(String id, Player player, int level) {
            SkillData data = skills.get(id);
            
            // Check Prerequisite
            if (data.requirement != null) {
                PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
                if (pd.getSkillLevel(data.requirement) <= 0) {
                    player.sendMessage(ChatColor.RED + "You need " + data.requirement + " to use this skill!");
                    return;
                }
            }

            // Check Stamina
            if (staminaSystem.useStaminaForSkill(player, data.cost)) {
                data.action.accept(player, level);
            }
        }

        private static class SkillData {
            final double cost;
            final String requirement;
            final BiConsumer<Player, Integer> action;

            SkillData(double cost, String requirement, BiConsumer<Player, Integer> action) {
                this.cost = cost;
                this.requirement = requirement;
                this.action = action;
            }
        }
    }
}
