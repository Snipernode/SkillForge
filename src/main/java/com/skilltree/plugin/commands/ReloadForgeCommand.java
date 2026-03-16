package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadForgeCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public ReloadForgeCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skillforge.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        plugin.reloadConfig();
        if (plugin.getStaminaSystem() != null) {
            plugin.getStaminaSystem().loadConfig();
        }
        if (plugin.getThirstSystem() != null) {
            plugin.getThirstSystem().loadConfig();
        }
        if (plugin.getPassoutSystem() != null) {
            plugin.getPassoutSystem().loadConfig();
        }
        if (plugin.getKDHudBridge() != null) {
            plugin.getKDHudBridge().loadConfig();
        }
        if (plugin.getNpcJobSystem() != null) {
            plugin.getNpcJobSystem().loadConfig();
            plugin.getNpcJobSystem().saveData();
        }
        if (plugin.getSkillTreeSystem() != null && plugin.getSkillRegistry() != null) {
            plugin.getSkillTreeSystem().syncWithRegistry(plugin.getSkillRegistry());
        }
        if (plugin.getVoteRewardSystem() != null) {
            plugin.getVoteRewardSystem().loadConfig();
        }
        if (plugin.getConfig().getBoolean("nexo.inject.on_reloadforge", true)) {
            plugin.syncNexoAssets(true);
            sender.sendMessage("§6§l[SkillForge] §7Nexo assets synced.");
        }
        sender.sendMessage("§6§l[SkillForge] §aConfiguration reloaded successfully!");
        return true;
    }
}
