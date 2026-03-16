package com.skilltree.plugin.systems;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;

public class Quest {
    
    private final String questId;
    private final String questName;
    private final String description;
    private final String trackingType;
    private final int requiredAmount;
    private final QuestDifficulty difficulty;
    private final QuestType questType;
    private long startWorldTick = -1L;
    private UUID startWorldId;
    private static final long QUEST_TIMEOUT_TICKS = 120000L; // 5 Minecraft days at 20 TPS (120,000 ticks)
    
    /**
     * Quest types with their own scaling mechanics
     */
    public enum QuestType {
        MOB_KILL("mob kills", true),      // Very hard at top tiers (1M)
        ITEM_COLLECTION("item collection", false),
        FARMING("farming", false),        // Easier, so much higher numbers
        FISHING("fishing", false),        // Moderate difficulty
        MINING("mining", false),
        BREEDING("breeding", false),
        BLOCK_BREAK("block breaking", false),
        BLOCK_PLACE("block placement", false);
        
        private final String displayName;
        private final boolean isHighScaling;
        
        QuestType(String displayName, boolean isHighScaling) {
            this.displayName = displayName;
            this.isHighScaling = isHighScaling;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public boolean isHighScaling() {
            return isHighScaling;
        }
    }
    
    /**
     * Create a new Quest with QuestType enum
     */
    public Quest(String questId, String questName, String description, QuestType questType, String trackingType, QuestDifficulty difficulty) {
        this.questId = questId;
        this.questName = questName;
        this.description = description;
        this.questType = questType;
        this.trackingType = trackingType;
        this.difficulty = difficulty;
        this.requiredAmount = getAmountForType(questType, difficulty);
        this.startWorldTick = -1L;
    }
    
    /**
     * Legacy constructor for backward compatibility
     */
    public Quest(String questId, String questName, String description, String questTypeStr, String trackingType, int requiredAmount) {
        this.questId = questId;
        this.questName = questName;
        this.description = description;
        this.questType = QuestType.ITEM_COLLECTION;
        this.trackingType = trackingType;
        this.requiredAmount = requiredAmount;
        this.difficulty = QuestDifficulty.EASY;
        this.startWorldTick = -1L;
    }
    
    /**
     * Legacy constructor with difficulty
     */
    public Quest(String questId, String questName, String description, String questTypeStr, String trackingType, QuestDifficulty difficulty, boolean isFarmingQuest) {
        this.questId = questId;
        this.questName = questName;
        this.description = description;
        this.questType = isFarmingQuest ? QuestType.FARMING : QuestType.ITEM_COLLECTION;
        this.trackingType = trackingType;
        this.difficulty = difficulty;
        this.requiredAmount = getAmountForType(this.questType, difficulty);
        this.startWorldTick = -1L;
    }
    
    private static int getAmountForType(QuestType type, QuestDifficulty difficulty) {
        return switch(type) {
            case MOB_KILL -> difficulty.getMobKillAmount();
            case FARMING -> difficulty.getFarmingAmount();
            case FISHING -> difficulty.getFishingAmount();
            default -> difficulty.getStandardAmount();
        };
    }
    
    public String getQuestId() {
        return questId;
    }
    
    public String getQuestName() {
        return questName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public QuestType getQuestTypeEnum() {
        return questType;
    }
    
    public QuestType getQuestType() {
        return questType;
    }
    
    public String getTrackingType() {
        return trackingType;
    }
    
    public int getRequiredAmount() {
        return requiredAmount;
    }
    
    /**
     * Get the full tracking ID for quest handler
     * @return The tracking ID (e.g., "mobkill_zombie")
     */
    public String getFullTrackingId() {
        return trackingType.toLowerCase();
    }
    
    public QuestDifficulty getDifficulty() {
        return difficulty;
    }
    
    /**
     * Create next difficulty level quest (for quest progression)
     */
    public Quest createNextDifficultyQuest() {
        QuestDifficulty nextDifficulty = difficulty.getNext();
        String baseName = questName.replaceAll(" - .*", "");
        return new Quest(questId, baseName + " - " + nextDifficulty.getDisplayName(), 
                        description, questType, trackingType, nextDifficulty);
    }
    
    public long getStartWorldTick() {
        return startWorldTick;
    }

    /**
     * @deprecated Use getStartWorldTick()
     */
    @Deprecated
    public long getStartTime() {
        return startWorldTick;
    }

    public void markStarted(World world) {
        if (world == null) {
            return;
        }
        this.startWorldId = world.getUID();
        this.startWorldTick = world.getFullTime();
    }
    
    /**
     * Check if quest has expired based on world-time ticks (5 Minecraft days = 120,000 ticks)
     */
    public boolean isExpired(World currentWorld) {
        long elapsedTicks = getElapsedWorldTicks(currentWorld);
        if (elapsedTicks < 0) {
            return false;
        }
        return elapsedTicks > QUEST_TIMEOUT_TICKS;
    }
    
    public long getTimeRemainingTicks(World currentWorld) {
        long elapsedTicks = getElapsedWorldTicks(currentWorld);
        if (elapsedTicks < 0) {
            return QUEST_TIMEOUT_TICKS;
        }
        return Math.max(0, QUEST_TIMEOUT_TICKS - elapsedTicks);
    }

    public long getTimeRemainingMs(World currentWorld) {
        return getTimeRemainingTicks(currentWorld) * 50L;
    }

    private long getElapsedWorldTicks(World currentWorld) {
        if (startWorldTick < 0) {
            return -1L;
        }
        World world = resolveWorld(currentWorld);
        if (world == null) {
            return -1L;
        }
        return world.getFullTime() - startWorldTick;
    }

    private World resolveWorld(World currentWorld) {
        if (currentWorld != null && startWorldId != null && startWorldId.equals(currentWorld.getUID())) {
            return currentWorld;
        }
        if (startWorldId != null) {
            World startWorld = Bukkit.getWorld(startWorldId);
            if (startWorld != null) {
                return startWorld;
            }
        }
        return currentWorld;
    }
}
