package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildCardCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public GuildCardCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command is only for players!");
                return true;
            }
            
            if (args.length == 0) {
                plugin.getGuildCardSystem().giveGuildCard(player);
                return true;
            }
            
            if (args.length == 1) {
                String targetName = args[0];
                Player target = plugin.getServer().getPlayer(targetName);
                
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                
                plugin.getGuildCardSystem().giveGuildCard(target);
                player.sendMessage(ChatColor.GREEN + "✓ Guild card created for " + target.getName());
                return true;
            }
        } catch (Exception ex) {
            // prevent command exception from bubbling up and killing the command thread
            plugin.getLogger().severe("Error executing /guildcard: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            sender.sendMessage("§cAn internal error occurred while running this command. See server log.");
            return true;
        }
        
        return false;
    }
}
