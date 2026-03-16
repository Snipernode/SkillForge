package com.skilltree.plugin.systems;

/**
 * Factory for creating common quests across different skill categories
 */
public class QuestFactory {
    
    // MOB KILL QUESTS
    public static Quest createZombieQuest(QuestDifficulty difficulty) {
        return new Quest("kill_zombies", "Zombie Slayer", "Defeat zombies in combat", 
                        Quest.QuestType.MOB_KILL, "mobkill_zombie", difficulty);
    }
    
    public static Quest createCreeperQuest(QuestDifficulty difficulty) {
        return new Quest("kill_creepers", "Creeper Killer", "Blast creepers to oblivion", 
                        Quest.QuestType.MOB_KILL, "mobkill_creeper", difficulty);
    }
    
    public static Quest createSkeletonQuest(QuestDifficulty difficulty) {
        return new Quest("kill_skeletons", "Skeleton Slayer", "Defeat skeletal warriors", 
                        Quest.QuestType.MOB_KILL, "mobkill_skeleton", difficulty);
    }
    
    public static Quest createSpiderQuest(QuestDifficulty difficulty) {
        return new Quest("kill_spiders", "Spider Hunter", "Hunt down spiders", 
                        Quest.QuestType.MOB_KILL, "mobkill_spider", difficulty);
    }

    public static Quest createEndermanQuest(QuestDifficulty difficulty) {
        return new Quest("kill_endermen", "Enderman Exterminator", "Hunt down endermen", 
                        Quest.QuestType.MOB_KILL, "mobkill_enderman", difficulty);
    }

    public static Quest createWitchQuest(QuestDifficulty difficulty) {
        return new Quest("kill_witches", "Witch Hunter", "Purge witches and their tricks", 
                        Quest.QuestType.MOB_KILL, "mobkill_witch", difficulty);
    }

    public static Quest createBlazeQuest(QuestDifficulty difficulty) {
        return new Quest("kill_blazes", "Blaze Buster", "Defeat blazes in the Nether", 
                        Quest.QuestType.MOB_KILL, "mobkill_blaze", difficulty);
    }

    public static Quest createWitherSkeletonQuest(QuestDifficulty difficulty) {
        return new Quest("kill_wither_skeletons", "Wither Skeleton Reaper", "Slay wither skeletons", 
                        Quest.QuestType.MOB_KILL, "mobkill_wither_skeleton", difficulty);
    }

    public static Quest createGuardianQuest(QuestDifficulty difficulty) {
        return new Quest("kill_guardians", "Guardian Bane", "Defeat ocean guardians", 
                        Quest.QuestType.MOB_KILL, "mobkill_guardian", difficulty);
    }

    public static Quest createGhastQuest(QuestDifficulty difficulty) {
        return new Quest("kill_ghasts", "Ghast Gasser", "Bring down ghasts from the skies", 
                        Quest.QuestType.MOB_KILL, "mobkill_ghast", difficulty);
    }
    
    // MINING QUESTS
    public static Quest createDiamondQuest(QuestDifficulty difficulty) {
        return new Quest("mine_diamonds", "Diamond Miner", "Mine precious diamonds", 
                        Quest.QuestType.MINING, "item_diamond", difficulty);
    }
    
    public static Quest createNetheriteQuest(QuestDifficulty difficulty) {
        return new Quest("mine_netherite", "Netherite Master", "Mine netherite ore from the depths", 
                        Quest.QuestType.MINING, "item_netherite", difficulty);
    }
    
    public static Quest createIronOreQuest(QuestDifficulty difficulty) {
        return new Quest("mine_iron", "Iron Collector", "Collect iron ore", 
                        Quest.QuestType.MINING, "item_iron_ore", difficulty);
    }
    
    public static Quest createGoldOreQuest(QuestDifficulty difficulty) {
        return new Quest("mine_gold", "Gold Digger", "Mine gold ore", 
                        Quest.QuestType.MINING, "item_gold_ore", difficulty);
    }

    public static Quest createCoalQuest(QuestDifficulty difficulty) {
        return new Quest("mine_coal", "Coal Miner", "Mine coal ore", 
                        Quest.QuestType.MINING, "item_coal", difficulty);
    }

    public static Quest createRedstoneQuest(QuestDifficulty difficulty) {
        return new Quest("mine_redstone", "Redstone Runner", "Mine redstone ore", 
                        Quest.QuestType.MINING, "item_redstone", difficulty);
    }

    public static Quest createEmeraldQuest(QuestDifficulty difficulty) {
        return new Quest("mine_emeralds", "Emerald Prospector", "Mine emerald ore", 
                        Quest.QuestType.MINING, "item_emerald", difficulty);
    }

    public static Quest createAncientDebrisQuest(QuestDifficulty difficulty) {
        return new Quest("mine_ancient_debris", "Ancient Debris Digger", "Mine ancient debris", 
                        Quest.QuestType.MINING, "item_ancient_debris", difficulty);
    }
    
