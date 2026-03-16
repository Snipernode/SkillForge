package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatBarSystem implements Listener {

    private final SkillForgePlugin plugin;
    private final boolean enabled;
    
    // Storage for the two bars
    private final Map<UUID, BossBar> thirstBars = new HashMap<>();
    private final Map<UUID, BossBar> staminaBars = new HashMap<>();

    public StatBarSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("hud.legacy_statbars_enabled", false);
        if (!enabled) {
            return;
        }
        startUIUpdateTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Create Thirst Bar
        BossBar thirstBar = Bukkit.createBossBar(ChatColor.BLUE + "Thirst", BarColor.BLUE, BarStyle.SEGMENTED_10);
        thirstBar.addPlayer(player);
        thirstBars.put(uuid, thirstBar);

        // Create Stamina Bar
        BossBar staminaBar = Bukkit.createBossBar(ChatColor.GREEN + "Stamina", BarColor.GREEN, BarStyle.SEGMENTED_20);
        staminaBar.addPlayer(player);
        staminaBars.put(uuid, staminaBar);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        if (thirstBars.containsKey(uuid)) {
            thirstBars.get(uuid).removeAll();
            thirstBars.remove(uuid);
        }
        if (staminaBars.containsKey(uuid)) {
            staminaBars.get(uuid).removeAll();
            staminaBars.remove(uuid);
        }
    }

    private void startUIUpdateTask() {
        if (!enabled) return;
        // Update UI every tick for smoothness
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                    if (!data.hasSelectedGamemode()) continue;

                    UUID uuid = player.getUniqueId();

                    // --- Update Thirst Bar ---
                    if (thirstBars.containsKey(uuid)) {
                        BossBar bar = thirstBars.get(uuid);
                        double thirstPct = Math.max(0, Math.min(1, data.getThirst() / 100.0));
                        bar.setProgress(thirstPct);

                        if (data.getThirst() <= 20) {
                            bar.setColor(BarColor.RED);
                            bar.setTitle(ChatColor.RED + "Critical Thirst");
                        } else if (data.getThirst() <= 50) {
                            bar.setColor(BarColor.YELLOW);
                            bar.setTitle(ChatColor.YELLOW + "Thirsty");
                        } else {
                            bar.setColor(BarColor.BLUE);
                            bar.setTitle(ChatColor.BLUE + "Thirst");
                        }
                    }

                    // --- Update Stamina Bar ---
                    if (staminaBars.containsKey(uuid)) {
                        BossBar bar = staminaBars.get(uuid);
                        double staminaPct = Math.max(0, Math.min(1, data.getStamina() / 100.0));
                        bar.setProgress(staminaPct);

                        if (data.getStamina() <= 20) {
                            bar.setColor(BarColor.RED);
                            bar.setTitle(ChatColor.RED + "Exhausted");
                        } else {
                            bar.setColor(BarColor.GREEN);
                            bar.setTitle(ChatColor.GREEN + "Stamina");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
