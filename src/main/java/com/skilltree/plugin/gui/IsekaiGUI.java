package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.IsekaiItemType;
import com.skilltree.plugin.systems.IsekaiSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IsekaiGUI implements Listener {
    private static final String TITLE = "Isekai Relics";

    private final SkillForgePlugin plugin;
    private final IsekaiSystem isekaiSystem;
    private final NamespacedKey choiceKey;

    public IsekaiGUI(SkillForgePlugin plugin, IsekaiSystem isekaiSystem) {
        this.plugin = plugin;
        this.isekaiSystem = isekaiSystem;
        this.choiceKey = new NamespacedKey(plugin, "isekai_choice");
    }

    public void openSelection(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, GuiStyle.title(TITLE));

        int[] slots = new int[] {10, 12, 14, 16, 28, 30, 32, 34};
        int idx = 0;
        for (IsekaiItemType type : IsekaiItemType.values()) {
            if (idx >= slots.length) break;
            ItemStack item = createChoiceItem(type);
            inv.setItem(slots[idx], item);
            idx++;
        }

        ItemStack info = GuiIcons.icon(plugin, "isekai", Material.BOOK, ChatColor.GOLD + "Reincarnation Vow",
                Arrays.asList(ChatColor.GRAY + "One relic per soul.",
                        ChatColor.GRAY + "Lost relics return after long absence.",
                        ChatColor.DARK_GRAY + "Use right-click (melee) or left-click air (ranged) to awaken it.",
                        ChatColor.DARK_GRAY + "Choose carefully."));
        inv.setItem(40, info);

        GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        player.openInventory(inv);
    }

    private ItemStack createChoiceItem(IsekaiItemType type) {
        List<String> lore = new ArrayList<>();
        for (String line : type.getDescription()) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "Click to bind this relic.");

        ItemStack item = GuiStyle.item(type.getMaterial(), ChatColor.GOLD + type.getDisplayName(), lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(choiceKey, PersistentDataType.STRING, type.getKey());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() == null) return;
        String title = event.getView().getTitle();
        if (title == null || !title.contains(TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String key = meta.getPersistentDataContainer().get(choiceKey, PersistentDataType.STRING);
        if (key == null || key.isEmpty()) return;

        Player player = (Player) event.getWhoClicked();
        IsekaiItemType type = IsekaiItemType.fromKey(key);
        if (type == null) return;

        player.closeInventory();
        isekaiSystem.grantIsekaiItem(player, type);
    }
}
