package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;


public class CosmeticsEffectsSystem {
    
    private final SkillForgePlugin plugin;
    
    public CosmeticsEffectsSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        startEffectsTask();
    }
    
    private void startEffectsTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
                    if (!data.hasSelectedGamemode()) continue;
                    String activeCosmetic = data.getActiveCosmetic();
                    if (activeCosmetic == null) continue;
                    applyEffect(player, activeCosmetic);
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }
    
    private void applyEffect(Player player, String cosmeticId) {
        switch (cosmeticId) {
            case "rainbow_trail" -> applyRainbowTrail(player);
            case "particle_aura" -> applyParticleAura(player);
            case "fire_aura" -> applyFireAura(player);
            case "ice_trail" -> applyIceTrail(player);
            case "thunder_effect" -> applyThunderEffect(player);
            case "shadow_cloak" -> applyShadowCloak(player);
            case "light_halo" -> applyLightHalo(player);
            case "starlight_aura" -> applyStarlightAura(player);
            case "void_trail" -> applyVoidTrail(player);
            case "wings_cosmetic" -> applyWingsEffect(player);
        }
    }
    
    private void applyRainbowTrail(Player player) {
        Color[] colors = {Color.RED, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE, Color.FUCHSIA};
        int index = (int) ((System.currentTimeMillis() / 100) % colors.length);
        Color color = colors[index];
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.8f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, dust);
    }
    
    private void applyParticleAura(Player player) {
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0);
        player.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, player.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
    }
    
    private void applyFireAura(Player player) {
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
    }
    
    private void applyIceTrail(Player player) {
        Color cyan = Color.fromARGB(255, 0, 255, 255);
        Particle.DustOptions dust = new Particle.DustOptions(cyan, 0.6f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, dust);
        player.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, player.getLocation().add(0, 0.5, 0), 3, 0.15, 0.15, 0.15, 0);
    }
    
    private void applyThunderEffect(Player player) {
        if (Math.random() < 0.1) {
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0);
        }
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 0.5, 0), 2, 0.15, 0.15, 0.15, 0);
    }
    
    private void applyShadowCloak(Player player) {
        // FALLING_DUST requires BlockData; passing "extra" alone throws IllegalArgumentException on 1.21+
        player.getWorld().spawnParticle(
                Particle.FALLING_DUST,
                player.getLocation().add(0, 0.5, 0),
                2,
                0.3,
                0.3,
                0.3,
                Material.GRAY_CONCRETE_POWDER.createBlockData()
        );
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 2, 0.2, 0.3, 0.2, 0.05);
    }
    
    private void applyLightHalo(Player player) {
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 2, 0), 6, 0.5, 0.1, 0.5, 0);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.8, 0), 2, 0.4, 0.2, 0.4, 0);
    }
    
    private void applyStarlightAura(Player player) {
        double angle = (System.currentTimeMillis() / 20.0) % (2 * Math.PI);
        double x = Math.cos(angle) * 0.8;
        double z = Math.sin(angle) * 0.8;
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(x, 1, z), 1);
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0);
    }
    
    private void applyVoidTrail(Player player) {
        Color purple = Color.fromARGB(255, 128, 0, 255);
        Particle.DustOptions dust = new Particle.DustOptions(purple, 0.7f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, dust);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.3, 0), 2, 0.15, 0.15, 0.15, 0.05);
    }
    
    private void applyWingsEffect(Player player) {
        double angle = (System.currentTimeMillis() / 10.0) % (2 * Math.PI);
        double wingX = Math.cos(angle) * 0.6;
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(-wingX, 1.2, 0), 2);
        player.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(wingX, 1.2, 0), 2);
    }
}
