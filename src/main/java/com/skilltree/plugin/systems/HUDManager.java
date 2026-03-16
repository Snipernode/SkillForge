package com.skilltree.plugin.systems;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HUDManager implements Listener {

    private final SkillForgePlugin plugin;
    private final boolean protocolLibAvailable;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Double> lastHealthScaleValues = new HashMap<>();
    private final Map<UUID, BossBar> healthBars = new HashMap<>();
    private final Map<UUID, Double> lastHealthValues = new HashMap<>();
    private final Map<UUID, Double> lastMaxHealthValues = new HashMap<>();
    private final Map<UUID, BossBar> staminaBars = new HashMap<>();
    private final Map<UUID, BossBar> thirstBarsFallback = new HashMap<>();
    private final Map<UUID, String> thirstObjectives = new HashMap<>();
    private final Map<UUID, List<String>> thirstEntries = new HashMap<>();
    private final Set<UUID> warnedMissingProtocol = new HashSet<>();

    private static final ChatColor[] UNIQUE_COLORS = new ChatColor[]{
            ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    };
    private static final char GLYPH_HEART_EMPTY = '\uE100';
    private static final char GLYPH_HEART_QUARTER = '\uE101';
    private static final char GLYPH_HEART_HALF = '\uE102';
    private static final char GLYPH_HEART_THREE_QUARTER = '\uE103';
    private static final char GLYPH_HEART_FULL = '\uE104';

    public HUDManager(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.protocolLibAvailable = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        this.protocolManager = protocolLibAvailable ? ProtocolLibrary.getProtocolManager() : null;
        startUpdateTask();
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerHUD(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void updatePlayerHUD(Player player) {
        updateHealthBar(player);
        updateThirstBar(player);
        updateStaminaBar(player);
    }

    private void updateHealthBar(Player player) {
        if (!plugin.getConfig().getBoolean("hud.custom_health.enabled", true)) {
            clearVanillaHealthScale(player);
            removeHealthBar(player);
            return;
        }

        UUID uid = player.getUniqueId();
        double maxHp = getMaxHealth(player);
        double hp = Math.max(0.0, Math.min(maxHp, player.getHealth()));
        double progress = Math.max(0.0, Math.min(1.0, hp / Math.max(1.0, maxHp)));
        double mcHeartsPerGuiHeart = plugin.getConfig().getDouble(
                "hud.custom_health.mc_hearts_per_gui_heart",
                plugin.getConfig().getDouble("hud.vanilla_heart_override.mc_hearts_per_gui_heart", 2.0)
        );
        if (mcHeartsPerGuiHeart <= 0.0) mcHeartsPerGuiHeart = 2.0;

        // Real HUD change: compress vanilla hearts so one displayed heart represents multiple MC hearts.
        applyVanillaHealthScale(player, maxHp, mcHeartsPerGuiHeart);

        boolean showBossbar = plugin.getConfig().getBoolean("hud.custom_health.show_bossbar", false);
        if (!showBossbar) {
            removeHealthBar(player);
            return;
        }

        BossBar bar = healthBars.computeIfAbsent(uid, id ->
                Bukkit.createBossBar(ChatColor.RED + "Health", BarColor.RED, BarStyle.SEGMENTED_10));

        // Only change the rendered health HUD when health/max-health actually changes.
        double prevHp = lastHealthValues.getOrDefault(uid, -1.0);
        double prevMax = lastMaxHealthValues.getOrDefault(uid, -1.0);
        boolean changed = Math.abs(prevHp - hp) > 0.01 || Math.abs(prevMax - maxHp) > 0.01;
        if (changed) {
            boolean glyphHearts = plugin.getConfig().getBoolean("hud.custom_health.use_glyph_hearts", true);
            bar.setTitle(ChatColor.RED + "Health "
                    + renderGuiHeartText(hp, maxHp, mcHeartsPerGuiHeart, glyphHearts)
                    + ChatColor.DARK_GRAY + " (" + formatHp(hp) + "/" + formatHp(maxHp) + " HP)");
            bar.setProgress(progress);
            lastHealthValues.put(uid, hp);
            lastMaxHealthValues.put(uid, maxHp);
        }

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void updateThirstBar(Player player) {
        if (!plugin.getConfig().getBoolean("hud.thirst_bar_enabled", true)) {
            removeThirstHud(player);
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        double max = Math.max(1.0, data.getMaxThirst());
        double thirstPct = Math.max(0.0, Math.min(1.0, data.getThirst() / max));

        String mode = plugin.getConfig().getString("hud.thirst_display_mode", "BOSSBAR");
        if (mode == null) mode = "BOSSBAR";

        if (mode.equalsIgnoreCase("VERTICAL")) {
            if (protocolLibAvailable) {
                removeThirstBossBar(player);
                updateVerticalThirst(player, thirstPct);
            } else {
                // Hard-fallback to bossbar if ProtocolLib is missing.
                if (warnedMissingProtocol.add(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "ProtocolLib missing: using bossbar thirst HUD.");
                }
                removeVerticalThirst(player);
                updateThirstBossBarFallback(player, thirstPct);
            }
            return;
        }

        if (mode.equalsIgnoreCase("AUTO")) {
            if (protocolLibAvailable) {
                removeThirstBossBar(player);
                updateVerticalThirst(player, thirstPct);
            } else {
                removeVerticalThirst(player);
                updateThirstBossBarFallback(player, thirstPct);
            }
            return;
        }

        // Default and safest: BOSSBAR
        removeVerticalThirst(player);
        updateThirstBossBarFallback(player, thirstPct);
    }

    private void updateStaminaBar(Player player) {
        if (!plugin.getConfig().getBoolean("hud.stamina_bar_enabled", true)) {
            removeStaminaBar(player);
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        double max = Math.max(1.0, data.getMaxStamina());
        double staminaPct = Math.max(0.0, Math.min(1.0, data.getStamina() / max));

        BossBar bar = staminaBars.computeIfAbsent(player.getUniqueId(), id ->
                Bukkit.createBossBar(ChatColor.GREEN + "Stamina", BarColor.GREEN, getStaminaStyle()));
        bar.setProgress(staminaPct);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private BarStyle getStaminaStyle() {
        String style = plugin.getConfig().getString("hud.stamina_bar_style", "BAR");
        if (style == null) style = "BAR";
        return style.equalsIgnoreCase("CIRCLE") ? BarStyle.SEGMENTED_10 : BarStyle.SOLID;
    }

    private void updateThirstBossBarFallback(Player player, double pct) {
        BossBar bar = thirstBarsFallback.computeIfAbsent(player.getUniqueId(), id ->
                Bukkit.createBossBar(ChatColor.AQUA + "Thirst", BarColor.BLUE, BarStyle.SEGMENTED_10));
        bar.setProgress(pct);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void updateVerticalThirst(Player player, double pct) {
        int segments = Math.max(5, plugin.getConfig().getInt("hud.thirst_segments", 10));
        int filled = (int) Math.round(pct * segments);
        filled = Math.max(0, Math.min(segments, filled));

        String objective = getThirstObjective(player);
        ensureObjective(player, objective);

        List<String> last = thirstEntries.getOrDefault(player.getUniqueId(), Collections.emptyList());
        if (!last.isEmpty()) {
            for (String entry : last) {
                sendScorePacket(player, objective, entry, 0, EnumWrappers.ScoreboardAction.REMOVE);
            }
        }

        List<String> current = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            int indexFromBottom = i + 1;
            boolean isFilled = indexFromBottom <= filled;
            ChatColor color = isFilled ? ChatColor.AQUA : ChatColor.DARK_GRAY;
            ChatColor unique = UNIQUE_COLORS[i % UNIQUE_COLORS.length];
            String entry = color + "█" + unique;
            current.add(entry);
        }

        for (int i = 0; i < segments; i++) {
            int score = segments - i;
            sendScorePacket(player, objective, current.get(i), score, EnumWrappers.ScoreboardAction.CHANGE);
        }
        thirstEntries.put(player.getUniqueId(), current);
    }

    private void ensureObjective(Player player, String objective) {
        if (objective == null) return;
        if (!thirstObjectives.containsKey(player.getUniqueId())) {
            thirstObjectives.put(player.getUniqueId(), objective);
            sendObjectivePacket(player, objective, 0);
            sendDisplayPacket(player, objective);
        }
    }

    private String getThirstObjective(Player player) {
        return thirstObjectives.computeIfAbsent(player.getUniqueId(), id -> {
            String raw = id.toString().replace("-", "");
            return ("sft" + raw).substring(0, Math.min(16, 3 + raw.length()));
        });
    }

    private void sendObjectivePacket(Player player, String objective, int mode) {
        if (protocolManager == null) return;
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
            packet.getStrings().write(0, objective);
            packet.getIntegers().write(0, mode);
            packet.getChatComponents().write(0, WrappedChatComponent.fromText(ChatColor.AQUA + "Thirst"));
            writeObjectiveRenderType(packet);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    private void sendDisplayPacket(Player player, String objective) {
        if (protocolManager == null) return;
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE);
            packet.getIntegers().write(0, 1); // sidebar
            packet.getStrings().write(0, objective);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    private void sendScorePacket(Player player, String objective, String entry, int score, EnumWrappers.ScoreboardAction action) {
        if (protocolManager == null) return;
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_SCORE);
            packet.getStrings().write(0, entry);
            packet.getEnumModifier(EnumWrappers.ScoreboardAction.class, 0).write(0, action);
            packet.getStrings().write(1, objective);
            if (action != EnumWrappers.ScoreboardAction.REMOVE) {
                packet.getIntegers().write(0, score);
            }
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    private void removeThirstHud(Player player) {
        removeThirstBossBar(player);
        removeVerticalThirst(player);
    }

    private void removeThirstBossBar(Player player) {
        UUID uid = player.getUniqueId();
        BossBar fallback = thirstBarsFallback.remove(uid);
        if (fallback != null) fallback.removeAll();
    }

    private void removeVerticalThirst(Player player) {
        UUID uid = player.getUniqueId();
        String objective = thirstObjectives.remove(uid);
        List<String> last = thirstEntries.remove(uid);
        if (objective == null) return;
        if (last != null && protocolLibAvailable) {
            for (String entry : last) {
                sendScorePacket(player, objective, entry, 0, EnumWrappers.ScoreboardAction.REMOVE);
            }
        }
        if (protocolLibAvailable) {
            sendObjectivePacket(player, objective, 1);
        }
    }

    private void writeObjectiveRenderType(PacketContainer packet) {
        try {
            if (packet == null) return;
            @SuppressWarnings("rawtypes")
            var modifier = packet.getEnumModifier((Class) Enum.class, 0);
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = modifier.getField(0).getType().asSubclass(Enum.class);

            Enum<?> chosen = null;
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase("INTEGER")) {
                    chosen = constant;
                    break;
                }
            }
            if (chosen == null && enumClass.getEnumConstants().length > 0) {
                chosen = enumClass.getEnumConstants()[0];
            }
            if (chosen != null) {
                modifier.write(0, chosen);
            }
        } catch (Throwable ignored) {
        }
    }

    private void removeStaminaBar(Player player) {
        BossBar bar = staminaBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void removeHealthBar(Player player) {
        UUID uid = player.getUniqueId();
        BossBar bar = healthBars.remove(uid);
        lastHealthValues.remove(uid);
        lastMaxHealthValues.remove(uid);
        lastHealthScaleValues.remove(uid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void applyVanillaHealthScale(Player player, double maxHp, double mcHeartsPerGuiHeart) {
        double targetScale = maxHp / mcHeartsPerGuiHeart;
        if (targetScale < 1.0) targetScale = 1.0;

        UUID uid = player.getUniqueId();
        double prev = lastHealthScaleValues.getOrDefault(uid, -1.0);
        if (Math.abs(prev - targetScale) <= 0.01 && player.isHealthScaled()) return;

        player.setHealthScaled(true);
        player.setHealthScale(targetScale);
        lastHealthScaleValues.put(uid, targetScale);
    }

    private void clearVanillaHealthScale(Player player) {
        UUID uid = player.getUniqueId();
        if (player.isHealthScaled()) {
            player.setHealthScaled(false);
        }
        lastHealthScaleValues.remove(uid);
    }

    private double getMaxHealth(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            return Math.max(1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        return 20.0;
    }

    private String formatHp(double hp) {
        long rounded = Math.round(hp);
        if (Math.abs(hp - rounded) < 0.05) return Long.toString(rounded);
        return String.format("%.1f", hp);
    }

    private String renderGuiHeartText(double hp, double maxHp, double mcHeartsPerGuiHeart, boolean glyphHearts) {
        if (glyphHearts) {
            return renderGlyphHeartText(hp, maxHp, mcHeartsPerGuiHeart);
        }
        double hpPerGuiHeart = 2.0 * mcHeartsPerGuiHeart;
        int slots = Math.max(1, (int) Math.ceil(maxHp / hpPerGuiHeart));
        StringBuilder sb = new StringBuilder(slots * 3 + 8);
        for (int i = 0; i < slots; i++) {
            double fill = (hp - (i * hpPerGuiHeart)) / hpPerGuiHeart;
            if (fill >= 1.0) {
                sb.append(ChatColor.RED).append("♥");
            } else if (fill >= 0.75) {
                sb.append(ChatColor.GOLD).append("♥");
            } else if (fill >= 0.5) {
                sb.append(ChatColor.YELLOW).append("♥");
            } else if (fill >= 0.25) {
                sb.append(ChatColor.GRAY).append("♥");
            } else {
                sb.append(ChatColor.DARK_GRAY).append("♡");
            }
        }
        return sb.toString();
    }

    private String renderGlyphHeartText(double hp, double maxHp, double mcHeartsPerGuiHeart) {
        double hpPerGuiHeart = 2.0 * mcHeartsPerGuiHeart; // 2.0 ratio => 4 HP per GUI heart
        int slots = Math.max(1, (int) Math.ceil(maxHp / hpPerGuiHeart));
        StringBuilder sb = new StringBuilder(slots + 8);

        for (int i = 0; i < slots; i++) {
            double segmentHp = hp - (i * hpPerGuiHeart);
            double fill = Math.max(0.0, Math.min(1.0, segmentHp / hpPerGuiHeart));
            int quarterSteps = (int) Math.floor((fill * 4.0) + 1.0e-6);
            if (fill > 0.0 && quarterSteps == 0) quarterSteps = 1;
            if (quarterSteps < 0) quarterSteps = 0;
            if (quarterSteps > 4) quarterSteps = 4;

            switch (quarterSteps) {
                case 4 -> sb.append(GLYPH_HEART_FULL);
                case 3 -> sb.append(GLYPH_HEART_THREE_QUARTER);
                case 2 -> sb.append(GLYPH_HEART_HALF);
                case 1 -> sb.append(GLYPH_HEART_QUARTER);
                default -> sb.append(GLYPH_HEART_EMPTY);
            }
        }
        return ChatColor.WHITE + sb.toString();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearVanillaHealthScale(event.getPlayer());
        removeHealthBar(event.getPlayer());
        removeThirstHud(event.getPlayer());
        removeStaminaBar(event.getPlayer());
        warnedMissingProtocol.remove(event.getPlayer().getUniqueId());
    }
}