    // FARMING QUESTS
    public static Quest createWheatQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_wheat", "Wheat Farmer", "Harvest wheat crops", 
                        Quest.QuestType.FARMING, "item_wheat", difficulty);
    }
    
    public static Quest createPotatoQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_potatoes", "Potato Master", "Harvest potatoes", 
                        Quest.QuestType.FARMING, "item_potato", difficulty);
    }
    
    public static Quest createCarrotQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_carrots", "Carrot Collector", "Harvest carrots", 
                        Quest.QuestType.FARMING, "item_carrot", difficulty);
    }

    public static Quest createSugarCaneQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_sugar_cane", "Sugar Cane Farmer", "Harvest sugar cane", 
                        Quest.QuestType.FARMING, "item_sugar_cane", difficulty);
    }

    public static Quest createBeetrootQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_beetroot", "Beetroot Harvester", "Harvest beetroot", 
                        Quest.QuestType.FARMING, "item_beetroot", difficulty);
    }

    public static Quest createNetherWartQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_nether_wart", "Nether Wart Farmer", "Harvest nether wart", 
                        Quest.QuestType.FARMING, "item_nether_wart", difficulty);
    }

    public static Quest createMelonQuest(QuestDifficulty difficulty) {
        return new Quest("harvest_melon", "Melon Baron", "Harvest melon slices", 
                        Quest.QuestType.FARMING, "item_melon_slice", difficulty);
    }
    
    public static Quest createBreedingQuest(QuestDifficulty difficulty) {
        return new Quest("breed_animals", "Animal Breeder", "Breed livestock", 
                        Quest.QuestType.BREEDING, "breed_all", difficulty);
    }

    public static Quest createCowBreedingQuest(QuestDifficulty difficulty) {
        return new Quest("breed_cows", "Cow Breeder", "Breed cows", 
                        Quest.QuestType.BREEDING, "breed_cow", difficulty);
    }
    
    // FISHING QUESTS
    public static Quest createFishingQuest(QuestDifficulty difficulty) {
        return new Quest("catch_fish", "Master Fisherman", "Catch fish from waters", 
                        Quest.QuestType.FISHING, "item_fish", difficulty);
    }
    
    public static Quest createTreasureQuest(QuestDifficulty difficulty) {
        return new Quest("fish_treasure", "Treasure Hunter", "Fish up rare treasures", 
                        Quest.QuestType.FISHING, "item_treasure", difficulty);
    }

    public static Quest createCodQuest(QuestDifficulty difficulty) {
        return new Quest("catch_cod", "Cod Catcher", "Catch cod while fishing", 
                        Quest.QuestType.FISHING, "item_cod", difficulty);
    }

    public static Quest createSalmonQuest(QuestDifficulty difficulty) {
        return new Quest("catch_salmon", "Salmon Seeker", "Catch salmon while fishing", 
                        Quest.QuestType.FISHING, "item_salmon", difficulty);
    }

    public static Quest createPufferfishQuest(QuestDifficulty difficulty) {
        return new Quest("catch_pufferfish", "Pufferfish Pro", "Catch pufferfish while fishing", 
                        Quest.QuestType.FISHING, "item_pufferfish", difficulty);
    }
    
    // BUILDING QUESTS
    public static Quest createBlockPlacementQuest(QuestDifficulty difficulty) {
        return new Quest("place_blocks", "Master Builder", "Place blocks to build", 
                        Quest.QuestType.BLOCK_PLACE, "place_all", difficulty);
    }

    public static Quest createTorchPlacementQuest(QuestDifficulty difficulty) {
        return new Quest("place_torches", "Torch Placer", "Place torches", 
                        Quest.QuestType.BLOCK_PLACE, "place_torch", difficulty);
    }

    public static Quest createCobblestonePlacementQuest(QuestDifficulty difficulty) {
        return new Quest("place_cobblestone", "Cobblestone Mason", "Place cobblestone blocks", 
                        Quest.QuestType.BLOCK_PLACE, "place_cobblestone", difficulty);
    }

    public static Quest createGlassPlacementQuest(QuestDifficulty difficulty) {
        return new Quest("place_glass", "Glass Architect", "Place glass blocks", 
                        Quest.QuestType.BLOCK_PLACE, "place_glass", difficulty);
    }
    
    public static Quest createBlockBreakQuest(QuestDifficulty difficulty) {
        return new Quest("break_blocks", "Demolition Expert", "Break blocks for resources", 
                        Quest.QuestType.BLOCK_BREAK, "block_all", difficulty);
    }

    public static Quest createStoneBreakQuest(QuestDifficulty difficulty) {
        return new Quest("break_stone", "Stone Breaker", "Break stone blocks", 
                        Quest.QuestType.BLOCK_BREAK, "block_stone", difficulty);
    }

    public static Quest createOakLogBreakQuest(QuestDifficulty difficulty) {
        return new Quest("break_oak_logs", "Oak Logger", "Break oak logs", 
                        Quest.QuestType.BLOCK_BREAK, "block_oak_log", difficulty);
    }

    public static Quest createDeepslateBreakQuest(QuestDifficulty difficulty) {
        return new Quest("break_deepslate", "Deepslate Driller", "Break deepslate blocks", 
                        Quest.QuestType.BLOCK_BREAK, "block_deepslate", difficulty);
    }
    
    // ITEM COLLECTION QUESTS
    public static Quest createItemCollectionQuest(String itemId, String itemName, QuestDifficulty difficulty) {
        return new Quest("collect_" + itemId, itemName + " Collector", "Collect " + itemName, 
                        Quest.QuestType.ITEM_COLLECTION, "item_" + itemId, difficulty);
    }
}
