package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CastCommand implements CommandExecutor {
    private final SkillForgePlugin plugin;
    private final SkillRegistry skillRegistry;

    public CastCommand(SkillForgePlugin plugin, SkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.skillRegistry = skillRegistry;
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
        
        // Retrieve player data to get the skill level
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int level = data.getSkillLevel(abilityId);

        // Execute the skill using the registry
        // The registry handles checks for prerequisites, cooldowns, and stamina
        skillRegistry.execute(abilityId, player, level);
        
        return true;
    }
}
