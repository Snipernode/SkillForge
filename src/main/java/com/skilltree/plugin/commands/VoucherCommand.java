package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.managers.VoucherManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class VoucherCommand implements CommandExecutor {
    private final SkillForgePlugin plugin;
    private final VoucherManager voucherManager;

    public VoucherCommand(SkillForgePlugin plugin, VoucherManager voucherManager) {
        this.plugin = plugin;
        this.voucherManager = voucherManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (voucherManager == null || !voucherManager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Vouchers are disabled.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            sendUsage(sender);
            return true;
        }
        if (!sender.hasPermission("skillforge.voucher.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /voucher give <player> <type> [value] [amount]");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        String type = voucherManager.canonicalType(args[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown voucher type: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Valid: sp, crate, referral, coupon, rank");
            return true;
        }

        String value = args.length >= 4 ? args[3] : null;
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        amount = Math.max(1, amount);

        ItemStack voucher = createVoucherByType(sender, type, value);
        if (voucher == null) return true;
        int dropped = giveItems(target, voucher, amount);

        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " " + type + " voucher(s) to " + target.getName() + ".");
        target.sendMessage(ChatColor.GOLD + "You received " + amount + " " + type + " voucher(s).");
        if (dropped > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Dropped " + dropped + " voucher(s) at target location (inventory full).");
        }
        return true;
    }

    private ItemStack createVoucherByType(CommandSender sender, String type, String value) {
        if (type.equals("sp")) {
            int sp;
            try {
                sp = value == null ? voucherManager.getDefaultSpAmount() : Integer.parseInt(value);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "SP voucher value must be a number. Example: /voucher give <p> sp 5");
                return null;
            }
            if (sp <= 0) {
                sender.sendMessage(ChatColor.RED + "SP voucher value must be > 0.");
                return null;
            }
            return voucherManager.createSpVoucher(sp);
        }
        if (type.equals("crate")) {
            try {
                return voucherManager.createCrateVoucher(value);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + e.getMessage());
                return null;
            }
        }
        if (type.equals("referral")) {
            try {
                return voucherManager.createReferralVoucher(value);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + e.getMessage());
                return null;
            }
        }
        if (type.equals("coupon")) {
            return voucherManager.createCouponVoucher(value);
        }
        if (type.equals("rank")) {
            return voucherManager.createRankVoucher(value);
        }
        sender.sendMessage(ChatColor.RED + "Unknown voucher type: " + type);
        return null;
    }

    private int giveItems(Player target, ItemStack prototype, int amount) {
        int dropped = 0;
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack stack = prototype.clone();
            stack.setAmount(stackSize);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                for (ItemStack extra : overflow.values()) {
                    if (extra == null) continue;
                    dropped += extra.getAmount();
                    target.getWorld().dropItemNaturally(target.getLocation(), extra);
                }
            }
            remaining -= stackSize;
        }
        return dropped;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Voucher commands:");
        sender.sendMessage(ChatColor.GRAY + "/voucher give <player> sp [points] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/voucher give <player> crate [type|amount|type:amount] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/voucher give <player> referral [auto|type|amount|type:amount] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/voucher give <player> coupon [code] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/voucher give <player> rank [rank] [amount]");
    }
}
