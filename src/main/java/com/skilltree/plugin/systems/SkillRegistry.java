package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.utils.ProtectionUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import com.skilltree.plugin.systems.StaminaSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;

public class SkillRegistry {

    private final SkillForgePlugin plugin;
    private final StaminaSystem staminaSystem;
    private final Map<String, SkillData> skillActions = new HashMap<>();

    public SkillRegistry(SkillForgePlugin plugin, StaminaSystem staminaSystem) {
        this.plugin = plugin;
        this.staminaSystem = staminaSystem;
    }

    public void registerSkill(String id, double staminaCost, String requirement, BiConsumer<Player, Integer> action) {
        skillActions.put(id, new SkillData(staminaCost, requirement, action));
    }

    public boolean hasSkill(String id) {
        return id != null && skillActions.containsKey(id);
    }

    public java.util.Set<String> getRegisteredSkillIds() {
        return new java.util.HashSet<>(skillActions.keySet());
    }

    public void execute(String id, Player player, int level) {
        SkillData data = skillActions.get(id);
        if (data == null) return;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);

        // 1. Check Prerequisite
        if (data.requirement != null) {
            if (pd.getSkillLevel(data.requirement) <= 0) {
                player.sendMessage(ChatColor.RED + "You need " + data.requirement + " to use this skill!");
                return;
            }
        }

        // 2. Check Cooldown
        if (pd.isAbilityOnCooldown(id)) {
            long remaining = pd.getAbilityCooldownRemaining(id);
            player.sendMessage(ChatColor.RED + "Ability on cooldown for " + (remaining / 1000) + "s");
            return;
        }

        // 3. Check Stamina
        if (!staminaSystem.useStaminaForSkill(player, data.cost)) {
            player.sendMessage(ChatColor.RED + "Not enough stamina!");
            return;
        }

