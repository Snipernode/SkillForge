package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UseInnateCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public UseInnateCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        
        // Default: Execute assigned innate skill
        if (args.length == 0) {
            plugin.getInnateSkillSystem().executeInnateSkill(player);
            return true;
        }
        
        // Roll for a random innate skill
        if (args[0].equalsIgnoreCase("roll")) {
            plugin.getInnateSkillSystem().autoRoll(player);
            return true;
        }
        
        // Open upgrade GUI
        // CHANGED: Access GUI directly from plugin instead of via System
        if (args[0].equalsIgnoreCase("upgrade")) {
            plugin.getInnateUpgradeGUI().openForPlayer(player);
            return true;
        }
        
        // Show help menu
        if (args[0].equalsIgnoreCase("help")) {
            player.sendMessage(ChatColor.GOLD + "=== Innate Skill Commands ===");
            player.sendMessage(ChatColor.YELLOW + "/innate" + ChatColor.GRAY + " - Use your assigned innate skill");
            player.sendMessage(ChatColor.YELLOW + "/innate roll" + ChatColor.GRAY + " - Auto-roll for a random innate skill (1-51)");
            player.sendMessage(ChatColor.YELLOW + "/innate <1-51>" + ChatColor.GRAY + " - Assign a specific skill by number");
            player.sendMessage(ChatColor.YELLOW + "/innate upgrade" + ChatColor.GRAY + " - Open upgrade menu (Costs 10 SP/level)");
            player.sendMessage(ChatColor.YELLOW + "/innate help" + ChatColor.GRAY + " - Show this message");
            player.sendMessage("");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Special: Jack of None");
            player.sendMessage(ChatColor.GRAY + "If you roll 'Jack of None', you can use any innate skill");
            player.sendMessage(ChatColor.GRAY + "at 50% effectiveness. You can reroll freely with this.");
            return true;
        }
        
        // Manual roll by number
        try {
            int rollNumber = Integer.parseInt(args[0]);
            plugin.getInnateSkillSystem().assignInnateSkill(player, rollNumber);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number. Use /innate help");
            return true;
        }
    }
}
