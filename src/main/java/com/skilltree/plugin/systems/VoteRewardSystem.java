package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.managers.CrateKeyLedger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoteRewardSystem implements Listener {
    private final SkillForgePlugin plugin;
    private final CrateKeyLedger keyLedger;
    private final CrateBlockSystem crateBlockSystem;
    private final Map<String, Long> recentVotes = new ConcurrentHashMap<>();

    private boolean enabled;
    private String keyType;
    private int keysPerVote;
    private boolean giveKeyItems;
    private boolean broadcastEnabled;
    private String playerMessage;
    private String broadcastMessage;
    private List<String> acceptedServices;
    private List<String> rewardCommands;
    private boolean requireDiscordLink;
    private String voteLinkRequiredMessage;

    public VoteRewardSystem(SkillForgePlugin plugin, CrateKeyLedger keyLedger, CrateBlockSystem crateBlockSystem) {
        this.plugin = plugin;
        this.keyLedger = keyLedger;
        this.crateBlockSystem = crateBlockSystem;
        loadConfig();
        registerDynamicVoteHook();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("votes.enabled", true);
        keyType = plugin.getConfig().getString("votes.key_type", "vote");
        if (keyType == null || keyType.isBlank()) keyType = "vote";
        keysPerVote = Math.max(1, plugin.getConfig().getInt("votes.keys_per_vote", 1));
        giveKeyItems = plugin.getConfig().getBoolean("votes.give_key_items", true);
        broadcastEnabled = plugin.getConfig().getBoolean("votes.broadcast.enabled", false);
        playerMessage = plugin.getConfig().getString("votes.messages.player", "&aThanks for voting on &e%service%&a! You got &e%amount%x %key_type% key(s)&a.");
        broadcastMessage = plugin.getConfig().getString("votes.messages.broadcast", "&b%player% voted on &e%service%&b!");
        acceptedServices = plugin.getConfig().getStringList("votes.accepted_services");
        rewardCommands = plugin.getConfig().getStringList("votes.commands");
        requireDiscordLink = plugin.getConfig().getBoolean("kdhud.require_discord_link_for.votes", false);
        voteLinkRequiredMessage = plugin.getConfig().getString(
                "kdhud.messages.vote_link_required",
                "&cLink your Discord first with &e/link <code>&c to receive vote rewards."
        );
    }

    @SuppressWarnings("unchecked")
    private void registerDynamicVoteHook() {
        Class<? extends Event> voteEventClass = null;
        for (String className : new String[]{
                "com.vexsoftware.votifier.model.VotifierEvent",
                "com.vexsoftware.votifier.bungee.events.VotifierEvent"
        }) {
            try {
                Class<?> c = Class.forName(className);
                if (Event.class.isAssignableFrom(c)) {
                    voteEventClass = (Class<? extends Event>) c;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (voteEventClass == null) {
            plugin.getLogger().info("Vote hook idle: NuVotifier event class not found.");
            return;
        }

        Plugin nuvotifier = findPluginIgnoreCase("NuVotifier");
        if (nuvotifier == null || !nuvotifier.isEnabled()) {
            plugin.getLogger().info("Vote hook idle: NuVotifier plugin not enabled.");
            return;
        }

        plugin.getServer().getPluginManager().registerEvent(
                voteEventClass,
                this,
                EventPriority.NORMAL,
                (listener, event) -> handleVoteEvent(event),
                plugin,
                true
        );
        plugin.getLogger().info("Vote hook enabled via " + voteEventClass.getName());
    }

    private Plugin findPluginIgnoreCase(String name) {
        Plugin exact = Bukkit.getPluginManager().getPlugin(name);
        if (exact != null) return exact;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private void handleVoteEvent(Event event) {
        if (!enabled || event == null) return;
        Object vote = invokeNoArg(event, "getVote");
        if (vote == null) return;

        String username = asString(
                invokeNoArg(vote, "getUsername"),
                invokeNoArg(vote, "getPlayerName")
        );
        String service = asString(
                invokeNoArg(vote, "getServiceName"),
                invokeNoArg(vote, "getService")
        );
        String address = asString(invokeNoArg(vote, "getAddress"));

        if (username == null || username.isBlank()) return;
        if (service == null || service.isBlank()) service = "unknown-service";
        if (!isAcceptedService(service)) return;
        if (isDuplicateVote(username, service, address)) return;

        UUID uuid = resolvePlayerUuid(username);
        if (uuid == null) {
            plugin.getLogger().warning("Vote received but player could not be resolved: " + username + " @ " + service);
            return;
        }

        grantVoteReward(uuid, username, service);
    }

    private boolean isAcceptedService(String service) {
        if (acceptedServices == null || acceptedServices.isEmpty()) return true;
        String normService = normalizeService(service);
        for (String entry : acceptedServices) {
            String normEntry = normalizeService(entry);
            if (normEntry.isBlank()) continue;
            if (normService.equals(normEntry) || normService.contains(normEntry) || normEntry.contains(normService)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeService(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replace("https://", "").replace("http://", "").replace("www.", "").trim();
    }

    private boolean isDuplicateVote(String username, String service, String address) {
        long now = System.currentTimeMillis();
        String key = username.toLowerCase(Locale.ROOT) + "|" + normalizeService(service) + "|" + (address == null ? "" : address);
        Long last = recentVotes.put(key, now);
        return last != null && (now - last) < 10_000L;
    }

    private UUID resolvePlayerUuid(String username) {
        try {
            return UUID.fromString(username);
        } catch (IllegalArgumentException ignored) {
        }

        Player online = Bukkit.getPlayerExact(username);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(username);
        return offline != null ? offline.getUniqueId() : null;
    }

    private void grantVoteReward(UUID uuid, String username, String service) {
        if (requireDiscordLink) {
            KDHudBridge bridge = plugin.getKDHudBridge();
            if (bridge == null || !bridge.isLinked(uuid)) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.sendMessage(color(voteLinkRequiredMessage));
                }
                plugin.getLogger().info("Vote reward skipped (unlinked Discord): player=" + username + ", service=" + service);
                return;
            }
        }

        Player online = Bukkit.getPlayer(uuid);
        long amount = keysPerVote;
        if (giveKeyItems && online != null && crateBlockSystem != null) {
            crateBlockSystem.giveKeyItems(online, keyType, amount);
        } else {
            keyLedger.addKeys(uuid, keyType, amount);
        }

        runRewardCommands(uuid, username, service, amount);

        if (online != null) {
            online.sendMessage(color(playerMessage)
                    .replace("%service%", service)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%key_type%", keyType)
            );
        }

        if (broadcastEnabled) {
            Bukkit.broadcastMessage(color(broadcastMessage)
                    .replace("%player%", username)
                    .replace("%service%", service)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%key_type%", keyType));
        }

        plugin.getLogger().info("Vote reward: player=" + username + ", service=" + service + ", keys=" + amount + " " + keyType);
    }

    private void runRewardCommands(UUID uuid, String username, String service, long amount) {
        if (rewardCommands == null || rewardCommands.isEmpty()) return;
        for (String raw : rewardCommands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw
                    .replace("%player%", username)
                    .replace("%uuid%", String.valueOf(uuid))
                    .replace("%service%", service)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%key_type%", keyType);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String asString(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            String s = String.valueOf(value).trim();
            if (!s.isBlank()) return s;
        }
        return null;
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