        // 4. Execute Action
        try {
            data.action.accept(player, level);
        } catch (Throwable t) {
            plugin.getLogger().warning("Skill execution failed for '" + id + "' (" + player.getName() + "): " + t.getMessage());
            player.sendMessage(ChatColor.RED + "That skill failed to execute. Please try again.");
        }
    }

    /**
     * Executes a skill action directly for innate usage.
     * This intentionally bypasses prerequisite, stamina, and ability cooldown checks because
     * innate skills have their own progression/cooldown path.
     */
    public boolean executeInnate(String id, Player player, int level) {
        SkillData data = skillActions.get(id);
        if (data == null) return false;
        try {
            data.action.accept(player, level);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Innate skill execution failed for '" + id + "' (" + player.getName() + "): " + t.getMessage());
            player.sendMessage(ChatColor.RED + "That innate skill failed to execute.");
            return false;
        }
    }

    public void loadDefaultSkills() {

        // --- COMBAT (16 SKILLS) ---
        registerSkill("combat_whirlwind", 10.0, null, (player, level) -> {
            double radius = 0.2 + level;
            double damage = 0.2 + (level * 0.5);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    ((LivingEntity) entity).damage(damage, player);
                }
            }
            player.sendMessage(ChatColor.RED + "⚔ Whirlwind!");
        });
        registerSkill("combat_laststand", 0.0, null, (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1 + (level / 5), false, false));
            player.sendMessage(ChatColor.RED + "🛡 Last Stand!");
        });
        registerSkill("combat_execute", 15.0, null, (player, level) -> {
            double damage = 15.0 + (level * 2);
            double radius = 5.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    LivingEntity target = (LivingEntity) entity;
                    double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
                    if (target.getHealth() < maxHealth * 0.4) target.damage(damage, player);
                }
            }
            player.sendMessage(ChatColor.RED + "⚡ Execute!");
        });
        registerSkill("combat_shieldbash", 15.0, "combat_laststand", (player, level) -> {
            double damage = 4.0 + level;
            double radius = 3.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    LivingEntity target = (LivingEntity) entity;
                    target.damage(damage, player);
                    Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    target.setVelocity(dir.multiply(2.0));
                }
            }
            player.sendMessage(ChatColor.GOLD + "Shield Bash!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("combat_shieldbash", 2000);
        });
        registerSkill("combat_battlecry", 20.0, "combat_whirlwind", (player, level) -> {
            int duration = 200 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            player.sendMessage(ChatColor.BOLD + "BATTLE CRY!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("combat_battlecry", 5000);
        });
        registerSkill("combat_charge", 25.0, null, (player, level) -> {
            Vector dir = player.getLocation().getDirection();
            player.setVelocity(dir.multiply(1.5 + (level * 0.2)).setY(0.2));
            new BukkitRunnable() {
                int ticks = 0;
                public void run() {
                    ticks++;
                    if (ticks > 10 || player.isOnGround()) { this.cancel(); return; }
                    for (Entity entity : player.getNearbyEntities(1.5, 2, 1.5)) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                            ((LivingEntity) entity).damage(5.0 + level, player); this.cancel();
                        }
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            player.sendMessage(ChatColor.GRAY + "Charge!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("combat_charge", 3000);
        });
        registerSkill("berserker_bloodrage", 10.0, "combat_execute", (player, level) -> {
            double healthCost = 2.0 + level;
            if (player.getHealth() > healthCost) {
                player.damage(healthCost);
                int duration = 200 + (level * 20);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1 + (level/5)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.0f, 0.5f);
                player.sendMessage(ChatColor.RED + "BLOODRAGE!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("berserker_bloodrage", 10000);
            } else player.sendMessage(ChatColor.RED + "Not enough health to sacrifice!");
        });
        registerSkill("berserker_rage", 0.0, "berserker_bloodrage", (player, level) -> {
            player.sendMessage(ChatColor.DARK_RED + "Your rage builds as your health drops.");
        });
        registerSkill("berserker_shout", 25.0, "combat_battlecry", (player, level) -> {
            double radius = 8.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) {
                    ((Mob) entity).setTarget(null);
                    Vector dir = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(2.0);
                    ((LivingEntity)entity).setVelocity(dir);
                }
            }
            player.sendMessage(ChatColor.BOLD + "RAAAH!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("berserker_shout", 8000);
        });
        registerSkill("paladin_holylight", 30.0, "combat_laststand", (player, level) -> {
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
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("paladin_holylight", 15000);
        });
        registerSkill("paladin_divineshield", 50.0, "paladin_holylight", (player, level) -> {
            int duration = 40 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 10));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 5));
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            player.sendMessage(ChatColor.YELLOW + "DIVINE SHIELD!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("paladin_divineshield", 20000);
        });
        registerSkill("paladin_hammer", 20.0, "combat_shieldbash", (player, level) -> {
            Entity target = getTargetEntity(player, 15);
            if (target instanceof LivingEntity && !(target instanceof Player)) {
                LivingEntity le = (LivingEntity) target;
                le.damage(5.0 + level, player);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + (level * 10), 10));
                le.getWorld().spawnParticle(Particle.FLASH, le.getLocation(), 1);
                player.sendMessage(ChatColor.GOLD + "Hammer of Justice!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("paladin_hammer", 5000);
            } else player.sendMessage(ChatColor.RED + "No valid target!");
        });
        registerSkill("defender_taunt", 15.0, "combat_shieldbash", (player, level) -> {
            double radius = 10.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) ((Mob) entity).setTarget(player);
            }
            player.sendMessage(ChatColor.RED + "TAUNT!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("defender_taunt", 8000);
        });
        registerSkill("defender_shieldwall", 30.0, "defender_taunt", (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 4));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 4));
            player.sendMessage(ChatColor.GRAY + "Shield Wall!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("defender_shieldwall", 25000);
        });
        registerSkill("defender_lastgasp", 0.0, "combat_laststand", (player, level) -> {
            player.sendMessage(ChatColor.GOLD + "Your determination to protect others strengthens.");
        });
        
        // --- DUELIST ---
        registerSkill("duelist_parry", 10.0, null, (player, level) -> {
            // 1. Play Explosion Sound
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            
            // 2. Deal Knockback to nearby Hostile Mobs
            double knockbackStrength = 2.0 + (level * 0.5);
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    // Vector from player to mob
                    Vector direction = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    
                    // Check if mob is hostile (Player or Tameable)
                    boolean isHostile = (mob instanceof Player) || (mob instanceof Tameable && ((Tameable) mob).getOwner() != null);
                    
                    if (isHostile) {
                        // Apply knockback
                        mob.setVelocity(direction.multiply(knockbackStrength));
                    }
                }
            }
            
            player.sendMessage(ChatColor.GRAY + "Parry Stance!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("duelist_parry", 3000);
        });
        registerSkill("duelist_riposte", 15.0, "duelist_parry", (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
            player.sendMessage(ChatColor.GRAY + "Riposte!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("duelist_riposte", 5000);
        });
        registerSkill("duelist_precision", 0.0, "duelist_riposte", (player, level) -> {
            player.setMetadata("precision_active", new FixedMetadataValue(plugin, level));
            player.sendMessage(ChatColor.GRAY + "Precision Activated!");
        });

        // --- AGILITY (15 SKILLS) ---
        registerSkill("agility_shadowstep", 20.0, null, (player, level) -> {
            Entity target = null;
            double range = 10 + level;
            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    target = entity; break;
                }
            }
            if (target != null) {
                Vector dir = target.getLocation().getDirection().setY(0).normalize().multiply(-1);
                Location loc = target.getLocation().add(dir);
                if (ProtectionUtils.isProtectedArea(player, loc)) { player.sendMessage(ChatColor.RED + "You cannot teleport there!"); return; }
                player.teleport(loc);
                player.sendMessage(ChatColor.DARK_PURPLE + "👤 Shadowstep!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_shadowstep", 8000000);
            } else player.sendMessage(ChatColor.RED + "No target!");
        });
        registerSkill("agility_adrenaline", 25.0, null, (player, level) -> {
            int duration = 2 + (level * 5);
            double radius = 5.0;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 10, false, false));
                    mob.setAI(false);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (mob.isValid()) mob.setAI(true); }, duration);
                }
            }
            player.sendMessage(ChatColor.AQUA + "⏱ Adrenaline Rush!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_adrenaline", 10000);
        });
        registerSkill("agility_dash", 15.0, null, (player, level) -> {
            Vector dir = player.getLocation().getDirection().setY(0);
            player.setVelocity(dir.multiply(1.5 + (level * 0.2)));
            player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 10);
            player.sendMessage(ChatColor.WHITE + "Dash!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_dash", 3000);
        });
        registerSkill("agility_evasion", 30.0, "agility_adrenaline", (player, level) -> {
            int duration = 100 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
            player.sendMessage(ChatColor.GRAY + "Evasion Mode!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_evasion", 15000);
        });
        registerSkill("agility_smokebomb", 25.0, "agility_shadowstep", (player, level) -> {
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
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_smokebomb", 12000);
        });
        registerSkill("agility_dodge", 0.0, null, (player, level) -> {
            Vector velocity = player.getVelocity().multiply(2.0).add(new Vector(0, 0.5, 0));
            player.setVelocity(velocity);
            Location playerLoc = player.getLocation();
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, playerLoc, 40, 1, 1, 1, 0.1);
            player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 25, 0.5, 0.5, 0.5, 0.2);
            player.playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            player.sendMessage(ChatColor.GREEN + "Dodge!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_dodge", 1500);
        });
        registerSkill("agility_climb", 5.0, null, (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 2 + level, false, false));
            player.sendMessage(ChatColor.YELLOW + "Climb!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_climb", 5000);
        });
        registerSkill("agility_parkour", 10.0, null, (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1 + level / 5, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 2, false, false));
            player.sendMessage(ChatColor.AQUA + "Parkour!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_parkour", 10000);
        });
        registerSkill("agility_acrobatics", 15.0, null, (player, level) -> {
            player.setFallDistance(0); player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false));
            player.sendMessage(ChatColor.GRAY + "Acrobatics!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_acrobatics", 7000);
        });
        registerSkill("agility_grapple", 20.0, null, (player, level) -> {
            Block block = player.getTargetBlockExact(30);
            if (block != null && block.getType().isSolid()) {
                Vector dir = block.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                player.setVelocity(dir.multiply(1.5 + (level * 0.1)));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                player.sendMessage(ChatColor.DARK_GRAY + "Grapple!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_grapple", 8000);
            }
        });
        registerSkill("agility_stealth", 25.0, null, (player, level) -> {
            int duration = 150 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, false));
            player.sendMessage(ChatColor.DARK_GRAY + "Stealth Mode!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_stealth", 20000);
        });
        registerSkill("agility_mirrorimage", 35.0, "agility_stealth", (player, level) -> {
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation(), 20);
            for(int i = 0; i < 2 + level/5; i++) {
                ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation().add((new Random().nextDouble()-0.5)*2, 0, (new Random().nextDouble()-0.5)*2), EntityType.ARMOR_STAND);
                stand.setInvisible(true); stand.setInvulnerable(true); stand.setMarker(true); stand.setCustomNameVisible(false); stand.setCustomName("MirrorImage");
                new BukkitRunnable() { public void run() { if(stand.isValid()) stand.remove(); } }.runTaskLater(plugin, 60L);
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Mirror Image!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_mirrorimage", 30000);
        });
        registerSkill("agility_phantom_step", 50.0, "agility_shadowstep", (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, true, false));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
            player.sendMessage(ChatColor.DARK_PURPLE + "Phantom Step!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_phantom_step", 15000);
        });
        registerSkill("agility_whirlwind_slice", 60.0, "combat_whirlwind", (player, level) -> {
            double damage = 1.0 + level;
            for(Entity entity : player.getNearbyEntities(3, 3, 3)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    ((LivingEntity) entity).damage(damage, player);
                }
            }
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.0f);
            player.sendMessage(ChatColor.RED + "Whirlwind Slice!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("agility_whirlwind_slice", 12000);
        });

        // --- INTELLECT (15 SKILLS) ---
        registerSkill("intellect_frostbolt", 15.0, null, (player, level) -> {
            Entity target = getTargetEntity(player, 20);
            if (target instanceof LivingEntity && !(target instanceof Player)) {
                LivingEntity le = (LivingEntity) target;
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                player.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                player.sendMessage(ChatColor.BLUE + "Frostbolt!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("intellect_frostbolt", 10000);
            } else player.sendMessage(ChatColor.RED + "No valid target!");
        });

        // --- MINING (13 SKILLS) ---
        registerSkill("mining_veinminer", 25.0, null, (player, level) -> {
            int radius = 1 + level / 3;
            Block target = player.getTargetBlockExact(5);
            if (target != null && isOre(target.getType())) {
                Location loc = target.getLocation();
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Block b = loc.clone().add(x, y, z).getBlock();
                            if (b.getType() == target.getType()) b.breakNaturally();
                        }
                    }
                }
                player.sendMessage(ChatColor.GRAY + "Vein Mined!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("mining_veinminer", 8000);
            }
        });
        registerSkill("mining_seismic_strike", 35.0, null, (player, level) -> {
            if (ProtectionUtils.isProtectedArea(player, player.getLocation())) { player.sendMessage(ChatColor.RED + "Cannot use here!"); return; }
            Block target = player.getTargetBlockExact(15);
            if (target != null) {
                Location loc = target.getLocation();
                player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        for (int z = -2; z <= 2; z++) {
                            Block b = loc.clone().add(x, y, z).getBlock();
                            if (b.getType().isSolid()) b.breakNaturally();
                        }
                    }
                }
                player.sendMessage(ChatColor.DARK_GRAY + "Seismic Strike!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("mining_seismic_strike", 15000);
            }
        });
        registerSkill("mining_fortune_strike", 45.0, "mining_veinminer", (player, level) -> {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool != null && tool.getType().name().contains("PICKAXE")) {
                Block target = player.getTargetBlockExact(5);
                if (target != null && isOre(target.getType())) {
                    target.breakNaturally();
                    for (int i = 0; i < level; i++) target.breakNaturally();
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0.5, 0.5, 0.5), 10);
                    player.sendMessage(ChatColor.GOLD + "Fortune Strike!");
                    plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("mining_fortune_strike", 12000);
                }
            }
        });

        // --- ARCHERY (13 SKILLS) ---
        registerSkill("archery_multishot", 20.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            Vector dir = player.getLocation().getDirection();
            Arrow arrow1 = player.launchProjectile(Arrow.class); arrow1.setVelocity(dir.clone().rotateAroundY(Math.toRadians(10)).multiply(2.0));
            Arrow arrow2 = player.launchProjectile(Arrow.class); arrow2.setVelocity(dir.clone().rotateAroundY(Math.toRadians(-10)).multiply(2.0));
            player.sendMessage(ChatColor.GOLD + "Multishot!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_multishot", 5000);
        });
        registerSkill("archery_venomous_arrow", 30.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 300, level));
            player.sendMessage(ChatColor.DARK_GREEN + "Arrows poisoned!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_venomous_arrow", 10000);
        });
        registerSkill("archery_ice_arrow", 35.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, level));
            player.sendMessage(ChatColor.BLUE + "Arrows chilled!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_ice_arrow", 10000);
        });
        registerSkill("archery_fire_arrow", 35.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, level / 5, false, false));
            player.sendMessage(ChatColor.RED + "Arrows ignited!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_fire_arrow", 10000);
        });
        registerSkill("archery_volley", 40.0, "archery_multishot", (player, level) -> {
            Location loc = player.getTargetBlockExact(50).getLocation().add(0, 10, 0);
            int arrows = 10 + (level * 2);
            player.sendMessage(ChatColor.GREEN + "Volley!");
            new BukkitRunnable() {
                int shot = 0;
                public void run() {
                    if (shot >= arrows) { this.cancel(); return; }
                    Arrow arrow = (Arrow) player.getWorld().spawnEntity(loc, EntityType.ARROW);
                    arrow.setShooter(player); arrow.setDamage(2.0 + level);
                    arrow.setVelocity(new Vector(0, -1.5, 0).add(new Vector((Math.random()-0.5), 0, (Math.random()-0.5))));
                    shot++;
                }
            }.runTaskTimer(plugin, 0L, 2L);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_volley", 15000);
        });
        registerSkill("archery_piercing_shot", 45.0, "archery_multishot", (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be drives a bow!"); return; }
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setPierceLevel((byte) (1 + level / 5));
            player.sendMessage(ChatColor.GRAY + "Piercing Shot!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_piercing_shot", 8000);
        });
        registerSkill("archery_kinetic_shot", 50.0, null, (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setMetadata("kinetic_arrow", new FixedMetadataValue(plugin, level));
            player.sendMessage(ChatColor.YELLOW + "Kinetic Shot Ready!");
        });
        registerSkill("archery_rain_of_arrows", 60.0, "archery_volley", (player, level) -> {
            if (player.getInventory().getItemInMainHand().getType() != Material.BOW) { player.sendMessage(ChatColor.RED + "Must be holding a bow!"); return; }
            Location target = player.getTargetBlockExact(30).getLocation();
            player.getWorld().playSound(target, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
            for(int i = 0; i < 20 + level * 2; i++) {
                Location loc = target.clone().add((new Random().nextDouble()-0.5)*10, 20, (new Random().nextDouble()-0.5)*10);
                Arrow arrow = (Arrow) player.getWorld().spawnEntity(loc, EntityType.ARROW);
                arrow.setShooter(player); arrow.setVelocity(loc.toVector().subtract(target.toVector()).normalize().multiply(1.5));
            }
            player.sendMessage(ChatColor.GOLD + "Rain of Arrows!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("archery_rain_of_arrows", 30000);
        });

        // --- NATURE / HUNTER (13 SKILLS) ---
        registerSkill("nature_root", 20.0, null, (player, level) -> {
            double radius = 5.0 + level;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity le = (LivingEntity) entity;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + (level * 10), 10));
                    player.getWorld().playEffect(le.getLocation(), Effect.STEP_SOUND, Material.OAK_LOG);
                }
            }
            player.sendMessage(ChatColor.GREEN + "Entangling Roots!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("nature_root", 10000);
        });
        registerSkill("nature_regrowth", 30.0, "nature_root", (player, level) -> {
            double healAmount = 4.0 + (level * 2);
            player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + healAmount));
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation(), 10);
            player.sendMessage(ChatColor.DARK_GREEN + "Nature's Grasp Heals You!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("nature_regrowth", 15000);
        });
        registerSkill("summon_wolf", 40.0, null, (player, level) -> {
            Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
            wolf.setTamed(true); wolf.setOwner(player);
            wolf.setCustomName(ChatColor.GREEN + player.getName() + "'s Wolf");
            wolf.setCustomNameVisible(true);
            wolf.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0 + (level * 10));
            wolf.setHealth(wolf.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.sendMessage(ChatColor.GREEN + "Howl!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("summon_wolf", 20000);
        });
        registerSkill("summon_beastrage", 20.0, "summon_wolf", (player, level) -> {
            for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                if (entity instanceof Tameable && ((Tameable) entity).getOwner().equals(player)) {
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
                }
            }
            player.sendMessage(ChatColor.GREEN + "Beast Rage!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("summon_beastrage", 10000);
        });
        registerSkill("hunter_mark", 25.0, null, (player, level) -> {
            Entity target = getTargetEntity(player, 30);
            if (target instanceof LivingEntity && !(target instanceof Player)) {
                LivingEntity le = (LivingEntity) target;
                le.setMetadata("hunter_mark", new FixedMetadataValue(plugin, level));
                player.getWorld().spawnParticle(Particle.CRIT, le.getLocation(), 10);
                player.sendMessage(ChatColor.RED + "Marked!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("hunter_mark", 8000);
            }
        });
        registerSkill("hunter_bloodhound", 35.0, "hunter_mark", (player, level) -> {
            int duration = 300 + (level * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
            player.sendMessage(ChatColor.GRAY + "Bloodhound Scent Active!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("hunter_bloodhound", 20000);
        });
        registerSkill("nature_call_lightning", 45.0, "nature_regrowth", (player, level) -> {
            double radius = 10.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    ((LivingEntity) entity).damage(5.0 + level);
                    entity.getWorld().strikeLightningEffect(entity.getLocation());
                }
            }
            player.sendMessage(ChatColor.YELLOW + "Lightning Called!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("nature_call_lightning", 25000);
        });
        registerSkill("summon_bear", 50.0, "summon_wolf", (player, level) -> {
            PolarBear bear = (PolarBear) player.getWorld().spawnEntity(player.getLocation(), EntityType.POLAR_BEAR);
            bear.setCustomName(ChatColor.GOLD + player.getName() + "'s Bear");
            bear.setCustomNameVisible(true);
            bear.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0 + (level * 15));
            player.sendMessage(ChatColor.GOLD + "Bear Summoned!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("summon_bear", 30000);
        });

        registerSkill("nature_earthquake", 60.0, "nature_call_lightning", (player, level) -> {
            if (ProtectionUtils.isProtectedArea(player, player.getLocation())) { player.sendMessage(ChatColor.RED + "Cannot use here!"); return; }
            double radius = 15.0 + level;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            for(Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    if (entity instanceof Player && ProtectionUtils.isProtectedArea(player, entity.getLocation())) continue;
                    ((LivingEntity)entity).damage(3.0 + level);
                    Vector knock = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.0);
                    ((LivingEntity)entity).setVelocity(knock);
                }
            }
            player.sendMessage(ChatColor.DARK_RED + "Earthquake!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("nature_earthquake", 40000);
        });

        // --- UTILITY / MISC (10 SKILLS) ---
        registerSkill("util_recall", 50.0, null, (player, level) -> {
            Location bed = player.getBedSpawnLocation();
            if (bed != null) {
                if (ProtectionUtils.isProtectedArea(player, bed)) { player.sendMessage(ChatColor.RED + "Cannot recall to a protected area!"); return; }
                player.teleport(bed);
                player.getWorld().playSound(bed, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.sendMessage(ChatColor.WHITE + "Recall!");
            } else player.sendMessage(ChatColor.RED + "No bed spawn set!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("util_recall", 30000);
        });
        registerSkill("util_safefall", 10.0, null, (player, level) -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100 + (level * 20), 0));
            player.sendMessage(ChatColor.GRAY + "Safe Fall activated.");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("util_safefall", 20000);
        });
        registerSkill("builder_scaffold", 5.0, null, (player, level) -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() > 0) {
                Block target = player.getTargetBlockExact(5);
                if (target != null) {
                    Location loc = target.getRelative(player.getFacing()).getLocation();
                    if (ProtectionUtils.isProtectedArea(player, loc)) { player.sendMessage(ChatColor.RED + "Cannot build here!"); return; }
                    loc.getBlock().setType(hand.getType()); hand.setAmount(hand.getAmount() - 1);
                }
            }
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("builder_scaffold", 1000);
        });
        registerSkill("builder_wall", 20.0, "builder_scaffold", (player, level) -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() > 0) {
                Location center = player.getTargetBlockExact(10).getLocation();
                if (ProtectionUtils.isProtectedArea(player, center)) { player.sendMessage(ChatColor.RED + "Cannot build here!"); return; }
                Vector dir = player.getLocation().getDirection().setY(0).normalize();
                Vector side = new Vector(-dir.getZ(), 0, dir.getX());
                for (int i = -2; i <= 2; i++) {
                    for (int y = 0; y < 3 + level; y++) {
                        Location loc = center.clone().add(side.clone().multiply(i)).add(0, y, 0);
                        if (hand.getAmount() > 0) { loc.getBlock().setType(hand.getType()); hand.setAmount(hand.getAmount() - 1); }
                    }
                }
            }
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("builder_wall", 5000);
        });
        registerSkill("chef_feast", 30.0, null, (player, level) -> {
            double radius = 10.0;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player) {
                    Player p = (Player) entity;
                    p.setFoodLevel(20); p.setSaturation(20);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                    p.sendMessage(ChatColor.GOLD + "Delicious!");
                }
            }
            player.sendMessage(ChatColor.GOLD + "Feast Served!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("chef_feast", 60000);
        });
        registerSkill("alchemy_brew", 20.0, "util_recall", (player, level) -> {
            PotionEffectType[] effects = {PotionEffectType.SPEED, PotionEffectType.REGENERATION, PotionEffectType.STRENGTH, PotionEffectType.JUMP_BOOST};
            PotionEffectType type = effects[new Random().nextInt(effects.length)];
            int duration = 200 + (level * 20);
            player.addPotionEffect(new PotionEffect(type, duration, 1));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Brewed " + type.getName() + "!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("alchemy_brew", 15000);
        });
        registerSkill("alchemy_transmute", 10.0, null, (player, level) -> {
            Block target = player.getTargetBlockExact(5);
            if (target != null && target.getType() == Material.COBBLESTONE) {
                if (ProtectionUtils.isProtectedArea(player, target.getLocation())) { player.sendMessage(ChatColor.RED + "Cannot transmute here!"); return; }
                double chance = 0.1 + (level * 0.05);
                if (Math.random() < chance) { target.setType(Material.IRON_ORE); player.sendMessage(ChatColor.GREEN + "Transmutation successful!"); }
                else { player.sendMessage(ChatColor.GRAY + "The stone crumbles..."); target.setType(Material.AIR); }
            }
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("alchemy_transmute", 5000);
        });
        registerSkill("fish_grapple", 15.0, null, (player, level) -> {
            Block block = player.getTargetBlockExact(30);
            if (block != null) {
                Vector dir = block.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                player.setVelocity(dir.multiply(1.5 + (level * 0.1)));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                player.sendMessage(ChatColor.DARK_GRAY + "Grapple!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("fish_grapple", 8000);
            }
        });
        registerSkill("fish_harpoon", 20.0, "fish_grapple", (player, level) -> {
            Entity target = getTargetEntity(player, 20);
            if (target instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) target;
                le.damage(5.0 + level, player);
                Vector pull = player.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(0.5);
                le.setVelocity(pull);
                player.sendMessage(ChatColor.BLUE + "Harpooned!");
                plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("fish_harpoon", 10000);
            }
        });
        registerSkill("bard_speed", 0.0, null, (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Speed.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("bard_speed", 5000);
        });
        registerSkill("bard_healing", 0.0, "bard_speed", (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Healing.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("bard_healing", 5000);
        });
        registerSkill("bard_resistance", 0.0, "bard_healing", (player, level) -> {
            player.sendMessage(ChatColor.YELLOW + "Playing Song of Resistance.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("bard_resistance", 5000);
        });
        registerSkill("bard_discord", 0.0, "bard_resistance", (player, level) -> {
            player.sendMessage(ChatColor.DARK_PURPLE + "Playing Discord.");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            for(Entity entity : player.getNearbyEntities(10,10,10)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity)entity).addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
                    ((LivingEntity)entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                }
            }
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("bard_discord", 15000);
        });

        // ==========================================
        // MAGIC / INTELLECT (Active)
        // ==========================================
        registerSkill("magic_meteor", 30.0, null, (player, level) -> {
            player.getWorld().strikeLightning(player.getLocation().add(0, 30, 0));
            player.getWorld().createExplosion(player.getLocation(), 3.5f + (level * 0.05f), false, false, player);
            player.sendMessage(ChatColor.DARK_RED + "☄ Meteor Strike!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_meteor", 12000);
        });

        registerSkill("magic_chainlightning", 25.0, null, (player, level) -> {
            double damage = 6.0 + (level * 0.5);
            double radius = 8.0 + (level * 0.1);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity) entity).damage(damage, player);
                    entity.getWorld().strikeLightningEffect(entity.getLocation());
                }
            }
            player.sendMessage(ChatColor.YELLOW + "⚡ Chain Lightning!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_chainlightning", 8000);
        });

        registerSkill("magic_fireball", 20.0, null, (player, level) -> {
            player.launchProjectile(Fireball.class);
            player.sendMessage(ChatColor.RED + "🔥 Fireball!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_fireball", 3000);
        });

        registerSkill("magic_frostbolt", 18.0, null, (player, level) -> {
            Snowball snowball = player.launchProjectile(Snowball.class);
            snowball.setVelocity(snowball.getVelocity().multiply(1.5 + (level * 0.05)));
            double radius = 6.0 + (level * 0.1);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + (level * 5), 1));
                }
            }
            player.sendMessage(ChatColor.AQUA + "❄ Frostbolt!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_frostbolt", 2000);
        });

        registerSkill("magic_manashield", 15.0, null, (player, level) -> {
            int duration = 120 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, true, false));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 Mana Shield!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_manashield", 4000);
        });

        registerSkill("magic_invisibility", 22.0, null, (player, level) -> {
            int duration = 100 + (level * 15);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, false, false));
            player.sendMessage(ChatColor.GRAY + "👻 Invisibility!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_invisibility", 12000);
        });

        registerSkill("magic_timefreeze", 28.0, null, (player, level) -> {
            double radius = 6.0 + (level * 0.1);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80 + (level * 5), 3));
                    ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80 + (level * 5), 1));
                }
            }
            player.sendMessage(ChatColor.DARK_AQUA + "⏳ Time Freeze!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("magic_timefreeze", 15000);
        });

        registerSkill("intellect_mindblast", 20.0, null, (player, level) -> {
            double damage = 5.0 + (level * 0.6);
            double radius = 6.0 + (level * 0.1);
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity target = (LivingEntity) entity;
                    target.damage(damage, player);
                    Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    target.setVelocity(dir.multiply(1.2));
                }
            }
            player.sendMessage(ChatColor.AQUA + "🧠 Mind Blast!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("intellect_mindblast", 6000);
        });

        registerSkill("intellect_mindshield", 15.0, null, (player, level) -> {
            int duration = 120 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, 0, true, false));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🛡 Mind Shield!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("intellect_mindshield", 9000);
        });

        registerSkill("intellect_amplify", 10.0, null, (player, level) -> {
            int duration = 100 + (level * 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, false));
            player.sendMessage(ChatColor.GOLD + "✨ Amplify!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("intellect_amplify", 7000);
        });

        registerSkill("farming_bountyharvest", 20.0, null, (player, level) -> {
            int radius = 3 + Math.min(4, level / 20);
            int harvested = 0;
            Location base = player.getLocation();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = base.getBlock().getRelative(x, y, z);
                        if (!(block.getBlockData() instanceof Ageable ageable)) continue;
                        if (ageable.getAge() < ageable.getMaximumAge()) continue;
                        Material cropType = block.getType();
                        block.breakNaturally();
                        block.setType(cropType);
                        if (block.getBlockData() instanceof Ageable replanted) {
                            replanted.setAge(0);
                            block.setBlockData(replanted);
                        }
                        harvested++;
                    }
                }
            }
            player.sendMessage(ChatColor.GREEN + "🌾 Bounty Harvest! Crops: " + harvested);
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("farming_bountyharvest", 12000);
        });

        registerSkill("fishing_deeptreasure", 15.0, null, (player, level) -> {
            Material[] loot = new Material[] {
                Material.NAUTILUS_SHELL, Material.HEART_OF_THE_SEA, Material.NAME_TAG,
                Material.ENCHANTED_BOOK, Material.PRISMARINE_CRYSTALS
            };
            Material reward = loot[new Random().nextInt(loot.length)];
            player.getInventory().addItem(new ItemStack(reward, 1));
            player.sendMessage(ChatColor.AQUA + "🎣 Deep Treasure!");
            plugin.getPlayerDataManager().getPlayerData(player).setAbilityCooldown("fishing_deeptreasure", 10000);
        });

        // Ensure all skills defined in SkillTreeSystem have an executable implementation.
        registerMissingSkillTreeSkills();
    }

    private void registerMissingSkillTreeSkills() {
        if (plugin.getSkillTreeSystem() == null) return;
        Map<String, SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getAllSkillNodes();
        for (SkillTreeSystem.SkillNode node : nodes.values()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) continue;
            if (skillActions.containsKey(node.getId())) continue;

            final SkillTreeSystem.SkillNode ref = node;
            registerSkill(ref.getId(), inferDefaultStaminaCost(ref), null, (player, level) -> {
                executeFallbackSkill(ref, player, level);
            });
        }
    }

    private double inferDefaultStaminaCost(SkillTreeSystem.SkillNode node) {
        String category = node.getCategory() == null ? "" : node.getCategory().toLowerCase();
        return switch (category) {
            case "combat", "magic", "mastery" -> 18.0;
            case "agility" -> 15.0;
            case "mining", "farming", "fishing", "intellect" -> 12.0;
            default -> 10.0;
        };
    }

    private void executeFallbackSkill(SkillTreeSystem.SkillNode node, Player player, int level) {
        String id = node.getId().toLowerCase();
        String category = node.getCategory() == null ? "" : node.getCategory().toLowerCase();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        // Dedicated implementations for missing tree-only active skills.
        switch (id) {
            case "agility_blink" -> {
                Location from = player.getLocation();
                Vector dir = from.getDirection().normalize();
                Location to = from.clone().add(dir.multiply(4.0 + Math.min(16.0, level * 0.08)));
                if (to.getBlock().getType().isSolid()) to.add(0, 1, 0);
                if (ProtectionUtils.isProtectedArea(player, to)) {
                    player.sendMessage(ChatColor.RED + "Cannot blink into a protected area.");
                    return;
                }
                player.teleport(to);
                player.getWorld().spawnParticle(Particle.PORTAL, from, 40, 0.3, 0.6, 0.3, 0.1);
                player.getWorld().spawnParticle(Particle.PORTAL, to, 40, 0.3, 0.6, 0.3, 0.1);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Blink!");
                data.setAbilityCooldown(id, 3500);
                return;
            }
            case "agility_timeshift" -> {
                int duration = 80 + (level * 4);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, Math.min(3, level / 30), true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, Math.min(2, level / 45), true, false));
                player.sendMessage(ChatColor.AQUA + "Time Shift!");
                data.setAbilityCooldown(id, 8000);
                return;
            }
            case "mining_explosive" -> {
                Location loc = player.getTargetBlockExact(20) != null
                        ? player.getTargetBlockExact(20).getLocation().add(0.5, 0.5, 0.5)
                        : player.getLocation();
                if (ProtectionUtils.isProtectedArea(player, loc)) {
                    player.sendMessage(ChatColor.RED + "Cannot use explosive mining here.");
                    return;
                }
                float power = (float) Math.min(2.0 + (level * 0.02), 5.0);
                player.getWorld().createExplosion(loc, power, false, false, player);
                player.sendMessage(ChatColor.RED + "Explosive Mining!");
                data.setAbilityCooldown(id, 12000);
                return;
            }
            case "mining_gemdiscovery" -> {
                Material[] gems = new Material[]{Material.DIAMOND, Material.EMERALD, Material.LAPIS_LAZULI, Material.REDSTONE};
                Material reward = gems[new Random().nextInt(gems.length)];
                int amount = Math.max(1, Math.min(6, 1 + (level / 25)));
                player.getInventory().addItem(new ItemStack(reward, amount));
                player.sendMessage(ChatColor.GREEN + "Gem Discovery: " + amount + "x " + reward.name());
                data.setAbilityCooldown(id, 10000);
                return;
            }
            case "farming_summongolem" -> {
                IronGolem golem = player.getWorld().spawn(player.getLocation(), IronGolem.class);
                golem.setPlayerCreated(true);
                golem.setCustomName(ChatColor.GREEN + player.getName() + "'s Farm Golem");
                golem.setCustomNameVisible(true);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (golem.isValid()) golem.remove();
                }, 20L * (20 + Math.min(40, level / 2)));
                player.sendMessage(ChatColor.GREEN + "Farm Golem summoned.");
                data.setAbilityCooldown(id, 30000);
                return;
            }
            case "fishing_summonwhale" -> {
                Dolphin dolphin = player.getWorld().spawn(player.getLocation(), Dolphin.class);
                dolphin.setCustomName(ChatColor.AQUA + player.getName() + "'s Tide Beast");
                dolphin.setCustomNameVisible(true);
                dolphin.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20 * (20 + Math.min(40, level / 2)), 0, false, false));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (dolphin.isValid()) dolphin.remove();
                }, 20L * (20 + Math.min(40, level / 2)));
                player.sendMessage(ChatColor.AQUA + "Tide Beast summoned.");
                data.setAbilityCooldown(id, 25000);
                return;
            }
            default -> {
            }
        }

        // Generic category implementations for remaining missing skills.
        switch (category) {
            case "combat" -> {
                int duration = 80 + (level * 3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.min(2, level / 30), true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, Math.min(1, level / 50), true, false));
                player.sendMessage(ChatColor.RED + node.getName() + " activated.");
                data.setAbilityCooldown(id, 6000);
            }
            case "agility" -> {
                int duration = 80 + (level * 3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, Math.min(2, level / 35), true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, Math.min(1, level / 45), true, false));
                player.sendMessage(ChatColor.GREEN + node.getName() + " activated.");
                data.setAbilityCooldown(id, 5500);
            }
            case "mining" -> {
                int duration = 120 + (level * 3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, Math.min(2, level / 35), true, false));
                player.sendMessage(ChatColor.GRAY + node.getName() + " activated.");
                data.setAbilityCooldown(id, 7000);
            }
            case "intellect" -> {
                int duration = 120 + (level * 3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, Math.min(1, level / 40), true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, true, false));
                player.giveExp(Math.max(1, level / 8));
                player.sendMessage(ChatColor.BLUE + node.getName() + " activated.");
                data.setAbilityCooldown(id, 7000);
            }
            case "farming" -> {
                int radius = 2 + Math.min(3, level / 30);
                Location base = player.getLocation();
                int grown = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Block block = base.getBlock().getRelative(x, y, z);
                            if (!(block.getBlockData() instanceof Ageable age)) continue;
                            if (age.getAge() >= age.getMaximumAge()) continue;
                            age.setAge(Math.min(age.getMaximumAge(), age.getAge() + 1 + (level / 50)));
                            block.setBlockData(age);
                            grown++;
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + node.getName() + " growth pulse: " + grown + " crops.");
                data.setAbilityCooldown(id, 9000);
            }
            case "fishing" -> {
                Material[] fish = new Material[]{Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};
                Material reward = fish[new Random().nextInt(fish.length)];
                int amount = Math.max(1, Math.min(5, 1 + (level / 30)));
                player.getInventory().addItem(new ItemStack(reward, amount));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 80 + (level * 2), 0, true, false));
                player.sendMessage(ChatColor.AQUA + node.getName() + " caught " + amount + "x " + reward.name() + ".");
                data.setAbilityCooldown(id, 7000);
            }
            case "magic" -> {
                int duration = 100 + (level * 3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.min(1, level / 45), true, false));
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.1);
                player.sendMessage(ChatColor.LIGHT_PURPLE + node.getName() + " channeled.");
                data.setAbilityCooldown(id, 6500);
            }
            case "mastery" -> {
                int duration = 120 + (level * 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.min(1, level / 40), true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, Math.min(1, level / 60), true, false));
                player.sendMessage(ChatColor.GOLD + node.getName() + " stance active.");
                data.setAbilityCooldown(id, 8000);
            }
            default -> {
                player.sendMessage(ChatColor.YELLOW + node.getName() + " activated.");
                data.setAbilityCooldown(id, 5000);
            }
        }
    }

    private boolean isOre(Material m) {
        return m == Material.COAL_ORE || m == Material.IRON_ORE || m == Material.GOLD_ORE ||
               m == Material.DIAMOND_ORE || m == Material.REDSTONE_ORE || m == Material.LAPIS_ORE ||
               m == Material.EMERALD_ORE || m == Material.NETHER_QUARTZ_ORE || m == Material.NETHER_GOLD_ORE;
    }

    private Entity getTargetEntity(Player player, int range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult ray = player.getWorld().rayTraceEntities(
                eye,
                direction,
                range,
                0.35,
                entity -> entity != player && entity instanceof LivingEntity
        );
        if (ray != null && ray.getHitEntity() != null && ray.getHitEntity() != player) {
            return ray.getHitEntity();
        }
        return null;
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
