package com.skilltree.plugin.listeners;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.managers.VoucherManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class VoucherListener implements Listener {
    private final SkillForgePlugin plugin;
    private final VoucherManager voucherManager;

    public VoucherListener(SkillForgePlugin plugin, VoucherManager voucherManager) {
        this.plugin = plugin;
        this.voucherManager = voucherManager;
    }

    @EventHandler
    public void onVoucherUse(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (voucherManager == null || !voucherManager.isEnabled()) return;

        ItemStack item = event.getItem();
        if (!voucherManager.isVoucher(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        VoucherManager.RedeemResult result = voucherManager.redeem(player, item);
        if (!result.isSuccess()) {
            player.sendMessage(result.getMessage() != null ? result.getMessage() : ChatColor.RED + "Voucher redeem failed.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        consumeOne(player);
        player.sendMessage(result.getMessage());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    private void consumeOne(Player player) {
        if (player == null) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null) return;
        int amount = inHand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        inHand.setAmount(amount - 1);
        player.getInventory().setItemInMainHand(inHand);
    }
}
