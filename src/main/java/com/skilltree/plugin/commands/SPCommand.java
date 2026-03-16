package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SPCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public SPCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        String category = (args.length > 0 && args[0] != null && !args[0].isBlank()) ? args[0] : "combat";

        if (plugin.getSkillSanctumSystem() != null && plugin.getSkillSanctumSystem().isEnabled()) {
            plugin.getSkillSanctumSystem().open(player, category);
        } else if (plugin.getSkillGraphSystem() != null) {
            plugin.getSkillGraphSystem().open(player, category);
        } else {
            player.sendMessage("§cSkill sanctum is unavailable right now.");
        }
        return true;
    }
}
