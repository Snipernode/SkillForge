package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class InnateSkillSystem {
    
    private final SkillForgePlugin plugin;
    private final Map<String, String> rollRanges;
    private final List<String> innateSkillPool;
    
    public InnateSkillSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.rollRanges = new HashMap<>();
        this.innateSkillPool = new ArrayList<>();
        initializeRollRanges();
    }
    
    private void initializeRollRanges() {
        // 1. COMBAT (16 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "combat_whirlwind", "combat_laststand", "combat_execute", "combat_shieldbash", "combat_battlecry", "combat_charge",
            "berserker_bloodrage", "berserker_rage", "berserker_shout",
            "paladin_holylight", "paladin_divineshield", "paladin_hammer",
            "defender_taunt", "defender_shieldwall", "defender_lastgasp",
            "duelist_parry", "duelist_riposte", "duelist_precision",
            // SECRET COMBAT SKILLS
            "combat_bladestorm", "combat_rampage", "combat_titansblood", "combat_guardianangel", "combat_furyofthegods", "combat_finalstand"
        ));

        // 2. AGILITY (15 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "agility_shadowstep", "agility_adrenaline", "agility_dash", "agility_evasion", "agility_smokebomb",
            "agility_dodge", "agility_climb", "agility_parkour", "agility_acrobatics", "agility_grapple",
            "agility_stealth", "agility_mirrorimage", "agility_phantom_step", "agility_whirlwind_slice",
            // SECRET AGILITY SKILLS
            "agility_windwalk", "agility_assassinsstep", "agility_timeskip", "agility_mistform", "agility_overdrive"
        ));

        // 3. INTELLECT (15 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "intellect_frostbolt", "intellect_mindblast", "intellect_mindshield", "intellect_amplify",
            "intellect_mending", "intellect_aura", "intellect_arcana", "intellect_alchemy", "intellect_scholarship",
            // SECRET INTELLECT SKILLS
            "intellect_mindcontrol", "intellect_levitation", "intellect_mindstorm", "intellect_timewarp", "intellect_realityshift"
        ));

        // 4. MINING (13 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "mining_veinminer", "mining_seismic_strike", "mining_fortune_strike", "mining_laser_drill",
            "mining_explosive", "mining_gemdiscovery", "mining_haste", "mining_treasure_sense",
            // SECRET MINING SKILLS
            "mining_earthshatter", "mining_lavaburst", "mining_diamondtouch", "mining_deepcore", "mining_autosmelt"
        ));

        // 5. ARCHERY (13 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "archery_multishot", "archery_venomous_arrow", "archery_ice_arrow", "archery_fire_arrow",
            "archery_volley", "archery_piercing_shot", "archery_kinetic_shot", "archery_rain_of_arrows",
            // SECRET ARCHERY SKILLS
            "archery_sniper", "archery_barrage", "archery_explosivearrow", "archery_guidedarrow", "archery_windrunner"
        ));

        // 6. NATURE/HUNTER (13 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "nature_root", "nature_regrowth", "nature_call_lightning", "nature_earthquake",
            "summon_wolf", "summon_bear", "summon_beastrage", "hunter_mark", "hunter_bloodhound",
            // SECRET NATURE SKILLS
            "nature_natureswrath", "nature_wildcall", "nature_ancientprotection", "nature_beastmaster", "nature_forestguard"
        ));

        // 7. FARMING (10 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "farming_growth", "farming_harvest", "farming_breeding", "farming_pestcontrol", "farming_irrigation",
            "farming_bountyharvest", "farming_summongolem",
            // SECRET FARMING SKILLS
            "farming_superharvest", "farming_earthblessing", "farming_goldencrops", "farming_animalwhisper", "farming_seasonsblessing"
        ));

        // 8. FISHING (10 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "fishing_luck", "fishing_speed", "fishing_rare", "fishing_underwater",
            "fishing_deeptreasure", "fishing_summonwhale", "fishing_grapple", "fishing_harpoon",
            // SECRET FISHING SKILLS
            "fishing_megajaw", "fishing_abyssalhook", "fishing_tsunami", "fishing_davyjones", "fishing_leviathan"
        ));

        // 9. MAGIC (14 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "magic_fireball", "magic_frostbolt", "magic_manashield", "magic_spellcraft", "magic_manapool",
            "magic_invisibility", "magic_meteor", "magic_timefreeze", "magic_chainlightning",
            // SECRET MAGIC SKILLS
            "magic_arcaneblast", "magic_frostnova", "magic_lichform", "magic_pyroblast", "magic_thunderstorm"
        ));

        // 10. UTILITY (10 Skills) - Added 5 Secret Skills
        innateSkillPool.addAll(Arrays.asList(
            "util_recall", "util_safefall", "builder_scaffold", "builder_wall", "chef_feast",
            "alchemy_brew", "alchemy_transmute", "bard_speed", "bard_healing", "bard_resistance", "bard_discord",
            // SECRET UTILITY SKILLS
            "util_teleport", "util_godmode", "util_craftingtable", "util_superheal", "util_stoneaura", "util_timereverse"
        ));

        // 11. SPECIAL (1 Skill)
        innateSkillPool.add("jack_of_none");

        // Map rolls 1-56 to the pool
        for (int i = 0; i < innateSkillPool.size(); i++) {
            rollRanges.put(String.valueOf(i + 1), innateSkillPool.get(i));
        }
    }
    
    public void assignInnateSkill(Player player, int rollNumber) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        boolean hasJack = "jack_of_none".equals(data.getInnateSkillId());
        boolean canReroll = hasJack || hasAllSkillsMaxed(player);

        if (data.getInnateSkillId() != null && !canReroll) {
            player.sendMessage(ChatColor.RED + "You can only reroll when all skills are level 100 or if you have Jack of None!");
            return;
        }
        
        String skillId = rollRanges.getOrDefault(String.valueOf(rollNumber), null);
        
        if (skillId == null) {
            player.sendMessage(ChatColor.RED + "Roll outside innate skill range (1-" + innateSkillPool.size() + ")");
            return;
        }
        
        data.setInnateSkillId(skillId);
        data.setInnateSkillLevel(1);
        
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 50, 2, 2, 0.1);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.0f);
        
        SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
        String skillName = node != null ? node.getName() : skillId;
        
        if ("jack_of_none".equals(skillId)) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Innate Skill Assigned: " + ChatColor.MAGIC + "Jack of None");
            player.sendMessage(ChatColor.GRAY + "You can now use any innate skill, but at reduced effectiveness.");
        } else {
            player.sendMessage(ChatColor.GOLD + "✨ Innate Skill Assigned: " + ChatColor.YELLOW + skillName);
        }
    }
    
    private boolean hasAllSkillsMaxed(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getAllSkillNodes().values()) {
            int level = data.getSkillLevel(node.getId());
            if (level < 100) {
                return false;
            }
        }
        return true;
    }
    
    public void autoRoll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        boolean hasJack = "jack_of_none".equals(data.getInnateSkillId());
        boolean canReroll = hasJack || hasAllSkillsMaxed(player);

        if (data.getInnateSkillId() != null && !canReroll) {
            player.sendMessage(ChatColor.RED + "You can only reroll when all skills are level 100 or if you have Jack of None!");
            return;
        }
        
        Random random = new Random();
        // Pool size is 56 (1-56)
        int rollNumber = random.nextInt(innateSkillPool.size()) + 1;
        
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 1.5, 1.5, 1.5, 0.1);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 2.0f);
        
        player.sendMessage(ChatColor.AQUA + "🎲 Rolled: " + ChatColor.GOLD + rollNumber);
        
        assignInnateSkill(player, rollNumber);
    }
    
    public void executeInnateSkill(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        String rawSkillId = data.getInnateSkillId();
        
        if (rawSkillId == null) {
            player.sendMessage(ChatColor.RED + "No innate skill assigned! Use /roll 1-" + innateSkillPool.size());
            return;
        }

        String skillId = canonicalSkillId(rawSkillId);

        boolean isJackActive = player.hasMetadata("jack_of_none_active");
        
        if (data.isInnateSkillOnCooldown()) {
            long remaining = data.getInnateSkillCooldownRemaining();
            player.sendMessage(ChatColor.RED + "Innate skill on cooldown for " + (remaining / 1000) + "s");
            return;
        }

        int level = data.getInnateSkillLevel();
        
        // Execute the skill
        executeSkillLogic(player, skillId, level, isJackActive);
    }

    private void executeSkillLogic(Player player, String skillId, int level, boolean isJackActive) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Helper to apply penalty if Jack of None is active
        double effectiveness = isJackActive ? 0.5 : 1.0;

        switch (skillId) {
            /* --- JACK OF NONE (Toggle) --- */
            case "jack_of_none":
                if (isJackActive) {
                    player.removeMetadata("jack_of_none_active", plugin);
                    player.sendMessage(ChatColor.RED + "Jack of None " + ChatColor.BOLD + "DEACTIVATED");
                } else {
                    player.setMetadata("jack_of_none_active", new FixedMetadataValue(plugin, true));
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Jack of None " + ChatColor.BOLD + "ACTIVATED");
                    world.spawnParticle(Particle.PORTAL, loc, 50, 1, 1, 1);
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                }
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(1000);
                break;

            /* --- COMBAT --- */
            case "combat_whirlwind":
                double wwRadius = (3.0 + (level * 0.2)) * effectiveness;
                world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                for (Entity e : player.getNearbyEntities(wwRadius, wwRadius, wwRadius)) {
                    if (e instanceof LivingEntity && !(e instanceof Player)) {
                        ((LivingEntity) e).damage((5.0 + level) * effectiveness, player);
                        Vector dir = e.getLocation().toVector().subtract(loc.toVector()).normalize();
                        e.setVelocity(dir.multiply(1.5));
                    }
                }
                player.sendMessage(ChatColor.BOLD + "WHIRLWIND!");
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(10000);
                break;
            
            case "combat_laststand":
                if (player.getHealth() <= 6.0) {
                    int duration = (int) ((200 + (level * 20)) * effectiveness);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 4));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 1));
                    player.sendMessage(ChatColor.GOLD + "Last Stand activated!");
                } else {
                    player.sendMessage(ChatColor.RED + "Health too high for Last Stand!");
                    return;
                }
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(15000);
                break;

            case "combat_execute":
                LivingEntity execTarget = getTargetEntity(player, 5);
                if (execTarget != null && execTarget.getHealth() < (execTarget.getMaxHealth() * 0.3)) {
                    double dmg = (10.0 + (level * 2.0)) * effectiveness;
                    execTarget.damage(dmg, player);
                    world.playSound(execTarget.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                    player.sendMessage(ChatColor.RED + "EXECUTE!");
                } else {
                    player.sendMessage(ChatColor.GRAY + "No valid execute target.");
                    return;
                }
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(12000);
                break;

            /* --- AGILITY --- */
            case "agility_shadowstep":
                LivingEntity shadowTarget = getTargetEntity(player, 15);
                if (shadowTarget != null) {
                    Location behind = shadowTarget.getLocation().clone();
                    Vector dir = behind.getDirection().multiply(-1).normalize();
                    behind.add(dir);
                    behind.setYaw(shadowTarget.getLocation().getYaw() + 180);
                    player.teleport(behind);
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.sendMessage(ChatColor.DARK_PURPLE + "Shadowstep!");
                } else {
                    player.sendMessage(ChatColor.RED + "No target in range.");
                    return;
                }
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(8000);
                break;

            case "agility_blink":
                Vector direction = player.getLocation().getDirection().normalize();
                double distance = (8.0 + level) * effectiveness;
                Location blinkLoc = player.getLocation().add(direction.multiply(distance));
                player.teleport(blinkLoc);
                world.spawnParticle(Particle.PORTAL, blinkLoc, 20, 0.5, 1, 0.5);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Blink!");
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(6000);
                break;

            /* --- MAGIC --- */
            case "magic_fireball":
                Fireball fb = player.launchProjectile(Fireball.class);
                fb.setIsIncendiary(true);
                fb.setYield((float) ((2.0f + (level * 0.5f)) * effectiveness));
                player.sendMessage(ChatColor.GOLD + "Cast Fireball!");
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(8000);
                break;

            case "magic_invisibility":
                int duration = (int) ((200 + (level * 10)) * effectiveness);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
                player.sendMessage(ChatColor.GRAY + "Shadow Cloak activated!");
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(12000);
                break;

            /* --- MINING --- */
            case "mining_explosive":
                Block explosiveCenter = player.getTargetBlock(null, 5);
                if (explosiveCenter != null && !explosiveCenter.getType().isAir()) {
                    Location explodeLoc = explosiveCenter.getLocation();
                    world.createExplosion(explodeLoc, (float) (3.0f * effectiveness), false);
                    int explRadius = (int) ((2 + (level / 10)) * effectiveness);
                    for (int x = -explRadius; x <= explRadius; x++) {
                        for (int y = -explRadius; y <= explRadius; y++) {
                            for (int z = -explRadius; z <= explRadius; z++) {
                                Block b = explodeLoc.clone().add(x, y, z).getBlock();
                                if (b.getType() != Material.BEDROCK && b.getType() != Material.OBSIDIAN) {
                                    b.breakNaturally();
                                }
                            }
                        }
                    }
                    player.sendMessage(ChatColor.GRAY + "Explosive Mining!");
                    plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(16000);
                }
                break;
            
            /* --- FISHING --- */
            case "fishing_deeptreasure":
                if (player.isInWater()) {
                    world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.0f);
                    world.spawnParticle(Particle.RAIN, loc, 30, 1, 1, 1);
                    
                    ItemStack treasure;
                    Random rand = new Random();
                    int roll = rand.nextInt(100);
                    if (roll < 10) treasure = new ItemStack(Material.HEART_OF_THE_SEA);
                    else if (roll < 30) treasure = new ItemStack(Material.NAUTILUS_SHELL);
                    else if (roll < 60) treasure = new ItemStack(Material.PRISMARINE_SHARD);
                    else treasure = new ItemStack(Material.COD);
                    
                    player.getInventory().addItem(treasure);
                    player.sendMessage(ChatColor.AQUA + "Deep Treasure found!");
                } else {
                    player.sendMessage(ChatColor.RED + "Must be in water!");
                    return;
                }
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(14000);
                break;

            /* --- FARMING --- */
            case "farming_bountyharvest":
                int harvestRadius = (int) ((5 + level) * effectiveness);
                int cropsHarvested = 0;
                for (int x = -harvestRadius; x <= harvestRadius; x++) {
                    for (int z = -harvestRadius; z <= harvestRadius; z++) {
                        Block b = loc.clone().add(x, 0, z).getBlock();
                        Material type = b.getType();
                        if (isCrop(type)) {
                            Collection<ItemStack> drops = b.getDrops();
                            for (ItemStack drop : drops) {
                                world.dropItemNaturally(b.getLocation(), drop);
                            }
                            b.setType(type); 
                            cropsHarvested++;
                        }
                    }
                }
                world.playSound(loc, Sound.ITEM_HOE_TILL, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GREEN + "Bounty Harvest: Gathered " + cropsHarvested + " crops!");
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(12000);
                break;

            default:
                if (executeRegistryOrGenericFallback(player, skillId, level, effectiveness)) {
                    return;
                }
                player.sendMessage(ChatColor.GRAY + "Skill logic not implemented: " + skillId);
                break;
        }
    }

    private String canonicalSkillId(String skillId) {
        if (skillId == null) return null;
        String id = skillId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "fishing_grapple" -> "fish_grapple";
            case "fishing_harpoon" -> "fish_harpoon";
            case "magic_chain_lightning" -> "magic_chainlightning";
            case "magic_frostanova", "magic_frostnova" -> "intellect_frostbolt";
            case "intellect_aura" -> "intellect_amplify";
            case "intellect_mending" -> "paladin_holylight";
            default -> id;
        };
    }

    private boolean executeRegistryOrGenericFallback(Player player, String skillId, int level, double effectiveness) {
        String canonical = canonicalSkillId(skillId);
        SkillRegistry registry = plugin.getSkillRegistry();
        int effectiveLevel = Math.max(1, (int) Math.floor(level * effectiveness));

        if (registry != null && canonical != null && registry.hasSkill(canonical)) {
            boolean ok = registry.executeInnate(canonical, player, effectiveLevel);
            if (ok) {
                // Keep innate cooldown separate from normal ability cooldowns.
                plugin.getPlayerDataManager().getPlayerData(player).setInnateSkillCooldown(10000);
                return true;
            }
        }

        return executeGenericInnate(player, canonical, effectiveLevel, effectiveness);
    }

    private boolean executeGenericInnate(Player player, String skillId, int level, double effectiveness) {
        if (skillId == null) return false;
        Location loc = player.getLocation();
        World world = player.getWorld();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        String id = skillId.toLowerCase(Locale.ROOT);

        if (id.startsWith("combat_") || id.startsWith("berserker_") || id.startsWith("paladin_") || id.startsWith("defender_") || id.startsWith("duelist_")) {
            int duration = (int) ((120 + (level * 6L)) * effectiveness);
            int strAmp = Math.min(2, Math.max(0, level / 35));
            int resAmp = Math.min(1, Math.max(0, level / 50));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strAmp, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, resAmp, true, false));
            world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
            player.sendMessage(ChatColor.RED + "Combat Innate surged: " + id);
            data.setInnateSkillCooldown(10000);
            return true;
        }

        if (id.startsWith("agility_")) {
            int duration = (int) ((120 + (level * 6L)) * effectiveness);
            int speedAmp = Math.min(2, Math.max(0, level / 35));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedAmp, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, Math.min(1, level / 50), true, false));
            world.spawnParticle(Particle.CLOUD, loc.add(0, 1, 0), 20, 0.4, 0.2, 0.4, 0.03);
            player.sendMessage(ChatColor.GREEN + "Agility Innate surged: " + id);
            data.setInnateSkillCooldown(9000);
            return true;
        }

        if (id.startsWith("magic_") || id.startsWith("intellect_")) {
            int duration = (int) ((120 + (level * 6L)) * effectiveness);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.min(1, level / 45), true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, true, false));
            world.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.08);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Arcane Innate surged: " + id);
            data.setInnateSkillCooldown(10000);
            return true;
        }

        if (id.startsWith("mining_")) {
            int duration = (int) ((140 + (level * 5L)) * effectiveness);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, Math.min(2, level / 35), true, false));
            world.playSound(loc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
            player.sendMessage(ChatColor.GRAY + "Mining Innate surged: " + id);
            data.setInnateSkillCooldown(9000);
            return true;
        }

        if (id.startsWith("archery_")) {
            int duration = (int) ((140 + (level * 5L)) * effectiveness);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, Math.min(1, level / 45), true, false));
            player.sendMessage(ChatColor.GOLD + "Archery Innate surged: " + id);
            data.setInnateSkillCooldown(9000);
            return true;
        }

        if (id.startsWith("nature_") || id.startsWith("summon_") || id.startsWith("hunter_")) {
            int duration = (int) ((120 + (level * 5L)) * effectiveness);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, Math.min(1, level / 50), true, false));
            world.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 25, 0.6, 0.4, 0.6, 0.04);
            player.sendMessage(ChatColor.DARK_GREEN + "Nature Innate surged: " + id);
            data.setInnateSkillCooldown(11000);
            return true;
        }

        if (id.startsWith("farming_") || id.startsWith("fishing_") || id.startsWith("fish_")) {
            data.regenStamina(Math.max(10.0, level * 0.8));
            data.restoreThirst(Math.max(8.0, level * 0.7));
            player.sendMessage(ChatColor.AQUA + "Resource Innate surged: " + id);
            data.setInnateSkillCooldown(10000);
            return true;
        }

        if (id.startsWith("util_") || id.startsWith("builder_") || id.startsWith("alchemy_") || id.startsWith("bard_")) {
            int duration = (int) ((100 + (level * 5L)) * effectiveness);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0, true, false));
            player.sendMessage(ChatColor.YELLOW + "Utility Innate surged: " + id);
            data.setInnateSkillCooldown(8000);
            return true;
        }

        return false;
    }

    private LivingEntity getTargetEntity(Player player, int range) {
        List<Entity> nearby = player.getNearbyEntities(range, range, range);
        ArrayList<LivingEntity> livingNearby = new ArrayList<>();
        for (Entity e : nearby) {
            if (e instanceof LivingEntity) livingNearby.add((LivingEntity) e);
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        
        LivingEntity target = null;
        double minDistance = Double.MAX_VALUE;

        for (LivingEntity entity : livingNearby) {
            Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector());
            double dot = toEntity.normalize().dot(direction);
            
            if (dot > 0.8) {
                double distance = entity.getLocation().distance(eye);
                if (distance < minDistance) {
                    minDistance = distance;
                    target = entity;
                }
            }
        }
        return target;
    }

    private boolean isCrop(Material material) {
        return material == Material.WHEAT || material == Material.CARROTS || 
               material == Material.POTATOES || material == Material.BEETROOTS ||
               material == Material.NETHER_WART || material == Material.COCOA;
    }
}
