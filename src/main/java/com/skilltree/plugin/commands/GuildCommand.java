package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.GuildHallBuilder;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildCommand implements CommandExecutor {
    
    private final SkillForgePlugin plugin;
    
    public GuildCommand(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§c§l[SkillForge] §cUsage: /guild create <name>");
                    return true;
                }
                String guildName = args[1];
                plugin.getGuildSystem().createGuild(player, guildName);
                break;
                
            case "invite":
                if (args.length < 2) {
                    // open anvil invite GUI
                    new com.skilltree.plugin.gui.AnvilInviteGUI(plugin, player, plugin.getGuildSystem().getPlayerGuild(player.getUniqueId())).open();
                    return true;
                }
                String targetName = args[1];
                plugin.getGuildSystem().invitePlayer(player, targetName);
                break;
            case "list":
                new com.skilltree.plugin.gui.GuildListGUI(plugin, player, 0).open();
                break;
                
            case "accept":
                if (args.length < 2) {
                    player.sendMessage("§c§l[SkillForge] §cUsage: /guild accept <guildname>");
                    return true;
                }
                String guildToJoin = args[1];
                plugin.getGuildSystem().acceptInvite(player, guildToJoin);
                break;
                
            case "leave":
                plugin.getGuildSystem().leaveGuild(player);
                break;
                
            case "info":
                showGuildInfo(player);
                break;

            case "hall":
            case "buildhall":
                if (!player.isOp() && !player.hasPermission("skillforge.admin")) {
                    player.sendMessage("§c§l[SkillForge] §cYou need admin permissions.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c§l[SkillForge] §cUsage: /guild hall <hunters|merchant|adventurer|thieves|all|clear> [size] [north|east|south|west]");
                    return true;
                }
                if (args[1].equalsIgnoreCase("clear")) {
                    int radius = 160;
                    if (args.length >= 3) {
                        try {
                            radius = Integer.parseInt(args[2]);
                        } catch (NumberFormatException ignored) {
                            player.sendMessage("§c§l[SkillForge] §cInvalid radius. Example: /guild hall clear 180");
                            return true;
                        }
                    }
                    GuildHallBuilder.clearGuildArea(player.getLocation(), radius);
                    player.sendMessage("§a§l[SkillForge] §eCleared guild structures in a " + radius + " block radius.");
                    return true;
                }

                int hallSize = GuildHallBuilder.DEFAULT_HALL_SIZE;
                int facingArgIndex = 2;
                if (args.length >= 3) {
                    int parsed = parseHallSize(args[2]);
                    if (parsed > 0) {
                        hallSize = parsed;
                        facingArgIndex = 3;
                    } else if (parseFacing(args[2]) == null) {
                        player.sendMessage("§c§l[SkillForge] §cInvalid size. Use 50, 75, 100 or small/medium/large.");
                        return true;
                    }
                }

                BlockFace facing = BlockFace.SOUTH;
                if (args.length >= facingArgIndex + 1) {
                    BlockFace parsedFacing = parseFacing(args[facingArgIndex]);
                    if (parsedFacing == null) {
                        player.sendMessage("§c§l[SkillForge] §cInvalid rotation. Use north, east, south, or west.");
                        return true;
                    }
                    facing = parsedFacing;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    GuildHallBuilder.buildAll(player.getLocation(), hallSize, facing);
                    player.sendMessage("§a§l[SkillForge] §eBuilt all four " + hallSize + "x" + hallSize + " guild halls facing " + facing.name().toLowerCase() + ".");
                    return true;
                }
                GuildHallBuilder.GuildHallType type = GuildHallBuilder.GuildHallType.fromInput(args[1]);
                if (type == null) {
                    player.sendMessage("§c§l[SkillForge] §cUnknown hall type. Use hunters, merchant, adventurer, thieves, or all.");
                    return true;
                }
                GuildHallBuilder.buildHall(player.getLocation(), type, hallSize, facing);
                player.sendMessage("§a§l[SkillForge] §eBuilt " + type.displayName() + " (" + hallSize + "x" + hallSize + ", facing " + facing.name().toLowerCase() + ").");
                break;
                
            default:
                showHelp(player);
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§6§l[SkillForge] §eGuild Commands:");
        player.sendMessage("§7/guild create <name> - Create a new guild");
        player.sendMessage("§7/guild invite <player> - Invite a player to your guild");
        player.sendMessage("§7/guild accept <name> - Accept a guild invite");
        player.sendMessage("§7/guild leave - Leave your current guild");
        player.sendMessage("§7/guild info - View guild information");
        if (player.isOp() || player.hasPermission("skillforge.admin")) {
            player.sendMessage("§7/guild hall <type|all> [size] [facing] - Build guild halls with rotation");
            player.sendMessage("§7/guild hall clear [radius] - Remove guild halls in an area");
        }
    }

    private int parseHallSize(String input) {
        if (input == null || input.isBlank()) {
            return GuildHallBuilder.DEFAULT_HALL_SIZE;
        }
        String raw = input.trim().toLowerCase();
        switch (raw) {
            case "small":
                return 50;
            case "medium":
                return 75;
            case "large":
                return 100;
            default:
                try {
                    int parsed = Integer.parseInt(raw);
                    return switch (parsed) {
                        case 50, 75, 100 -> parsed;
                        default -> -1;
                    };
                } catch (NumberFormatException ignored) {
                    return -1;
                }
        }
    }

    private BlockFace parseFacing(String input) {
        if (input == null || input.isBlank()) return BlockFace.SOUTH;
        return switch (input.trim().toLowerCase()) {
            case "n", "north" -> BlockFace.NORTH;
            case "e", "east" -> BlockFace.EAST;
            case "s", "south" -> BlockFace.SOUTH;
            case "w", "west" -> BlockFace.WEST;
            default -> null;
        };
    }
    
    private void showGuildInfo(Player player) {
        String guildName = plugin.getGuildSystem().getPlayerGuild(player.getUniqueId());
        
        if (guildName == null) {
            player.sendMessage("§c§l[SkillForge] §cYou are not in a guild!");
            return;
        }
        
        var guild = plugin.getGuildSystem().getGuild(guildName);
        player.sendMessage("§6§l========== " + guildName + " ==========");
        player.sendMessage("§7Members: §a" + guild.getMemberCount() + "/50");
        player.sendMessage("§7Your Role: §b" + guild.getRole(player.getUniqueId()));
        player.sendMessage("§7Created: §e" + formatTime(guild.getCreatedAt()));
    }
    
    private String formatTime(long millis) {
        long seconds = (System.currentTimeMillis() - millis) / 1000;
        long days = seconds / 86400;
        if (days > 0) return days + "d ago";
        long hours = (seconds % 86400) / 3600;
        if (hours > 0) return hours + "h ago";
        long minutes = (seconds % 3600) / 60;
        if (minutes > 0) return minutes + "m ago";
        return "just now";
    }
}
