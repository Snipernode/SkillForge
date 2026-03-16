package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.AbilityExecutionSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CastCommand implements CommandExecutor {

    private final SkillForgePlugin plugin;
    private final AbilityExecutionSystem abilitySystem;

    public CastCommand(SkillForgePlugin plugin, AbilityExecutionSystem abilitySystem) {
        this.plugin = plugin;
        this.abilitySystem = abilitySystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /cast <ability_id>");
            return true;
        }

        String abilityId = args[0].toLowerCase();
        
        abilitySystem.triggerAbility(player, abilityId, false);
        
        return true;
    }
}
