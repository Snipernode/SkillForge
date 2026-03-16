package com.skilltree.plugin.data;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final SkillForgePlugin plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private final Map<UUID, String> uuidToNameIndex;
    private final Map<UUID, File> loadedDataFiles;
    private final File dataFolder;

    public PlayerDataManager(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        this.uuidToNameIndex = new HashMap<>();
        this.loadedDataFiles = new HashMap<>();
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        rebuildNameIndex();
    }

    public PlayerData getPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        String currentName = player.getName();

        if (!playerDataMap.containsKey(playerId)) {
            playerDataMap.put(playerId, loadPlayerData(playerId, currentName));
        }

        PlayerData data = playerDataMap.get(playerId);
        if (data != null) {
            data.setLastKnownName(currentName);
            uuidToNameIndex.put(playerId, currentName);

            // Migrate/rename file to current player name when possible.
            File currentFile = loadedDataFiles.get(playerId);
            File targetFile = getNameDataFile(currentName);
            if (targetFile != null) {
                File moved = moveDataFile(playerId, currentFile, targetFile);
                loadedDataFiles.put(playerId, moved);
            }
        }
        return data;
    }

    public PlayerData getPlayerData(UUID playerId) {
        if (!playerDataMap.containsKey(playerId)) {
            playerDataMap.put(playerId, loadPlayerData(playerId, uuidToNameIndex.get(playerId)));
        }
        return playerDataMap.get(playerId);
    }

    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }

    private PlayerData loadPlayerData(UUID playerId, String preferredName) {
        File playerFile = resolveExistingDataFile(playerId, preferredName);
        loadedDataFiles.put(playerId, playerFile);

        PlayerData data = new PlayerData(playerId);

        double maxStamina = plugin.getConfig().getDouble("stamina.max-stamina", 100.0);
        double maxThirst = plugin.getConfig().getDouble("thirst.max-thirst", 100.0);
        data.setMaxStamina(maxStamina);
        data.setMaxThirst(maxThirst);

        String loadedName = preferredName;

        if (playerFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

            // Basic Stats
            data.setEvershards(config.getInt("evershards", 0));
            data.setSkillPoints(config.getInt("skillpoints", 0));
            data.setXpRemainder(config.getInt("xpRemainder", 0));
            data.setTotalSkillsPurchased(config.getInt("totalSkillsPurchased", 0));
            data.setDebateRating(config.getInt("debate.rating", 0));
            data.setDebateWins(config.getInt("debate.wins", 0));
            data.setDebateLosses(config.getInt("debate.losses", 0));
            data.setDebateCurrentStreak(config.getInt("debate.currentStreak", 0));
            data.setDebateBestStreak(config.getInt("debate.bestStreak", data.getDebateCurrentStreak()));
            data.setDebateLastAt(config.getLong("debate.lastAt", 0L));

            // Gamemode Logic
            String gamemodeStr = config.getString("gamemode", "NONE");
            try {
                data.setGamemode(PlayerData.Gamemode.valueOf(gamemodeStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid gamemode found for " + playerId + ": " + gamemodeStr);
                data.setGamemode(PlayerData.Gamemode.NONE);
            }

            // Load Kingdom
            data.setKingdom(config.getString("kingdom", null));

            // Permanent-death modes: check if player is currently dead/locked
            if (data.getGamemode() == PlayerData.Gamemode.HARDCORE
                    || data.getGamemode() == PlayerData.Gamemode.THE_END) {
                data.setHardcoreDead(config.getBoolean("hardcore_dead", false));
            }

            // Vitals
            data.setStamina(config.getDouble("stamina", maxStamina));
            data.setThirst(config.getDouble("thirst", maxThirst));

            // Skills
            if (config.contains("skills") && config.getConfigurationSection("skills") != null) {
                for (String skillName : config.getConfigurationSection("skills").getKeys(false)) {
                    data.setSkillLevel(skillName, config.getInt("skills." + skillName, 0));
                }
            }

            // Ability Bindings
            if (config.contains("ability_slots") && config.getConfigurationSection("ability_slots") != null) {
                for (String slotKey : config.getConfigurationSection("ability_slots").getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        String skillId = config.getString("ability_slots." + slotKey);
                        if (skillId != null) {
                            data.bindAbility(slot, skillId);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid ability slot key found: " + slotKey);
                    }
                }
            }

            // Cosmetics
            if (config.contains("cosmetics")) {
                for (String cosmetic : config.getStringList("cosmetics")) {
                    data.unlockCosmetic(cosmetic);
                }
            }

            String activeCosmetic = config.getString("activeCosmetic", null);
            data.setActiveCosmetic(activeCosmetic);

            // Innate Skill
            String innateSkillId = config.getString("innateSkill.id", null);
            int innateSkillLevel = config.getInt("innateSkill.level", 0);
            if (innateSkillId != null) {
                data.setInnateSkillId(innateSkillId);
                data.setInnateSkillLevel(innateSkillLevel);
            }

            // Mastery Points
            if (config.contains("mastery_points") && config.getConfigurationSection("mastery_points") != null) {
                for (String key : config.getConfigurationSection("mastery_points").getKeys(false)) {
                    data.setMasteryPoints(key, config.getInt("mastery_points." + key, 0));
                }
            }

            // Isekai item data
            data.setIsekaiItemId(config.getString("isekai.item.id", null));
            data.setIsekaiItemType(config.getString("isekai.item.type", null));
            data.setIsekaiItemLevel(config.getInt("isekai.item.level", 1));
            if (config.contains("isekai.item.abilities")) {
                data.setIsekaiAbilities(config.getStringList("isekai.item.abilities"));
            }
            data.setIsekaiLastSeenAt(config.getLong("isekai.lastSeenAt", 0L));

            // Playtime
            data.setPlaytimeMillis(config.getLong("playtime", 0));

            // Name metadata
            String fromConfigName = firstNonBlank(config.getString("lastKnownName"), config.getString("playerName"));
            if (fromConfigName != null) {
                loadedName = fromConfigName;
            } else if (!isUuidFilename(playerFile)) {
                loadedName = stripExtension(playerFile.getName());
            }
        } else {
            // Defaults for new files
            data.setStamina(maxStamina);
            data.setThirst(maxThirst);
        }

        data.setLastKnownName(loadedName);
        if (data.getLastKnownName() != null) {
            uuidToNameIndex.put(playerId, data.getLastKnownName());
            File target = getNameDataFile(data.getLastKnownName());
            if (target != null) {
                File moved = moveDataFile(playerId, playerFile, target);
                loadedDataFiles.put(playerId, moved);
            }
        }

        return data;
    }

    public void savePlayerData(UUID playerId) {
        PlayerData data = playerDataMap.get(playerId);
        if (data == null) return;

        File saveFile = resolveSaveFile(playerId, data);
        FileConfiguration config = new YamlConfiguration();

        config.set("uuid", playerId.toString());
        config.set("lastKnownName", data.getLastKnownName());
        config.set("playerName", data.getLastKnownName());

        config.set("evershards", data.getEvershards());
        config.set("skillpoints", data.getSkillPoints());
        config.set("xpRemainder", data.getXpRemainder());
        config.set("totalSkillsPurchased", data.getTotalSkillsPurchased());
        config.set("debate.rating", data.getDebateRating());
        config.set("debate.wins", data.getDebateWins());
        config.set("debate.losses", data.getDebateLosses());
        config.set("debate.currentStreak", data.getDebateCurrentStreak());
        config.set("debate.bestStreak", data.getDebateBestStreak());
        config.set("debate.lastAt", data.getDebateLastAt());

        // Save Gamemode
        config.set("gamemode", data.getGamemode().name());
        config.set("kingdom", data.getKingdom());

        // Save permanent-death state
        if (data.getGamemode() == PlayerData.Gamemode.HARDCORE
                || data.getGamemode() == PlayerData.Gamemode.THE_END) {
            config.set("hardcore_dead", data.isHardcoreDead());
        }

        config.set("stamina", data.getStamina());
        config.set("thirst", data.getThirst());

        // Save Skills
        for (Map.Entry<String, Integer> entry : data.getAllSkillLevels().entrySet()) {
            config.set("skills." + entry.getKey(), entry.getValue());
        }

        // Save Ability Bindings
        for (Map.Entry<Integer, String> entry : data.getAllAbilityBindings().entrySet()) {
            config.set("ability_slots." + entry.getKey(), entry.getValue());
        }

        config.set("cosmetics", data.getUnlockedCosmetics().stream().toList());
        config.set("activeCosmetic", data.getActiveCosmetic());

        if (data.getInnateSkillId() != null) {
            config.set("innateSkill.id", data.getInnateSkillId());
            config.set("innateSkill.level", data.getInnateSkillLevel());
        }

        // Mastery Points
        for (Map.Entry<String, Integer> entry : data.getAllMasteryPoints().entrySet()) {
            config.set("mastery_points." + entry.getKey(), entry.getValue());
        }

        // Isekai item data
        if (data.hasIsekaiItem()) {
            config.set("isekai.item.id", data.getIsekaiItemId());
            config.set("isekai.item.type", data.getIsekaiItemType());
            config.set("isekai.item.level", data.getIsekaiItemLevel());
            config.set("isekai.item.abilities", data.getIsekaiAbilities());
        }
        config.set("isekai.lastSeenAt", data.getIsekaiLastSeenAt());

        config.set("playtime", data.getPlaytimeMillis());

        try {
            config.save(saveFile);
            loadedDataFiles.put(playerId, saveFile);
            if (data.getLastKnownName() != null) {
                uuidToNameIndex.put(playerId, data.getLastKnownName());
            }
            cleanupLegacyUuidFile(playerId, saveFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player data for " + playerId);
            e.printStackTrace();
        }
    }

    public void saveAllPlayers() {
        for (UUID playerId : new ArrayList<>(playerDataMap.keySet())) {
            savePlayerData(playerId);
        }
    }

    public void unloadPlayer(UUID playerId) {
        savePlayerData(playerId);
        playerDataMap.remove(playerId);
        loadedDataFiles.remove(playerId);
    }

    private void rebuildNameIndex() {
        uuidToNameIndex.clear();
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            UUID uuid = parseUuid(cfg.getString("uuid"));
            if (uuid == null) {
                uuid = parseUuid(stripExtension(f.getName()));
            }
            if (uuid == null) continue;

            String name = firstNonBlank(cfg.getString("lastKnownName"), cfg.getString("playerName"));
            if (name == null && !isUuidFilename(f)) {
                name = stripExtension(f.getName());
            }

            if (name != null) {
                uuidToNameIndex.put(uuid, name);
                File target = getNameDataFile(name);
                if (target != null) {
                    moveDataFile(uuid, f, target);
                }
            }
        }
    }

    private File resolveExistingDataFile(UUID playerId, String preferredName) {
        File byName = getNameDataFile(preferredName);
        if (byName != null && byName.exists()) return byName;

        String indexedName = uuidToNameIndex.get(playerId);
        File byIndexedName = getNameDataFile(indexedName);
        if (byIndexedName != null && byIndexedName.exists()) return byIndexedName;

        File byUuid = getUuidDataFile(playerId);
        if (byUuid.exists()) return byUuid;

        if (byName != null) return byName;
        if (byIndexedName != null) return byIndexedName;
        return byUuid;
    }

    private File resolveSaveFile(UUID playerId, PlayerData data) {
        String name = data.getLastKnownName();
        File target = getNameDataFile(name);
        File current = loadedDataFiles.get(playerId);

        if (target == null) {
            if (current != null) return current;
            return getUuidDataFile(playerId);
        }
        return moveDataFile(playerId, current, target);
    }

    private File moveDataFile(UUID playerId, File source, File target) {
        if (target == null) return source != null ? source : getUuidDataFile(playerId);
        if (source == null) {
            File legacy = getUuidDataFile(playerId);
            if (legacy.exists()) source = legacy;
            else source = target;
        }

        if (!source.exists()) return target;
        if (source.equals(target)) return target;
        if (target.exists()) return target;

        if (!source.renameTo(target)) {
            plugin.getLogger().warning("Failed to migrate player data file: " + source.getName() + " -> " + target.getName());
            return source;
        }
        return target;
    }

    private void cleanupLegacyUuidFile(UUID playerId, File activeFile) {
        File legacy = getUuidDataFile(playerId);
        if (!legacy.exists()) return;
        if (activeFile != null && activeFile.equals(legacy)) return;
        if (!legacy.delete()) {
            plugin.getLogger().warning("Could not remove legacy UUID playerdata file: " + legacy.getName());
        }
    }

    private File getUuidDataFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }

    private File getNameDataFile(String name) {
        String safe = sanitizeName(name);
        if (safe == null) return null;
        return new File(dataFolder, safe + ".yml");
    }

    private String sanitizeName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isUuidFilename(File file) {
        if (file == null) return false;
        return parseUuid(stripExtension(file.getName())) != null;
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
