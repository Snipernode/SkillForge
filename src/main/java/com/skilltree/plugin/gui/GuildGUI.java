package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.GuildData;
import com.skilltree.plugin.systems.GuildSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GuildGUI implements Listener {

    private final SkillForgePlugin plugin;
    private final Player viewer;
    private final Inventory inv;
    private final GuildData guild;

    public GuildGUI(SkillForgePlugin plugin, Player viewer, GuildData guild) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.guild = guild;
        this.inv = Bukkit.createInventory(null, 54, GuiStyle.title("Guild: " + guild.getGuildName()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        build();
    }

    private void build() {
        // Info
        List<String> lore = new ArrayList<>();
        lore.add("§7Leader: §a" + Bukkit.getOfflinePlayer(guild.getLeaderId()).getName());
        lore.add("§7Members: §a" + guild.getMemberCount());
        lore.add("");
        lore.add("§7Click members to manage (if permitted).");
        ItemStack info = GuiIcons.icon(plugin, "guild", Material.BOOK, "§b§lGuild Charter", lore);
        inv.setItem(4, info);

        // Members list
        int slot = 9;
        for (UUID uid : guild.getMembers().keySet()) {
            String name = Bukkit.getOfflinePlayer(uid).getName();
            GuildData.MemberRole role = guild.getMembers().get(uid);
            ItemStack p = new ItemStack(Material.PAPER);
            ItemMeta pm = p.getItemMeta();
            pm.setDisplayName("§e" + name + " §7(" + role.name() + ")");
            pm.setLore(Arrays.asList("§7Role: §a" + role.name(),
                                     "§7Click to manage if you have access"));
            p.setItemMeta(pm);
            inv.setItem(slot, p);
            slot++;
            if (slot == 18) slot = 27;
        }

        // Invite button
        ItemStack invite = new ItemStack(Material.MAP);
        ItemMeta imt = invite.getItemMeta();
        imt.setDisplayName("§aInvite Player");
        imt.setLore(Arrays.asList("§7Use /guild invite <player>"));
        invite.setItemMeta(imt);
        inv.setItem(49, invite);

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§cBack");
        back.setItemMeta(bm);
        inv.setItem(45, back);

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
            return;
        }
        if (slot == 49) {
            // Open anvil invite UI
            new AnvilInviteGUI(plugin, p, guild.getGuildName()).open();
            p.closeInventory();
            return;
        }

        // Clicking a member (paper) - if viewer is leader, toggle senior role
        if (slot >= 9 && slot < 9 + guild.getMemberCount()) {
            // resolve the clicked member by iterating
            List<UUID> list = new ArrayList<>(guild.getMembers().keySet());
            int idx = slot - 9;
            if (idx < list.size()) {
                UUID target = list.get(idx);
                if (guild.canManage(viewer.getUniqueId())) {
                    // leader can kick
                    plugin.getGuildSystem().kickMember(guild.getGuildName(), viewer.getUniqueId(), target);
                    p.sendMessage("§a§l[SkillForge] §eKicked player from guild.");
                    open();
                } else if (guild.canInvite(viewer.getUniqueId())) {
                    // senior can promote members (no demote)
                    guild.promoteToSenior(target);
                    p.sendMessage("§a§l[SkillForge] §ePromoted player to Senior.");
                    open();
                } else {
                    p.sendMessage("§cYou don't have permission to manage members.");
                }
            }
        }
    }
}
