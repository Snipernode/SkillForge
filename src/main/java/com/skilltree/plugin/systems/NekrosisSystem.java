package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class NekrosisSystem implements Listener {
    private final SkillForgePlugin plugin;

    public NekrosisSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSplurgerPop(EntityDamageByEntityEvent event) {
        // Nekrotic Splurger pop logic (assuming custom mob check)
        if (event.getDamager().getName().contains("Nekrotic Splurger") && event.getEntity() instanceof Player player) {
            infect(player);
        }
    }

    private void infect(Player player) {
        var data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data.isNekrotic()) return;
        
        data.setNekrotic(true);
        data.setNekrosisDay(1);
        player.sendMessage(ChatColor.DARK_GREEN + "You have been infected with Nekrosis. Find a cure before it's too late.");
    }

    @EventHandler
    public void onCure(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        var data = plugin.getPlayerDataManager().getPlayerData(player);
        
        if (!data.isNekrotic()) return;
        if (data.getNekrosisDay() >= 4) return; // Past grace period

        ItemStack item = player.getInventory().getItemInMainHand();
        // Check for leech/maggot (Simplified check)
        if (item.getType() == Material.GHAST_TEAR || item.getType() == Material.FERMENTED_SPIDER_EYE) {
            data.setNekrotic(false);
            data.setNekrosisDay(0);
            player.sendMessage(ChatColor.GREEN + "The Nekrosis has been purged from your body.");
            item.setAmount(item.getAmount() - 1);
        }
    }

    public void updateNekrosis(Player player) {
        var data = plugin.getPlayerDataManager().getPlayerData(player);
        if (!data.isNekrotic()) return;

        int day = data.getNekrosisDay();
        if (day >= 5) {
            player.setHealth(0);
            player.sendMessage(ChatColor.DARK_RED + "The Nekrosis has rotted you away completely.");
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, day - 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, day - 1));
    }
}
