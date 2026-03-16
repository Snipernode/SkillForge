package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StaminaSystem implements Listener {
    
    private final SkillForgePlugin plugin;
    private final Map<UUID, Double> lastY;
    
    // Config settings
    private boolean enabled;
    private double sprintDrainPerTick;
    private double jumpDrain;
    private double regenPerSecond;
    private boolean requireSelectedGamemode;
    
    public StaminaSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.lastY = new HashMap<>();
        loadConfig();
        
        if (enabled) {
            startRegenTask();
        }
    }
    
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("stamina.enabled", true);
        sprintDrainPerTick = getDoubleCompat("stamina.sprint-drain-per-tick", "stamina.sprint_drain_per_tick", 0.15);
        jumpDrain = getDoubleCompat("stamina.jump-drain", "stamina.jump_drain", 5.0);
        regenPerSecond = getDoubleCompat("stamina.regen-per-second", "stamina.regen_per_second", 3.0);
        requireSelectedGamemode = plugin.getConfig().getBoolean("stamina.require-selected-gamemode", false);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // --- Core Logic ---

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                    if (!shouldProcessVitals(data)) continue;
                    
                    // Only regen if not sprinting
                    if (!player.isSprinting()) {
                        double regenPerTick = regenPerSecond / 20.0;
                        data.regenStamina(regenPerTick);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    /**
     * Called by AbilityExecutor to consume stamina for skills
     */
    public boolean useStaminaForSkill(Player player, double amount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data.getStamina() >= amount) {
            data.drainStamina(amount);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Not enough stamina!");
            return false;
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        if (!shouldProcessVitals(data)) return;
        
        UUID uuid = player.getUniqueId();
        
        if (player.isSprinting()) {
            data.drainStamina(sprintDrainPerTick);
            
            if (data.getStamina() <= 0) {
                player.setSprinting(false);
            }
        }
        
        double prevY = lastY.getOrDefault(uuid, event.getFrom().getY());
        double currentY = event.getTo().getY();
        
        if (currentY > prevY && (currentY - prevY) > 0.3) {
            if (!player.isFlying() && !player.isGliding()) {
                data.drainStamina(jumpDrain);
            }
        }
        
        lastY.put(uuid, currentY);
    }
    
    public void cleanupPlayer(UUID playerId) {
        lastY.remove(playerId);
    }

    private boolean shouldProcessVitals(PlayerData data) {
        if (data == null) return false;
        return !requireSelectedGamemode || data.hasSelectedGamemode();
    }

    private double getDoubleCompat(String primaryPath, String legacyPath, double def) {
        if (plugin.getConfig().isSet(primaryPath)) {
            return plugin.getConfig().getDouble(primaryPath, def);
        }
        return plugin.getConfig().getDouble(legacyPath, def);
    }
}
