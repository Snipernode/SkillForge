package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.GuildData;
import com.skilltree.plugin.systems.GuildSystem;
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

import java.util.ArrayList;
import java.util.List;

public class GuildListGUI implements Listener {

    private final SkillForgePlugin plugin;
    private final Player viewer;
    private final Inventory inv;
    private final int page;
    private final List<GuildData> all;
    private static final int PER_PAGE = 45;

    public GuildListGUI(SkillForgePlugin plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(null, 54, GuiStyle.title("Guilds — Page " + (this.page+1)));
        this.all = new ArrayList<>(plugin.getGuildSystem().getAllGuilds());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        build();
    }

    private void build() {
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            GuildData g = all.get(idx);
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName("§e" + g.getGuildName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Leader: §a" + Bukkit.getOfflinePlayer(g.getLeaderId()).getName());
            lore.add("§7Members: §a" + g.getMemberCount());
            lore.add("§7Click to view guild");
            im.setLore(lore);
            it.setItemMeta(im);
            inv.setItem(i, it);
        }

        // Navigation
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta(); pm.setDisplayName("§aPrevious"); prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }
        if ((page+1) * PER_PAGE < all.size()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta(); nm.setDisplayName("§aNext"); next.setItemMeta(nm);
            inv.setItem(53, next);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta(); cm.setDisplayName("§cClose"); close.setItemMeta(cm);
        inv.setItem(49, close);

        ItemStack info = GuiIcons.icon(plugin, "guild", Material.BOOK, "§6§lGuild Registry",
                java.util.List.of("§7Total guilds: §a" + all.size(),
                                  "§7Browse and inspect guilds here."));
        inv.setItem(4, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
    }

    public void open() {
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(inv)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        if (slot == 45) {
            p.closeInventory();
            new GuildListGUI(plugin, p, page-1).open();
            HandlerList.unregisterAll(this);
            return;
        }
        if (slot == 53) {
            p.closeInventory();
            new GuildListGUI(plugin, p, page+1).open();
            HandlerList.unregisterAll(this);
            return;
        }
        if (slot == 49) {
            p.closeInventory();
            HandlerList.unregisterAll(this);
            return;
        }

        int idx = page * PER_PAGE + slot;
        if (idx < all.size()) {
            GuildData g = all.get(idx);
            p.closeInventory();
            new com.skilltree.plugin.gui.GuildGUI(plugin, p, g).open();
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getInventory().equals(inv)) return;
        HandlerList.unregisterAll(this);
    }
}
