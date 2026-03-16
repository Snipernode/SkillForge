package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GamemodeSelectionGUI implements Listener {
    
    private final SkillForgePlugin plugin;
    private final Set<UUID> pendingSelection;
    private static final String GUI_TITLE = GuiStyle.title("Choose Your Gamemode");
    
    public GamemodeSelectionGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.pendingSelection = new HashSet<>();
    }
    
    public void openForPlayer(Player player) {
        // Check if config allows gamemodes
        if (!plugin.getConfig().getBoolean("gamemodes.enabled", true)) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            if (data.getGamemode() == null || data.getGamemode() == PlayerData.Gamemode.NONE) {
                data.setGamemode(PlayerData.Gamemode.CASUAL);
            }
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        // Only force-lock the menu if player has never selected a mode.
        if (data.getGamemode() == null || data.getGamemode() == PlayerData.Gamemode.NONE) {
            pendingSelection.add(player.getUniqueId());
        }
        
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        
        // --- CASUAL MODE ---
        ItemStack casualItem = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta casualMeta = casualItem.getItemMeta();
        casualMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CASUAL MODE");
        casualMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "A relaxed experience for all players.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.GREEN + "+ Keep inventory on death",
            ChatColor.GREEN + "+ Keep experience on death",
            ChatColor.GREEN + "+ Lower pressure gameplay",
            "",
            ChatColor.YELLOW + "Click to select CASUAL mode!"
        ));
        casualItem.setItemMeta(casualMeta);
        
        // --- ROGUELITE MODE ---
        ItemStack rogueliteItem = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta rogueliteMeta = rogueliteItem.getItemMeta();
        rogueliteMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "ROGUELITE MODE");
        rogueliteMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "A challenging hardcore experience.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.RED + "- Lose inventory on death",
            ChatColor.RED + "- Lose experience on death",
            ChatColor.GREEN + "+ Keep skills and Evershards",
            ChatColor.GREEN + "+ Greater sense of achievement",
            "",
            ChatColor.YELLOW + "Click to select ROGUELITE mode!"
        ));
        rogueliteItem.setItemMeta(rogueliteMeta);

        // --- ROGUELIKE MODE ---
        ItemStack roguelikeItem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta roguelikeMeta = roguelikeItem.getItemMeta();
        roguelikeMeta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ROGUELIKE MODE");
        roguelikeMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "The ultimate challenge. No second chances.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.RED + "- EVERYTHING is deleted on death",
            ChatColor.RED + "- Skill Tree is reset",
            ChatColor.RED + "- Must choose Kingdom and Mode again",
            "",
            ChatColor.YELLOW + "Click to select ROGUELIKE mode!"
        ));
        roguelikeItem.setItemMeta(roguelikeMeta);

        // --- HARDCORE MODE ---
        ItemStack hardcoreItem = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemMeta hardcoreMeta = hardcoreItem.getItemMeta();
        hardcoreMeta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "HARDCORE MODE");
        hardcoreMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "Death is PERMANENT. High stakes.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.RED + "- All stuff deleted on death",
            ChatColor.RED + "- Inventory NOT restored on revival",
            ChatColor.GREEN + "+ Revive via $5 OR Death Realm trials",
            "",
            ChatColor.YELLOW + "Click to select HARDCORE mode!"
        ));
        hardcoreItem.setItemMeta(hardcoreMeta);

        // --- PROTAGONIST MODE ---
        ItemStack protagonistItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta protagonistMeta = protagonistItem.getItemMeta();
        protagonistMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "PROTAGONIST MODE");
        protagonistMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "High intensity, fast cycle gameplay.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.GREEN + "+ Increased Skill Point gain",
            ChatColor.RED + "- No revive when downed",
            ChatColor.RED + "- Self-revive tokens disabled",
            ChatColor.YELLOW + "- Death is immediate and fast",
            "",
            ChatColor.YELLOW + "Click to select PROTAGONIST mode!"
        ));
        protagonistItem.setItemMeta(protagonistMeta);

        // --- THE END MODE ---
        ItemStack theEndItem = new ItemStack(Material.DRAGON_HEAD);
        ItemMeta theEndMeta = theEndItem.getItemMeta();
        theEndMeta.setDisplayName(ChatColor.BLACK + "" + ChatColor.BOLD + "THE END MODE");
        theEndMeta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "Final mode. One life only.",
            "",
            ChatColor.WHITE + "Features:",
            ChatColor.GREEN + "+ Highest Skill Point gain",
            ChatColor.RED + "- No revive when downed",
            ChatColor.RED + "- Self-revive tokens disabled",
            ChatColor.DARK_RED + "- Death = permanent ban",
            ChatColor.DARK_RED + "- No trials. No payment return.",
            "",
            ChatColor.YELLOW + "Click to select THE END mode!"
        ));
        theEndItem.setItemMeta(theEndMeta);
        
        // --- INFO ITEM ---
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Choose Wisely!");
        infoMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "This choice affects death and recovery rules.",
            "",
            ChatColor.YELLOW + "Skills and Evershards are preserved",
            ChatColor.YELLOW + "in most modes."
        ));
        infoItem.setItemMeta(infoMeta);
        
        // Layout: 
        // Row 1: [Filler] [Casual] [Filler] [Roguelite] [Filler] ...
        // Row 2: [Filler] [Roguelike] [Filler] [Hardcore] [Filler] ...
        
        gui.setItem(10, casualItem);
        gui.setItem(12, rogueliteItem);
        gui.setItem(14, roguelikeItem);
        gui.setItem(16, hardcoreItem);
        gui.setItem(29, protagonistItem);
        gui.setItem(33, theEndItem);
        gui.setItem(22, infoItem);
        
        // Header and background
        ItemStack header = GuiStyle.item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Oath of the Realm",
                Arrays.asList(ChatColor.GRAY + "Choose carefully. This is permanent."));
        gui.setItem(4, header);

        ItemStack filler = GuiStyle.fillerPane();
        GuiStyle.fillBorder(gui, filler);
        GuiStyle.fillEmpty(gui, filler);
        
        player.openInventory(gui);
    }
    
    public boolean isPendingSelection(UUID playerId) {
        return pendingSelection.contains(playerId);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == GuiStyle.FILLER) return;
        if (clicked.getType() == Material.BOOK) return;
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        PlayerData.Gamemode current = data.getGamemode();
        if (current == null) current = PlayerData.Gamemode.NONE;

        PlayerData.Gamemode target = null;
        String modeName = null;
        String description = null;
        
        if (clicked.getType() == Material.GOLDEN_APPLE) {
            target = PlayerData.Gamemode.CASUAL;
            modeName = "CASUAL";
            description = "You will keep your inventory on death.";
        } else if (clicked.getType() == Material.NETHERITE_SWORD) {
            target = PlayerData.Gamemode.ROGUELITE;
            modeName = "ROGUELITE";
            description = "You will lose your inventory on death, but keep your skills.";
        } else if (clicked.getType() == Material.TOTEM_OF_UNDYING) {
            target = PlayerData.Gamemode.ROGUELIKE;
            modeName = "ROGUELIKE";
            description = "Everything will be deleted on death. Skill tree reset.";
        } else if (clicked.getType() == Material.WITHER_SKELETON_SKULL) {
            target = PlayerData.Gamemode.HARDCORE;
            modeName = "HARDCORE";
            description = "Death is permanent. Revival requires payment or trials.";
        } else if (clicked.getType() == Material.NETHER_STAR) {
            target = PlayerData.Gamemode.PROTAGONIST;
            modeName = "PROTAGONIST";
            description = "Increased SP gain. No downed revive. Fast immediate death-respawn loop.";
        } else if (clicked.getType() == Material.DRAGON_HEAD) {
            target = PlayerData.Gamemode.THE_END;
            modeName = "THE END";
            description = "Highest SP gain. No revive. Death permanently bans this profile.";
        }

        if (target == null) return;
        if (target == current) {
            player.sendMessage(ChatColor.YELLOW + "You are already in " + current.name() + " mode.");
            return;
        }
        if (!canSwitchMode(current, target)) {
            player.sendMessage(ChatColor.RED + "You can only upgrade your gamemode.");
            player.sendMessage(ChatColor.GRAY + "Downgrade is only allowed if your current mode is THE_END.");
            return;
        }

        data.setGamemode(target);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
        finalizeSelection(player, modeName, description);
    }
    
    private void finalizeSelection(Player player, String modeName, String description) {
        // Remove from pending list IMMEDIATELY to prevent re-opening
        pendingSelection.remove(player.getUniqueId());
        
        player.closeInventory();
        player.sendMessage("");
        
        ChatColor color = ChatColor.WHITE;
        if (modeName.equals("CASUAL")) color = ChatColor.GREEN;
        else if (modeName.equals("ROGUELITE")) color = ChatColor.RED;
        else if (modeName.equals("ROGUELIKE")) color = ChatColor.DARK_RED;
        else if (modeName.equals("HARDCORE")) color = ChatColor.DARK_GRAY;
        else if (modeName.equals("PROTAGONIST")) color = ChatColor.AQUA;
        else if (modeName.equals("THE END")) color = ChatColor.BLACK;

        player.sendMessage(color + "" + ChatColor.BOLD + modeName + " MODE SELECTED!");
        player.sendMessage(ChatColor.WHITE + description);
        player.sendMessage("");
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        // Only force reopen if the player is STILL pending a selection
        if (pendingSelection.contains(player.getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Check again in case they selected in the split second before close
                    if (player.isOnline() && pendingSelection.contains(player.getUniqueId())) {
                        openForPlayer(player);
                        player.sendMessage(ChatColor.YELLOW + "You must select a gamemode to continue!");
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    private boolean canSwitchMode(PlayerData.Gamemode current, PlayerData.Gamemode target) {
        if (current == null || current == PlayerData.Gamemode.NONE) return true;
        if (current == PlayerData.Gamemode.THE_END) return true; // Only mode allowed to downgrade
        return modeRank(target) >= modeRank(current); // upgrades only
    }

    private int modeRank(PlayerData.Gamemode mode) {
        if (mode == null) return 0;
        return switch (mode) {
            case NONE -> 0;
            case CASUAL -> 1;
            case ROGUELITE -> 2;
            case ROGUELIKE -> 3;
            case HARDCORE -> 4;
            case PROTAGONIST -> 5;
            case THE_END -> 6;
        };
    }
}
