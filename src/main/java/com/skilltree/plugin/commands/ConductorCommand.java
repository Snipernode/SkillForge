package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.FastTravelSystem;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;

public class ConductorCommand implements CommandExecutor {

    private final SkillForgePlugin plugin;
    private final String trainType;

    public ConductorCommand(SkillForgePlugin plugin, String trainType) {
        this.plugin = plugin;
        this.trainType = trainType;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skillforge.conductor")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        Location origin = null;
        Player fallback = null;
        if (sender instanceof Player player) {
            origin = player.getLocation();
            fallback = player;
        } else if (sender instanceof BlockCommandSender blockSender) {
            origin = blockSender.getBlock().getLocation();
        } else if (sender instanceof Entity entity) {
            origin = entity.getLocation();
        }

        if (origin == null) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player, entity, or command block.");
            return true;
        }

        FastTravelSystem system = plugin.getFastTravelSystem();
        if (system == null) {
            sender.sendMessage(ChatColor.RED + "FastTravel system unavailable.");
            return true;
        }

        Map<String, Object> info = system.openSelectionForNearestStation(origin, trainType, fallback);
        if (info == null) {
            sender.sendMessage(ChatColor.RED + "No stations found nearby.");
            return true;
        }

        int count = (int) info.getOrDefault("count", 0);
        String name = (String) info.getOrDefault("name", "Unknown");
        if (count <= 0) {
            sender.sendMessage(ChatColor.YELLOW + "Nearest station: " + name + " (no players in area).");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Opened " + trainType + " selection at " + name + " for " + count + " player(s).");
        }
        return true;
    }
}
