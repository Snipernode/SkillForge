package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.managers.CrateKeyLedger;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

public class CrateRewardManager implements Listener {
    private final SkillForgePlugin plugin;
    private final CrateKeyLedger keyLedger;
    private final ZoneId zoneId;
    private final File file;
    private final Object fileLock = new Object();
    private FileConfiguration config;

    public CrateRewardManager(SkillForgePlugin plugin, CrateKeyLedger keyLedger) {
        this.plugin = plugin;
        this.keyLedger = keyLedger;
        this.zoneId = ZoneId.systemDefault();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.file = new File(dataFolder, "crate-rewards.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create crate-rewards.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("crate_rewards.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> grantLoginRewards(player), 20L);
    }

    public void grantLoginRewards(Player player) {
        if (player == null || !player.isOnline()) return;

        String uuid = player.getUniqueId().toString();
        RewardState state = getState(uuid);

        LocalDate today = LocalDate.now(zoneId);
        LocalDate lastLogin = parseDate(state.lastLoginDate);
        LocalDate lastWeekly = parseDate(state.lastWeeklyDate);
        LocalDate lastMonthly = parseDate(state.lastMonthlyDate);

        boolean newDay = lastLogin == null || !lastLogin.equals(today);
        int dailyStreak = state.dailyStreak;

        if (newDay) {
            if (lastLogin != null && lastLogin.equals(today.minusDays(1))) {
                dailyStreak = dailyStreak + 1;
            } else {
                dailyStreak = 1;
            }
            awardKey(player, "crate_rewards.daily", "daily", dailyStreak);
            lastLogin = today;
        }

        if (isNewWeek(lastWeekly, today)) {
            awardKey(player, "crate_rewards.weekly", "lucky", dailyStreak);
            lastWeekly = today;
        }

        if (isNewMonth(lastMonthly, today)) {
            awardKey(player, "crate_rewards.monthly", "gemstone", dailyStreak);
            lastMonthly = today;
        }

        state.lastLoginDate = dateToString(lastLogin);
        state.dailyStreak = dailyStreak;
        state.lastWeeklyDate = dateToString(lastWeekly);
        state.lastMonthlyDate = dateToString(lastMonthly);
        saveState(uuid, state);
    }

    public long grantBuddyKeys(Player player) {
        if (player == null) return 0;
        String uuid = player.getUniqueId().toString();
        RewardState state = getState(uuid);
        long base = plugin.getConfig().getLong("crate_rewards.buddy.base_keys", 1L);
        long multiplier = plugin.getConfig().getLong("crate_rewards.buddy.multiplier", 2L);
        if (base <= 0) base = 1;
        if (multiplier < 1) multiplier = 1;
        long amount = base;
        if (state.buddyGrants > 0) {
            amount = (long) Math.round(base * Math.pow(multiplier, state.buddyGrants));
        }
        String keyType = plugin.getConfig().getString("crate_rewards.buddy.key_type", "buddy");
        if (keyType == null || keyType.isBlank()) keyType = "buddy";
        boolean useKeyItems = plugin.getConfig().getBoolean("crate_rewards.give_key_items", true);
        CrateBlockSystem crateBlockSystem = plugin.getCrateBlockSystem();
        if (useKeyItems && crateBlockSystem != null) {
            crateBlockSystem.giveKeyItems(player, keyType, amount);
        } else {
            keyLedger.addKeys(player.getUniqueId(), keyType, amount);
        }
        state.buddyGrants = state.buddyGrants + 1;
        saveState(uuid, state);
        return amount;
    }

    private void awardKey(Player player, String basePath, String fallbackKeyType, int streak) {
        int amount = plugin.getConfig().getInt(basePath + ".keys", 1);
        if (amount <= 0) return;
        String keyType = plugin.getConfig().getString(basePath + ".key_type", fallbackKeyType);
        if (keyType == null || keyType.isBlank()) keyType = fallbackKeyType;
        boolean useKeyItems = plugin.getConfig().getBoolean("crate_rewards.give_key_items", true);
        CrateBlockSystem crateBlockSystem = plugin.getCrateBlockSystem();
        if (useKeyItems && crateBlockSystem != null) {
            crateBlockSystem.giveKeyItems(player, keyType, amount);
        } else {
            keyLedger.addKeys(player.getUniqueId(), keyType, amount);
        }
        runCommands(
                plugin.getConfig().getStringList(basePath + ".commands"),
                player,
                amount,
                keyType,
                streak
        );
    }

    private void runCommands(List<String> commands, Player player, int amount, String keyType, int streak) {
        if (commands == null || commands.isEmpty()) return;
        CommandSender console = plugin.getServer().getConsoleSender();
        for (String rawCommand : commands) {
            String command = rawCommand
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%key_type%", keyType)
                    .replace("%streak%", String.valueOf(streak));
            plugin.getServer().dispatchCommand(console, command);
        }
    }

    private boolean isNewWeek(LocalDate lastWeekly, LocalDate today) {
        if (lastWeekly == null) return true;
        WeekFields fields = WeekFields.of(Locale.getDefault());
        int lastWeek = lastWeekly.get(fields.weekOfWeekBasedYear());
        int lastYear = lastWeekly.get(fields.weekBasedYear());
        int currentWeek = today.get(fields.weekOfWeekBasedYear());
        int currentYear = today.get(fields.weekBasedYear());
        return lastWeek != currentWeek || lastYear != currentYear;
    }

    private boolean isNewMonth(LocalDate lastMonthly, LocalDate today) {
        if (lastMonthly == null) return true;
        YearMonth last = YearMonth.from(lastMonthly);
        YearMonth current = YearMonth.from(today);
        return !last.equals(current);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String dateToString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private RewardState getState(String uuid) {
        synchronized (fileLock) {
            String base = "players." + uuid;
            RewardState state = new RewardState();
            state.lastLoginDate = config.getString(base + ".last_login", null);
            state.lastWeeklyDate = config.getString(base + ".last_weekly", null);
            state.lastMonthlyDate = config.getString(base + ".last_monthly", null);
            state.dailyStreak = config.getInt(base + ".daily_streak", 0);
            state.buddyGrants = config.getLong(base + ".buddy_grants", 0L);
            return state;
        }
    }

    private void saveState(String uuid, RewardState state) {
        synchronized (fileLock) {
            String base = "players." + uuid;
            config.set(base + ".last_login", state.lastLoginDate);
            config.set(base + ".last_weekly", state.lastWeeklyDate);
            config.set(base + ".last_monthly", state.lastMonthlyDate);
            config.set(base + ".daily_streak", state.dailyStreak);
            config.set(base + ".buddy_grants", state.buddyGrants);
            save();
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save crate-rewards.yml: " + e.getMessage());
        }
    }

    private static final class RewardState {
        private String lastLoginDate;
        private String lastWeeklyDate;
        private String lastMonthlyDate;
        private int dailyStreak;
        private long buddyGrants;
    }
}
