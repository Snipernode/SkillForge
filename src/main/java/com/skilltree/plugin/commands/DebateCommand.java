package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.DebateSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebateCommand implements CommandExecutor {

    private final SkillForgePlugin plugin;

    public DebateCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        DebateSystem debateSystem = plugin.getDebateSystem();
        if (debateSystem == null || !debateSystem.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Debate is unavailable right now.");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("skillforge.admin") && !player.hasPermission("skillforge.debate")) {
            player.sendMessage(ChatColor.RED + "Debate is currently restricted to admins.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("status")) {
            debateSystem.showStatus(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("begin") || args[0].equalsIgnoreCase("challenge")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /debate start <" + String.join("|", debateSystem.schools()) + ">");
                return true;
            }
            debateSystem.startDebate(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("schools")) {
            player.sendMessage(ChatColor.GOLD + "Debate schools: " + ChatColor.YELLOW + String.join(", ", debateSystem.schools()));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /debate [status|schools|start <school>]");
        return true;
    }
}
