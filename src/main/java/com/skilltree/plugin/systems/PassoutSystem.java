package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassoutSystem implements Listener {

    private final SkillForgePlugin plugin;
    private final Map<UUID, Long> lastPassoutAt = new ConcurrentHashMap<>();

    private boolean enabled;
    private boolean requireSelectedGamemode;
    private double triggerStamina;
    private double triggerThirst;
    private long cooldownMillis;
    private int blackoutTicks;
    private double recoverStamina;
    private double recoverThirst;
    private boolean dropItemEnabled;
    private boolean preferArmor;
    private boolean includeHeldItems;

    public PassoutSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        if (enabled) {
            startTask();
        }
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("passout.enabled", true);
        requireSelectedGamemode = plugin.getConfig().getBoolean("passout.require-selected-gamemode", false);
        triggerStamina = plugin.getConfig().getDouble("passout.trigger-stamina", 0.0);
        triggerThirst = plugin.getConfig().getDouble("passout.trigger-thirst", 0.0);
        cooldownMillis = Math.max(1L, plugin.getConfig().getLong("passout.cooldown-seconds", 180L)) * 1000L;
        blackoutTicks = Math.max(40, plugin.getConfig().getInt("passout.blackout-seconds", 6) * 20);
        recoverStamina = Math.max(0.0, plugin.getConfig().getDouble("passout.restore-stamina-after-passout", 15.0));
        recoverThirst = Math.max(0.0, plugin.getConfig().getDouble("passout.restore-thirst-after-passout", 15.0));
        dropItemEnabled = plugin.getConfig().getBoolean("passout.drop-item-enabled", true);
        preferArmor = plugin.getConfig().getBoolean("passout.prefer-armor", true);
        includeHeldItems = plugin.getConfig().getBoolean("passout.include-held-items", true);
    }

    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void processPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.isDead()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;
        if (requireSelectedGamemode && !data.hasSelectedGamemode()) return;

        if (data.getStamina() > triggerStamina || data.getThirst() > triggerThirst) return;

        long now = System.currentTimeMillis();
        long last = lastPassoutAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMillis) return;

        triggerPassout(player, data, now);
    }

    private void triggerPassout(Player player, PlayerData data, long now) {
        lastPassoutAt.put(player.getUniqueId(), now);

        player.sendTitle(
                ChatColor.DARK_RED + "You Passed Out",
                ChatColor.GRAY + "No stamina and no thirst",
                5, Math.max(40, blackoutTicks / 2), 10
        );
        player.sendMessage(ChatColor.DARK_RED + "You collapsed from exhaustion and dehydration.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.6f);
        player.setVelocity(new Vector(0, 0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blackoutTicks, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, blackoutTicks, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, blackoutTicks, 10, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, blackoutTicks, 2, false, true));

        if (dropItemEnabled) {
            dropLossItem(player);
        }

        if (recoverStamina > 0) data.regenStamina(recoverStamina);
        if (recoverThirst > 0) data.restoreThirst(recoverThirst);
    }

    private void dropLossItem(Player player) {
        PlayerInventory inv = player.getInventory();
        List<LossSlotType> armorSlots = List.of(
                LossSlotType.HELMET, LossSlotType.CHESTPLATE, LossSlotType.LEGGINGS, LossSlotType.BOOTS
        );
        List<LossSlotType> heldSlots = List.of(LossSlotType.MAIN_HAND, LossSlotType.OFF_HAND);

        List<LossSlotType> candidates = new ArrayList<>();
        for (LossSlotType type : armorSlots) {
            if (hasItem(inv, type)) {
                candidates.add(type);
            }
        }

        if ((!preferArmor || candidates.isEmpty()) && includeHeldItems) {
            for (LossSlotType type : heldSlots) {
                if (hasItem(inv, type)) {
                    candidates.add(type);
                }
            }
        }

        if (candidates.isEmpty() && includeHeldItems) {
            for (LossSlotType type : heldSlots) {
                if (hasItem(inv, type)) {
                    candidates.add(type);
                }
            }
        }

        if (candidates.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You were lucky and did not drop an item.");
            return;
        }

        LossSlotType chosen = candidates.get((int) (Math.random() * candidates.size()));
        ItemStack current = getItem(inv, chosen);
        if (current == null || current.getType() == Material.AIR) return;

        ItemStack drop;
        if (current.getAmount() > 1) {
            drop = current.clone();
            drop.setAmount(1);
            current.setAmount(current.getAmount() - 1);
            setItem(inv, chosen, current);
        } else {
            drop = current.clone();
            setItem(inv, chosen, null);
        }

        Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), drop);
        dropped.setPickupDelay(40);
        player.sendMessage(ChatColor.RED + "You dropped: " + ChatColor.YELLOW + formatItemName(drop) + ChatColor.GRAY + " (" + chosen.display + ")");
    }

    private String formatItemName(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return "Unknown";
        return stack.getType().name().toLowerCase().replace('_', ' ');
    }

    private boolean hasItem(PlayerInventory inv, LossSlotType type) {
        ItemStack item = getItem(inv, type);
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private ItemStack getItem(PlayerInventory inv, LossSlotType type) {
        return switch (type) {
            case HELMET -> inv.getHelmet();
            case CHESTPLATE -> inv.getChestplate();
            case LEGGINGS -> inv.getLeggings();
            case BOOTS -> inv.getBoots();
            case MAIN_HAND -> inv.getItemInMainHand();
            case OFF_HAND -> inv.getItemInOffHand();
        };
    }

    private void setItem(PlayerInventory inv, LossSlotType type, ItemStack item) {
        switch (type) {
            case HELMET -> inv.setHelmet(item);
            case CHESTPLATE -> inv.setChestplate(item);
            case LEGGINGS -> inv.setLeggings(item);
            case BOOTS -> inv.setBoots(item);
            case MAIN_HAND -> inv.setItemInMainHand(item == null ? new ItemStack(Material.AIR) : item);
            case OFF_HAND -> inv.setItemInOffHand(item == null ? new ItemStack(Material.AIR) : item);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        lastPassoutAt.remove(event.getPlayer().getUniqueId());
    }

    private enum LossSlotType {
        HELMET("helmet"),
        CHESTPLATE("chestplate"),
        LEGGINGS("leggings"),
        BOOTS("boots"),
        MAIN_HAND("main hand"),
        OFF_HAND("off hand");

        private final String display;

        LossSlotType(String display) {
            this.display = display;
        }
    }
}
