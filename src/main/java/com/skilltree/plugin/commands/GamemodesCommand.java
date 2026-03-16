package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodesCommand implements CommandExecutor {

    private final SkillForgePlugin plugin;

    public GamemodesCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("gamemodes.enabled", true)) {
            player.sendMessage(ChatColor.RED + "Gamemodes are currently disabled.");
            return true;
        }
        if (plugin.getGamemodeSelectionGUI() == null) {
            player.sendMessage(ChatColor.RED + "Gamemode menu is unavailable.");
            return true;
        }

        plugin.getGamemodeSelectionGUI().openForPlayer(player);
        return true;
    }
}

