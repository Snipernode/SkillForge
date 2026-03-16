package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class BindCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public BindCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("shop")) {
            if (!player.hasPermission("skillforge.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to bind the shop.");
                return true;
            }
            player.getPersistentDataContainer().set(new NamespacedKey(plugin, "binding_shop"), PersistentDataType.BYTE, (byte) 1);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "[Shop] " + ChatColor.YELLOW + "Right-click an NPC (or Entity) to bind the shop to it.");
            return true;
        }
        
        plugin.getBindAbilityGUI().openForPlayer(player);
        return true;
    }
}
