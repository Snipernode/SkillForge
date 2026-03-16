package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EvershardsCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public EvershardsCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        player.sendMessage("§6§l[SkillForge] §eYour Evershards: §a" + data.getEvershards() + " ES");
        return true;
    }
}
