package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.listeners.PlayerEventListener;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InnateUpgradeGUI implements Listener {
    
    private final SkillForgePlugin plugin;
    private final Map<String, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;
    private static final int MAX_LEVEL = 255;
    
    public InnateUpgradeGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void openForPlayer(Player player) {
        openForPlayer(player, 0);
    }
    
    public void openForPlayer(Player player, int page) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        String innateId = data.getInnateSkillId();
        
        if (innateId == null) {
            player.sendMessage(ChatColor.RED + "You have no innate skill assigned!");
            return;
        }
        
        // Store current page
        playerPages.put(player.getName(), page);
        
        Inventory inv = Bukkit.createInventory(null, 54, GuiStyle.title("Innate Skill Upgrade"));
        
        int currentLevel = data.getInnateSkillLevel();
        int skillPoints = data.getSkillPoints();
        
        // Calculate which levels to display
        int startLevel = (page * ITEMS_PER_PAGE) + 1;
        int endLevel = Math.min(startLevel + ITEMS_PER_PAGE - 1, MAX_LEVEL);
        
        // Add level upgrade items
        int slot = 0;
        for (int level = startLevel; level <= endLevel; level++) {
            if (slot >= 45) break; // Reserve space for bottom row
            
            Material mat = level <= currentLevel ? Material.LIME_DYE : Material.GRAY_DYE;
            ItemStack upgrade = new ItemStack(mat);
            ItemMeta meta = upgrade.getItemMeta();
            
            if (level <= currentLevel) {
                meta.setDisplayName(ChatColor.GREEN + "Level " + level);
            } else if (skillPoints >= 10 * (level - currentLevel)) {
                meta.setDisplayName(ChatColor.YELLOW + "Level " + level + " (Click to Upgrade)");
            } else {
                meta.setDisplayName(ChatColor.RED + "Level " + level + " (Need " + (10 * (level - currentLevel)) + " SP)");
            }
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cost: 10 SP per level");
            lore.add(ChatColor.DARK_GRAY + "Current: " + currentLevel);
            meta.setLore(lore);
            upgrade.setItemMeta(meta);
            inv.setItem(slot, upgrade);
            slot++;
        }
        
        // Info Item
        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Current Level: " + currentLevel);
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Skill Points: " + skillPoints);
        infoLore.add(ChatColor.GRAY + "Cost per Level: 10 SP");
        infoLore.add("");
        infoLore.add(ChatColor.AQUA + "Page " + (page + 1) + " of " + getTotalPages());
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);
        
        // Previous page button
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "← Previous Page");
            List<String> prevLore = new ArrayList<>();
            prevLore.add(ChatColor.GRAY + "Levels " + Math.max(1, (page - 1) * ITEMS_PER_PAGE + 1) + "-" + Math.min((page) * ITEMS_PER_PAGE, MAX_LEVEL));
            prevMeta.setLore(prevLore);
            prevButton.setItemMeta(prevMeta);
            inv.setItem(45, prevButton);
        }
        
        // Next page button
        if (endLevel < MAX_LEVEL) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page →");
            List<String> nextLore = new ArrayList<>();
            int nextPageStart = (page + 1) * ITEMS_PER_PAGE + 1;
            int nextPageEnd = Math.min(nextPageStart + ITEMS_PER_PAGE - 1, MAX_LEVEL);
            nextLore.add(ChatColor.GRAY + "Levels " + nextPageStart + "-" + nextPageEnd);
            nextMeta.setLore(nextLore);
            nextButton.setItemMeta(nextMeta);
            inv.setItem(53, nextButton);
        }

        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        
        player.openInventory(inv);
    }
    
    private int getTotalPages() {
        return (int) Math.ceil((double) MAX_LEVEL / ITEMS_PER_PAGE);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Innate Skill Upgrade")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        // Navigation
        if (slot == 45) {
            // Previous page
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openForPlayer(player, currentPage - 1);
            }
            return;
        }
        
        if (slot == 53) {
            // Next page
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if ((currentPage + 1) * ITEMS_PER_PAGE < MAX_LEVEL) {
                openForPlayer(player, currentPage + 1);
            }
            return;
        }
        
        if (slot == 49 || slot < 0 || slot >= 45) return; // Ignore info item and invalid slots
        
        // Calculate level from slot and current page
        int currentPage = playerPages.getOrDefault(player.getName(), 0);
        int level = (currentPage * ITEMS_PER_PAGE) + slot + 1;
        
        // Validate level range (1-255)
        if (level < 1 || level > MAX_LEVEL) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int currentLevel = data.getInnateSkillLevel();
        
        if (level <= currentLevel) {
            player.sendMessage(ChatColor.YELLOW + "You already have this level!");
        } else {
            int neededSp = 10 * (level - currentLevel);
            if (data.removeSkillPoints(neededSp)) {
                data.setInnateSkillLevel(level);
                player.sendMessage(ChatColor.GREEN + "Innate skill upgraded to level " + level + "!");
                
                // --- UPDATE EFFECTS AND ITEM ---
                // Get the listener instance from the main plugin
                PlayerEventListener listener = plugin.getPlayerEventListener();
                
                if (listener != null) {
                    // Re-apply the potion effect with the new level
                    listener.applyInnateSkillEffects(player);
                    
                    // Update the item in their hand to reflect the new level
                    listener.giveSkillItem(player);
                }
            } else {
                player.sendMessage(ChatColor.RED + "You need " + neededSp + " skill points!");
            }
        }
        
        openForPlayer(player, playerPages.getOrDefault(player.getName(), 0));
    }
}
