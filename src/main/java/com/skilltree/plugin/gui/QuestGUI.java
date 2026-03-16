package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.Quest;
import com.skilltree.plugin.systems.QuestDifficulty;
import com.skilltree.plugin.systems.QuestFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class QuestGUI implements Listener {
    private final SkillForgePlugin plugin;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private static final Map<String, Quest> QUEST_CACHE = new HashMap<>();
    
    static {
        // Initialize quest cache with all available quests
        // EASY QUESTS
        QUEST_CACHE.put("easy_zombies", QuestFactory.createZombieQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_wheat", QuestFactory.createWheatQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_fishing", QuestFactory.createFishingQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_iron", QuestFactory.createIronOreQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_coal", QuestFactory.createCoalQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_cod", QuestFactory.createCodQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_sugarcane", QuestFactory.createSugarCaneQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_stone", QuestFactory.createStoneBreakQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_torches", QuestFactory.createTorchPlacementQuest(QuestDifficulty.EASY));
        QUEST_CACHE.put("easy_cows", QuestFactory.createCowBreedingQuest(QuestDifficulty.EASY));
        
        // NORMAL QUESTS
        QUEST_CACHE.put("med_skeletons", QuestFactory.createSkeletonQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_creepers", QuestFactory.createCreeperQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_potatoes", QuestFactory.createPotatoQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_gold", QuestFactory.createGoldOreQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_breeding", QuestFactory.createBreedingQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_treasure", QuestFactory.createTreasureQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_endermen", QuestFactory.createEndermanQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_redstone", QuestFactory.createRedstoneQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_beetroot", QuestFactory.createBeetrootQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_salmon", QuestFactory.createSalmonQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_oak_logs", QuestFactory.createOakLogBreakQuest(QuestDifficulty.NORMAL));
        QUEST_CACHE.put("med_cobblestone", QuestFactory.createCobblestonePlacementQuest(QuestDifficulty.NORMAL));
        
        // HARD QUESTS
        QUEST_CACHE.put("hard_spiders", QuestFactory.createSpiderQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_diamonds", QuestFactory.createDiamondQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_carrots", QuestFactory.createCarrotQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_blocks", QuestFactory.createBlockBreakQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_blazes", QuestFactory.createBlazeQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_witches", QuestFactory.createWitchQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_emeralds", QuestFactory.createEmeraldQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_nether_wart", QuestFactory.createNetherWartQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_pufferfish", QuestFactory.createPufferfishQuest(QuestDifficulty.HARD));
        QUEST_CACHE.put("hard_deepslate", QuestFactory.createDeepslateBreakQuest(QuestDifficulty.HARD));
        
        // HARDCORE QUESTS
        QUEST_CACHE.put("extreme_netherite", QuestFactory.createNetheriteQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_placement", QuestFactory.createBlockPlacementQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_wither_skeletons", QuestFactory.createWitherSkeletonQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_guardians", QuestFactory.createGuardianQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_ghasts", QuestFactory.createGhastQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_ancient_debris", QuestFactory.createAncientDebrisQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_melon", QuestFactory.createMelonQuest(QuestDifficulty.HARDCORE));
        QUEST_CACHE.put("extreme_glass", QuestFactory.createGlassPlacementQuest(QuestDifficulty.HARDCORE));
    }

    public QuestGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        openDifficultySelection(player);
    }
    
    private void openDifficultySelection(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title("Quest Board - Select Difficulty"));

        int easyCount = getQuestsByDifficulty(QuestDifficulty.EASY).size();
        int normalCount = getQuestsByDifficulty(QuestDifficulty.NORMAL).size();
        int hardCount = getQuestsByDifficulty(QuestDifficulty.HARD).size();
        int hardcoreCount = getQuestsByDifficulty(QuestDifficulty.HARDCORE).size();
        
        // Easy Quests
        ItemStack easyItem = createDifficultyItem(Material.GRASS_BLOCK, "Easy Quests", 
            ChatColor.GREEN + "Beginner challenges", easyCount + " quests available");
        inv.setItem(11, easyItem);
        
        // Normal Quests
        ItemStack normalItem = createDifficultyItem(Material.IRON_ORE, "Normal Quests", 
            ChatColor.YELLOW + "Standard challenges", normalCount + " quests available");
        inv.setItem(13, normalItem);
        
        // Hard Quests
        ItemStack hardItem = createDifficultyItem(Material.DIAMOND_ORE, "Hard Quests", 
            ChatColor.LIGHT_PURPLE + "Veteran challenges", hardCount + " quests available");
        inv.setItem(15, hardItem);
        
        // Hardcore Quests
        ItemStack hardcoreItem = createDifficultyItem(Material.NETHERITE_BLOCK, "Hardcore Quests", 
            ChatColor.RED + "Legendary challenges", hardcoreCount + " quests available");
        inv.setItem(22, hardcoreItem);

        ItemStack header = GuiIcons.icon(plugin, "quest", Material.BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Adventurer's Board",
                Arrays.asList(ChatColor.GRAY + "Pick a difficulty to view quests.",
                              ChatColor.DARK_GRAY + "You can hold up to 3 active quests."));
        inv.setItem(4, header);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        
        trackInventory(player, inv);
        player.openInventory(inv);
    }

    private void trackInventory(Player player, Inventory inv) {
        if (player == null || inv == null) return;
        openInventories.put(player.getUniqueId(), inv);
    }

    private boolean isTrackedInventory(Player player, Inventory top) {
        if (player == null || top == null) return false;
        Inventory tracked = openInventories.get(player.getUniqueId());
        return tracked != null && tracked.equals(top);
    }
    
    private void openQuestList(Player player, QuestDifficulty difficulty) {
        Inventory inv = Bukkit.createInventory(null, 54, GuiStyle.title(difficulty.getDisplayName() + " Quests"));
        
        List<Quest> quests = getQuestsByDifficulty(difficulty);
        int slot = 0;
        
        for (Quest quest : quests) {
            if (slot >= 45) break;
            
            ItemStack questItem = createQuestItem(player, quest);
            inv.setItem(slot, questItem);
            slot++;
        }
        
        // Info item
        ItemStack info = GuiIcons.icon(plugin, "quest", Material.BOOK, ChatColor.BLUE + "Adventurer's Notice",
                Arrays.asList(
                        ChatColor.YELLOW + "• You can have up to 3 active quests",
                        ChatColor.YELLOW + "• Quests have a 5 day time limit",
                        ChatColor.YELLOW + "• Complete for skill points & rewards!"
                ));
        inv.setItem(49, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        
        trackInventory(player, inv);
        player.openInventory(inv);
    }
    
    private ItemStack createDifficultyItem(Material material, String name, String color, String subtitle) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + subtitle);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to view quests");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createQuestItem(Player player, Quest quest) {
        Material material = getQuestMaterial(quest.getQuestType());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + quest.getQuestName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + quest.getDescription());
            lore.add("");
            
            // Progress info
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            int progress = data.getQuestProgress(quest.getFullTrackingId());
            int required = quest.getRequiredAmount();
            
            if (progress > 0) {
                lore.add(ChatColor.AQUA + "Progress: " + ChatColor.WHITE + progress + "/" + required);
            } else {
                lore.add(ChatColor.GRAY + "Required: " + ChatColor.YELLOW + required);
            }
            
            lore.add("");
            lore.add(ChatColor.GREEN + "Reward: " + quest.getRequiredAmount() / 10 + " Skill Points");
            lore.add(ChatColor.YELLOW + "Click to accept quest");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private Material getQuestMaterial(Quest.QuestType type) {
        return switch(type) {
            case MOB_KILL -> Material.DIAMOND_SWORD;
            case MINING -> Material.DIAMOND_PICKAXE;
            case FARMING -> Material.GOLDEN_HOE;
            case FISHING -> Material.FISHING_ROD;
            case BREEDING -> Material.SADDLE;
            case BLOCK_BREAK -> Material.STONE;
            case BLOCK_PLACE -> Material.GRASS_BLOCK;
            default -> Material.BOOK;
        };
    }
    
    private List<Quest> getQuestsByDifficulty(QuestDifficulty difficulty) {
        List<Quest> quests = new ArrayList<>();
        for (Quest quest : QUEST_CACHE.values()) {
            if (quest.getDifficulty() == difficulty) {
                quests.add(quest);
            }
        }
        quests.sort(Comparator.comparing(Quest::getQuestName));
        return quests;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null || !clicked.hasItemMeta()) return;
        
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        
        // Difficulty selection
        if (displayName.contains("Easy Quests")) {
            openQuestList(player, QuestDifficulty.EASY);
        } else if (displayName.contains("Normal Quests")) {
            openQuestList(player, QuestDifficulty.NORMAL);
        } else if (displayName.contains("Hard Quests")) {
            openQuestList(player, QuestDifficulty.HARD);
        } else if (displayName.contains("Hardcore Quests")) {
            openQuestList(player, QuestDifficulty.HARDCORE);
        } else {
            // Find and accept quest
            Quest quest = findQuestByName(displayName);
            if (quest != null) {
                if (plugin.getQuestLeader().startQuest(player, quest)) {
                    player.closeInventory();
                }
            }
        }
    }
    
    private Quest findQuestByName(String name) {
        for (Quest quest : QUEST_CACHE.values()) {
            if (quest.getQuestName().equalsIgnoreCase(name)) {
                return quest;
            }
        }
        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Inventory tracked = openInventories.get(player.getUniqueId());
        if (tracked != null && tracked.equals(top)) {
            openInventories.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;
        event.setCancelled(true);
    }
}
