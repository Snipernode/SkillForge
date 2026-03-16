package com.skilltree.plugin.commands;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.gui.PersonalJournalGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PersonalJournalCommand implements CommandExecutor {

    private final PersonalJournalGUI personalJournalGUI;

    public PersonalJournalCommand(SkillForgePlugin plugin, PersonalJournalGUI personalJournalGUI) {
        this.personalJournalGUI = personalJournalGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (personalJournalGUI == null) {
            player.sendMessage(ChatColor.RED + "Personal Journal is unavailable right now.");
            return true;
        }

        if (!player.hasPermission("skillforge.journal")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the Personal Journal.");
            return true;
        }

        if (args.length == 0) {
            personalJournalGUI.openForPlayer(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 1) {
                boolean given = personalJournalGUI.giveJournal(player, true);
                if (given) {
                    player.sendMessage(ChatColor.GREEN + "Personal Journal added to your inventory.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "You already have a Personal Journal.");
                }
                return true;
            }

            if (!player.isOp() && !player.hasPermission("skillforge.admin")) {
                player.sendMessage(ChatColor.RED + "You need admin permission to give journals to others.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            boolean given = personalJournalGUI.giveJournal(target, false);
            if (given) {
                player.sendMessage(ChatColor.GREEN + "Gave a Personal Journal to " + target.getName() + ".");
                target.sendMessage(ChatColor.GOLD + "You received a Personal Journal.");
            } else {
                player.sendMessage(ChatColor.RED + "Could not give a Personal Journal.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("open")) {
            personalJournalGUI.openForPlayer(player);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /journal [open|give [player]]");
        return true;
    }
}
