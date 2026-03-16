package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.NpcJobSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class JobCommand implements CommandExecutor {
    private final SkillForgePlugin plugin;
    private final NpcJobSystem jobs;

    public JobCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.jobs = plugin.getNpcJobSystem();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (jobs == null || !jobs.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Jobs are disabled.");
            return true;
        }

        if (args.length == 0) {
            if (player == null) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /job <status|join|accept|quit|voice|offer|setrank>");
                return true;
            }
            sendHelp(player);
            player.sendMessage(jobs.formatStatus(player));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Only players can use /job status.");
                    return true;
                }
                player.sendMessage(jobs.formatStatus(player));
                NpcJobSystem.JobOffer offer = jobs.getPendingOffer(player.getUniqueId());
                if (offer != null) {
                    long sec = Math.max(0L, (offer.getExpiresAt() - System.currentTimeMillis()) / 1000L);
                    player.sendMessage(ChatColor.YELLOW + "Offer: " + offer.getType().display() + ChatColor.GRAY +
                            " from " + offer.getGuild().display() + " (rank " + offer.getRankRequired() + ", " + sec + "s)");
                }
                return true;
            }
            case "join" -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Only players can use /job join.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /job join <merchant|mercenary>");
                    return true;
                }
                NpcJobSystem.CareerGuild guild = NpcJobSystem.CareerGuild.fromInput(args[1]);
                if (guild == null) {
                    player.sendMessage(ChatColor.RED + "Unknown guild. Use merchant or mercenary.");
                    return true;
                }
                jobs.joinCareer(player, guild);
                return true;
            }
            case "accept" -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Only players can use /job accept.");
                    return true;
                }
                jobs.acceptOffer(player);
                return true;
            }
            case "quit", "leave" -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Only players can use /job quit.");
                    return true;
                }
                jobs.quitJob(player);
                return true;
            }
            case "voice" -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Only players can use /job voice.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /job voice <on|off>");
                    return true;
                }
                boolean enabled = args[1].equalsIgnoreCase("on")
                        || args[1].equalsIgnoreCase("true")
                        || args[1].equalsIgnoreCase("enable");
                jobs.setVoiceEnabled(player.getUniqueId(), enabled);
                player.sendMessage(ChatColor.GREEN + "Job voice lines: " + (enabled ? "ON" : "OFF"));
                return true;
            }
            case "bounty" -> {
                if (args.length == 1) {
                    if (player == null) {
                        sender.sendMessage(ChatColor.RED + "Usage: /job bounty <player> OR /job bounty place <player> <amount>");
                        return true;
                    }
                    long own = jobs.getBounty(player.getUniqueId());
                    sender.sendMessage(ChatColor.YELLOW + "Current bounty on you: " + own);
                    return true;
                }
                if (args[1].equalsIgnoreCase("place")) {
                    if (player == null) {
                        sender.sendMessage(ChatColor.RED + "Only players can place bounties.");
                        return true;
                    }
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /job bounty place <player> <amount>");
                        return true;
                    }
                    UUID targetId = plugin.getServer().getOfflinePlayer(args[2]).getUniqueId();
                    long amount = Math.max(1L, parseLong(args, 3, 1L));
                    jobs.placeBounty(player, targetId, amount);
                    return true;
                }
                UUID targetId = plugin.getServer().getOfflinePlayer(args[1]).getUniqueId();
                long bounty = jobs.getBounty(targetId);
                sender.sendMessage(ChatColor.YELLOW + "Bounty on " + args[1] + ": " + bounty);
                return true;
            }
            case "offer" -> {
                if (!sender.hasPermission("skillforge.job.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /job offer <player> <merchant|mercenary> <type> [rankRequired]");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                NpcJobSystem.CareerGuild guild = NpcJobSystem.CareerGuild.fromInput(args[2]);
                NpcJobSystem.JobType type = NpcJobSystem.JobType.fromInput(args[3]);
                int rankRequired = Math.max(1, parseInt(args, 4, 1));
                if (guild == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown guild.");
                    return true;
                }
                if (type == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown type. Types: " + String.join(", ", jobs.listJobTypes(guild)));
                    return true;
                }
                String offeredBy = sender.getName();
                if (jobs.offerJob(target, guild, type, rankRequired, offeredBy)) {
                    sender.sendMessage(ChatColor.GREEN + "Offered " + type.display() + " to " + target.getName() + ".");
                }
                return true;
            }
            case "setrank" -> {
                if (!sender.hasPermission("skillforge.job.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /job setrank <player> <merchant|mercenary> <rank>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                UUID targetId;
                String targetName;
                if (target != null) {
                    targetId = target.getUniqueId();
                    targetName = target.getName();
                } else {
                    targetId = plugin.getServer().getOfflinePlayer(args[1]).getUniqueId();
                    targetName = args[1];
                }
                NpcJobSystem.CareerGuild guild = NpcJobSystem.CareerGuild.fromInput(args[2]);
                int rank = Math.max(1, parseInt(args, 3, 1));
                if (guild == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown guild.");
                    return true;
                }
                if (jobs.setCareerRank(targetId, guild, rank)) {
                    sender.sendMessage(ChatColor.GREEN + "Set " + targetName + " to " + guild.display() + " rank " + rank + ".");
                }
                return true;
            }
            default -> {
                if (player == null) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /job <status|join|accept|quit|voice|offer|setrank>");
                    return true;
                }
                sendHelp(player);
                return true;
            }
        }
    }

    private int parseInt(String[] args, int index, int fallback) {
        if (args == null || index < 0 || index >= args.length) return fallback;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String[] args, int index, long fallback) {
        if (args == null || index < 0 || index >= args.length) return fallback;
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "Job Commands");
        player.sendMessage(ChatColor.GRAY + "/job status");
        player.sendMessage(ChatColor.GRAY + "/job join <merchant|mercenary>");
        player.sendMessage(ChatColor.GRAY + "/job accept");
        player.sendMessage(ChatColor.GRAY + "/job quit");
        player.sendMessage(ChatColor.GRAY + "/job bounty <player>");
        player.sendMessage(ChatColor.GRAY + "/job bounty place <player> <amount>");
        player.sendMessage(ChatColor.GRAY + "/job voice <on|off>");
        if (player.hasPermission("skillforge.job.admin")) {
            player.sendMessage(ChatColor.GRAY + "/job offer <player> <merchant|mercenary> <type> [rank]");
            player.sendMessage(ChatColor.GRAY + "/job setrank <player> <merchant|mercenary> <rank>");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "NPC integration: set FancyNPC action to run /job offer <player> ...");
    }
}
