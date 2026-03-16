package com.skilltree.plugin.managers;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class SkillItemManager {
    
    private final SkillForgePlugin plugin;
    
    // Keys to store data inside the item (NBT Data)
    private final NamespacedKey SKILL_ITEM_KEY;
    private final NamespacedKey SKILL_ID_KEY;

    public SkillItemManager(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.SKILL_ITEM_KEY = new NamespacedKey(plugin, "is_skill_item");
        this.SKILL_ID_KEY = new NamespacedKey(plugin, "skill_id");
    }

    /**
     * Creates an ItemStack for a specific skill.
     * 
     * @param skillId The ID of the skill (e.g., "fireball")
     * @param displayName The name shown on the item
     * @param material The material of the item
     * @param lore The description lines
     * @return The created ItemStack
     */
    public ItemStack createSkillItem(String skillId, String displayName, Material material, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            
            // Set Lore
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            
            // Add a dummy enchantment to make it look shiny (Glow)
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            
            // Mark item as a Skill Item using PersistentDataContainer so we can identify it later
            meta.getPersistentDataContainer().set(SKILL_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(SKILL_ID_KEY, PersistentDataType.STRING, skillId);
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    /**
     * Checks if an item is a skill item created by this plugin.
     */
    public boolean isSkillItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SKILL_ITEM_KEY, PersistentDataType.BYTE);
    }

    /**
     * Retrieves the Skill ID from an item.
     */
    public String getSkillId(ItemStack item) {
        if (!isSkillItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(SKILL_ID_KEY, PersistentDataType.STRING);
    }
}
