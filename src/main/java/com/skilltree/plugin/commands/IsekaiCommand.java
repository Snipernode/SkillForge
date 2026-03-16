package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.IsekaiSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IsekaiCommand implements CommandExecutor {

    private final SkillForgePlugin plugin;

    public IsekaiCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        IsekaiSystem system = plugin.getIsekaiSystem();
        if (system == null) {
            player.sendMessage(ChatColor.RED + "Isekai system is unavailable.");
            return true;
        }

        if (!player.hasPermission("skillforge.isekai")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use /isekai.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            system.showIsekaiInfo(player);
            return true;
        }

        if (!system.isEligible(player)) {
            system.sendEligibilityMessage(player);
            return true;
        }

        if (system.hasIsekaiItem(player)) {
            player.sendMessage(ChatColor.YELLOW + "You already carry a reincarnation relic.");
            system.showIsekaiInfo(player);
            return true;
        }

        if (plugin.getIsekaiGUI() != null) {
            plugin.getIsekaiGUI().openSelection(player);
        } else {
            player.sendMessage(ChatColor.RED + "Isekai selection is unavailable.");
        }
        return true;
    }
}
