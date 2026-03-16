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

public class BattlepassGUI implements Listener {
    
    private final SkillForgePlugin plugin;
    private final Player player;
    private final Inventory inventory;
    
    public BattlepassGUI(SkillForgePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, GuiStyle.title("Campaign Ledger"));
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setupItems();
    }
    
    private void setupItems() {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        ItemStack filler = GuiStyle.fillerPane();
        
        ItemStack xpBottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xpBottle.getItemMeta();
        xpMeta.setDisplayName("§b§lEvershard Pouch");
        xpMeta.setLore(Arrays.asList(
            "§7Current ES: §a" + data.getEvershards(),
            "§7EP per kill: §e+5 EP",
            "",
            "§7Earned from combat and quests"
        ));
        xpBottle.setItemMeta(xpMeta);
        inventory.setItem(11, xpBottle);
        
        ItemStack emerald = new ItemStack(Material.EMERALD);
        ItemMeta emeraldMeta = emerald.getItemMeta();
        emeraldMeta.setDisplayName("§a§lBuy Skill Point");
        int cost = plugin.getEvershardSystem().calculateSkillPointCost(data);
        emeraldMeta.setLore(Arrays.asList(
            "§7Current SP: §b" + data.getSkillPoints(),
            "§7Next SP Cost: §e" + cost + " EP",
            "",
            "§eClick to purchase 1 Skill Point!"
        ));
        emerald.setItemMeta(emeraldMeta);
        inventory.setItem(13, emerald);
        
        ItemStack netherStar = new ItemStack(Material.NETHER_STAR);
        ItemMeta starMeta = netherStar.getItemMeta();
        starMeta.setDisplayName("§d§lVanity Wardrobe");
        starMeta.setLore(Arrays.asList(
            "§7Unlocked: §a" + data.getUnlockedCosmetics().size(),
            "§7Unlock Cost: §e10,000 EP",
            "",
            "§eClick to browse cosmetics!"
        ));
        netherStar.setItemMeta(starMeta);
        inventory.setItem(15, netherStar);

        ItemStack header = GuiStyle.item(Material.BOOK, "§6§lCampaign Ledger", Arrays.asList(
            "§7Track progress and spend ES here.",
            "§7Cosmetics are unlocked account-wide."
        ));
        inventory.setItem(4, header);

        GuiStyle.fillBorder(inventory, filler);
        GuiStyle.fillEmpty(inventory, filler);
    }
    
    public void open() {
        player.openInventory(inventory);
    }
    
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        if (slot == 13) {
            if (plugin.getEvershardSystem().purchaseSkillPoint(player)) {
                player.sendMessage("§a§l[SkillForge] §eYou purchased 1 Skill Point!");
                player.closeInventory();
            } else {
                player.sendMessage("§c§l[SkillForge] §cNot enough Evershards!");
            }
        } else if (slot == 15) {
            player.closeInventory();
            new CosmeticsGUI(plugin, player).open();
        }
    }
}
