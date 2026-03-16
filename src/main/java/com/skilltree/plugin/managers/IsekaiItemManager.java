package com.skilltree.plugin.managers;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IsekaiItemManager {

    private final NamespacedKey ISEKAI_ITEM_KEY;
    private final NamespacedKey ISEKAI_OWNER_KEY;
    private final NamespacedKey ISEKAI_ID_KEY;
    private final NamespacedKey ISEKAI_LEVEL_KEY;

    public IsekaiItemManager(SkillForgePlugin plugin) {
        this.ISEKAI_ITEM_KEY = new NamespacedKey(plugin, "is_isekai_item");
        this.ISEKAI_OWNER_KEY = new NamespacedKey(plugin, "isekai_owner");
        this.ISEKAI_ID_KEY = new NamespacedKey(plugin, "isekai_id");
        this.ISEKAI_LEVEL_KEY = new NamespacedKey(plugin, "isekai_level");
    }

    public ItemStack createItem(UUID owner, String itemId, Material material, String displayName, int level, List<String> abilities) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + displayName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Bound to: " + owner.toString());
            lore.add(ChatColor.GRAY + "Growth Level: " + ChatColor.AQUA + level);
            lore.add("");
            if (abilities != null && !abilities.isEmpty()) {
                lore.add(ChatColor.LIGHT_PURPLE + "Isekai Skills:");
                for (String a : abilities) {
                    lore.add(ChatColor.GRAY + "• " + a);
                }
            }
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Lost relics return after long absence.");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(ISEKAI_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(ISEKAI_OWNER_KEY, PersistentDataType.STRING, owner.toString());
            meta.getPersistentDataContainer().set(ISEKAI_ID_KEY, PersistentDataType.STRING, itemId);
            meta.getPersistentDataContainer().set(ISEKAI_LEVEL_KEY, PersistentDataType.INTEGER, level);

            applyEnchantments(meta, material, level);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isIsekaiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ISEKAI_ITEM_KEY, PersistentDataType.BYTE);
    }

    public boolean isOwner(ItemStack item, UUID owner) {
        if (!isIsekaiItem(item)) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(ISEKAI_OWNER_KEY, PersistentDataType.STRING);
        return id != null && id.equals(owner.toString());
    }

    public String getItemId(ItemStack item) {
        if (!isIsekaiItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ISEKAI_ID_KEY, PersistentDataType.STRING);
    }

    public void updateItem(ItemStack item, String displayName, int level, List<String> abilities) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(ChatColor.GOLD + displayName);
        meta.getPersistentDataContainer().set(ISEKAI_LEVEL_KEY, PersistentDataType.INTEGER, level);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Bound Relic");
        lore.add(ChatColor.GRAY + "Growth Level: " + ChatColor.AQUA + level);
        lore.add("");
        if (abilities != null && !abilities.isEmpty()) {
            lore.add(ChatColor.LIGHT_PURPLE + "Isekai Skills:");
            for (String a : abilities) {
                lore.add(ChatColor.GRAY + "• " + a);
            }
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Lost relics return after long absence.");
        meta.setLore(lore);

        applyEnchantments(meta, item.getType(), level);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    private void applyEnchantments(ItemMeta meta, Material material, int level) {
        int tier = Math.max(1, level / 10);
        if (material.name().contains("SWORD") || material.name().contains("AXE") || material.name().contains("DAGGER") || material.name().contains("PICKAXE")) {
            meta.addEnchant(Enchantment.SHARPNESS, Math.min(5, 1 + tier), true);
            meta.addEnchant(Enchantment.UNBREAKING, Math.min(3, 1 + (tier / 2)), true);
        } else if (material == Material.BOW || material == Material.CROSSBOW) {
            meta.addEnchant(Enchantment.POWER, Math.min(5, 1 + tier), true);
            meta.addEnchant(Enchantment.UNBREAKING, Math.min(3, 1 + (tier / 2)), true);
        } else if (material == Material.TRIDENT) {
            meta.addEnchant(Enchantment.IMPALING, Math.min(5, 1 + tier), true);
            meta.addEnchant(Enchantment.LOYALTY, Math.min(3, 1 + (tier / 2)), true);
        } else {
            meta.addEnchant(Enchantment.UNBREAKING, Math.min(3, 1 + (tier / 2)), true);
            meta.addEnchant(Enchantment.KNOCKBACK, Math.min(2, 1 + (tier / 3)), true);
        }
    }
}
