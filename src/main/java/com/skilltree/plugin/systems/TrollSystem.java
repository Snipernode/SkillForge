package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrollSystem implements Listener, CommandExecutor {

    private final SkillForgePlugin plugin;
    private final Set<UUID> driftCursed = ConcurrentHashMap.newKeySet();

    public TrollSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!driftCursed.contains(player.getUniqueId())) return;

        int duration = plugin.getConfig().getInt("troll.nausea_duration_ticks", 80);
        if (duration > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, true, false, false));
        }

        double strength = plugin.getConfig().getDouble("troll.drift_strength", 0.50);
        Vector dir = player.getLocation().getDirection();
        if (dir.lengthSquared() > 0.0001) {
            Vector left = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(strength);
            player.setVelocity(player.getVelocity().add(left));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        UUID id = player.getUniqueId();
        if (driftCursed.contains(id)) {
            driftCursed.remove(id);
            player.sendMessage(ChatColor.GRAY + "The drift fades.");
        } else {
            driftCursed.add(id);
            player.sendMessage(ChatColor.DARK_PURPLE + "A strange vertigo takes hold...");
        }
        return true;
    }
}
