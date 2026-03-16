package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DeathShopGUI implements Listener {
    private final SkillForgePlugin plugin;

    public DeathShopGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title("Death Shop"));
        
        // Example Items
        inv.setItem(11, createItem(Material.IRON_SWORD, "Starter Sword", 10));
        inv.setItem(13, createItem(Material.GOLDEN_APPLE, "Resurrection Fruit", 50));
        inv.setItem(15, createItem(Material.TOTEM_OF_UNDYING, "Life Bond", 100));

        ItemStack info = GuiStyle.item(Material.BOOK, ChatColor.RED + "" + ChatColor.BOLD + "Relics of the Fallen",
                java.util.List.of(ChatColor.GRAY + "Spend Death Points on recovery items.",
                                  ChatColor.DARK_GRAY + "Future upgrades will appear here."));
        inv.setItem(4, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, int cost) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(java.util.List.of(ChatColor.GRAY + "Cost: " + ChatColor.RED + cost + " Death Points"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Death Shop")) return;
        event.setCancelled(true);
        // Purchase logic would go here
    }
}
