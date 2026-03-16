package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.managers.CrateKeyLedger;
import com.skilltree.plugin.systems.CrateBlockSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class CrateCommand implements CommandExecutor {
    private final SkillForgePlugin plugin;
    private final CrateKeyLedger ledger;
    private final CrateBlockSystem crateBlockSystem;
    private final Random random = new Random();

    public CrateCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.ledger = plugin.getCrateKeyLedger();
        this.crateBlockSystem = plugin.getCrateBlockSystem();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("crates.enabled", true)) {
            sender.sendMessage(ChatColor.RED + "Crates are disabled.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("keys")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            sendKeyCounts(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("open")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /crate open <type>");
                return true;
            }
            openCrate(player, args[1]);
            return true;
        }

        if (sub.equals("buddy")) {
            if (!sender.hasPermission("skillforge.crate.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /crate buddy <player>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            long amount = plugin.getCrateRewardManager().grantBuddyKeys(target);
            sender.sendMessage(ChatColor.GREEN + "Granted " + amount + " buddy key(s) to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " buddy key(s).");
            return true;
        }

        if (sub.equals("give")) {
            if (!sender.hasPermission("skillforge.crate.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /crate give <player> <type> [amount]");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            String type = args[2].toLowerCase();
            long amount = parseLong(args, 3, 1L);
            amount = Math.max(1L, amount);

            if (!isValidType(type)) {
                sender.sendMessage(ChatColor.RED + "Unknown crate type: " + type);
                return true;
            }

            if (crateBlockSystem != null) {
                crateBlockSystem.giveKeyItems(target, type, amount);
            } else {
                ledger.addKeys(target.getUniqueId(), type, amount);
            }
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " " + type + " key item(s) to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " " + type + " key item(s).");
            return true;
        }

        if (sub.equals("placer") || sub.equals("place")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            if (!sender.hasPermission("skillforge.crate.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (crateBlockSystem == null) {
                sender.sendMessage(ChatColor.RED + "Crate block system unavailable.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /crate placer <type> [amount]");
                return true;
            }
            String type = args[1].toLowerCase();
            if (!isValidType(type)) {
                sender.sendMessage(ChatColor.RED + "Unknown crate type: " + type);
                return true;
            }
            int amount = (int) Math.max(1L, parseLong(args, 2, 1L));
            giveStacks(player, crateBlockSystem.createCratePlacerItem(type), amount);
            sender.sendMessage(ChatColor.GREEN + "Given " + amount + " crate placer(s) for type: " + type + ".");
            sender.sendMessage(ChatColor.GRAY + "Place: right-click block face. Edit: shift-right-click placed crate.");
            return true;
        }

        if (sub.equals("editor") || sub.equals("edit")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            if (!sender.hasPermission("skillforge.crate.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /crate editor <type>");
                return true;
            }
            String type = args[1].toLowerCase();
            if (!isValidType(type)) {
                plugin.getConfig().createSection("crates.types." + type);
                plugin.getConfig().set("crates.types." + type + ".display_name",
                        Character.toUpperCase(type.charAt(0)) + type.substring(1) + " Crate");
                plugin.getConfig().set("crates.types." + type + ".rewards", List.of());
                plugin.saveConfig();
            }
            if (plugin.getCrateLootTableGUI() == null) {
                player.sendMessage(ChatColor.RED + "Crate editor GUI is unavailable.");
                return true;
            }
            plugin.getCrateLootTableGUI().openEditor(player, type);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Crate commands:");
        sender.sendMessage(ChatColor.GRAY + "/crate keys");
        sender.sendMessage(ChatColor.GRAY + "/crate open <type>");
        sender.sendMessage(ChatColor.GRAY + "/crate give <player> <type> [amount]");
        sender.sendMessage(ChatColor.GRAY + "/crate placer <type> [amount]");
        sender.sendMessage(ChatColor.GRAY + "/crate buddy <player>");
        sender.sendMessage(ChatColor.GRAY + "/crate editor <type>");
    }

    private void sendKeyCounts(Player player) {
        ConfigurationSection types = plugin.getConfig().getConfigurationSection("crates.types");
        if (types == null || types.getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.RED + "No crates are configured.");
            return;
        }
        Map<String, Long> ledgerKeys = ledger.getAllKeys(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "Your crate keys:");
        for (String type : types.getKeys(false)) {
            long count;
            if (crateBlockSystem != null) {
                count = crateBlockSystem.getTotalKeyCount(player, type.toLowerCase());
            } else {
                count = ledgerKeys.getOrDefault(type.toLowerCase(), 0L);
            }
            player.sendMessage(ChatColor.YELLOW + "- " + type + ": " + count);
        }
    }

    private void openCrate(Player player, String type) {
        if (type == null) return;
        String normalized = type.toLowerCase();
        if (!isValidType(normalized)) {
            player.sendMessage(ChatColor.RED + "Unknown crate type: " + normalized);
            return;
        }
        List<ItemStack> rewards = crateBlockSystem != null
                ? crateBlockSystem.getRewards(normalized)
                : List.of();
        if (rewards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This crate has no rewards configured.");
            return;
        }
        boolean consumed = crateBlockSystem != null
                ? crateBlockSystem.consumeOneKey(player, normalized)
                : ledger.removeKeys(player.getUniqueId(), normalized, 1L) >= 0;
        if (!consumed) {
            player.sendMessage(ChatColor.RED + "You don't have any " + normalized + " keys.");
            return;
        }
        ItemStack item = rewards.get(random.nextInt(rewards.size())).clone();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack extra : overflow.values()) {
                if (extra != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
        }
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(item.getItemMeta().getDisplayName())
                : item.getType().name();
        player.sendMessage(ChatColor.GREEN + "You opened a " + normalized + " crate and received "
                + item.getAmount() + "x " + itemName + "!");
    }

    private boolean isValidType(String type) {
        if (type == null || type.isBlank()) return false;
        if (crateBlockSystem != null) {
            return crateBlockSystem.isValidCrateType(type);
        }
        return plugin.getConfig().isConfigurationSection("crates.types." + type.toLowerCase());
    }

    private void giveStacks(Player player, ItemStack prototype, int amount) {
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            int size = Math.min(64, remaining);
            ItemStack stack = prototype.clone();
            stack.setAmount(size);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                for (ItemStack extra : overflow.values()) {
                    if (extra != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), extra);
                    }
                }
            }
            remaining -= size;
        }
    }

    private long parseLong(String[] args, int index, long fallback) {
        if (args == null || index < 0 || index >= args.length) return fallback;
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
