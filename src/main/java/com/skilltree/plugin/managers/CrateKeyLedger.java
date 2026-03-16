package com.skilltree.plugin.managers;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrateKeyLedger {
    private final SkillForgePlugin plugin;
    private final File file;
    private final Object fileLock = new Object();
    private FileConfiguration config;

    public CrateKeyLedger(SkillForgePlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "crate-keys.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create crate-keys.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public long addKeys(UUID uuid, String keyType, long amount) {
        if (uuid == null || keyType == null || amount <= 0) {
            return 0;
        }
        String normalized = keyType.toLowerCase();
        synchronized (fileLock) {
            String path = "players." + uuid + "." + normalized;
            long current = config.getLong(path, 0L);
            long updated = current + amount;
            config.set(path, updated);
            save();
            return updated;
        }
    }

    public long removeKeys(UUID uuid, String keyType, long amount) {
        if (uuid == null || keyType == null || amount <= 0) {
            return 0;
        }
        String normalized = keyType.toLowerCase();
        synchronized (fileLock) {
            String path = "players." + uuid + "." + normalized;
            long current = config.getLong(path, 0L);
            if (current < amount) {
                return -1L;
            }
            long updated = current - amount;
            config.set(path, updated);
            save();
            return updated;
        }
    }

    public long getKeys(UUID uuid, String keyType) {
        if (uuid == null || keyType == null) {
            return 0;
        }
        String normalized = keyType.toLowerCase();
        synchronized (fileLock) {
            return config.getLong("players." + uuid + "." + normalized, 0L);
        }
    }

    public Map<String, Long> getAllKeys(UUID uuid) {
        if (uuid == null) return Collections.emptyMap();
        synchronized (fileLock) {
            String base = "players." + uuid;
            if (!config.isConfigurationSection(base)) return Collections.emptyMap();
            Map<String, Long> out = new HashMap<>();
            for (String key : config.getConfigurationSection(base).getKeys(false)) {
                out.put(key, config.getLong(base + "." + key, 0L));
            }
            return out;
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save crate-keys.yml: " + e.getMessage());
        }
    }
}
