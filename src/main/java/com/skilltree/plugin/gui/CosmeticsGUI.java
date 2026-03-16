package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CosmeticsGUI implements Listener {
    
    private final SkillForgePlugin plugin;
    private final Player player;
    private final Map<Integer, String> cosmeticIds;
    private final int UNLOCK_COST = 10000;
    private final int TOTAL_COSMETICS = 10;
    private Inventory currentInventory;
    
    public CosmeticsGUI(SkillForgePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.cosmeticIds = new HashMap<>();
        this.currentInventory = null;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public void open() {
        Inventory inventory = Bukkit.createInventory(null, 54, GuiStyle.title("Cosmetics Wardrobe"));
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        cosmeticIds.clear();
        
        addCosmetic(inventory, 10, "rainbow_trail", Material.REDSTONE, "§c§lRainbow Trail", data);
        addCosmetic(inventory, 11, "particle_aura", Material.GLOWSTONE_DUST, "§e§lParticle Aura", data);
        addCosmetic(inventory, 12, "wings_cosmetic", Material.ELYTRA, "§b§lAngel Wings", data);
        addCosmetic(inventory, 13, "fire_aura", Material.BLAZE_POWDER, "§c§lFire Aura", data);
        addCosmetic(inventory, 14, "ice_trail", Material.ICE, "§b§lIce Trail", data);
        addCosmetic(inventory, 19, "thunder_effect", Material.LIGHTNING_ROD, "§e§lThunder Effect", data);
        addCosmetic(inventory, 20, "shadow_cloak", Material.BLACK_WOOL, "§8§lShadow Cloak", data);
        addCosmetic(inventory, 21, "light_halo", Material.END_ROD, "§f§lLight Halo", data);
        addCosmetic(inventory, 22, "starlight_aura", Material.AMETHYST_SHARD, "§d§lStarlight Aura", data);
        addCosmetic(inventory, 23, "void_trail", Material.OBSIDIAN, "§5§lVoid Trail", data);
        
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§d§lWardrobe Info");
        infoMeta.setLore(Arrays.asList(
            "§7Unlock Cost: §e" + UNLOCK_COST + " EP",
            "§7Your ES: §a" + data.getEvershards(),
            "§7Unlocked: §a" + data.getUnlockedCosmetics().size() + "§7/§a" + TOTAL_COSMETICS
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c§lBack");
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);

        GuiStyle.fillBorder(inventory, GuiStyle.fillerPane());
        GuiStyle.fillEmpty(inventory, GuiStyle.fillerPane());
        
        this.currentInventory = inventory;
        player.openInventory(inventory);
    }
    
    private void addCosmetic(Inventory inventory, int slot, String id, Material material, String name, PlayerData data) {
        boolean unlocked = data.hasCosmeticUnlocked(id);
        boolean active = id.equals(data.getActiveCosmetic());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (active) {
            meta.setDisplayName("§a§l" + name.substring(2) + " (EQUIPPED)");
        } else {
            meta.setDisplayName(name);
        }
        
        if (unlocked) {
            if (active) {
                meta.setLore(Arrays.asList("§a§l✓ EQUIPPED!", "", "§7Click to unequip"));
            } else {
                meta.setLore(Arrays.asList("§a§lUNLOCKED!", "", "§7Click to equip"));
            }
        } else {
            meta.setLore(Arrays.asList("§7Cost: §e" + UNLOCK_COST + " EP", "", "§eClick to unlock!"));
        }
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        cosmeticIds.put(slot, id);
    }
    
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (currentInventory == null || !event.getInventory().equals(currentInventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        event.setCancelled(true);
        
        int slot = event.getSlot();
        if (slot == 49) {
            player.closeInventory();
            if (plugin.getUnifiedPanelGUI() != null) {
                plugin.getUnifiedPanelGUI().openForPlayer(player);
            }
            return;
        }
        
        String cosmeticId = cosmeticIds.get(slot);
        if (cosmeticId != null) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            if (!data.hasCosmeticUnlocked(cosmeticId)) {
                if (data.removeEvershards(UNLOCK_COST)) {
                    data.unlockCosmetic(cosmeticId);
                    player.sendMessage("§a§l[SkillForge] §eCosmetic unlocked!");
                    player.closeInventory();
                    open();
                } else {
                    player.sendMessage("§c§l[SkillForge] §cNot enough Evershards! Need " + UNLOCK_COST + " ES.");
                }
            } else {
                String activeCosmetic = data.getActiveCosmetic();
                if (cosmeticId.equals(activeCosmetic)) {
                    data.setActiveCosmetic(null);
                    player.sendMessage("§e§l[SkillForge] §fCosmetic unequipped!");
                } else {
                    data.setActiveCosmetic(cosmeticId);
                    player.sendMessage("§a§l[SkillForge] §eCosmetic equipped!");
                }
                player.closeInventory();
                open();
            }
        }
    }
}
