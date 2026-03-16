package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcJobSystem implements Listener {

    public enum CareerGuild {
        MERCHANT("Merchant Guild"),
        MERCENARY("Mercenary Guild");

        private final String display;

        CareerGuild(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }

        public static CareerGuild fromInput(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String norm = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
            return switch (norm) {
                case "merchant", "merchants", "merchantguild" -> MERCHANT;
                case "mercenary", "mercenaries", "mercenaryguild", "merc" -> MERCENARY;
                default -> null;
            };
        }
    }

    public enum JobType {
        FARMING("Farming Contract", CareerGuild.MERCHANT, 64, 80),
        BREEDING("Breeding Contract", CareerGuild.MERCHANT, 8, 100),
        FISHING("Fishing Contract", CareerGuild.MERCHANT, 10, 90),
        DELIVERY("Delivery Route", CareerGuild.MERCHANT, 450, 120),
        HUNTING("Hunting Contract", CareerGuild.MERCENARY, 20, 130),
        BOUNTY("Bounty Contract", CareerGuild.MERCENARY, 1, 300);

        private final String display;
        private final CareerGuild guild;
        private final int defaultDailyTarget;
        private final int basePay;

        JobType(String display, CareerGuild guild, int defaultDailyTarget, int basePay) {
            this.display = display;
            this.guild = guild;
            this.defaultDailyTarget = defaultDailyTarget;
            this.basePay = basePay;
        }

        public String display() {
            return display;
        }

        public CareerGuild guild() {
            return guild;
        }

        public int defaultDailyTarget() {
            return defaultDailyTarget;
        }

        public int basePay() {
            return basePay;
        }

        public static JobType fromInput(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String norm = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
            for (JobType value : values()) {
                String key = value.name().toLowerCase(Locale.ROOT).replace("_", "");
                if (key.equals(norm)) return value;
            }
            return null;
        }
    }

    private static final Set<Material> FARMABLE_BLOCKS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );

    private final SkillForgePlugin plugin;
    private final File dataFile;
    private final Map<UUID, CareerProgress> careers = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveJob> activeJobs = new ConcurrentHashMap<>();
    private final Map<UUID, JobOffer> pendingOffers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bountyPool = new ConcurrentHashMap<>();
    private final Map<UUID, Double> deliveryBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> voiceEnabled = new ConcurrentHashMap<>();

    private boolean enabled;
    private long dailyIntervalMs;
    private long payIntervalMs;
    private long offlineFireMs;
    private long rehireLockMs;
    private long offerTimeoutMs;
    private int maxMissedDays;
    private int maxRank;
    private int rankUpEveryCompletions;
    private boolean requireDiscordLinkForJobs;
    private String linkRequiredMessage;

    public NpcJobSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npc-jobs.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create npc-jobs.yml: " + e.getMessage());
            }
        }
        loadConfig();
        loadData();
        startTick();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("jobs.enabled", true);
        long dailyMinutes = Math.max(1L, plugin.getConfig().getLong("jobs.daily_interval_minutes", 20L));
        long payDays = Math.max(1L, plugin.getConfig().getLong("jobs.pay_interval_ingame_days", 14L));
        long offlineHours = Math.max(1L, plugin.getConfig().getLong("jobs.offline_fire_irl_hours", 24L));
        long offlineIngameDays = Math.max(1L, plugin.getConfig().getLong("jobs.offline_fire_ingame_days", 5L));
        long lockHours = Math.max(1L, plugin.getConfig().getLong("jobs.rehire_lock_irl_hours", 24L));
        long lockIngameDays = Math.max(1L, plugin.getConfig().getLong("jobs.rehire_lock_ingame_days", 5L));
        long offerTimeoutSec = Math.max(15L, plugin.getConfig().getLong("jobs.offer_timeout_seconds", 120L));

        dailyIntervalMs = dailyMinutes * 60_000L;
        payIntervalMs = payDays * 20L * 60L * 1000L; // 20 minutes per in-game day.
        long offlineByIrl = offlineHours * 60L * 60L * 1000L;
        long offlineByIngame = offlineIngameDays * 20L * 60L * 1000L;
        offlineFireMs = Math.min(offlineByIrl, offlineByIngame); // "5 in-game days OR 1 irl day" => first threshold wins.

        long lockByIrl = lockHours * 60L * 60L * 1000L;
        long lockByIngame = lockIngameDays * 20L * 60L * 1000L;
        rehireLockMs = Math.max(lockByIrl, lockByIngame); // lock stays meaningful even on fast day cycles.

        offerTimeoutMs = offerTimeoutSec * 1000L;
        maxMissedDays = Math.max(0, plugin.getConfig().getInt("jobs.max_missed_days", 1));
        maxRank = Math.max(1, plugin.getConfig().getInt("jobs.max_rank", 10));
        rankUpEveryCompletions = Math.max(1, plugin.getConfig().getInt("jobs.rank_up_every_completions", 20));
        requireDiscordLinkForJobs = plugin.getConfig().getBoolean("kdhud.require_discord_link_for.jobs", false);
        linkRequiredMessage = plugin.getConfig().getString(
                "kdhud.messages.link_required",
                "&cLink your Discord first with &e/link <code>&c."
        );
    }

    public void saveData() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, CareerProgress> entry : careers.entrySet()) {
            UUID id = entry.getKey();
            CareerProgress career = entry.getValue();
            String base = "careers." + id;
            cfg.set(base + ".guild", career.guild.name());
            cfg.set(base + ".rank", career.rank);
            cfg.set(base + ".completed_days", career.completedDays);
            cfg.set(base + ".total_paid", career.totalPaid);
        }
        for (Map.Entry<UUID, ActiveJob> entry : activeJobs.entrySet()) {
            UUID id = entry.getKey();
            ActiveJob job = entry.getValue();
            String base = "active." + id;
            cfg.set(base + ".guild", job.guild.name());
            cfg.set(base + ".type", job.type.name());
            cfg.set(base + ".rank_required", job.rankRequired);
            cfg.set(base + ".daily_target", job.dailyTarget);
            cfg.set(base + ".daily_progress", job.dailyProgress);
            cfg.set(base + ".missed_days", job.missedDays);
            cfg.set(base + ".completed_since_pay", job.completedSincePay);
            cfg.set(base + ".started_at", job.startedAt);
            cfg.set(base + ".last_daily_at", job.lastDailyAt);
            cfg.set(base + ".last_pay_at", job.lastPayAt);
            cfg.set(base + ".last_seen_at", job.lastSeenAt);
            cfg.set(base + ".offered_by", job.offeredBy);
        }
        for (Map.Entry<UUID, Long> entry : lockedUntil.entrySet()) {
            cfg.set("cooldowns." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Long> entry : bountyPool.entrySet()) {
            cfg.set("bounties." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Boolean> entry : voiceEnabled.entrySet()) {
            cfg.set("voice." + entry.getKey(), entry.getValue());
        }

        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save npc-jobs.yml: " + e.getMessage());
        }
    }

    private void loadData() {
        careers.clear();
        activeJobs.clear();
        lockedUntil.clear();
        bountyPool.clear();
        voiceEnabled.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection careersSection = cfg.getConfigurationSection("careers");
        if (careersSection != null) {
            for (String idRaw : careersSection.getKeys(false)) {
                UUID id = parseUuid(idRaw);
                if (id == null) continue;
                CareerGuild guild = CareerGuild.fromInput(cfg.getString("careers." + idRaw + ".guild"));
                if (guild == null) continue;
                CareerProgress progress = new CareerProgress(guild);
                progress.rank = Math.max(1, cfg.getInt("careers." + idRaw + ".rank", 1));
                progress.completedDays = Math.max(0, cfg.getInt("careers." + idRaw + ".completed_days", 0));
                progress.totalPaid = Math.max(0, cfg.getLong("careers." + idRaw + ".total_paid", 0L));
                careers.put(id, progress);
            }
        }

        ConfigurationSection activeSection = cfg.getConfigurationSection("active");
        if (activeSection != null) {
            for (String idRaw : activeSection.getKeys(false)) {
                UUID id = parseUuid(idRaw);
                if (id == null) continue;
                CareerGuild guild = CareerGuild.fromInput(cfg.getString("active." + idRaw + ".guild"));
                JobType type = JobType.fromInput(cfg.getString("active." + idRaw + ".type"));
                if (guild == null || type == null) continue;

                ActiveJob job = new ActiveJob();
                job.playerId = id;
                job.guild = guild;
                job.type = type;
                job.rankRequired = Math.max(1, cfg.getInt("active." + idRaw + ".rank_required", 1));
                job.dailyTarget = Math.max(1, cfg.getInt("active." + idRaw + ".daily_target", type.defaultDailyTarget()));
                job.dailyProgress = Math.max(0, cfg.getInt("active." + idRaw + ".daily_progress", 0));
                job.missedDays = Math.max(0, cfg.getInt("active." + idRaw + ".missed_days", 0));
                job.completedSincePay = Math.max(0, cfg.getInt("active." + idRaw + ".completed_since_pay", 0));
                long now = System.currentTimeMillis();
                job.startedAt = cfg.getLong("active." + idRaw + ".started_at", now);
                job.lastDailyAt = cfg.getLong("active." + idRaw + ".last_daily_at", now);
                job.lastPayAt = cfg.getLong("active." + idRaw + ".last_pay_at", now);
                job.lastSeenAt = cfg.getLong("active." + idRaw + ".last_seen_at", now);
                job.offeredBy = cfg.getString("active." + idRaw + ".offered_by", "npc");
                activeJobs.put(id, job);
            }
        }

        ConfigurationSection cooldownSection = cfg.getConfigurationSection("cooldowns");
        if (cooldownSection != null) {
            for (String idRaw : cooldownSection.getKeys(false)) {
                UUID id = parseUuid(idRaw);
                if (id == null) continue;
                lockedUntil.put(id, cfg.getLong("cooldowns." + idRaw, 0L));
            }
        }

        ConfigurationSection bountySection = cfg.getConfigurationSection("bounties");
        if (bountySection != null) {
            for (String idRaw : bountySection.getKeys(false)) {
                UUID id = parseUuid(idRaw);
                if (id == null) continue;
                long value = Math.max(0L, cfg.getLong("bounties." + idRaw, 0L));
                if (value > 0L) {
                    bountyPool.put(id, value);
                }
            }
        }

        ConfigurationSection voiceSection = cfg.getConfigurationSection("voice");
        if (voiceSection != null) {
            for (String idRaw : voiceSection.getKeys(false)) {
                UUID id = parseUuid(idRaw);
                if (id == null) continue;
                voiceEnabled.put(id, cfg.getBoolean("voice." + idRaw, true));
            }
        }
    }

    public void shutdown() {
        saveData();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CareerProgress getCareer(UUID playerId) {
        if (playerId == null) return null;
        return careers.get(playerId);
    }

    public ActiveJob getActiveJob(UUID playerId) {
        if (playerId == null) return null;
        return activeJobs.get(playerId);
    }

    public JobOffer getPendingOffer(UUID playerId) {
        if (playerId == null) return null;
        JobOffer offer = pendingOffers.get(playerId);
        if (offer == null) return null;
        if (offer.expiresAt < System.currentTimeMillis()) {
            pendingOffers.remove(playerId);
            return null;
        }
        return offer;
    }

    public long getLockedUntil(UUID playerId) {
        return lockedUntil.getOrDefault(playerId, 0L);
    }

    public boolean joinCareer(Player player, CareerGuild guild) {
        if (!enabled || player == null || guild == null) return false;
        if (requireDiscordLinkForJobs) {
            KDHudBridge bridge = plugin.getKDHudBridge();
            if (bridge == null || !bridge.isLinked(player.getUniqueId())) {
                player.sendMessage(color(linkRequiredMessage));
                return false;
            }
        }
        CareerProgress current = careers.get(player.getUniqueId());
        if (current != null) {
            if (current.guild == guild) {
                player.sendMessage(prefix() + ChatColor.YELLOW + "You are already in " + guild.display() + ".");
                return false;
            }
            player.sendMessage(prefix() + ChatColor.RED + "You are already aligned with " + current.guild.display() + ".");
            player.sendMessage(prefix() + ChatColor.GRAY + "Career switching is disabled right now.");
            return false;
        }
        careers.put(player.getUniqueId(), new CareerProgress(guild));
        say(player, "join", mapOf(
                "%guild%", guild.display(),
                "%rank%", "1"
        ));
        saveData();
        return true;
    }

    public boolean setCareerRank(UUID targetId, CareerGuild guild, int rank) {
        if (targetId == null || guild == null) return false;
        rank = Math.max(1, Math.min(maxRank, rank));
        CareerProgress career = careers.computeIfAbsent(targetId, id -> new CareerProgress(guild));
        career.guild = guild;
        career.rank = rank;
        saveData();
        return true;
    }

    public boolean offerJob(Player target, CareerGuild guild, JobType type, int rankRequired, String offeredBy) {
        if (!enabled || target == null || guild == null || type == null) return false;
        if (type.guild() != guild) {
            target.sendMessage(prefix() + ChatColor.RED + "That job type belongs to " + type.guild().display() + ".");
            return false;
        }
        JobOffer offer = new JobOffer();
        offer.playerId = target.getUniqueId();
        offer.guild = guild;
        offer.type = type;
        offer.rankRequired = Math.max(1, rankRequired);
        offer.offeredBy = (offeredBy == null || offeredBy.isBlank()) ? "npc" : offeredBy;
        offer.expiresAt = System.currentTimeMillis() + offerTimeoutMs;
        pendingOffers.put(target.getUniqueId(), offer);

        String expiry = String.valueOf(Math.max(1, offerTimeoutMs / 1000L));
        say(target, "offer", mapOf(
                "%guild%", guild.display(),
                "%job%", type.display(),
                "%rank_required%", String.valueOf(offer.rankRequired),
                "%offered_by%", offer.offeredBy,
                "%offer_seconds%", expiry
        ));
        target.sendMessage(prefix() + ChatColor.GRAY + "Use /job accept to take it.");
        return true;
    }

    public boolean acceptOffer(Player player) {
        if (!enabled || player == null) return false;
        JobOffer offer = getPendingOffer(player.getUniqueId());
        if (offer == null) {
            player.sendMessage(prefix() + ChatColor.RED + "You do not have a pending job offer.");
            return false;
        }
        pendingOffers.remove(player.getUniqueId());
        return startJob(player, offer.guild, offer.type, offer.rankRequired, offer.offeredBy);
    }

    public boolean startJob(Player player, CareerGuild guild, JobType type, int rankRequired, String offeredBy) {
        if (!enabled || player == null || guild == null || type == null) return false;
        if (requireDiscordLinkForJobs) {
            KDHudBridge bridge = plugin.getKDHudBridge();
            if (bridge == null || !bridge.isLinked(player.getUniqueId())) {
                player.sendMessage(color(linkRequiredMessage));
                return false;
            }
        }
        if (activeJobs.containsKey(player.getUniqueId())) {
            player.sendMessage(prefix() + ChatColor.RED + "You already have an active job.");
            return false;
        }
        long lockUntil = lockedUntil.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        if (lockUntil > now) {
            long sec = (lockUntil - now) / 1000L;
            player.sendMessage(prefix() + ChatColor.RED + "You are blacklisted from jobs for " + sec + "s.");
            return false;
        }

        CareerProgress career = careers.get(player.getUniqueId());
        if (career == null || career.guild != guild) {
            player.sendMessage(prefix() + ChatColor.RED + "Join " + guild.display() + " first: /job join " + guild.name().toLowerCase(Locale.ROOT));
            return false;
        }
        if (career.rank < rankRequired) {
            player.sendMessage(prefix() + ChatColor.RED + "This contract requires rank " + rankRequired + ".");
            return false;
        }
        if (type.guild() != guild) {
            player.sendMessage(prefix() + ChatColor.RED + "That contract is not offered by your guild.");
            return false;
        }

        ActiveJob job = new ActiveJob();
        job.playerId = player.getUniqueId();
        job.guild = guild;
        job.type = type;
        job.rankRequired = Math.max(1, rankRequired);
        job.dailyTarget = Math.max(1, jobTargetFromConfig(type, type.defaultDailyTarget()));
        job.dailyProgress = 0;
        job.missedDays = 0;
        job.completedSincePay = 0;
        job.startedAt = now;
        job.lastDailyAt = now;
        job.lastPayAt = now;
        job.lastSeenAt = now;
        job.offeredBy = offeredBy == null ? "npc" : offeredBy;
        activeJobs.put(player.getUniqueId(), job);
        deliveryBuffer.remove(player.getUniqueId());

        say(player, "accepted", mapOf(
                "%guild%", guild.display(),
                "%job%", type.display(),
                "%target%", String.valueOf(job.dailyTarget),
                "%pay_interval%", String.valueOf(Math.max(1, payIntervalMs / 1000L))
        ));
        saveData();
        return true;
    }

    public boolean quitJob(Player player) {
        if (!enabled || player == null) return false;
        ActiveJob removed = activeJobs.remove(player.getUniqueId());
        pendingOffers.remove(player.getUniqueId());
        deliveryBuffer.remove(player.getUniqueId());
        if (removed == null) {
            player.sendMessage(prefix() + ChatColor.YELLOW + "You do not have an active job.");
            return false;
        }
        player.sendMessage(prefix() + ChatColor.YELLOW + "You left your contract: " + removed.type.display() + ".");
        saveData();
        return true;
    }

    public boolean setVoiceEnabled(UUID playerId, boolean enabledValue) {
        if (playerId == null) return false;
        voiceEnabled.put(playerId, enabledValue);
        saveData();
        return true;
    }

    public long getBounty(UUID playerId) {
        if (playerId == null) return 0L;
        return bountyPool.getOrDefault(playerId, 0L);
    }

    public boolean placeBounty(Player placer, UUID target, long amount) {
        if (!enabled || placer == null || target == null || amount <= 0L) return false;
        if (placer.getUniqueId().equals(target)) {
            placer.sendMessage(prefix() + ChatColor.RED + "You cannot place a bounty on yourself.");
            return false;
        }
        CareerProgress career = careers.get(placer.getUniqueId());
        if (career == null || career.guild != CareerGuild.MERCENARY) {
            placer.sendMessage(prefix() + ChatColor.RED + "Only Mercenary Guild members can place bounties.");
            return false;
        }

        PlayerData placerData = plugin.getPlayerDataManager().getPlayerData(placer);
        if (placerData == null) return false;
        if (placerData.getEvershards() < amount) {
            placer.sendMessage(prefix() + ChatColor.RED + "Not enough Evershards.");
            return false;
        }
        placerData.removeEvershards((int) Math.min(Integer.MAX_VALUE, amount));
        plugin.getPlayerDataManager().savePlayerData(placer.getUniqueId());

        bountyPool.merge(target, amount, Long::sum);
        saveData();
        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null || name.isBlank()) name = target.toString();
        placer.sendMessage(prefix() + ChatColor.GREEN + "Bounty posted: " + amount + " on " + name + ".");
        return true;
    }

    private void startTick() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                tickOffers();
                tickJobs();
            }
        }.runTaskTimer(plugin, 20L, 20L * 30L); // 30s cadence.
    }

    private void tickOffers() {
        long now = System.currentTimeMillis();
        pendingOffers.entrySet().removeIf(e -> e.getValue() == null || e.getValue().expiresAt < now);
    }

    private void tickJobs() {
        long now = System.currentTimeMillis();
        for (ActiveJob job : new ArrayList<>(activeJobs.values())) {
            Player online = Bukkit.getPlayer(job.playerId);
            if (online != null && online.isOnline()) {
                job.lastSeenAt = now;
            }

            if (now - job.lastSeenAt >= offlineFireMs) {
                fireJob(job.playerId, "Inactive too long.");
                continue;
            }

            while (now - job.lastDailyAt >= dailyIntervalMs) {
                if (job.dailyProgress >= job.dailyTarget) {
                    job.completedSincePay++;
                    CareerProgress career = careers.get(job.playerId);
                    if (career != null) {
                        career.completedDays++;
                        maybeRankUp(job.playerId, career);
                    }
                } else {
                    job.missedDays++;
                    if (online != null) {
                        say(online, "missed_day", mapOf(
                                "%job%", job.type.display(),
                                "%target%", String.valueOf(job.dailyTarget),
                                "%progress%", String.valueOf(job.dailyProgress),
                                "%missed%", String.valueOf(job.missedDays)
                        ));
                    }
                }
                job.dailyProgress = 0;
                deliveryBuffer.remove(job.playerId);
                job.lastDailyAt += dailyIntervalMs;
            }

            if (job.missedDays > maxMissedDays) {
                fireJob(job.playerId, "Too many missed daily tasks.");
                continue;
            }

            if (now - job.lastPayAt >= payIntervalMs) {
                int cycles = (int) ((now - job.lastPayAt) / payIntervalMs);
                for (int i = 0; i < cycles; i++) {
                    payout(job.playerId);
                }
                job.lastPayAt += (long) cycles * payIntervalMs;
            }
        }
    }

    private void payout(UUID playerId) {
        ActiveJob job = activeJobs.get(playerId);
        if (job == null) return;
        CareerProgress career = careers.get(playerId);
        if (career == null) return;

        int completed = job.completedSincePay;
        if (completed <= 0) return;

        long amount = (long) completed * job.type.basePay() * Math.max(1, career.rank);
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        if (data != null) {
            data.addEvershards((int) Math.min(Integer.MAX_VALUE, amount));
            plugin.getPlayerDataManager().savePlayerData(playerId);
        }
        career.totalPaid += amount;
        job.completedSincePay = 0;
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            say(online, "paid", mapOf(
                    "%amount%", String.valueOf(amount),
                    "%job%", job.type.display(),
                    "%rank%", String.valueOf(career.rank)
            ));
        }
    }

    private void maybeRankUp(UUID playerId, CareerProgress career) {
        if (career == null) return;
        if (career.rank >= maxRank) return;
        int required = career.rank * rankUpEveryCompletions;
        if (career.completedDays < required) return;
        career.rank++;
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            say(online, "rank_up", mapOf(
                    "%rank%", String.valueOf(career.rank),
                    "%guild%", career.guild.display()
            ));
        }
    }

    private void fireJob(UUID playerId, String reason) {
        ActiveJob removed = activeJobs.remove(playerId);
        deliveryBuffer.remove(playerId);
        pendingOffers.remove(playerId);
        if (removed == null) return;
        long until = System.currentTimeMillis() + rehireLockMs;
        lockedUntil.put(playerId, until);
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            say(online, "fired", mapOf(
                    "%job%", removed.type.display(),
                    "%reason%", reason,
                    "%lock_seconds%", String.valueOf(Math.max(1L, rehireLockMs / 1000L))
            ));
        }
        saveData();
    }

    private int jobTargetFromConfig(JobType type, int fallback) {
        String path = "jobs.types." + type.name().toLowerCase(Locale.ROOT) + ".daily_target";
        return Math.max(1, plugin.getConfig().getInt(path, fallback));
    }

    private void incrementProgress(Player player, JobType targetType, int amount) {
        if (player == null || amount <= 0) return;
        ActiveJob job = activeJobs.get(player.getUniqueId());
        if (job == null || job.type != targetType) return;
        int before = job.dailyProgress;
        job.dailyProgress = Math.min(job.dailyTarget, job.dailyProgress + amount);
        if (before < job.dailyTarget && job.dailyProgress >= job.dailyTarget) {
            say(player, "daily_complete", mapOf(
                    "%job%", job.type.display(),
                    "%target%", String.valueOf(job.dailyTarget)
            ));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer() == null) return;
        UUID id = event.getPlayer().getUniqueId();
        ActiveJob job = activeJobs.get(id);
        if (job != null) {
            job.lastSeenAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        ActiveJob job = activeJobs.get(event.getPlayer().getUniqueId());
        if (job != null) {
            job.lastSeenAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onCropBreak(BlockBreakEvent event) {
        if (!enabled || event.isCancelled() || event.getPlayer() == null) return;
        Block block = event.getBlock();
        if (block == null) return;
        if (!FARMABLE_BLOCKS.contains(block.getType())) return;

        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }
        incrementProgress(event.getPlayer(), JobType.FARMING, 1);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!enabled || event.isCancelled()) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        incrementProgress(player, JobType.BREEDING, 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!enabled || event.isCancelled() || event.getPlayer() == null) return;
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            incrementProgress(event.getPlayer(), JobType.FISHING, 1);
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (!enabled || event == null || event.getEntity() == null) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntityType() == EntityType.PLAYER) return;
        incrementProgress(killer, JobType.HUNTING, 1);
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if (!enabled || event == null || event.getEntity() == null) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        incrementProgress(killer, JobType.BOUNTY, 1);
        claimBounty(killer, event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled || event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        ActiveJob job = activeJobs.get(player.getUniqueId());
        if (job == null || job.type != JobType.DELIVERY) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        double distance = from.distance(to);
        if (distance <= 0.0 || distance > 10.0) return;

        double buffer = deliveryBuffer.getOrDefault(player.getUniqueId(), 0.0D) + distance;
        int whole = (int) Math.floor(buffer);
        if (whole > 0) {
            incrementProgress(player, JobType.DELIVERY, whole);
            buffer -= whole;
        }
        deliveryBuffer.put(player.getUniqueId(), buffer);
    }

    public String formatStatus(Player player) {
        if (player == null) return "No player";
        CareerProgress career = careers.get(player.getUniqueId());
        ActiveJob job = activeJobs.get(player.getUniqueId());
        long lockUntil = lockedUntil.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        if (career == null) {
            sb.append(ChatColor.GRAY).append("Career: none");
        } else {
            sb.append(ChatColor.GOLD).append("Career: ").append(career.guild.display())
                    .append(ChatColor.GRAY).append(" (Rank ").append(career.rank).append(")");
        }
        if (job == null) {
            sb.append(ChatColor.GRAY).append(" | Job: none");
        } else {
            sb.append(ChatColor.GREEN).append(" | Job: ").append(job.type.display())
                    .append(ChatColor.GRAY).append(" [").append(job.dailyProgress).append("/").append(job.dailyTarget).append("]");
        }
        if (lockUntil > now) {
            sb.append(ChatColor.RED).append(" | Blacklisted: ").append((lockUntil - now) / 1000L).append("s");
        }
        return sb.toString();
    }

    public List<String> listJobTypes(CareerGuild guild) {
        List<String> out = new ArrayList<>();
        for (JobType type : JobType.values()) {
            if (guild != null && type.guild() != guild) continue;
            out.add(type.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private void claimBounty(Player killer, UUID victimId) {
        if (killer == null || victimId == null) return;
        if (killer.getUniqueId().equals(victimId)) return;
        long bounty = bountyPool.getOrDefault(victimId, 0L);
        if (bounty <= 0L) return;
        CareerProgress career = careers.get(killer.getUniqueId());
        if (career == null || career.guild != CareerGuild.MERCENARY) return;

        bountyPool.remove(victimId);
        PlayerData killerData = plugin.getPlayerDataManager().getPlayerData(killer);
        if (killerData != null) {
            killerData.addEvershards((int) Math.min(Integer.MAX_VALUE, bounty));
            plugin.getPlayerDataManager().savePlayerData(killer.getUniqueId());
        }
        String victimName = Bukkit.getOfflinePlayer(victimId).getName();
        if (victimName == null || victimName.isBlank()) victimName = victimId.toString();
        say(killer, "bounty_claimed", mapOf(
                "%amount%", String.valueOf(bounty),
                "%victim%", victimName
        ));
        Player victim = Bukkit.getPlayer(victimId);
        if (victim != null) {
            victim.sendMessage(prefix() + ChatColor.RED + "Your bounty was claimed by " + killer.getName() + ".");
        }
        saveData();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String prefix() {
        return ChatColor.GOLD + "[Jobs] " + ChatColor.RESET;
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    private Map<String, String> mapOf(String... kv) {
        if (kv == null || kv.length == 0) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private void say(Player player, String key, Map<String, String> placeholders) {
        if (player == null) return;
        boolean voiceLinesEnabled = plugin.getConfig().getBoolean("jobs.voice_lines.enabled", true);
        if (!voiceLinesEnabled) return;
        boolean playerPref = voiceEnabled.getOrDefault(player.getUniqueId(), true);
        if (!playerPref) return;

        String base = "jobs.voice_lines." + key;
        List<String> lines = plugin.getConfig().getStringList(base + ".messages");
        if (lines.isEmpty()) {
            String fallback = plugin.getConfig().getString(base + ".message", "");
            if (fallback != null && !fallback.isBlank()) lines = List.of(fallback);
        }
        if (!lines.isEmpty()) {
            String message = lines.get((int) (Math.random() * lines.size()));
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            player.sendMessage(color(message));
        }

        String sound = plugin.getConfig().getString(base + ".sound", "");
        float volume = (float) plugin.getConfig().getDouble(base + ".volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble(base + ".pitch", 1.0);
        if (sound != null && !sound.isBlank()) {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Throwable ignored) {
            }
        }
    }

    public static final class CareerProgress {
        private CareerGuild guild;
        private int rank;
        private int completedDays;
        private long totalPaid;

        private CareerProgress(CareerGuild guild) {
            this.guild = guild;
            this.rank = 1;
            this.completedDays = 0;
            this.totalPaid = 0L;
        }

        public CareerGuild getGuild() {
            return guild;
        }

        public int getRank() {
            return rank;
        }

        public int getCompletedDays() {
            return completedDays;
        }

        public long getTotalPaid() {
            return totalPaid;
        }
    }

    public static final class ActiveJob {
        private UUID playerId;
        private CareerGuild guild;
        private JobType type;
        private int rankRequired;
        private int dailyTarget;
        private int dailyProgress;
        private int missedDays;
        private int completedSincePay;
        private long startedAt;
        private long lastDailyAt;
        private long lastPayAt;
        private long lastSeenAt;
        private String offeredBy;

        public CareerGuild getGuild() {
            return guild;
        }

        public JobType getType() {
            return type;
        }

        public int getRankRequired() {
            return rankRequired;
        }

        public int getDailyTarget() {
            return dailyTarget;
        }

        public int getDailyProgress() {
            return dailyProgress;
        }

        public int getMissedDays() {
            return missedDays;
        }
    }

    public static final class JobOffer {
        private UUID playerId;
        private CareerGuild guild;
        private JobType type;
        private int rankRequired;
        private String offeredBy;
        private long expiresAt;

        public CareerGuild getGuild() {
            return guild;
        }

        public JobType getType() {
            return type;
        }

        public int getRankRequired() {
            return rankRequired;
        }

        public String getOfferedBy() {
            return offeredBy;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
