package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.SkillTreeSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.skilltree.plugin.gui.ShopGUI;

import java.util.*;
import java.util.stream.Collectors;

public class SkillTreeGUI implements Listener {
    private final SkillForgePlugin plugin;
    private final Player player;
    private Inventory inventory;
    private final Map<Integer, ShopItem> shopItems;

    // Pagination State
    private int currentPage = 0;
    private CreativeTab currentTab = CreativeTab.BUILDING_BLOCKS;

    // Define Creative Tabs
    private enum CreativeTab {
        BUILDING_BLOCKS("§a§lBuilding Blocks", Material.BRICKS),
        COLORED_BLOCKS("§d§lColored Blocks", Material.MAGENTA_WOOL),
        NATURAL_BLOCKS("§2§lNatural Blocks", Material.GRASS_BLOCK),
        FUNCTIONAL_BLOCKS("§e§lFunctional Blocks", Material.CRAFTING_TABLE),
        REDSTONE_BLOCKS("§c§lRedstone Blocks", Material.REDSTONE_BLOCK),
        TOOLS_AND_UTILITIES("§b§lTools & Utilities", Material.IRON_PICKAXE),
        COMBAT("§6§lCombat", Material.DIAMOND_SWORD),
        FOOD_AND_DRINKS("§f§lFood & Drinks", Material.COOKED_BEEF),
        INGREDIENTS("§7§lIngredients", Material.SUGAR),
        SPAWN_EGGS("§e§lSpawn Eggs", Material.COW_SPAWN_EGG),
        MASTERY("§5§lWeapon Mastery", Material.NETHERITE_SWORD);
        
        final String displayName;
        final Material icon;
        
        CreativeTab(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
    }

    // Data structure to hold items per creative tab
    private final Map<CreativeTab, List<ShopItem>> tabItems = new EnumMap<>(CreativeTab.class);

    public SkillTreeGUI(SkillForgePlugin plugin, Player player) {
    this.plugin = plugin;
    this.player = player;
    this.shopItems = new HashMap<>();
    
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    initializeCreativeItems(); // Populate all items
    updateInventory();        // Create and fill the inventory
}


