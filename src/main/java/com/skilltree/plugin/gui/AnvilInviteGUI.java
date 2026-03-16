package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AnvilInviteGUI implements Listener {

    private final SkillForgePlugin plugin;
    private final Player viewer;
    private final String guildName;
    private final Inventory inv;

    public AnvilInviteGUI(SkillForgePlugin plugin, Player viewer, String guildName) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.guildName = guildName;
        this.inv = Bukkit.createInventory(null, 3, GuiStyle.title("Invite: " + guildName));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        build();
    }

    private void build() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta pm = paper.getItemMeta();
        if (pm != null) {
            pm.setDisplayName("§eSeal an invitation");
            pm.setLore(java.util.List.of("§7Result slot will show the name.",
                                        "§7Click the result to send invite."));
            paper.setItemMeta(pm);
        }
        inv.setItem(0, paper);
    }

    public void open() {
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(inv)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        // Anvil result slot is 2
        if (slot == 2) {
            ItemStack result = inv.getItem(2);
            if (result != null && result.getItemMeta() != null && result.getItemMeta().hasDisplayName()) {
                String name = result.getItemMeta().getDisplayName();
                // Clean color codes
                name = org.bukkit.ChatColor.stripColor(name).trim();
                plugin.getGuildSystem().invitePlayerByName(viewer, name);
            }
            p.closeInventory();
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getInventory().equals(inv)) return;
        HandlerList.unregisterAll(this);
    }
}
