package com.skilltree.plugin.listeners;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.AbilityExecutionSystem;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class AbilityItemListener implements Listener {
    private final SkillForgePlugin plugin;
    private final AbilityExecutionSystem abilitySystem;

    public AbilityItemListener(SkillForgePlugin plugin, AbilityExecutionSystem abilitySystem) {
        this.plugin = plugin;
        this.abilitySystem = abilitySystem;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only trigger if not sneaking (since sneaking is used for the hotbar system)
        if (event.getPlayer().isSneaking()) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String itemName = ChatColor.stripColor(meta.getDisplayName());
        boolean isLeftClick = event.getAction().toString().contains("LEFT");

        // Generic skill items created by SkillItemManager (PDC-based)
        NamespacedKey skillItemKey = new NamespacedKey(plugin, "is_skill_item");
        NamespacedKey skillIdKey = new NamespacedKey(plugin, "skill_id");
        if (meta.getPersistentDataContainer().has(skillItemKey, PersistentDataType.BYTE)) {
            String skillId = meta.getPersistentDataContainer().get(skillIdKey, PersistentDataType.STRING);
            if (skillId != null && !skillId.isBlank()) {
                abilitySystem.triggerAbility(event.getPlayer(), skillId.toLowerCase(), isLeftClick);
                event.setCancelled(true);
                return;
            }
        }

        // Example: If the item is named "Fireball Wand", cast fireball
        if (itemName.equalsIgnoreCase("Fireball Wand")) {
            abilitySystem.triggerAbility(event.getPlayer(), "magic_fireball", isLeftClick);
            event.setCancelled(true);
        }
        // Example: If the item is named "Frost Dagger", cast frostbolt
        else if (itemName.equalsIgnoreCase("Frost Dagger")) {
            abilitySystem.triggerAbility(event.getPlayer(), "magic_frostbolt", isLeftClick);
            event.setCancelled(true);
        }
    }
}
