package com.skilltree.plugin.systems;

public enum QuestDifficulty {
    EASY(1, "Easy"),
    NORMAL(2, "Normal"),
    HARD(3, "Hard"),
    HARDCORE(4, "Hardcore"),
    GODLIKE(5, "Godlike");
    
    private final int tier;
    private final String displayName;
    
    QuestDifficulty(int tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }
    
    public int getTier() {
        return tier;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the next difficulty level
     */
    public QuestDifficulty getNext() {
        if (this == GODLIKE) return GODLIKE;
        return values()[this.ordinal() + 1];
    }
    
    /**
     * Get mob kill quest amount for this difficulty
     * Mobs require player kills, so scaling is very high at top tiers
     */
    public int getMobKillAmount() {
        return switch(this) {
            case EASY -> 5;
            case NORMAL -> 10;
            case HARD -> 20;
            case HARDCORE -> 100;
            case GODLIKE -> 1_000_000;
        };
    }
    
    /**
     * Get standard quest amount for this difficulty
     * Used for: item collection, mining, breeding, blocks
     */
    public int getStandardAmount() {
        return switch(this) {
            case EASY -> 5;
            case NORMAL -> 10;
            case HARD -> 20;
            case HARDCORE -> 100;
            case GODLIKE -> 500;
        };
    }
    
    /**
     * Get farming quest amount (much higher due to easier mechanic)
     * Used for: farming/crop collection
     */
    public int getFarmingAmount() {
        return switch(this) {
            case EASY -> 100;
            case NORMAL -> 200;
            case HARD -> 500;
            case HARDCORE -> 1000;
            case GODLIKE -> 5000;
        };
    }
    
    /**
     * Get fishing quest amount
     * Fishing is moderately easier than mob kills but harder than farming
     */
    public int getFishingAmount() {
        return switch(this) {
            case EASY -> 50;
            case NORMAL -> 100;
            case HARD -> 250;
            case HARDCORE -> 500;
            case GODLIKE -> 2500;
        };
    }
}
