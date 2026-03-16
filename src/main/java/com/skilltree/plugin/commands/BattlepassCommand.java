package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.gui.BattlepassGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BattlepassCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public BattlepassCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        new BattlepassGUI(plugin, player).open();
        return true;
    }
}
