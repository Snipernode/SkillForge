package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PersonalJournalGUI implements Listener {
    private final SkillForgePlugin plugin;
    private final NamespacedKey journalMarkerKey;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private static final String TITLE = GuiStyle.title("Personal Journal");

    // Two-page layout: left page (10-12,19-21) + right page (14-16,23-25)
    private static final int SLOT_SKILL_PANEL = 10;
    private static final int SLOT_BIND = 11;
    private static final int SLOT_QUESTS = 12;
    private static final int SLOT_SHOP = 19;
    private static final int SLOT_BATTLEPASS = 20;
    private static final int SLOT_GAMEMODES = 21;

    private static final int SLOT_GUILD = 14;
    private static final int SLOT_CRATES = 15;
    private static final int SLOT_TRAIN_STATION = 16;
    private static final int SLOT_ISEKAI = 23;
    private static final int SLOT_INNATE_USE = 24;
    private static final int SLOT_INNATE_UPGRADE = 25;

    private static final int SLOT_ADMIN_RELOAD = 47;
    private static final int SLOT_GIVE_BOOK = 49;
    private static final int SLOT_CLOSE = 53;

    public PersonalJournalGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.journalMarkerKey = new NamespacedKey(plugin, "personal_journal");
    }

    public void openForPlayer(Player player) {
        if (player == null) return;
        if (!player.hasPermission("skillforge.journal")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the Personal Journal.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        inv.setItem(SLOT_SKILL_PANEL, button(Material.NETHER_STAR, ChatColor.GOLD + "Skill Panel",
                ChatColor.GRAY + "Open your skill categories and",
                ChatColor.GRAY + "upgrade paths."));
        inv.setItem(SLOT_BIND, button(Material.BOOK, ChatColor.YELLOW + "Bind Skills",
                ChatColor.GRAY + "Bind active skills to hotbar slots."));
        inv.setItem(SLOT_QUESTS, button(Material.WRITABLE_BOOK, ChatColor.AQUA + "Quest Board",
                ChatColor.GRAY + "View and accept quests."));
        inv.setItem(SLOT_GUILD, button(Material.SHIELD, ChatColor.GREEN + "Guild",
                ChatColor.GRAY + "Guild actions and hall tools."));
        inv.setItem(SLOT_SHOP, button(Material.EMERALD, ChatColor.GREEN + "Shop",
                ChatColor.GRAY + "Open the item shop."));
        inv.setItem(SLOT_BATTLEPASS, button(Material.EXPERIENCE_BOTTLE, ChatColor.LIGHT_PURPLE + "Battlepass",
                ChatColor.GRAY + "Open your battlepass progress."));
        inv.setItem(SLOT_INNATE_USE, button(Material.BLAZE_POWDER, ChatColor.RED + "Use Innate",
                ChatColor.GRAY + "Trigger your innate ability."));
        inv.setItem(SLOT_INNATE_UPGRADE, button(Material.ENCHANTED_BOOK, ChatColor.BLUE + "Innate Upgrade",
                ChatColor.GRAY + "Upgrade innate skills."));
        inv.setItem(SLOT_GAMEMODES, button(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Gamemodes",
                ChatColor.GRAY + "Open the gamemode selection UI."));
        inv.setItem(SLOT_TRAIN_STATION, button(Material.MINECART, ChatColor.DARK_AQUA + "Nearest Station",
                ChatColor.GRAY + "Open train station stops",
                ChatColor.GRAY + "for your nearest station area."));
        inv.setItem(SLOT_CRATES, button(Material.CHEST, ChatColor.GOLD + "Crates",
                ChatColor.GRAY + "Open crate UI and key actions."));
        inv.setItem(SLOT_ISEKAI, button(Material.NETHERITE_SWORD, ChatColor.DARK_PURPLE + "Isekai",
                ChatColor.GRAY + "Reincarnation and relic tools."));

        inv.setItem(SLOT_GIVE_BOOK, button(Material.WRITTEN_BOOK, ChatColor.WHITE + "Get Journal Copy",
                ChatColor.GRAY + "Get another Personal Journal book."));
        if (player.isOp() || player.hasPermission("skillforge.admin")) {
            inv.setItem(SLOT_ADMIN_RELOAD, button(Material.REDSTONE, ChatColor.RED + "Reload SkillForge",
                    ChatColor.GRAY + "Runs /reloadforge"));
        } else {
            inv.setItem(SLOT_ADMIN_RELOAD, button(Material.GRAY_DYE, ChatColor.DARK_GRAY + "Admin Locked"));
        }
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, ChatColor.RED + "Close"));

        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        playOpenSound(player);
    }

    public ItemStack createJournalItem() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GOLD + "Personal Journal");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right-click to open command tools.");
        lore.add(ChatColor.DARK_GRAY + "No chat command typing required.");
        lore.add(ChatColor.YELLOW + "Use /journal if this item is lost.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(journalMarkerKey, PersistentDataType.BYTE, (byte) 1);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isJournalItem(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(journalMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean hasJournal(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isJournalItem(item)) return true;
        }
        return isJournalItem(player.getInventory().getItemInOffHand());
    }

    public boolean giveJournal(Player player, boolean onlyIfMissing) {
        if (player == null) return false;
        if (onlyIfMissing && hasJournal(player)) return false;

        ItemStack journal = createJournalItem();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(journal);
        if (!leftovers.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), journal);
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("journal.auto_give_on_join", true)) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> giveJournal(player, true), 10L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;

        event.setCancelled(true);
        safeCursorClear(player);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        switch (slot) {
            case SLOT_SKILL_PANEL -> runCommand(player, "skillforge combat");
            case SLOT_BIND -> runCommand(player, "bind");
            case SLOT_QUESTS -> runCommand(player, "quest");
            case SLOT_GUILD -> runCommand(player, "guild");
            case SLOT_SHOP -> runCommand(player, "shop");
            case SLOT_BATTLEPASS -> runCommand(player, "battlepass");
            case SLOT_INNATE_USE -> runCommand(player, "innate");
            case SLOT_INNATE_UPGRADE -> runCommand(player, "innate upgrade");
            case SLOT_GAMEMODES -> runCommand(player, "gamemodes");
            case SLOT_CRATES -> runCommand(player, "crate");
            case SLOT_ISEKAI -> runCommand(player, "isekai info");
            case SLOT_TRAIN_STATION -> openNearestStation(player);
            case SLOT_ADMIN_RELOAD -> {
                if (player.isOp() || player.hasPermission("skillforge.admin")) {
                    runCommand(player, "reloadforge");
                } else {
                    player.sendMessage(ChatColor.RED + "Admin permission required.");
                }
            }
            case SLOT_GIVE_BOOK -> {
                boolean given = giveJournal(player, false);
                if (given) {
                    player.sendMessage(ChatColor.GREEN + "Personal Journal copy added.");
                }
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onUseJournal(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isJournalItem(item)) return;
        if (!player.hasPermission("skillforge.journal")) return;

        event.setCancelled(true);
        openForPlayer(player);
    }

    private void openNearestStation(Player player) {
        if (plugin.getFastTravelSystem() == null) {
            player.sendMessage(ChatColor.RED + "Train system is unavailable.");
            return;
        }
        Map<String, Object> result = plugin.getFastTravelSystem().openSelectionForNearestStation(player, null);
        int opened = 0;
        if (result != null && result.get("opened") instanceof Number n) {
            opened = n.intValue();
        }
        if (opened <= 0) {
            player.sendMessage(ChatColor.YELLOW + "No nearby station area found.");
        }
    }

    private boolean isTrackedInventory(Player player, Inventory top) {
        if (player == null || top == null) return false;
        Inventory tracked = openInventories.get(player.getUniqueId());
        return tracked != null && tracked.equals(top);
    }

    private void safeCursorClear(Player player) {
        try {
            player.setItemOnCursor(null);
        } catch (Throwable ignored) {
        }
    }

    private void runCommand(Player player, String commandLine) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand(commandLine));
    }

    private void playOpenSound(Player player) {
        String configured = plugin.getConfig().getString("journal.open_sound", "ITEM_BOOK_PAGE_TURN");
        try {
            Sound sound = Sound.valueOf(configured.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
        }
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        List<String> lore = new ArrayList<>();
        if (loreLines != null) {
            for (String line : loreLines) {
                if (line != null) lore.add(line);
            }
        }
        return GuiStyle.item(material, name, lore);
    }
}
