package com.skilltree.plugin.managers;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CrestItemManager {

    private final NamespacedKey CREST_ITEM_KEY;
    private final NamespacedKey CREST_ID_KEY;

    public CrestItemManager(SkillForgePlugin plugin) {
        this.CREST_ITEM_KEY = new NamespacedKey(plugin, "is_crest_item");
        this.CREST_ID_KEY = new NamespacedKey(plugin, "crest_id");
    }

    /**
     * Create a crest item used in crafting nation/faction armor.
     * The crest is identified by PDC so recipes can require an exact crest.
     */
    public ItemStack createCrest(String crestName, List<String> extraLore) {
        String safeName = crestName == null ? "Unknown Crest" : crestName.trim();
        String crestId = safeName.toLowerCase().replaceAll("[^a-z0-9_]+", "_");

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Crest: " + safeName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Used in recipes to create");
            lore.add(ChatColor.GRAY + "nation or faction armor.");
            if (extraLore != null && !extraLore.isEmpty()) {
                for (String line : extraLore) {
                    if (line != null && !line.isBlank()) {
                        lore.add(ChatColor.DARK_GRAY + ChatColor.stripColor(line));
                    }
                }
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(CREST_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(CREST_ID_KEY, PersistentDataType.STRING, crestId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCrest(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(CREST_ITEM_KEY, PersistentDataType.BYTE);
    }

    public String getCrestId(ItemStack item) {
        if (!isCrest(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(CREST_ID_KEY, PersistentDataType.STRING);
    }
}
