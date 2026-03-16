package com.skilltree.plugin.systems;

import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.SkillForgePlugin;

import java.util.HashMap;
import java.util.Map;

public class PlaystyleCalculator {

    private final SkillForgePlugin plugin;

    public PlaystyleCalculator(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Determines the player's playstyle based on their skill distribution.
     * 
     * @param playerData The player's data object.
     * @return A string representing the playstyle (e.g., "Ninja", "Tank").
     */
    public String determinePlaystyle(PlayerData playerData) {
        if (playerData == null) return "Novice";

        Map<String, Integer> categoryScores = new HashMap<>();
        
        // Initialize scores for known categories
        categoryScores.put("combat", 0);
        categoryScores.put("mining", 0);
        categoryScores.put("agility", 0);
        categoryScores.put("intellect", 0);
        categoryScores.put("farming", 0);
        categoryScores.put("fishing", 0);
        categoryScores.put("magic", 0);
        categoryScores.put("mastery", 0);

        // Calculate total levels per category
        for (Map.Entry<String, Integer> entry : playerData.getAllSkillLevels().entrySet()) {
            String skillId = entry.getKey();
            int level = entry.getValue();
            
            // Get the node definition to find the category
            SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
            
            if (node != null) {
                String category = node.getCategory();
                categoryScores.put(category, categoryScores.getOrDefault(category, 0) + level);
            }
        }

        // 1. Check for specific Class titles based on Key Skills
        // These override the general archetype if the specific conditions are met
        
        // Ninja: High Agility + Shadowstep/Blink
        boolean isNinja = categoryScores.get("agility") > 20 && 
                          (playerData.getSkillLevel("agility_shadowstep") > 0 || 
                           playerData.getSkillLevel("agility_blink") > 0);

        // Beserker: High Combat + Beserker skill
        boolean isBeserker = categoryScores.get("combat") > 20 && 
                             playerData.getSkillLevel("beserker") > 0;

        // Arcanist: High Magic + Intellect
        boolean isArcanist = categoryScores.get("magic") > 20 && 
                             categoryScores.get("intellect") > 20;

        if (isNinja) return "Ninja";
        if (isBeserker) return "Beserker";
        if (isArcanist) return "Arcanist";

        // 2. Determine General Archetype based on highest scoring category
        String highestCategory = categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("combat");

        return switch (highestCategory) {
            case "combat" -> "Offensive";
            case "defense" -> "Tank"; // Note: 'defense' isn't a top level cat, handled below
            case "agility" -> "Scout";
            case "intellect", "magic" -> "Support";
            case "mining" -> "Miner";
            case "farming" -> "Botanist";
            case "fishing" -> "Angler";
            case "mastery" -> "Veteran";
            default -> "Adventurer";
        };
    }
}
