package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class ThirstSystem implements Listener {
    
    private final SkillForgePlugin plugin;
    
    // Config settings
    private boolean enabled;
    private double defaultDrainPerMinute;
    private int drainIntervalTicks;
    private double waterBottleRestore;
    private double milkBucketRestore;
    private boolean requireSelectedGamemode;
    
    public ThirstSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        
        if (enabled) {
            startDrainTask();
            startEffectsTask();
        }
    }
    
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("thirst.enabled", true);
        defaultDrainPerMinute = getDoubleCompat("thirst.drain-per-minute", "thirst.default-drain-per-minute", 2.0);
        drainIntervalTicks = getIntCompat("thirst.drain-interval-ticks", "thirst.drain_interval_ticks", 1200); // 1 minute default
        waterBottleRestore = getDoubleCompat("thirst.water-bottle-restore", "thirst.water_bottle_restore", 30.0);
        milkBucketRestore = getDoubleCompat("thirst.milk-bucket-restore", "thirst.milk_bucket_restore", 50.0);
        requireSelectedGamemode = plugin.getConfig().getBoolean("thirst.require-selected-gamemode", false);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // --- Core Logic ---

    private void startDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double intervalSeconds = drainIntervalTicks / 20.0;
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                    if (!shouldProcessVitals(data)) continue;

                    // Get World Specific Drain Rate
                    String worldName = player.getWorld().getName();
                    double drainPerMinute = getWorldDrainPerMinute(worldName);
                    
                    double drainAmount = (drainPerMinute / 60.0) * intervalSeconds;
                    
                    data.drainThirst(drainAmount);
                    
                    if (data.getThirst() <= 10) {
                        player.sendMessage(ChatColor.RED + "You are extremely thirsty! Drink water soon!");
                    } else if (data.getThirst() <= 30) {
                        player.sendMessage(ChatColor.YELLOW + "You are getting thirsty...");
                    }
                }
            }
        }.runTaskTimer(plugin, drainIntervalTicks, drainIntervalTicks);
    }
    
    private void startEffectsTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                    if (!shouldProcessVitals(data)) continue;
                    
                    double thirst = data.getThirst();
                    
                    if (thirst <= 10) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, false));
                    } else if (thirst <= 30) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }
    
    @EventHandler
    public void onPlayerDrink(PlayerItemConsumeEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        if (!shouldProcessVitals(data)) return;
        
        Material item = event.getItem().getType();
        double restoreAmount = 0;
        
        if (item == Material.POTION) {
            restoreAmount = waterBottleRestore;
        } else if (item == Material.MILK_BUCKET) {
            restoreAmount = milkBucketRestore;
        }
        
        if (restoreAmount > 0) {
            double before = data.getThirst();
            data.restoreThirst(restoreAmount);
            double after = data.getThirst();
            
            player.sendMessage(ChatColor.AQUA + "Thirst restored! (" + 
                (int) before + " -> " + (int) after + ")");
        }
    }

    private boolean shouldProcessVitals(PlayerData data) {
        if (data == null) return false;
        return !requireSelectedGamemode || data.hasSelectedGamemode();
    }

    private double getWorldDrainPerMinute(String worldName) {
        String base = "worlds." + worldName + ".";
        if (plugin.getConfig().isSet(base + "thirst-drain-rate")) {
            return plugin.getConfig().getDouble(base + "thirst-drain-rate", defaultDrainPerMinute);
        }
        return plugin.getConfig().getDouble(base + "thirst_drain_rate", defaultDrainPerMinute);
    }

    private double getDoubleCompat(String primaryPath, String legacyPath, double def) {
        if (plugin.getConfig().isSet(primaryPath)) {
            return plugin.getConfig().getDouble(primaryPath, def);
        }
        return plugin.getConfig().getDouble(legacyPath, def);
    }

    private int getIntCompat(String primaryPath, String legacyPath, int def) {
        if (plugin.getConfig().isSet(primaryPath)) {
            return plugin.getConfig().getInt(primaryPath, def);
        }
        return plugin.getConfig().getInt(legacyPath, def);
    }
}
