package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ShopGUI {
    public static final String MAIN_TITLE = GuiStyle.title("Market Square");
    public static final String CATEGORY_TITLE_PREFIX = GuiStyle.title("Market: ");
    private final SkillForgePlugin plugin;

    public ShopGUI(SkillForgePlugin plugin, Player player) {
        this.plugin = plugin;
        openCategories(player);
    }

    public static void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
        
        int slot = 10;
        for (ShopCategory cat : ShopCategory.values()) {
            ItemStack item = new ItemStack(getCategoryMaterial(cat));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(cat.getDisplayName());
                meta.setLore(List.of("§7Click to view items"));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack info = GuiStyle.item(Material.BOOK, "§6§lMarket Herald",
                List.of("§7Spend Evershards on items.",
                        "§7Select a category to browse."));
        inv.setItem(4, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        player.openInventory(inv);
    }

    public static void openCategoryItems(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(null, 54, CATEGORY_TITLE_PREFIX + category.name());
        List<ShopItem> items = ShopItemRegistry.getItemsByCategory(category);
        for (int i = 0; i < items.size() && i < 45; i++) {
            inv.setItem(i, items.get(i).toDisplayStack());
        }
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cBack to Categories");
            back.setItemMeta(meta);
        }
        inv.setItem(49, back);

        ItemStack info = GuiStyle.item(Material.BOOK, "§6§l" + category.getDisplayName(),
                List.of("§7Browse wares and select amounts."));
        inv.setItem(4, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        
        player.openInventory(inv);
    }

    private static Material getCategoryMaterial(ShopCategory cat) {
        return switch (cat) {
            case OVERWORLD_RESOURCES -> Material.GRASS_BLOCK;
            case NETHER_RESOURCES -> Material.NETHERRACK;
            case SEEDS_AND_FARMING -> Material.WHEAT_SEEDS;
            default -> Material.CHEST;
        };
    }
    
    public void open() {
        // Legacy support if needed
    }
}
