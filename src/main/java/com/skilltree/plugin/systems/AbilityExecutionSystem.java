package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;

public class AbilityExecutionSystem implements Listener {
    
    private final SkillForgePlugin plugin;
    private final SkillRegistry skillRegistry;
    
    public AbilityExecutionSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.skillRegistry = plugin.getSkillRegistry();
    }
    
    // 1. Public method to trigger abilities from anywhere (Commands or Items)
    public void triggerAbility(Player player, String abilityId, boolean isLeftClick) {
        if (player == null || abilityId == null || abilityId.isBlank()) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        String normalizedId = abilityId.toLowerCase();
        String resolvedId = resolveRegistryAlias(normalizedId);
        String cooldownKey = data.getSkillLevel(normalizedId) > 0 ? normalizedId : resolvedId;
        int level = Math.max(data.getSkillLevel(normalizedId), data.getSkillLevel(resolvedId));
        
        if (requiresSelectedGamemode() && !data.hasSelectedGamemode()) {
            player.sendMessage(ChatColor.RED + "You haven't selected a class/gamemode yet!");
            return;
        }
        
        // Check if learned
        if (level <= 0) {
            player.sendMessage(ChatColor.RED + "Skill not learned");
            return;
        }

        // Check cooldown
        if (data.isAbilityOnCooldown(cooldownKey)) {
            long remaining = data.getAbilityCooldownRemaining(cooldownKey);
            player.sendMessage(ChatColor.RED + "Ability on cooldown for " + (remaining / 1000) + "s");
            return;
        }
        
        // Execute via registry if available, otherwise fallback to legacy ability switch
        if (skillRegistry != null && skillRegistry.hasSkill(resolvedId)) {
            skillRegistry.execute(resolvedId, player, level);
            applySpellcraftCooldownReduction(data, resolvedId);
            return;
        }

        executeAbility(player, data, cooldownKey, isLeftClick);
        applySpellcraftCooldownReduction(data, cooldownKey);
    }

    // 2. Keep the existing Shift-Click Hotbar functionality
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (requiresSelectedGamemode() && !data.hasSelectedGamemode()) return;
        
        int slot = player.getInventory().getHeldItemSlot() + 1;
        String abilityId = data.getAbilityAtSlot(slot);
        
        if (abilityId == null) return;
        
        boolean isLeftClick = event.getAction().toString().contains("LEFT");
        triggerAbility(player, abilityId, isLeftClick);
        event.setCancelled(true);
    }

    private boolean requiresSelectedGamemode() {
        return plugin.getConfig().getBoolean("skills.require-selected-gamemode", false);
    }

    private void applySpellcraftCooldownReduction(PlayerData data, String abilityId) {
        if (data == null || abilityId == null) return;
        long remaining = data.getAbilityCooldownRemaining(abilityId);
        if (remaining <= 0) return;

        int spellcraft = data.getSkillLevel("magic_spellcraft");
        int mageBrick = data.getSkillLevel("mastery_brick_mage");
        if (spellcraft <= 0 && mageBrick <= 0) return;

        double reduction = Math.min(0.60, spellcraft * 0.01 + mageBrick * 0.005);
        long reducedMillis = Math.max(250L, (long) Math.floor(remaining * (1.0 - reduction)));
        data.setAbilityCooldown(abilityId, reducedMillis);
    }
    
    private void executeAbility(Player player, PlayerData data, String abilityId, boolean isLeftClick) {
        switch(abilityId) {
            case "magic_fireball":
                executeFireball(player, data);
                break;
            case "magic_frostbolt":
                executeFrostbolt(player, data);
                break;
            case "magic_manashield":
                executeManaShield(player, data);
                break;
            case "magic_invisibility":
                executeInvisibility(player, data);
                break;
            case "agility_dodge":
                executeDodge(player, data);
                break;
            case "combat_strength":
                executeStrength(player, data);
                break;
            case "magic_meteor":
                executeMeteor(player, data);
                break;
            default:
                // Unknown active: treat as passive/utility refresh so binding never hard-fails.
                if (plugin.getSkillExecutionSystem() != null) {
                    plugin.getSkillExecutionSystem().applyAllSkills(player);
                }
                player.sendMessage(ChatColor.GRAY + "Passive skill refreshed: " + abilityId);
        }
    }

    private String resolveRegistryAlias(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return "";
        String normalized = abilityId.toLowerCase(Locale.ROOT);
        if (skillRegistry == null) return normalized;
        if (skillRegistry.hasSkill(normalized)) return normalized;

        String compact = normalized.replaceAll("[^a-z0-9]", "");
        for (String registered : skillRegistry.getRegisteredSkillIds()) {
            if (registered == null || registered.isBlank()) continue;
            String id = registered.toLowerCase(Locale.ROOT);
            if (id.equals(normalized)) return id;
            if (id.replaceAll("[^a-z0-9]", "").equals(compact)) return id;
        }
        return normalized;
    }
    
    // --- [Ability Logic Methods remain the same] ---
    
    private void executeWhirlwind(Player player, int level) {
        // Spin and damage nearby enemies
        double radius = 1.0 + level;
        double damage = 2.0 + (level * 0.5);
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)) {
                org.bukkit.entity.LivingEntity target = (org.bukkit.entity.LivingEntity) entity;
                target.damage(damage, player);
            }
        }
        
        player.sendMessage("§c⚔ Whirlwind!");
    }
    
    private void executeLastStand(Player player, int level) {
        // Grant temporary resistance
        int duration = 100 + (level * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1 + (level / 5), false, false));
        player.sendMessage("§c🛡 Last Stand!");
    }
    
    private void executeExecute(Player player, int level) {
        // High damage to low-health enemies
        double damage = 15.0 + (level * 2);
        double radius = 5.0;
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)) {
                org.bukkit.entity.LivingEntity target = (org.bukkit.entity.LivingEntity) entity;
                double maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                if (target.getHealth() < maxHealth * 0.4) {
                    target.damage(damage, player);
                }
            }
        }
        
        player.sendMessage("§c⚡ Execute!");
    }
    
    private void executeShadowstep(Player player, int level) {
        // Teleport behind target
        Entity target = null;
        double range = 10 + level;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)) {
                target = entity;
                break;
            }
        }
        if (target != null) {
            player.teleport(target.getLocation().add(target.getLocation().multiply(-2)));
            player.sendMessage("§5👤 Shadowstep!");
        } else {
            player.sendMessage("§cNo target!");
        }
    }
    
    private void executeBlink(Player player, int level) {
        // Quick teleport forward
        player.teleport(player.getLocation().add(player.getLocation().multiply(5 + level)));
        player.sendMessage("§d✨ Blink!");
    }
    
    private void executeMindblast(Player player, int level) {
        // Damage and knockback enemies
        double damage = 8.0 + (level * 1.5);
        double radius = 6.0 + level;
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)) {
                org.bukkit.entity.LivingEntity target = (org.bukkit.entity.LivingEntity) entity;
                target.damage(damage, player);
                target.setVelocity(target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5));
            }
        }
        
        player.sendMessage("§9🧠 Mindblast!");
    }
    
    private void executeMindshield(Player player, int level) {
        // Reflect damage
        int duration = 80 + (level * 15);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, level / 5, false, false));
        player.sendMessage("§9🛡 Mindshield!");
    }
    
    private void executeAmplify(Player player, int level) {
        // Increase outgoing damage
        int duration = 60 + (level * 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, level / 10, false, false));
        player.sendMessage("§9⚡ Amplify!");
    }
    
    private void executeBountyHarvest(Player player, int level) {
        player.sendMessage("§2🌾 Bountyharvest - Not yet implemented");
    }
    
    private void executeDeepTreasure(Player player, int level) {
        player.sendMessage("§b🎣 Deep Treasure - Not yet implemented");
    }
    
    private void executeExplosive(Player player, int level) {
        player.getWorld().createExplosion(player.getLocation(), 3.0f + level);
        player.sendMessage("§c💥 Explosive!");
    }
    
    private void executeFireball(Player player, int level) {
        player.launchProjectile(org.bukkit.entity.Fireball.class);
        player.sendMessage("§c🔥 Fireball!");
    }
    
    private void executeFrostbolt(Player player, int level) {
        org.bukkit.entity.Snowball snowball = player.launchProjectile(org.bukkit.entity.Snowball.class);
        snowball.setVelocity(snowball.getVelocity().multiply(1.5 + (level * 0.1)));
        player.sendMessage("§b❄ Frostbolt!");
    }
    
    private void executeManaShield(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100 + (level * 20), level / 5, false, false));
        player.sendMessage("§e🔮 Mana Shield!");
    }
    
    private void executeInvisibility(Player player, int level) {
        int duration = 100 + (level * 15);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, false, false));
        player.sendMessage("§7👻 Invisibility!");
    }
    
    private void executeMeteor(Player player, int level) {
        player.getWorld().strikeLightning(player.getLocation().add(0, 50, 0));
        player.getWorld().createExplosion(player.getLocation(), 5.0f + level);
        player.sendMessage("§4☄ Meteor!");
    }
    
    private void executeChainLightning(Player player, int level) {
        double damage = 10.0 + (level * 2);
        for (Entity entity : player.getNearbyEntities(10.0 + level, 10.0 + level, 10.0 + level)) {
            if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)) {
                ((org.bukkit.entity.LivingEntity) entity).damage(damage, player);
            }
        }
        player.sendMessage("§e⚡ Chain Lightning!");
    }
    
    private void executeChainLightning(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_chain_lightning");
        executeChainLightning(player, level);
    }
    
    private void executeMeteor(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_meteor");
        executeMeteor(player, level);
    }
  
    private void executeFireball(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_fireball");
        double damage = 5.0 + (level * 2.0);
        Location center = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
        
        player.getWorld().spawnParticle(Particle.FLAME, center, 50, 2, 2, 2, 0.1);
        player.getWorld().spawnParticle(Particle.LAVA, center, 30, 2, 2, 2, 0.05);
        player.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
        
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity target = (LivingEntity) entity;
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
        data.setAbilityCooldown("magic_fireball", 3000);
    }
    
    private void executeFrostbolt(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_frostbolt");
        int slowTicks = 100 + (level * 10);
        Location center = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2));
        
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 40, 1.5, 1.5, 1.5, 0.05);
        player.getWorld().spawnParticle(Particle.CLOUD, center, 25, 1.5, 1.5, 1.5, 0.1);
        player.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 2.0f);
        
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity target = (LivingEntity) entity;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, true, false));
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation(), 30, 1, 1, 1, 0.1);
            }
        }
        
        player.sendMessage(ChatColor.AQUA + "Frostbolt! " + ChatColor.YELLOW + "Slowing enemies...");
        data.setAbilityCooldown("magic_frostbolt", 2000);
    }
    
    private void executeManaShield(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_manashield");
        int resistTicks = 200 + (level * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistTicks, 0, true, false));
        
        Location playerLoc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, playerLoc, 50, 1, 1.5, 1, 0.1);
        player.getWorld().spawnParticle(Particle.END_ROD, playerLoc, 30, 1, 1.5, 1, 0.05);
        player.playSound(playerLoc, Sound.ITEM_SHIELD_BLOCK, 1.5f, 1.0f);
        
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Mana Shield! " + ChatColor.YELLOW + "Protected");
        data.setAbilityCooldown("magic_manashield", 4000);
    }private void executeMaxHealth(Player player, PlayerData data) {
    int level = data.getSkillLevel("MAX_HEALTH");
    double healthBoost = 2.0 * level; 
    
    // Using Attribute to modify max health
    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20 + healthBoost);
    
    player.sendMessage(ChatColor.RED + "Max Health increased!");
    data.setAbilityCooldown("MAX_HEALTH", 0); // Passive skills usually have no cooldown
}

    
    @SuppressWarnings("unused")
	private void executeDodge(Player player, PlayerData data) {
        int level = data.getSkillLevel("agility_dodge");
        Vector velocity = player.getVelocity().multiply(2.0).add(new Vector(0, 0.5, 0));
        player.setVelocity(velocity);
        
        Location playerLoc = player.getLocation();
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, playerLoc, 40, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 25, 0.5, 0.5, 0.5, 0.2);
        player.playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
        
        player.sendMessage(ChatColor.GREEN + "Dodge!");
        data.setAbilityCooldown("agility_dodge", 1500);
    }
    
    private void executeStrength(Player player, PlayerData data) {
        int level = data.getSkillLevel("combat_strength");
        int strengthTicks = 150 + (level * 10);
        int amplifier = Math.max(0, (level - 1) / 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, strengthTicks, amplifier, true, false));
        
        Location playerLoc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, playerLoc, 35, 1, 1.5, 1, 0.1);
        player.getWorld().spawnParticle(Particle.EXPLOSION, playerLoc, 10, 0.5, 0.5, 0.5, 0.05);
        player.playSound(playerLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        
        player.sendMessage(ChatColor.DARK_RED + "Strength Surge!");
        data.setAbilityCooldown("combat_strength", 2500);
    }
    
    private void executeInvisibility(Player player, PlayerData data) {
        int level = data.getSkillLevel("magic_invisibility");
        int invisTicks = 200 + (level * 5);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisTicks, 0, true, false));
        
        Location playerLoc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, playerLoc, 60, 1, 1, 1, 0.2);
        player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 40, 1, 1, 1, 0.15);
        player.playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        
        player.sendMessage(ChatColor.DARK_GRAY + "Shadow Cloak! " + ChatColor.YELLOW + "Becoming invisible...");
        data.setAbilityCooldown("magic_invisibility", 12000);
    }
}