    private void initializeCreativeItems() {
        // Helper to add items
        java.util.function.BiConsumer<CreativeTab, ShopItem> adder = (tab, item) -> 
            tabItems.computeIfAbsent(tab, k -> new ArrayList<>()).add(item);

        // Define exclusions (Items not obtainable in survival or specific exclusions)
        Set<Material> exclusions = new HashSet<>();
        exclusions.add(Material.DRAGON_EGG);
        exclusions.add(Material.NETHER_STAR);
        exclusions.add(Material.BARRIER);
        exclusions.add(Material.COMMAND_BLOCK);
        exclusions.add(Material.CHAIN_COMMAND_BLOCK);
        exclusions.add(Material.REPEATING_COMMAND_BLOCK);
        exclusions.add(Material.STRUCTURE_BLOCK);
        exclusions.add(Material.JIGSAW);
        exclusions.add(Material.DEBUG_STICK);
        exclusions.add(Material.KNOWLEDGE_BOOK);
        exclusions.add(Material.PLAYER_HEAD); // Usually obtained via other means
        exclusions.add(Material.SKELETON_SKULL); // Heads are often special
        exclusions.add(Material.WITHER_SKELETON_SKULL);
        exclusions.add(Material.CREEPER_HEAD);
        exclusions.add(Material.ZOMBIE_HEAD);
        exclusions.add(Material.DRAGON_HEAD);
        exclusions.add(Material.PISTON_HEAD); // Technical block
        
        // Iterate over all materials in the game
        for (Material material : Material.values()) {
            if (material.isAir()) continue;
            if (exclusions.contains(material)) continue;

            // 1. Check for Spawn Eggs - EXCLUDED
            // We explicitly skip these as per requirements.
            ItemStack tempItem = new ItemStack(material);
            if (tempItem.getItemMeta() instanceof org.bukkit.inventory.meta.SpawnEggMeta) {
                continue; 
            }

            // 2. Check for Tools & Utilities
            if (material.name().endsWith("_PICKAXE") || material.name().endsWith("_AXE") || 
                material.name().endsWith("_SHOVEL") || material.name().endsWith("_HOE") || 
                material.name().endsWith("_SWORD") || material.name().endsWith("_SHEARS") || 
                material.name().endsWith("_FLINT_AND_STEEL") || material.name().endsWith("_FISHING_ROD") ||
                material.name().endsWith("_COMPASS") || material.name().endsWith("_CLOCK")) {
                adder.accept(CreativeTab.TOOLS_AND_UTILITIES, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 3. Check for Combat
            if (material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE") || 
                material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS") || 
                material.name().endsWith("_SHIELD") || material.name().endsWith("_BOW") || 
                material.name().endsWith("_CROSSBOW") || material.name().endsWith("_TRIDENT") || 
                material.name().endsWith("_ARROW") || material.name().endsWith("_TIPPED_ARROW") || 
                material.name().endsWith("_SPECTRAL_ARROW")) {
                adder.accept(CreativeTab.COMBAT, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 4. Check for Food
            if (material.isEdible()) {
                adder.accept(CreativeTab.FOOD_AND_DRINKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 5. Check for Ingredients / Brewing / Misc
            if (material.isFuel() || material.name().endsWith("_DYE") || 
                material.name().endsWith("POTION") || material.name().endsWith("SPLASH_POTION") || 
                material.name().endsWith("LINGERING_POTION") || material.name().contains("GLASS_BOTTLE") ||
                material.name().contains("GLASS_PANE") || material.name().contains("STRING") ||
                material.name().contains("LEATHER") || material.name().contains("FEATHER") ||
                material.name().contains("GUNPOWDER") || material.name().contains("BLAZE_POWDER") ||
                material.name().contains("NETHER_WART") || material.name().contains("RABBIT_FOOT") ||
                material.name().contains("GLOWSTONE_DUST") || material.name().contains("REDSTONE_DUST") ||
                material.name().contains("SUGAR") || material.name().contains("SPIDER_EYE") ||
                material.name().contains("FERMENTED_SPIDER_EYE") || material.name().contains("GLISTERING_MELON_SLICE") ||
                material.name().contains("GOLDEN_CARROT") || material.name().contains("MAGMA_CREAM") ||
                material.name().contains("PHANTOM_MEMBRANE") || material.name().contains("TURTLE_HELMET") || // Helmet is combat, but scute is ingredient
                material.name().contains("SCUTE") || material.name().contains("NAUTILUS_SHELL") ||
                material.name().contains("HEART_OF_THE_SEA") || material.name().contains("PRISMARINE") ||
                material.name().contains("SHULKER_SHELL")) {
                adder.accept(CreativeTab.INGREDIENTS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 6. Check for Redstone Blocks
            if (material.name().contains("REDSTONE") || material.name().contains("REPEATER") || 
                material.name().contains("COMPARATOR") || material.name().contains("PISTON") || 
                material.name().contains("OBSERVER") || material.name().contains("HOPPER") || 
                material.name().contains("DROPPER") || material.name().contains("DISPENSER") ||
                material.name().contains("LEVER") || material.name().contains("BUTTON") || 
                material.name().contains("PRESSURE_PLATE") || material.name().contains("TRIPWIRE") ||
                material.name().contains("DAYLIGHT_DETECTOR") || material.name().contains("NOTE_BLOCK") ||
                material.name().contains("RAIL")) {
                adder.accept(CreativeTab.REDSTONE_BLOCKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 7. Check for Functional Blocks
            if (material.name().contains("CRAFTING_TABLE") || material.name().contains("FURNACE") || 
                material.name().contains("CHEST") || material.name().contains("BARREL") || 
                material.name().contains("SHULKER_BOX") || material.name().contains("BED") || 
                material.name().contains("ANVIL") || material.name().contains("GRINDSTONE") || 
                material.name().contains("SMITHING_TABLE") || material.name().contains("FLETCHING_TABLE") ||
                material.name().contains("BREWING_STAND") || material.name().contains("ENCHANTING_TABLE") ||
                material.name().contains("LOOM") || material.name().contains("CARTOGRAPHY_TABLE") ||
                material.name().contains("STONECUTTER") || material.name().contains("SMOKER") || 
                material.name().contains("BLAST_FURNACE") || material.name().contains("CAMPFIRE") ||
                material.name().contains("SOUL_CAMPFIRE") || material.name().contains("BELL") ||
                material.name().contains("LECTERN") || material.name().contains("COMPOSTER") ||
                material.name().contains("BEEHIVE") || material.name().contains("BEE_NEST") || 
                material.name().contains("RESPAWN_ANCHOR")) {
                adder.accept(CreativeTab.FUNCTIONAL_BLOCKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 8. Check for Colored Blocks
            if (material.name().contains("_WOOL") || material.name().contains("_CARPET") || 
                material.name().contains("_TERRACOTTA") || material.name().contains("GLAZED_TERRACOTTA") ||
                material.name().contains("_CONCRETE") || material.name().contains("_CONCRETE_POWDER") ||
                material.name().contains("_STAINED_GLASS") || material.name().contains("_STAINED_GLASS_PANE")) {
                adder.accept(CreativeTab.COLORED_BLOCKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 9. Check for Natural Blocks
            if (material.name().contains("DIRT") || material.name().contains("STONE") || 
                material.name().contains("SAND") || material.name().contains("GRAVEL") || 
                material.name().contains("LOG") || material.name().contains("WOOD") || 
                material.name().contains("PLANKS") || material.name().contains("LEAVES") || 
                material.name().contains("SAPLING") || material.name().contains("GRASS") || 
                material.name().contains("MYCELIUM") || material.name().contains("PODZOL") || 
                material.name().contains("COARSE_DIRT") || material.name().contains("NYLIUM") || 
                material.name().contains("ROOTS") || material.name().contains("FUNGUS") || 
                material.name().contains("VINES") || material.name().contains("LILY_PAD") || 
                material.name().contains("BAMBOO") || material.name().contains("COCOA") || 
                material.name().contains("SUGAR_CANE") || material.name().contains("CACTUS") || 
                material.name().contains("MELON") || material.name().contains("PUMPKIN") || 
                material.name().contains("SWEET_BERRY") || material.name().contains("KELP") || 
                material.name().contains("SEAGRASS") || material.name().contains("CORAL") || 
                material.name().contains("SEA_PICKLE") || material.name().contains("OBSIDIAN") || 
                material.name().contains("CRYING_OBSIDIAN") || material.name().contains("DEEPSLATE") || 
                material.name().contains("TUFF") || material.name().contains("CALCITE") || 
                material.name().contains("AMETHYST") || material.name().contains("SCULK") || 
                material.name().contains("MOSS") || material.name().contains("DRIPSTONE") || 
                material.name().contains("POINTED_DRIPSTONE") || material.name().contains("CAVE_VINES")) {
                adder.accept(CreativeTab.NATURAL_BLOCKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
                continue;
            }

            // 10. Default to Building Blocks if nothing else matched
            if (material.isBlock()) {
                adder.accept(CreativeTab.BUILDING_BLOCKS, new ShopItem(material, formatName(material.name()), calculatePrice(material), 1));
            }
        }

        // Add Weapon Mastery Skills to the Mastery Tab
        plugin.getSkillTreeSystem().getSkillsByCategory("mastery").forEach(node -> {
            adder.accept(CreativeTab.MASTERY, new ShopItem(Material.BOOK, node.getName(), node.getCostPerLevel(), 1));
        });

        // --- SPECIAL ADDITION ---
        // Add Emerald specifically as a currency converter
        adder.accept(CreativeTab.INGREDIENTS, new ShopItem(Material.EMERALD, "Sell Emerald (200 ES)", -200, 1));

        // IMPORTANT: After populating, sort the items in each list alphabetically by name for consistency.
        tabItems.values().forEach(list -> list.sort(Comparator.comparing(item -> item.name)));
    }

    /**
     * Helper method to calculate price based on material properties.
     */
    private int calculatePrice(Material material) {
        int basePrice = 1;
        
        // Rarity/Type checks
        if (material.name().contains("NETHERITE")) basePrice = 500;
        else if (material.name().contains("DIAMOND")) basePrice = 100;
        else if (material.name().contains("IRON") || material.name().contains("GOLD")) basePrice = 25;
        else if (material.name().contains("DRAGON") || material.name().contains("ELYTRA")) basePrice = 5000;
        else if (material.name().contains("TOTEM")) basePrice = 1000;
        else if (material.name().contains("ENCHANTED")) basePrice = 500;
        else if (material.name().contains("MUSIC_DISC")) basePrice = 500;
        else if (material.name().contains("SPAWN_EGG")) basePrice = 100;
        else if (material.name().contains("END_CRYSTAL")) basePrice = 500;
        else if (material.name().contains("SHULKER_BOX")) basePrice = 100;
        else if (material.name().contains("SCULK") || material.name().contains("ECHO")) basePrice = 50;
        
        // Adjust for stack size (typically items that stack to 1 are more valuable per unit)
        if (material.getMaxStackSize() == 1) {
            basePrice *= 5;
        }
        
        return basePrice;
    }

    /**
     * Helper method to format enum names to readable strings (e.g., DIAMOND_SWORD -> Diamond Sword)
     */
    private String formatName(String enumName) {
        return Arrays.stream(enumName.toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }


    private void updateInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(null, 54, GuiStyle.title("Merchant's Emporium"));
        } else {
            inventory.clear();
        }
        shopItems.clear();

        // --- TOP BAR (Creative Tabs) ---
        int tabSlot = 0;
        for (CreativeTab tab : CreativeTab.values()) {
            ItemStack item = new ItemStack(tab.icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(tab.displayName);
            List<String> lore = new ArrayList<>();
            if (tab == currentTab) {
                lore.add("§aSelected");
            } else {
                lore.add("§7Click to view");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(tabSlot, item);
            tabSlot++;
        }
        
        // --- CENTER (Items) ---
        List<ShopItem> items = tabItems.get(currentTab);
        if (items != null) {
            int itemsPerPage = 28; // 4 rows of 7 (slots 10-16, 19-25, 28-34, 37-43)
            int startIndex = currentPage * itemsPerPage;
            
            for (int i = 0; i < itemsPerPage; i++) {
                int itemIndex = startIndex + i;
                if (itemIndex >= items.size()) break;
                
                ShopItem shopItem = items.get(itemIndex);
                
                int row = i / 7;
                int col = i % 7;
                int slot = 10 + (row * 9) + col;
                
                addShopItemToInv(slot, shopItem);
            }
        }

        // --- BOTTOM BAR (Navigation) ---
        // Info Item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b§lMerchant's Notes");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Purchase items with Evershards (ES)");
        infoLore.add("");
        infoLore.add("§7Your ES: §a" + plugin.getPlayerDataManager().getPlayerData(player).getEvershards());
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        // Back Button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§lBack to Menu");
        back.setItemMeta(backMeta);
        inventory.setItem(45, back);

        // Page Navigation
        ItemStack prevPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevPage.getItemMeta();
        prevMeta.setDisplayName("§e§lPrevious Page");
        prevMeta.setLore(List.of("§7Go to previous page"));
        prevPage.setItemMeta(prevMeta);
        inventory.setItem(48, prevPage); // Left of info

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        nextMeta.setDisplayName("§e§lNext Page");
        nextMeta.setLore(List.of("§7Go to next page"));
        nextPage.setItemMeta(nextMeta);
        inventory.setItem(50, nextPage); // Right of info

        GuiStyle.fillEmpty(inventory, GuiStyle.fillerPane());
        
    }
    
    private void addShopItemToInv(int slot, ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.material, shopItem.amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + shopItem.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Cost: §e" + shopItem.cost + " ES");
        lore.add("");
        lore.add("§eClick to purchase!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        inventory.setItem(slot, item);
        shopItems.put(slot, shopItem);
    }
    
    public void open() {
        updateInventory();
        player.openInventory(inventory);
    }
    
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTopInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        
        // Handle Tab Selection (Top Row)
        if (slot >= 0 && slot < CreativeTab.values().length) {
            CreativeTab[] tabs = CreativeTab.values();
            if (slot < tabs.length) {
                currentTab = tabs[slot];
                currentPage = 0; // Reset page on tab change
                updateInventory();
            }
            return;
        }
        
        // Handle Navigation (Bottom Row)
        if (slot == 45) { // Back
            player.closeInventory();
            if (plugin.getUnifiedPanelGUI() != null) {
                plugin.getUnifiedPanelGUI().openForPlayer(player);
            }
            return;
        }
        
        if (slot == 48) { // Previous Page
            if (currentPage > 0) {
                currentPage--;
                updateInventory();
            }
            return;
        }
        
        if (slot == 50) { // Next Page
            List<ShopItem> items = tabItems.get(currentTab);
            int itemsPerPage = 28;
            if (items != null && (currentPage + 1) * itemsPerPage < items.size()) {
                currentPage++;
                updateInventory();
            }
            return;
        }

        // Handle Purchase
        ShopItem shopItem = shopItems.get(slot);
        if (shopItem != null) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            
            // Special handling for Skill Upgrades in Mastery Tab
            if (currentTab == CreativeTab.MASTERY) {
                String skillId = findSkillIdByName(shopItem.name);
                if (skillId != null) {
                    SkillTreeSystem.UpgradeResult result = plugin.getSkillTreeSystem().tryUpgradeSkill(player, skillId);
                    if (result == SkillTreeSystem.UpgradeResult.SUCCESS) {
                        player.sendMessage("§a§l[SkillForge] §eUpgraded " + shopItem.name + "!");
                        updateInventory();
                    } else {
                        player.sendMessage("§c§l[SkillForge] §c" + plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, skillId, result));
                    }
                    return;
                }
            }
            
            if (data.removeEvershards(shopItem.cost)) {
                ItemStack purchased = new ItemStack(shopItem.material, shopItem.amount);
                player.getInventory().addItem(purchased);
                player.sendMessage("§a§l[SkillForge] §ePurchased " + shopItem.name + "!");
                updateInventory(); // Refresh to show new ES balance
            } else {
                player.sendMessage("§c§l[SkillForge] §cNot enough Evershards!");
            }
        }
    }
    
    private String findSkillIdByName(String name) {
        return plugin.getSkillTreeSystem().getAllSkillNodes().values().stream()
                .filter(node -> node.getName().equalsIgnoreCase(name))
                .map(node -> node.getId())
                .findFirst()
                .orElse(null);
    }

    private static class ShopItem {
        final Material material;
        final String name;
        final int cost;
        final int amount;
        
        ShopItem(Material material, String name, int cost, int amount) {
            this.material = material;
            this.name = name;
            this.cost = cost;
            this.amount = amount;
        }
    }
}
