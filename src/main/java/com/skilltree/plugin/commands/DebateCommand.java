package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.SkillTreeSystem;
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
        
        if (!player.isOp()) {
            player.sendMessage("§cYou must be an OP to use this command!");
            return true;
        }
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        data.setEvershards(999999);
        data.setSkillPoints(999999);
        
        for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getAllSkillNodes().values()) {
            data.setSkillLevel(node.getId(), node.getMaxLevel());
        }
        
        player.sendMessage("§6§l[SkillForge] §aGod mode activated! All stats maxed!");
        player.sendMessage("§e§l[Note] §7Innate skills are NOT affected by this command - assign them with /roll!");
        
        return true;
    }
}
