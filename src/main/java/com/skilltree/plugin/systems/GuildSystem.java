package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.*;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.IOException;

public class GuildSystem {
    
    private final SkillForgePlugin plugin;
    private final Map<String, GuildData> guilds; // Guild name -> GuildData
    private final Map<UUID, String> playerGuilds; // Player UUID -> Guild name
    private final int MAX_GUILD_SIZE = 50;
    private final Map<UUID, String> invitePrompts = new HashMap<>(); // inviter -> guildName awaiting input

    public GuildSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.guilds = new HashMap<>();
        this.playerGuilds = new HashMap<>();
    }

    // Load guilds from disk
    public void loadGuilds() {
        File guildFolder = new File(plugin.getDataFolder(), "guilds");
        if (!guildFolder.exists()) return;

        File[] files = guildFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                String name = cfg.getString("name");
                UUID leader = UUID.fromString(cfg.getString("leader"));
                long createdAt = cfg.getLong("createdAt", System.currentTimeMillis());
                GuildData guild = new GuildData(name, leader, createdAt);

                if (cfg.contains("members")) {
                    for (String key : cfg.getConfigurationSection("members").getKeys(false)) {
                        try {
                            UUID pid = UUID.fromString(key);
                            String role = cfg.getString("members." + key);
                            guild.getMembers().put(pid, GuildData.MemberRole.valueOf(role));
                            playerGuilds.put(pid, name);
                        } catch (Exception ignored) {}
                    }
                }

                if (cfg.contains("pendingInvites")) {
                    for (String s : cfg.getStringList("pendingInvites")) {
                        try { guild.getPendingInvites().add(UUID.fromString(s)); } catch (Exception ignored) {}
                    }
                }

                if (cfg.contains("blacklist")) {
                    for (String s : cfg.getStringList("blacklist")) {
                        try { guild.getBlacklist().add(UUID.fromString(s)); } catch (Exception ignored) {}
                    }
                }

                guilds.put(name, guild);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load guild file: " + f.getName());
            }
        }
    }

    // Save all guilds to disk
    public void saveAllGuilds() {
        File guildFolder = new File(plugin.getDataFolder(), "guilds");
        if (!guildFolder.exists()) guildFolder.mkdirs();

        for (GuildData guild : guilds.values()) {
            File out = new File(guildFolder, guild.getGuildName() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("name", guild.getGuildName());
            cfg.set("leader", guild.getLeaderId().toString());
            cfg.set("createdAt", guild.getCreatedAt());

            Map<String, String> membersMap = new HashMap<>();
            for (Map.Entry<UUID, GuildData.MemberRole> me : guild.getMembers().entrySet()) {
                membersMap.put(me.getKey().toString(), me.getValue().name());
            }
            cfg.createSection("members", membersMap);

            List<String> invites = new ArrayList<>();
            for (UUID u : guild.getPendingInvites()) invites.add(u.toString());
            cfg.set("pendingInvites", invites);

            List<String> bl = new ArrayList<>();
            for (UUID u : guild.getBlacklist()) bl.add(u.toString());
            cfg.set("blacklist", bl);

            try {
                cfg.save(out);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save guild: " + guild.getGuildName());
            }
        }
    }


    // Management helpers
    public boolean promoteMember(String guildName, UUID operator, UUID target) {
        GuildData g = guilds.get(guildName);
        if (g == null) return false;
        if (!g.canManage(operator) && !g.getRole(operator).equals(GuildData.MemberRole.SENIOR)) return false;
        g.promoteToSenior(target);
        return true;
    }

    public boolean demoteMember(String guildName, UUID operator, UUID target) {
        GuildData g = guilds.get(guildName);
        if (g == null) return false;
        if (!g.canManage(operator)) return false;
        g.demoteToMember(target);
        return true;
    }

    public boolean kickMember(String guildName, UUID operator, UUID target) {
        GuildData g = guilds.get(guildName);
        if (g == null) return false;
        if (!g.canManage(operator)) return false;
        g.addToBlacklist(target);
        playerGuilds.remove(target);
        // persist
        saveAllGuilds();
        return true;
    }
    
    public boolean createGuild(Player player, String guildName) {
        // Check if guild already exists
        if (guilds.containsKey(guildName)) {
            player.sendMessage("§c§l[SkillForge] §cGuild '" + guildName + "' already exists!");
            return false;
        }
        
        // Check if player is already in a guild
        if (playerGuilds.containsKey(player.getUniqueId())) {
            player.sendMessage("§c§l[SkillForge] §cYou are already in a guild! Leave first.");
            return false;
        }
        
        // Create guild
        GuildData guild = new GuildData(guildName, player.getUniqueId());
        guilds.put(guildName, guild);
        playerGuilds.put(player.getUniqueId(), guildName);
        
        player.sendMessage("§a§l[SkillForge] §eGuild '" + guildName + "' created successfully!");
        // persist immediately
        saveAllGuilds();
        return true;
    }
    
    public boolean invitePlayer(Player inviter, String targetName) {
        String inviterGuild = getPlayerGuild(inviter.getUniqueId());
        if (inviterGuild == null) {
            inviter.sendMessage("§c§l[SkillForge] §cYou are not in a guild!");
            return false;
        }
        
        GuildData guild = guilds.get(inviterGuild);
        if (!guild.canInvite(inviter.getUniqueId())) {
            inviter.sendMessage("§c§l[SkillForge] §cYou don't have permission to invite!");
            return false;
        }
        
        if (guild.getMemberCount() >= MAX_GUILD_SIZE) {
            inviter.sendMessage("§c§l[SkillForge] §cGuild is at max capacity!");
            return false;
        }
        
        // Check if target is blacklisted
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target != null && guild.getBlacklist().contains(target.getUniqueId())) {
            inviter.sendMessage("§c§l[SkillForge] §cPlayer is blacklisted from this guild!");
            return false;
        }
        
            if (target != null) {
                guild.getPendingInvites().add(target.getUniqueId());
                // persist
                saveAllGuilds();
                target.sendMessage("§6§l[SkillForge] §eYou have been invited to join §6" + inviterGuild + "§e!");
                target.sendMessage("§7Type §b/guild accept " + inviterGuild + "§7 to join");
                inviter.sendMessage("§a§l[SkillForge] §eInvite sent to " + targetName + "!");
                return true;
            } else {
                inviter.sendMessage("§c§l[SkillForge] §cPlayer not found!");
                return false;
            }
    }

        public boolean invitePlayerByName(Player inviter, String targetName) {
            String inviterGuild = getPlayerGuild(inviter.getUniqueId());
            if (inviterGuild == null) {
                inviter.sendMessage("§c§l[SkillForge] §cYou are not in a guild!");
                return false;
            }
            GuildData guild = guilds.get(inviterGuild);
            if (!guild.canInvite(inviter.getUniqueId())) {
                inviter.sendMessage("§c§l[SkillForge] §cYou don't have permission to invite!");
                return false;
            }
            if (guild.getMemberCount() >= MAX_GUILD_SIZE) {
                inviter.sendMessage("§c§l[SkillForge] §cGuild is at max capacity!");
                return false;
            }

            // Resolve offline player
            org.bukkit.OfflinePlayer off = plugin.getServer().getOfflinePlayer(targetName);
            if (off == null) {
                inviter.sendMessage("§c§l[SkillForge] §cPlayer not found!");
                return false;
            }
            UUID tid = off.getUniqueId();
            if (guild.getBlacklist().contains(tid)) {
                inviter.sendMessage("§c§l[SkillForge] §cPlayer is blacklisted from this guild!");
                return false;
            }
            guild.getPendingInvites().add(tid);
            saveAllGuilds();
            if (off.isOnline()) {
                Player tp = off.getPlayer();
                tp.sendMessage("§6§l[SkillForge] §eYou have been invited to join §6" + inviterGuild + "§e!");
                tp.sendMessage("§7Type §b/guild accept " + inviterGuild + "§7 to join");
            }
            inviter.sendMessage("§a§l[SkillForge] §eInvite queued for " + targetName + "!");
            return true;
        }
    
    public boolean acceptInvite(Player player, String guildName) {
        GuildData guild = guilds.get(guildName);
        if (guild == null) {
            player.sendMessage("§c§l[SkillForge] §cGuild not found!");
            return false;
        }
        
        if (!guild.getPendingInvites().contains(player.getUniqueId())) {
            player.sendMessage("§c§l[SkillForge] §cYou don't have an invite to this guild!");
            return false;
        }
        
        if (playerGuilds.containsKey(player.getUniqueId())) {
            player.sendMessage("§c§l[SkillForge] §cLeave your current guild first!");
            return false;
        }
        
        guild.addMember(player.getUniqueId(), GuildData.MemberRole.MEMBER);
        playerGuilds.put(player.getUniqueId(), guildName);
        
        player.sendMessage("§a§l[SkillForge] §eYou joined guild §6" + guildName + "§e!");
        
        // Notify guild members
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && !member.equals(player)) {
                member.sendMessage("§6§l[SkillForge] §e" + player.getName() + " joined the guild!");
            }
        }
        
        // persist
        saveAllGuilds();
        return true;
    }
    
    public boolean leaveGuild(Player player) {
        String guildName = getPlayerGuild(player.getUniqueId());
        if (guildName == null) {
            player.sendMessage("§c§l[SkillForge] §cYou are not in a guild!");
            return false;
        }
        
        GuildData guild = guilds.get(guildName);
        if (guild.canManage(player.getUniqueId())) {
            player.sendMessage("§c§l[SkillForge] §cLeader cannot leave! Disband the guild or transfer leadership.");
            return false;
        }
        
        guild.removeMember(player.getUniqueId());
        playerGuilds.remove(player.getUniqueId());
        
        player.sendMessage("§a§l[SkillForge] §eYou left guild §6" + guildName + "§e!");
        
        // Notify guild members
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null) {
                member.sendMessage("§6§l[SkillForge] §e" + player.getName() + " left the guild!");
            }
        }
        
        // persist
        saveAllGuilds();
        return true;
    }
    
    public String getPlayerGuild(UUID playerId) {
        return playerGuilds.get(playerId);
    }
    
    public String getGuildTag(UUID playerId) {
        String guildName = getPlayerGuild(playerId);
        if (guildName == null) return "";
        
        GuildData guild = guilds.get(guildName);
        GuildData.MemberRole role = guild.getRole(playerId);
        
        if (role == GuildData.MemberRole.LEADER) {
            return "§c[" + guildName + "★]§r ";
        } else if (role == GuildData.MemberRole.SENIOR) {
            return "§e[" + guildName + "◆]§r ";
        } else {
            return "§9[" + guildName + "]§r ";
        }
    }
    
    public GuildData getGuild(String guildName) {
        return guilds.get(guildName);
    }
    
    public Collection<GuildData> getAllGuilds() {
        return guilds.values();
    }

    // Invite prompt lifecycle helpers
    public void startInvitePrompt(java.util.UUID inviter, String guildName) {
        invitePrompts.put(inviter, guildName);
        org.bukkit.entity.Player p = plugin.getServer().getPlayer(inviter);
        if (p != null) {
            p.sendMessage("§6§l[SkillForge] §eType the player name in chat to invite, or type §ccancel §eto abort.");
        }
    }

    public boolean hasInvitePrompt(java.util.UUID inviter) {
        return invitePrompts.containsKey(inviter);
    }

    public void cancelInvitePrompt(java.util.UUID inviter) {
        invitePrompts.remove(inviter);
        org.bukkit.entity.Player p = plugin.getServer().getPlayer(inviter);
        if (p != null) p.sendMessage("§c§l[SkillForge] §cInvite canceled.");
    }

    public void handleInviteResponse(org.bukkit.entity.Player inviter, String message) {
        UUID id = inviter.getUniqueId();
        if (!invitePrompts.containsKey(id)) return;
        invitePrompts.remove(id);
        if (message.equalsIgnoreCase("cancel")) {
            inviter.sendMessage("§c§l[SkillForge] §cInvite canceled.");
            return;
        }
        // Call invitePlayerByName synchronously
        invitePlayerByName(inviter, message);
    }
}
