package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class QuestLeader {
    
    private final SkillForgePlugin plugin;
    private final Map<String, Quest> activeQuests;
    
    public QuestLeader(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.activeQuests = new HashMap<>();
    }
    
    /**
     * Start a quest for a player
     * @param player The player
     * @param quest The quest to start
     * @return true if quest started, false if player has max quests
     */
    public boolean startQuest(Player player, Quest quest) {
        if (quest == null) {
            player.sendMessage(ChatColor.RED + "That quest is unavailable right now.");
            return false;
        }

        int playerQuestCount = countPlayerQuests(player);
        if (playerQuestCount >= 3) {
            player.sendMessage(ChatColor.RED + "You can only have 3 quests at a time!");
            player.sendMessage(ChatColor.GRAY + "Complete or cancel a quest before starting another.");
            return false;
        }

        // Use a per-player quest instance to avoid shared state across players.
        Quest assignedQuest = new Quest(
                quest.getQuestId(),
                quest.getQuestName(),
                quest.getDescription(),
                quest.getQuestTypeEnum(),
                quest.getTrackingType(),
                quest.getDifficulty()
        );
        assignedQuest.markStarted(player.getWorld());

        activeQuests.put(player.getName() + ":" + assignedQuest.getQuestId(), assignedQuest);
        player.sendMessage(ChatColor.GOLD + "📜 Quest Started: " + ChatColor.YELLOW + assignedQuest.getQuestName());
        player.sendMessage(ChatColor.GRAY + assignedQuest.getDescription());
        player.sendMessage(ChatColor.GRAY + "Difficulty: " + ChatColor.AQUA + assignedQuest.getDifficulty().getDisplayName());
        player.sendMessage(ChatColor.GRAY + "Required: " + ChatColor.YELLOW + assignedQuest.getRequiredAmount());
        player.sendMessage(ChatColor.GRAY + "Timeout: " + ChatColor.YELLOW + "5 Minecraft days");
        return true;
    }
    
    /**
     * Count active quests for a player
     */
    private int countPlayerQuests(Player player) {
        String prefix = player.getName() + ":";
        return (int) activeQuests.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .count();
    }
    
    /**
     * Complete a quest for a player
     * Validates that the player has met the requirements, then resets the quest progress
     * @param player The player
     * @param questId The quest ID to complete
     * @return true if quest was completed, false if requirements not met
     */
    public boolean completeQuest(Player player, String questId) {
        String key = player.getName() + ":" + questId;
        Quest quest = activeQuests.get(key);
        
        if (quest == null) {
            player.sendMessage(ChatColor.RED + "That quest is not active!");
            return false;
        }
        
        // Check if player has met the requirements
        QuestHandler questHandler = plugin.getQuestHandler();
        int currentProgress = questHandler.getQuestProgress(player, quest.getFullTrackingId());
        
        if (currentProgress < quest.getRequiredAmount()) {
            player.sendMessage(ChatColor.RED + "You haven't completed the quest requirements!");
            player.sendMessage(ChatColor.GRAY + "Progress: " + currentProgress + "/" + quest.getRequiredAmount());
            return false;
        }
        
        // Quest completed! Reset the entire quest progress for this activity type
        questHandler.resetQuestProgress(player, quest.getFullTrackingId());
        
        // Trigger visual/audio effects
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FIREWORK, loc, 50, 2, 2, 2, 0.3);
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 1.5f);
        
        // Notify player of completion
        player.sendMessage(ChatColor.GREEN + "✓ Quest Complete: " + ChatColor.YELLOW + quest.getQuestName());
        player.sendMessage(ChatColor.GOLD + "All " + quest.getQuestTypeEnum().getDisplayName() + " progress reset!");
        
        // Auto-scale quest to next difficulty if not already GODLIKE
        if (quest.getDifficulty() != QuestDifficulty.GODLIKE) {
            Quest nextQuest = quest.createNextDifficultyQuest();
            nextQuest.markStarted(player.getWorld());
            activeQuests.put(key, nextQuest);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "📈 Quest Scaled: " + ChatColor.YELLOW + nextQuest.getQuestName());
            player.sendMessage(ChatColor.GRAY + "New requirement: " + ChatColor.YELLOW + nextQuest.getRequiredAmount());
        } else {
            // Remove quest if it was already EXTREME
            activeQuests.remove(key);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 You've reached the maximum difficulty!");
        }
        
        return true;
    }
    
    /**
     * Get quest progress for a player
     * @param player The player
     * @param questId The quest ID
     * @return Progress object with current/required amounts, or null if quest not active
     */
    public QuestProgress getQuestProgress(Player player, String questId) {
        String key = player.getName() + ":" + questId;
        Quest quest = activeQuests.get(key);
        
        if (quest == null) {
            return null;
        }
        
        QuestHandler questHandler = plugin.getQuestHandler();
        int current = questHandler.getQuestProgress(player, quest.getFullTrackingId());
        
        return new QuestProgress(quest.getQuestName(), current, quest.getRequiredAmount());
    }
    
    /**
     * Cancel a quest for a player
     * @param player The player
     * @param questId The quest ID
     */
    public void cancelQuest(Player player, String questId) {
        String key = player.getName() + ":" + questId;
        if (activeQuests.remove(key) != null) {
            player.sendMessage(ChatColor.YELLOW + "Quest cancelled!");
        }
    }
    
    /**
     * Check if player has an active quest
     * @param player The player
     * @param questId The quest ID
     * @return true if quest is active
     */
    public boolean hasActiveQuest(Player player, String questId) {
        return activeQuests.containsKey(player.getName() + ":" + questId);
    }
    
    /**
     * Get all active quests for a player
     * @param player The player
     * @return Map of active quests for this player
     */
    public Map<String, Quest> getPlayerQuests(Player player) {
        Map<String, Quest> playerQuests = new HashMap<>();
        String prefix = player.getName() + ":";
        
        for (Map.Entry<String, Quest> entry : activeQuests.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String questId = entry.getKey().substring(prefix.length());
                playerQuests.put(questId, entry.getValue());
            }
        }
        
        return playerQuests;
    }
    
    /**
     * Check for and cancel expired quests (called periodically)
     */
    public void checkAndCancelExpiredQuests(Player player) {
        String prefix = player.getName() + ":";
        java.util.List<String> expiredKeys = new java.util.ArrayList<>();
        
        for (Map.Entry<String, Quest> entry : activeQuests.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                Quest quest = entry.getValue();
                if (quest.isExpired(player.getWorld())) {
                    expiredKeys.add(entry.getKey());
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "⏰ Quest Expired: " + ChatColor.YELLOW + quest.getQuestName());
                        player.sendMessage(ChatColor.GRAY + "You ran out of time! The quest has been cancelled.");
                    }
                }
            }
        }
        
        // Remove expired quests
        expiredKeys.forEach(activeQuests::remove);
    }
    
    /**
     * Inner class to represent quest progress
     */
    public static class QuestProgress {
        private final String questName;
        private final int current;
        private final int required;
        
        public QuestProgress(String questName, int current, int required) {
            this.questName = questName;
            this.current = current;
            this.required = required;
        }
        
        public String getQuestName() {
            return questName;
        }
        
        public int getCurrent() {
            return current;
        }
        
        public int getRequired() {
            return required;
        }
        
        public boolean isComplete() {
            return current >= required;
        }
        
        public int getPercentage() {
            if (required <= 0) return 0;
            return Math.min(100, (int) ((current * 100.0) / required));
        }
    }
}
