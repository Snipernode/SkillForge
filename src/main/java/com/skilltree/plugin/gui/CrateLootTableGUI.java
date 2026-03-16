package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrateLootTableGUI implements Listener {

    private static final int SIZE = 54;
    private static final int LOOT_LIMIT = 45;
    private static final int SLOT_SAVE = 45;
    private static final int SLOT_RELOAD = 46;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_CLEAR = 52;
    private static final int SLOT_CLOSE = 53;

    private final SkillForgePlugin plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    public CrateLootTableGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void openEditor(Player admin, String crateType) {
        if (admin == null || crateType == null || crateType.isBlank()) return;
        String type = crateType.toLowerCase();
        String title = ChatColor.DARK_GREEN + "Crate Editor: " + type;
        Inventory inv = Bukkit.createInventory(admin, SIZE, title);

        loadRewardsIntoInventory(type, inv);
        applyFooter(inv, type);

        sessions.put(admin.getUniqueId(), new EditorSession(type, inv));
        admin.openInventory(inv);
    }

    private void loadRewardsIntoInventory(String type, Inventory inv) {
        List<ItemStack> rewards = loadConfiguredRewards(type);
        int slot = 0;
        for (ItemStack reward : rewards) {
            if (slot >= LOOT_LIMIT) break;
            if (reward == null || reward.getType() == Material.AIR) continue;
            inv.setItem(slot++, reward.clone());
        }
    }

    private void applyFooter(Inventory inv, String type) {
        inv.setItem(SLOT_SAVE, GuiStyle.item(Material.EMERALD_BLOCK, ChatColor.GREEN + "Save Loot Table",
                List.of(ChatColor.GRAY + "Writes editor contents to config")));
        inv.setItem(SLOT_RELOAD, GuiStyle.item(Material.CLOCK, ChatColor.YELLOW + "Reload From Config",
                List.of(ChatColor.GRAY + "Discard unsaved changes")));
        inv.setItem(SLOT_INFO, GuiStyle.item(Material.BOOK, ChatColor.GOLD + "Editing: " + type,
                List.of(ChatColor.GRAY + "Top 45 slots are crate rewards",
                        ChatColor.GRAY + "Item meta/enchants are saved")));
        inv.setItem(SLOT_CLEAR, GuiStyle.item(Material.TNT, ChatColor.RED + "Clear Loot",
                List.of(ChatColor.GRAY + "Removes all reward slots")));
        inv.setItem(SLOT_CLOSE, GuiStyle.item(Material.BARRIER, ChatColor.RED + "Close", List.of()));
    }

    private List<ItemStack> loadConfiguredRewards(String type) {
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("crates.types." + type);
        if (section == null) return out;

        // Preferred format: serialized ItemStacks
        List<?> rawItems = section.getList("rewards_items");
        if (rawItems != null && !rawItems.isEmpty()) {
            for (Object o : rawItems) {
                if (o instanceof ItemStack item && item.getType() != Material.AIR) {
                    out.add(item.clone());
                }
            }
            if (!out.isEmpty()) return out;
        }

        // Legacy format fallback: "MATERIAL:amount"
        List<String> legacy = section.getStringList("rewards");
        for (String raw : legacy) {
            ItemStack parsed = parseLegacyReward(raw);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    private ItemStack parseLegacyReward(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null) return null;
        int amount = 1;
        if (parts.length >= 2) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        return new ItemStack(mat, Math.max(1, amount));
    }

    private void saveEditor(Player admin, EditorSession session) {
        String basePath = "crates.types." + session.type;
        if (plugin.getConfig().getConfigurationSection(basePath) == null) {
            plugin.getConfig().createSection(basePath);
        }

        List<ItemStack> items = new ArrayList<>();
        List<String> legacy = new ArrayList<>();
        for (int slot = 0; slot < LOOT_LIMIT; slot++) {
            ItemStack stack = session.inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;
            ItemStack copy = stack.clone();
            items.add(copy);
            legacy.add(copy.getType().name() + ":" + copy.getAmount());
        }

        plugin.getConfig().set(basePath + ".rewards_items", items);
        plugin.getConfig().set(basePath + ".rewards", legacy);
        plugin.saveConfig();
        admin.sendMessage(ChatColor.GREEN + "Saved crate loot table: " + session.type + " (" + items.size() + " entries)");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().getTopInventory().equals(session.inventory)) return;

        int raw = event.getRawSlot();
        if (raw < 0) return;

        // Footer controls in top inventory.
        if (raw < SIZE && raw >= LOOT_LIMIT) {
            event.setCancelled(true);
            if (raw == SLOT_SAVE) {
                saveEditor(player, session);
                return;
            }
            if (raw == SLOT_RELOAD) {
                for (int i = 0; i < LOOT_LIMIT; i++) session.inventory.setItem(i, null);
                loadRewardsIntoInventory(session.type, session.inventory);
                player.sendMessage(ChatColor.YELLOW + "Reloaded loot table from config.");
                return;
            }
            if (raw == SLOT_CLEAR) {
                for (int i = 0; i < LOOT_LIMIT; i++) session.inventory.setItem(i, null);
                player.sendMessage(ChatColor.RED + "Cleared editor slots (not saved yet).");
                return;
            }
            if (raw == SLOT_CLOSE) {
                player.closeInventory();
            }
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().getTopInventory().equals(session.inventory)) return;
        sessions.remove(player.getUniqueId());
    }

    private static final class EditorSession {
        private final String type;
        private final Inventory inventory;

        private EditorSession(String type, Inventory inventory) {
            this.type = type;
            this.inventory = inventory;
        }
    }
}
