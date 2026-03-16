package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class KDHudBridge {
    private final SkillForgePlugin plugin;
    private boolean enabled;
    private boolean warnedMissing;
    private Method getDiscordIdMethod;

    public KDHudBridge(SkillForgePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("kdhud.enabled", true);
        warnedMissing = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLinked(UUID playerId) {
        return getDiscordId(playerId) != null;
    }

    public String getDiscordId(UUID playerId) {
        if (!enabled || playerId == null) return null;
        Plugin kdhud = findPluginIgnoreCase("KDHUD");
        if (kdhud == null || !kdhud.isEnabled()) {
            warnMissingOnce("KDHUD plugin not found or disabled.");
            return null;
        }

        Method getter = resolveGetter();
        if (getter == null) {
            warnMissingOnce("KDHUD DB API not found (DBManager#getDiscordId).");
            return null;
        }
        try {
            Object result = getter.invoke(null, playerId.toString());
            if (result == null) return null;
            String id = String.valueOf(result).trim();
            return id.isBlank() ? null : id;
        } catch (Throwable ignored) {
            warnMissingOnce("KDHUD DB API invocation failed.");
            return null;
        }
    }

    private Method resolveGetter() {
        if (getDiscordIdMethod != null) return getDiscordIdMethod;
        try {
            Class<?> dbManager = Class.forName("com.everstar.hud.DBManager");
            getDiscordIdMethod = dbManager.getMethod("getDiscordId", String.class);
            return getDiscordIdMethod;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Plugin findPluginIgnoreCase(String name) {
        Plugin exact = Bukkit.getPluginManager().getPlugin(name);
        if (exact != null) return exact;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return p;
            }
        }
        return null;
    }

    private void warnMissingOnce(String reason) {
        if (warnedMissing) return;
        warnedMissing = true;
        plugin.getLogger().warning("[KDHUD Bridge] " + reason);
    }
}
