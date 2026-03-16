package com.skilltree.plugin.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiStyle {
    private GuiStyle() {}

    public static final String BRAND = ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "SkillForge"
            + ChatColor.GOLD + " RPG";
    public static final Material FILLER = Material.BROWN_STAINED_GLASS_PANE;

    public static String title(String name) {
        return BRAND + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + name;
    }

    public static ItemStack pane(Material material) {
        return pane(material, " ");
    }

    public static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack fillerPane() {
        return pane(FILLER, ChatColor.DARK_GRAY + " ");
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void fillBorder(Inventory inv, ItemStack filler) {
        int size = inv.getSize();
        if (size % 9 != 0) {
            fillEmpty(inv, filler);
            return;
        }
        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                    int slot = row * 9 + col;
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, filler.clone());
                    }
                }
            }
        }
    }

    public static void fillEmpty(Inventory inv, ItemStack filler) {
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler.clone());
            }
        }
    }
}
