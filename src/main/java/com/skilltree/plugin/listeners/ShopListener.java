package com.skilltree.plugin.listeners;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.gui.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ShopListener implements Listener {

    private final SkillForgePlugin plugin;

    public ShopListener(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        NamespacedKey bindingKey = new NamespacedKey(plugin, "binding_shop");
        if (player.getPersistentDataContainer().has(bindingKey, PersistentDataType.BYTE)) {
            player.getPersistentDataContainer().remove(bindingKey);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_shop_npc"), PersistentDataType.BYTE, (byte) 1);
            player.sendMessage("§a§l[Shop] §eSuccessfully bound the shop to this " + entity.getType().name() + "!");
            event.setCancelled(true);
            return;
        }

        if (entity.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_shop_npc"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            ShopGUI.openCategories(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals(ShopGUI.MAIN_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            for (ShopCategory cat : ShopCategory.values()) {
                if (clicked.getItemMeta().getDisplayName().equals(cat.getDisplayName())) {
                    ShopGUI.openCategoryItems(player, cat);
                    return;
                }
            }
        } else if (title.startsWith(ShopGUI.CATEGORY_TITLE_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 49) {
                ShopGUI.openCategories(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            String catName = title.substring(ShopGUI.CATEGORY_TITLE_PREFIX.length());
            try {
                ShopCategory cat = ShopCategory.valueOf(catName);
                List<ShopItem> items = ShopItemRegistry.getItemsByCategory(cat);
                if (slot < items.size()) {
                    QuantityGUI.open(player, items.get(slot));
                }
            } catch (Exception ignored) {}
        } else if (title.startsWith(QuantityGUI.TITLE_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 22) {
                // Hard to get back to correct category without state, defaulting to main
                ShopGUI.openCategories(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            String itemName = title.substring(QuantityGUI.TITLE_PREFIX.length());
            // This is a bit hacky but works for now in fast mode
            int amount = clicked.getAmount();
            int totalCost = 0;
            // Parse cost from lore
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
                for (String line : clicked.getItemMeta().getLore()) {
                    if (line.contains("Total Cost:")) {
                        totalCost = Integer.parseInt(line.replaceAll("[^0-9]", ""));
                        break;
                    }
                }
            }
            
            if (totalCost > 0) {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                if (data.removeEvershards(totalCost)) {
                    ItemStack purchase = new ItemStack(clicked.getType(), amount);
                    player.getInventory().addItem(purchase);
                    player.sendMessage("§a§l[Shop] §ePurchased " + amount + "x " + itemName + " for " + totalCost + " ES!");
                } else {
                    player.sendMessage("§c§l[Shop] Not enough Evershards!");
                }
            }
        }
    }
}
