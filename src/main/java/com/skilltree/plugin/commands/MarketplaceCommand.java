package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MarketplaceCommand implements CommandExecutor {
    private final SkillForgePlugin plugin;

    public MarketplaceCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        player.sendMessage("§6§l[Marketplace] §eThe Player Marketplace is coming soon! Barter with others for Starshards.");
        return true;
    }
}
