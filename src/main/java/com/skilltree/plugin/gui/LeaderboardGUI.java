package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardGUI {
    
    private final SkillForgePlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    
    public LeaderboardGUI(SkillForgePlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(null, 54, GuiStyle.title("Hall of Heroes"));
        setupLeaderboard();
    }
    
    private void setupLeaderboard() {
        // Get all players and sort by skill points
        List<Map.Entry<UUID, PlayerData>> playerList = plugin.getPlayerDataManager()
            .getPlayerDataMap()
            .entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getSkillPoints(), a.getValue().getSkillPoints()))
            .collect(Collectors.toList());
        
        // Add top 10 players
        int slot = 9;
        int rank = 1;
        
        for (int i = 0; i < Math.min(10, playerList.size()); i++) {
            Map.Entry<UUID, PlayerData> entry = playerList.get(i);
            PlayerData data = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            
            String playerName = player != null ? player.getName() : data.getLastKnownName();
            if (playerName == null || playerName.isBlank()) {
                playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            }
            if (playerName == null || playerName.isBlank()) {
                playerName = "Unknown";
            }
            int skillPoints = data.getSkillPoints();
            long playtimeMillis = data.getPlaytimeMillis();
            String playtimeStr = formatPlaytime(playtimeMillis);
            
            ItemStack item = new ItemStack(getRankMaterial(rank));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + rank + ". " + playerName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Skill Points: §a" + skillPoints);
            lore.add("§7Playtime: §b" + playtimeStr);
            meta.setLore(lore);
            item.setItemMeta(meta);
            
            inventory.setItem(slot, item);
            slot++;
            rank++;
            
            if (slot == 19) slot = 27; // Skip row 2
            if (slot == 37) slot = 45; // Skip row 3
        }
        
        // Add back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§lBack");
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);
        
        // Add info item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b§lHall Records");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Top adventurers by skill points");
        infoLore.add("§7Playtime shown in hours");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        GuiStyle.fillBorder(inventory, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inventory, GuiStyle.fillerPane());
    }
    
    private Material getRankMaterial(int rank) {
        return switch (rank) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.STONE;
        };
    }
    
    private String formatPlaytime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);
        return hours + "h " + minutes + "m";
    }
    
    public void open() {
        viewer.openInventory(inventory);
    }
}
