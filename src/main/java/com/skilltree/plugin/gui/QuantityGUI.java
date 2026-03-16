package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class QuantityGUI {
    public static final String TITLE_PREFIX = GuiStyle.title("Buy ");
    
    public static void open(Player player, ShopItem item) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + item.name());
        
        int[] amounts = {1, 8, 16, 32, 64};
        int[] slots = {10, 11, 13, 15, 16};
        
        for (int i = 0; i < amounts.length; i++) {
            inv.setItem(slots[i], item.toItemStack(amounts[i]));
        }
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cBack to Shop");
            back.setItemMeta(meta);
        }
        inv.setItem(22, back);

        ItemStack header = GuiStyle.item(Material.BOOK, "§6§lSelect Bundle",
                List.of("§7Select a quantity to purchase.",
                        "§7Costs scale with amount."));
        inv.setItem(4, header);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        
        player.openInventory(inv);
    }
}
