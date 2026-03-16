package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.*;

public class SkillTreeSystem {
    public static final int TIER_LEVEL_SIZE = 20;
    
    private final SkillForgePlugin plugin;
    private final Map<String, SkillNode> skillNodes;
    
    public SkillTreeSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.skillNodes = new HashMap<>();
        initializeSkillTree();
    }
    
    // ... (initializeSkillTree remains the same) ...
    private void initializeSkillTree() {
        // COMBAT TREE - 8 skills
        skillNodes.put("combat_endurance", new SkillNode("combat_endurance", "Endurance", "combat", "Increases maximum health by 5% per level", 1, 100));
        skillNodes.put("combat_critical", new SkillNode("combat_critical", "Critical Strike", "combat", "Increases critical damage by 3% per level", 1, 100));
        skillNodes.put("combat_strength", new SkillNode("combat_strength", "Strength", "combat", "Increases melee damage by 2% per level", 1, 100));
        skillNodes.put("combat_defense", new SkillNode("combat_defense", "Defense", "combat", "Reduces damage taken by 2% per level", 1, 100));
        skillNodes.put("combat_regeneration", new SkillNode("combat_regeneration", "Regeneration", "combat", "Gain health regen at 1% per level", 1, 100));
        skillNodes.put("combat_precision", new SkillNode("combat_precision", "Precision", "combat", "Increases accuracy by 1% per level", 1, 100));
        skillNodes.put("combat_parry", new SkillNode("combat_parry", "Parry", "combat", "Chance to parry attacks at 1% per level", 1, 100));
        skillNodes.put("beserker", new SkillNode("beserker", "Berserker", "combat", "Gain Strength, resistance, and health when at 3 hearts or less", 1, 100));
        
        // MINING TREE - 6 skills
        skillNodes.put("mining_efficiency", new SkillNode("mining_efficiency", "Mining Efficiency", "mining", "Increases mining speed by 5% per level", 1, 100));
        skillNodes.put("mining_fortune", new SkillNode("mining_fortune", "Fortune", "mining", "Increases ore drops by 2% per level", 1, 100));
        skillNodes.put("mining_durability", new SkillNode("mining_durability", "Tool Durability", "mining", "Reduces tool wear by 3% per level", 1, 100));
        skillNodes.put("mining_prospector", new SkillNode("mining_prospector", "Prospecting", "mining", "Find valuable ores 3% more often per level", 1, 100));
        skillNodes.put("mining_stoneworking", new SkillNode("mining_stoneworking", "Stoneworking", "mining", "Mine stone 4% faster per level", 1, 100));
        skillNodes.put("mining_excavation", new SkillNode("mining_excavation", "Excavation", "mining", "Dig dirt and sand 3% faster per level", 1, 100));
        
        // AGILITY TREE - 6 skills
        skillNodes.put("agility_speed", new SkillNode("agility_speed", "Speedstep", "agility", "Increases movement speed by 2% per level", 1, 100));
        skillNodes.put("agility_dodge", new SkillNode("agility_dodge", "Dodge", "agility", "Increases dodge chance by 1% per level", 1, 100));
        skillNodes.put("agility_jump", new SkillNode("agility_jump", "Jump Boost", "agility", "Increases jump height by 5% per level", 1, 100));
        skillNodes.put("agility_acrobatics", new SkillNode("agility_acrobatics", "Acrobatics", "agility", "Reduces fall damage by 2% per level", 1, 100));
        skillNodes.put("agility_parkour", new SkillNode("agility_parkour", "Parkour", "agility", "Climb walls 3% faster per level", 1, 100));
        skillNodes.put("agility_climbing", new SkillNode("agility_climbing", "Climbing Mastery", "agility", "Reduce climb stamina drain by 2% per level", 1, 100));
        
        // INTELLECT TREE - 6 skills
        skillNodes.put("intellect_wisdom", new SkillNode("intellect_wisdom", "Wisdom", "intellect", "Increases XP gain by 3% per level", 1, 100));
        skillNodes.put("intellect_enchanting", new SkillNode("intellect_enchanting", "Enchanting Mastery", "intellect", "Reduces enchanting cost by 2% per level", 1, 100));
        skillNodes.put("intellect_brewing", new SkillNode("intellect_brewing", "Brewing Mastery", "intellect", "Increases potion duration by 5% per level", 1, 100));
        skillNodes.put("intellect_arcana", new SkillNode("intellect_arcana", "Arcana", "intellect", "Unlock mystical enchantments at 2% per level", 1, 100));
        skillNodes.put("intellect_alchemy", new SkillNode("intellect_alchemy", "Alchemy", "intellect", "Combine potions 3% more effectively per level", 1, 100));
        skillNodes.put("intellect_scholarship", new SkillNode("intellect_scholarship", "Scholarship", "intellect", "Learn skills 2% faster per level", 1, 100));
        
        // FARMING TREE - 5 skills
        skillNodes.put("farming_growth", new SkillNode("farming_growth", "Growth", "farming", "Crops grow 3% faster per level", 1, 100));
        skillNodes.put("farming_harvest", new SkillNode("farming_harvest", "Harvest Yield", "farming", "Get 2% more crops per harvest per level", 1, 100));
        skillNodes.put("farming_breeding", new SkillNode("farming_breeding", "Animal Breeding", "farming", "Animals breed 2% faster per level", 1, 100));
        skillNodes.put("farming_pestcontrol", new SkillNode("farming_pestcontrol", "Pest Control", "farming", "Keep pests away at 1% per level", 1, 100));
        skillNodes.put("farming_irrigation", new SkillNode("farming_irrigation", "Irrigation", "farming", "Water spreads 3% farther per level", 1, 100));
        
        // FISHING TREE - 4 skills
        skillNodes.put("fishing_luck", new SkillNode("fishing_luck", "Fisher's Luck", "fishing", "Catch better fish at 2% per level", 1, 100));
        skillNodes.put("fishing_speed", new SkillNode("fishing_speed", "Quick Cast", "fishing", "Fish bite 3% faster per level", 1, 100));
        skillNodes.put("fishing_rare", new SkillNode("fishing_rare", "Rare Catch", "fishing", "Find treasures 2% more often per level", 1, 100));
        skillNodes.put("fishing_underwater", new SkillNode("fishing_underwater", "Deep Sea Diver", "fishing", "Breathe underwater 3% longer per level", 1, 100));
        
        // MAGIC TREE - 9 skills
        skillNodes.put("magic_fireball", new SkillNode("magic_fireball", "Fireball", "magic", "Cast fireballs with 2% more damage per level", 1, 100));
        skillNodes.put("magic_frostbolt", new SkillNode("magic_frostbolt", "Frostbolt", "magic", "Slow enemies by 1% more per level", 1, 100));
        skillNodes.put("magic_manashield", new SkillNode("magic_manashield", "Mana Shield", "magic", "Block 2% more damage per level", 1, 100));
        skillNodes.put("magic_spellcraft", new SkillNode("magic_spellcraft", "Spellcraft", "magic", "Reduce spell cooldowns by 1% per level", 1, 100));
        skillNodes.put("magic_manapool", new SkillNode("magic_manapool", "Mana Pool", "magic", "Increase max mana by 5% per level", 1, 100));
        skillNodes.put("magic_invisibility", new SkillNode("magic_invisibility", "Shadow Cloak", "magic", "Become invisible for 10s, scales with level", 1, 100));
        skillNodes.put("magic_meteor", new SkillNode("magic_meteor", "Meteor Strike", "magic", "Call meteors dealing area damage", 1, 100));
        skillNodes.put("magic_timefreeze", new SkillNode("magic_timefreeze", "Time Freeze", "magic", "Slow time for enemies in range", 1, 100));
        skillNodes.put("magic_chainlightning", new SkillNode("magic_chainlightning", "Chain Lightning", "magic", "Lightning that jumps between enemies", 1, 100));
        
        // COMBAT INNATE ABILITIES - 3 skills
        skillNodes.put("combat_whirlwind", new SkillNode("combat_whirlwind", "Whirlwind", "combat", "Spin and hit all nearby enemies", 1, 100));
        skillNodes.put("combat_laststand", new SkillNode("combat_laststand", "Last Stand", "combat", "Gain massive defense when low health", 1, 100));
        skillNodes.put("combat_execute", new SkillNode("combat_execute", "Execute", "combat", "Deal massive damage to low health enemies", 1, 100));
        
        // AGILITY INNATE ABILITIES - 3 skills
        skillNodes.put("agility_shadowstep", new SkillNode("agility_shadowstep", "Shadowstep", "agility", "Teleport forward instantly", 1, 100));
        skillNodes.put("agility_timeshift", new SkillNode("agility_timeshift", "Time Shift", "agility", "Move faster temporarily", 1, 100));
        skillNodes.put("agility_blink", new SkillNode("agility_blink", "Blink", "agility", "Short range teleport with evasion", 1, 100));
        
        // INTELLECT INNATE ABILITIES - 3 skills
        skillNodes.put("intellect_mindblast", new SkillNode("intellect_mindblast", "Mind Blast", "intellect", "Deal magical damage in area", 1, 100));
        skillNodes.put("intellect_mindshield", new SkillNode("intellect_mindshield", "Mind Shield", "intellect", "Reflect portion of damage taken", 1, 100));
        skillNodes.put("intellect_amplify", new SkillNode("intellect_amplify", "Amplify", "intellect", "Boost all ability effects", 1, 100));
        
        // FARMING INNATE ABILITIES - 2 skills
        skillNodes.put("farming_bountyharvest", new SkillNode("farming_bountyharvest", "Bounty Harvest", "farming", "Instantly harvest nearby crops for rewards", 1, 100));
        skillNodes.put("farming_summongolem", new SkillNode("farming_summongolem", "Summon Golem", "farming", "Summon a golem to help farm", 1, 100));
        
        // FISHING INNATE ABILITIES - 2 skills
        skillNodes.put("fishing_deeptreasure", new SkillNode("fishing_deeptreasure", "Deep Treasure", "fishing", "Fish up rare treasures from the deep", 1, 100));
        skillNodes.put("fishing_summonwhale", new SkillNode("fishing_summonwhale", "Summon Whale", "fishing", "Call a whale to aid you", 1, 100));
        
        // MINING INNATE ABILITIES - 2 skills
        skillNodes.put("mining_explosive", new SkillNode("mining_explosive", "Explosive Mining", "mining", "Mine large areas instantly", 1, 100));
        skillNodes.put("mining_gemdiscovery", new SkillNode("mining_gemdiscovery", "Gem Discovery", "mining", "Find hidden gems in ore", 1, 100));

        // WEAPON MASTERY TREE - 5 skills
        skillNodes.put("mastery_swords", new SkillNode("mastery_swords", "Sword Mastery", "mastery", "Increases damage and speed with swords", 1, 100));
        skillNodes.put("mastery_bows", new SkillNode("mastery_bows", "Bow Mastery", "mastery", "Increases projectile damage and draw speed", 1, 100));
        skillNodes.put("mastery_axes", new SkillNode("mastery_axes", "Axe Mastery", "mastery", "Increases damage and shield disable chance", 1, 100));
        skillNodes.put("mastery_firearms", new SkillNode("mastery_firearms", "Firearm Mastery", "mastery", "Increases firearm accuracy and reload speed", 1, 100));
        skillNodes.put("mastery_flail", new SkillNode("mastery_flail", "Flail Mastery", "mastery", "Master the flail to stop hitting yourself!", 1, 100));

        // BRICK PLAYSTYLE NODES - build your character brick by brick.
        skillNodes.put("mastery_brick_bulwark", new SkillNode("mastery_brick_bulwark", "Brick: Bulwark", "mastery", "Tank-focused brick that increases guard strength and stagger resistance.", 1, 100));
        skillNodes.put("mastery_brick_brawler", new SkillNode("mastery_brick_brawler", "Brick: Brawler", "mastery", "Aggressive brick that boosts close-range pressure and melee follow-up speed.", 1, 100));
        skillNodes.put("mastery_brick_duelist", new SkillNode("mastery_brick_duelist", "Brick: Duelist", "mastery", "Precision brick for single-target burst and recovery timing windows.", 1, 100));
        skillNodes.put("mastery_brick_ranger", new SkillNode("mastery_brick_ranger", "Brick: Ranger", "mastery", "Ranged brick that improves projectile consistency and target tracking.", 1, 100));
        skillNodes.put("mastery_brick_mage", new SkillNode("mastery_brick_mage", "Brick: Mage", "mastery", "Spell-control brick that improves cast flow and cooldown rhythm.", 1, 100));
        skillNodes.put("mastery_brick_harvester", new SkillNode("mastery_brick_harvester", "Brick: Harvester", "mastery", "Sustain brick for farming loops and resource uptime under pressure.", 1, 100));
        skillNodes.put("mastery_brick_miner", new SkillNode("mastery_brick_miner", "Brick: Miner", "mastery", "Extraction brick for route efficiency and ore-control pacing.", 1, 100));
        skillNodes.put("mastery_brick_explorer", new SkillNode("mastery_brick_explorer", "Brick: Explorer", "mastery", "Mobility brick that improves traversal tempo and scouting endurance.", 1, 100));
        skillNodes.put("mastery_brick_support", new SkillNode("mastery_brick_support", "Brick: Support", "mastery", "Team brick for utility uptime and ally-centered combat flow.", 1, 100));
        skillNodes.put("mastery_brick_controller", new SkillNode("mastery_brick_controller", "Brick: Controller", "mastery", "Zone-control brick for crowd control, peel, and area denial.", 1, 100));

        initializeDependencies();
    }
    
    public UpgradeResult tryUpgradeSkill(Player player, String skillId) {
        if (player == null || skillId == null) return UpgradeResult.SKILL_NOT_FOUND;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        SkillNode node = skillNodes.get(skillId);
        if (node == null) return UpgradeResult.SKILL_NOT_FOUND;

        int currentLevel = data.getSkillLevel(skillId);
        if (currentLevel >= node.getMaxLevel()) return UpgradeResult.MAX_LEVEL;

        List<SkillRequirement> unmet = getUnmetRequirements(data, node);
        if (!unmet.isEmpty()) return UpgradeResult.REQUIREMENT_NOT_MET;

        int cost = node.getCostPerLevel();
        if (data.removeSkillPoints(cost)) {
            data.incrementSkillLevel(skillId);
            if (plugin.getSkillExecutionSystem() != null) {
                plugin.getSkillExecutionSystem().applyAllSkills(player);
            }
            return UpgradeResult.SUCCESS;
        }
        return UpgradeResult.INSUFFICIENT_POINTS;
    }

    public boolean upgradeSkill(Player player, String skillId) {
        return tryUpgradeSkill(player, skillId) == UpgradeResult.SUCCESS;
    }

    public List<SkillRequirement> getUnmetRequirements(Player player, String skillId) {
        if (player == null || skillId == null) return Collections.emptyList();
        SkillNode node = skillNodes.get(skillId);
        if (node == null) return Collections.emptyList();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        return getUnmetRequirements(data, node);
    }

    public String getUpgradeFailureMessage(Player player, String skillId, UpgradeResult result) {
        if (result == null) result = UpgradeResult.UNKNOWN;
        SkillNode node = skillNodes.get(skillId);
        String display = node != null ? node.getName() : skillId;
        switch (result) {
            case SKILL_NOT_FOUND:
                return "Skill not found: " + skillId;
            case MAX_LEVEL:
                return display + " is already maxed.";
            case REQUIREMENT_NOT_MET: {
                List<SkillRequirement> unmet = getUnmetRequirements(player, skillId);
                if (!unmet.isEmpty()) {
                    SkillRequirement first = unmet.get(0);
                    SkillNode reqNode = skillNodes.get(first.requiredSkillId);
                    String reqName = reqNode != null ? reqNode.getName() : first.requiredSkillId;
                    return "Requires " + reqName + " level " + first.requiredLevel + ".";
                }
                return "Requirements not met.";
            }
            case INSUFFICIENT_POINTS:
                return "Not enough Skill Points.";
            default:
                return "Unable to upgrade " + display + ".";
        }
    }
    
    public Map<String, SkillNode> getAllSkillNodes() {
        return new HashMap<>(skillNodes);
    }

    /**
     * Ensures every registered executable skill is represented in the tree/panel.
     * Missing skills are auto-added with inferred categories so they can be upgraded
     * and surfaced in Skill Panel tabs.
     */
    public int syncWithRegistry(SkillRegistry registry) {
        if (registry == null) return 0;
        int added = 0;
        int defaultCost = Math.max(1, plugin.getConfig().getInt("skills.registry_default_cost_per_level", 1));
        int defaultMax = Math.max(1, plugin.getConfig().getInt("skills.registry_default_max_level", 100));

        for (String skillId : registry.getRegisteredSkillIds()) {
            if (skillId == null || skillId.isBlank()) continue;
            String id = skillId.toLowerCase(Locale.ROOT);
            if (skillNodes.containsKey(id)) continue;

            String category = inferPanelCategory(id);
            String name = humanizeSkillId(id);
            String desc = "Registry skill. Unlock levels to improve effectiveness and bind it in /bind.";
            skillNodes.put(id, new SkillNode(id, name, category, desc, defaultCost, defaultMax));
            added++;
        }
        return added;
    }
    
    public List<SkillNode> getSkillsByCategory(String category) {
        List<SkillNode> categorySkills = new ArrayList<>();
        for (SkillNode node : skillNodes.values()) {
            if (node.getCategory().equalsIgnoreCase(category)) {
                categorySkills.add(node);
            }
        }
        categorySkills.sort(Comparator
                .comparingInt(this::getDependencyDepth)
                .thenComparing(SkillNode::getName));
        return categorySkills;
    }

    private String inferPanelCategory(String skillId) {
        if (skillId == null || skillId.isBlank()) return "mastery";
        String id = skillId.toLowerCase(Locale.ROOT);
        String prefix = id.contains("_") ? id.substring(0, id.indexOf('_')) : id;

        return switch (prefix) {
            case "combat", "berserker", "paladin", "defender", "duelist", "archery", "hunter" -> "combat";
            case "mining", "builder" -> "mining";
            case "agility", "util" -> "agility";
            case "intellect" -> "intellect";
            case "farming", "chef" -> "farming";
            case "fishing", "fish" -> "fishing";
            case "magic", "alchemy", "bard", "nature", "summon" -> "magic";
            case "mastery" -> "mastery";
            default -> "mastery";
        };
    }

    private String humanizeSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) return "Unknown Skill";
        String[] parts = skillId.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? skillId : out.toString();
    }

    private int getDependencyDepth(SkillNode node) {
        if (node == null) return 0;
        Set<String> seen = new HashSet<>();
        int depth = 0;
        SkillNode cursor = node;
        while (cursor != null && cursor.getRequirements() != null && !cursor.getRequirements().isEmpty()) {
            SkillRequirement req = cursor.getRequirements().get(0);
            if (req == null || req.getRequiredSkillId() == null || req.getRequiredSkillId().isBlank()) break;
            if (!seen.add(req.getRequiredSkillId())) break;
            depth++;
            cursor = skillNodes.get(req.getRequiredSkillId());
        }
        return depth;
    }

    /**
     * Dynamically calculates the total number of skills in a category
     * based on the currently loaded skillNodes map.
     */
    private int getTotalSkillsInCategory(String category) {
        if (category == null) return 0;
        
        String targetCategory = category.toLowerCase();
        int count = 0;
        
        // Iterate over all loaded skill nodes and count matches
        for (SkillNode node : skillNodes.values()) {
            if (node.getCategory().equalsIgnoreCase(targetCategory)) {
                count++;
            }
        }
        
        return count;
    }

    /**
     * Returns an array of all SkillNodes currently loaded in the system.
     * This mimics the behavior of Enum.values().
     */
    public SkillNode[] values() {
        return skillNodes.values().toArray(new SkillNode[0]);
    }

    /**
     * Checks if the player has unlocked the specified skill.
     * A skill is considered "had" if its level is greater than 0.
     * 
     * @param player The player to check.
     * @param skillName The name of the skill to check.
     * @return true if the skill level > 0, false otherwise.
     */
    public boolean hasSkill(Player player, String skillName) {
        if (player == null || skillName == null) return false;
        
        // Retrieve the player's data from the manager
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        
        // Check if the skill level is greater than 0
        return data.getSkillLevel(skillName) > 0;
    }

    /**
     * Locks (resets) a specific skill for the player.
     * This sets the skill level to 0.
     * 
     * @param player The player whose skill is being locked.
     * @param skillName The name of the skill to lock.
     */
    public void lockSkill(Player player, String skillName) {
        if (player == null || skillName == null) return;
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        
        // Set the skill level to 0
        data.setSkillLevel(skillName, 0);
        
        // Save immediately to ensure persistence
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
    }

    /**
     * Unlocks a specific skill for the player.
     * This sets the skill level to 1 (default unlocked state).
     * 
     * @param target The player receiving the skill.
     * @param skillName The name of the skill to unlock.
     */
    public void unlockSkill(Player target, String skillName) {
        if (target == null || skillName == null) return;
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        
        // Set the skill level to 1 (Unlocked)
        data.setSkillLevel(skillName, 1);
        
        // Save immediately to ensure persistence
        plugin.getPlayerDataManager().savePlayerData(target.getUniqueId());
    }
    
    public static class SkillNode {
        private final String id;
        private final String name;
        private final String category;
        private final String description;
        private final int costPerLevel;
        private final int maxLevel;
        private final List<SkillRequirement> requirements;
        
        public SkillNode(String id, String name, String category, String description, int costPerLevel, int maxLevel) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.costPerLevel = costPerLevel;
            this.maxLevel = maxLevel;
            this.requirements = new ArrayList<>();
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public int getCostPerLevel() { return costPerLevel; }
        public int getMaxLevel() { return maxLevel; }
        public List<SkillRequirement> getRequirements() { return Collections.unmodifiableList(requirements); }
        private void setRequirements(List<SkillRequirement> requirements) {
            this.requirements.clear();
            if (requirements != null) this.requirements.addAll(requirements);
        }
    }

    public static final class SkillRequirement {
        private final String requiredSkillId;
        private final int requiredLevel;

        public SkillRequirement(String requiredSkillId, int requiredLevel) {
            this.requiredSkillId = requiredSkillId;
            this.requiredLevel = requiredLevel;
        }

        public String getRequiredSkillId() {
            return requiredSkillId;
        }

        public int getRequiredLevel() {
            return requiredLevel;
        }
    }

    public enum UpgradeResult {
        SUCCESS,
        SKILL_NOT_FOUND,
        MAX_LEVEL,
        REQUIREMENT_NOT_MET,
        INSUFFICIENT_POINTS,
        UNKNOWN
    }

    private List<SkillRequirement> getUnmetRequirements(PlayerData data, SkillNode node) {
        if (data == null || node == null || node.getRequirements().isEmpty()) return Collections.emptyList();
        List<SkillRequirement> unmet = new ArrayList<>();
        for (SkillRequirement req : node.getRequirements()) {
            if (req == null || req.requiredSkillId == null || req.requiredSkillId.isBlank()) continue;
            int have = data.getSkillLevel(req.requiredSkillId);
            if (have < req.requiredLevel) unmet.add(req);
        }
        return unmet;
    }

    private void initializeDependencies() {
        // Combat tree
        require("combat_strength", "combat_endurance");
        require("combat_defense", "combat_endurance");
        require("combat_critical", "combat_strength");
        require("combat_precision", "combat_strength");
        require("combat_parry", "combat_defense");
        require("combat_regeneration", "combat_defense");
        require("combat_whirlwind", "combat_strength");
        require("combat_execute", "combat_critical");
        require("combat_laststand", "combat_defense");
        require("beserker", "combat_execute", "combat_laststand");

        // Mining tree
        require("mining_stoneworking", "mining_efficiency");
        require("mining_durability", "mining_efficiency");
        require("mining_fortune", "mining_stoneworking");
        require("mining_prospector", "mining_stoneworking");
        require("mining_excavation", "mining_durability");
        require("mining_explosive", "mining_fortune");
        require("mining_gemdiscovery", "mining_prospector");

        // Agility tree
        require("agility_jump", "agility_speed");
        require("agility_dodge", "agility_speed");
        require("agility_acrobatics", "agility_jump");
        require("agility_parkour", "agility_jump");
        require("agility_shadowstep", "agility_dodge");
        require("agility_climbing", "agility_acrobatics");
        require("agility_blink", "agility_shadowstep");
        require("agility_timeshift", "agility_blink");

        // Intellect tree
        require("intellect_scholarship", "intellect_wisdom");
        require("intellect_enchanting", "intellect_scholarship");
        require("intellect_brewing", "intellect_scholarship");
        require("intellect_arcana", "intellect_enchanting");
        require("intellect_alchemy", "intellect_brewing");
        require("intellect_mindblast", "intellect_arcana");
        require("intellect_mindshield", "intellect_arcana");
        require("intellect_amplify", "intellect_mindblast", "intellect_mindshield");

        // Farming tree
        require("farming_harvest", "farming_growth");
        require("farming_irrigation", "farming_growth");
        require("farming_breeding", "farming_harvest");
        require("farming_pestcontrol", "farming_irrigation");
        require("farming_bountyharvest", "farming_breeding");
        require("farming_summongolem", "farming_pestcontrol");

        // Fishing tree
        require("fishing_speed", "fishing_luck");
        require("fishing_rare", "fishing_speed");
        require("fishing_underwater", "fishing_speed");
        require("fishing_deeptreasure", "fishing_rare");
        require("fishing_summonwhale", "fishing_underwater");

        // Magic tree
        require("magic_frostbolt", "magic_fireball");
        require("magic_spellcraft", "magic_fireball");
        require("magic_manapool", "magic_frostbolt");
        require("magic_manashield", "magic_spellcraft");
        require("magic_chainlightning", "magic_manapool");
        require("magic_invisibility", "magic_manashield");
        require("magic_meteor", "magic_chainlightning");
        require("magic_timefreeze", "magic_invisibility");

        // Mastery + Brick tree
        require("mastery_axes", "mastery_swords");
        require("mastery_bows", "mastery_swords");
        require("mastery_flail", "mastery_axes");
        require("mastery_firearms", "mastery_bows");
        require("mastery_brick_bulwark", "mastery_flail");
        require("mastery_brick_brawler", "mastery_flail");
        require("mastery_brick_duelist", "mastery_bows");
        require("mastery_brick_ranger", "mastery_bows");
        require("mastery_brick_mage", "mastery_firearms");
        require("mastery_brick_harvester", "mastery_brick_bulwark");
        require("mastery_brick_miner", "mastery_brick_brawler");
        require("mastery_brick_explorer", "mastery_brick_ranger");
        require("mastery_brick_support", "mastery_brick_duelist");
        require("mastery_brick_controller", "mastery_brick_mage", "mastery_brick_support");
    }

    private void setTierChain(int requiredLevel, String... chain) {
        if (chain == null || chain.length < 2) return;
        int reqLvl = Math.max(1, requiredLevel);
        for (int i = 1; i < chain.length; i++) {
            String prev = chain[i - 1];
            String current = chain[i];
            setRequirements(current, Collections.singletonList(new SkillRequirement(prev, reqLvl)));
        }
    }

    private void require(String skillId, String... requiredSkills) {
        if (requiredSkills == null || requiredSkills.length == 0) return;
        List<SkillRequirement> reqs = new ArrayList<>();
        for (String req : requiredSkills) {
            if (req == null || req.isBlank()) continue;
            reqs.add(new SkillRequirement(req, TIER_LEVEL_SIZE));
        }
        setRequirements(skillId, reqs);
    }

    private void setRequirements(String skillId, List<SkillRequirement> requirements) {
        if (skillId == null || skillId.isBlank()) return;
        SkillNode node = skillNodes.get(skillId);
        if (node == null) return;
        node.setRequirements(requirements);
    }
}
