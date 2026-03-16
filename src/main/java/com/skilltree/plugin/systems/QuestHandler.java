package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class QuestHandler {
    
    private final SkillForgePlugin plugin;
    
    public QuestHandler(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Update mob kill count for a player
     * @param player The player
     * @param mobType The type of mob killed (e.g., "zombie", "creeper")
     * @param amount Number of mobs killed
     */
    public void updateMobKillCount(Player player, String mobType, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addQuestProgress("mobkill_" + mobType.toLowerCase(), amount);
        data.addQuestProgress("mobkill_all", amount);
    }
    
    /**
     * Update item collection count for a player
     * @param player The player
     * @param itemType The type of item (e.g., "diamond", "iron_ore")
     * @param amount Number of items collected
     */
    public void updateItemCollected(Player player, String itemType, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addQuestProgress("item_" + itemType.toLowerCase(), amount);
        data.addQuestProgress("item_all", amount);
    }
    
    /**
     * Update animal breed count for a player
     * @param player The player
     * @param animalType The type of animal (e.g., "cow", "sheep")
     * @param amount Number of animals bred
     */
    public void updateBreedCount(Player player, String animalType, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addQuestProgress("breed_" + animalType.toLowerCase(), amount);
        data.addQuestProgress("breed_all", amount);
    }
    
    /**
     * Update block break count for a player
     * @param player The player
     * @param blockType The type of block (e.g., "stone", "dirt")
     * @param amount Number of blocks broken
     */
    public void updateBlockBroken(Player player, String blockType, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addQuestProgress("block_" + blockType.toLowerCase(), amount);
        data.addQuestProgress("block_all", amount);
    }
    
    /**
     * Update block placement count for a player
     * @param player The player
     * @param blockType The type of block (e.g., "stone", "dirt")
     * @param amount Number of blocks placed
     */
    public void updateBlockPlaced(Player player, String blockType, int amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addQuestProgress("place_" + blockType.toLowerCase(), amount);
        data.addQuestProgress("place_all", amount);
    }
    
    /**
     * Get quest progress for a player
     * @param player The player
     * @param questId The quest identifier (e.g., "mobkill_zombie")
     * @return The progress value
     */
    public int getQuestProgress(Player player, String questId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        return data.getQuestProgress(questId.toLowerCase());
    }
    
    /**
     * Set quest progress for a player
     * @param player The player
     * @param questId The quest identifier
     * @param progress The progress value
     */
    public void setQuestProgress(Player player, String questId, int progress) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.setQuestProgress(questId.toLowerCase(), progress);
    }
    
    /**
     * Reset quest progress for a player
     * @param player The player
     * @param questId The quest identifier
     */
    public void resetQuestProgress(Player player, String questId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.setQuestProgress(questId.toLowerCase(), 0);
    }
    
    /**
     * Get all quest progress for a player
     * @param player The player
     * @return Map of all quest progress
     */
    public Map<String, Integer> getAllQuestProgress(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        return data.getAllQuestProgress();
    }
}
