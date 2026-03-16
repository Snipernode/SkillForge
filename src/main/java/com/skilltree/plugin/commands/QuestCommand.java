package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.Quest;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class QuestCommand implements TabExecutor {
    private final SkillForgePlugin plugin;

    public QuestCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            plugin.getQuestGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list":
            case "active":
                showActiveQuests(player);
                return true;
            case "complete":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " complete <questId>");
                    return true;
                }
                completeQuest(player, args[1]);
                return true;
            case "cancel":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " cancel <questId>");
                    return true;
                }
                cancelQuest(player, args[1]);
                return true;
            case "help":
            default:
                sendHelp(player, label);
                return true;
        }
    }

    private void showActiveQuests(Player player) {
        Map<String, Quest> active = plugin.getQuestLeader().getPlayerQuests(player);
        if (active.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no active quests.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/quest" + ChatColor.GRAY + " to accept one.");
            return;
        }

        List<Map.Entry<String, Quest>> sorted = new ArrayList<>(active.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getValue().getQuestName(), String.CASE_INSENSITIVE_ORDER));

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Active Quests (" + sorted.size() + "/3)");
        for (Map.Entry<String, Quest> entry : sorted) {
            String questId = entry.getKey();
            Quest quest = entry.getValue();
            int progress = plugin.getQuestHandler().getQuestProgress(player, quest.getFullTrackingId());
            int required = quest.getRequiredAmount();
            long remainingTicks = quest.getTimeRemainingTicks(player.getWorld());

            String status = progress >= required
                    ? ChatColor.GREEN + "Ready to complete"
                    : ChatColor.YELLOW.toString() + progress + "/" + required;
            player.sendMessage(ChatColor.AQUA + "- " + ChatColor.WHITE + quest.getQuestName() + ChatColor.DARK_GRAY + " [" + questId + "]");
            player.sendMessage(ChatColor.GRAY + "  Progress: " + status + ChatColor.GRAY + " | Time Left: " + ChatColor.WHITE + formatTicks(remainingTicks));
        }

        player.sendMessage(ChatColor.DARK_GRAY + "Use /quest complete <id> or /quest cancel <id>");
    }

    private void completeQuest(Player player, String rawId) {
        String resolvedId = resolveQuestId(player, rawId);
        if (resolvedId == null) {
            player.sendMessage(ChatColor.RED + "No active quest matched '" + rawId + "'.");
            return;
        }
        plugin.getQuestLeader().completeQuest(player, resolvedId);
    }

    private void cancelQuest(Player player, String rawId) {
        String resolvedId = resolveQuestId(player, rawId);
        if (resolvedId == null) {
            player.sendMessage(ChatColor.RED + "No active quest matched '" + rawId + "'.");
            return;
        }
        plugin.getQuestLeader().cancelQuest(player, resolvedId);
    }

    private String resolveQuestId(Player player, String rawId) {
        Map<String, Quest> active = plugin.getQuestLeader().getPlayerQuests(player);
        if (active.containsKey(rawId)) {
            return rawId;
        }

        for (String id : active.keySet()) {
            if (id.equalsIgnoreCase(rawId)) {
                return id;
            }
        }

        for (Map.Entry<String, Quest> entry : active.entrySet()) {
            if (entry.getValue().getQuestName().equalsIgnoreCase(rawId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String formatTicks(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Quest Commands");
        player.sendMessage(ChatColor.GRAY + "/" + label + ChatColor.WHITE + " - Open quest board");
        player.sendMessage(ChatColor.GRAY + "/" + label + " list" + ChatColor.WHITE + " - Show active quests");
        player.sendMessage(ChatColor.GRAY + "/" + label + " complete <id>" + ChatColor.WHITE + " - Complete ready quest");
        player.sendMessage(ChatColor.GRAY + "/" + label + " cancel <id>" + ChatColor.WHITE + " - Cancel active quest");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterCompletions(args[0], List.of("list", "active", "complete", "cancel", "help"));
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("complete".equals(sub) || "cancel".equals(sub)) {
                return filterCompletions(args[1], new ArrayList<>(plugin.getQuestLeader().getPlayerQuests(player).keySet()));
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(String input, List<String> options) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(needle))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
