package com.skilltree.plugin.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public record ShopItem(
        Material material,
        String name,
        int baseCostPerUnit,
        ShopCategory category
) {
    public ItemStack toItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + name);
            meta.setLore(List.of(
                    "§7Quantity: §a" + amount,
                    "§7Total Cost: §e" + (baseCostPerUnit * amount) + " ES",
                    "",
                    "§eClick to buy this amount!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack toDisplayStack() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + name);
            meta.setLore(List.of(
                    "§7Unit Price: §e" + baseCostPerUnit + " ES",
                    "",
                    "§bClick to select amount!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
