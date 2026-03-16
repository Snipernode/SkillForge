package com.skilltree.plugin.systems;

import java.util.*;

public class GuildData {
    
    public enum MemberRole {
        LEADER, SENIOR, MEMBER
    }
    
    private final String guildName;
    private final UUID leaderId;
    private final long createdAt;
    private final Map<UUID, MemberRole> members; // UUID -> Role
    private final Set<UUID> pendingInvites;
    private final Set<UUID> blacklist; // Players who left/were kicked
    
    public GuildData(String guildName, UUID leaderId) {
        this.guildName = guildName;
        this.leaderId = leaderId;
        this.createdAt = System.currentTimeMillis();
        this.members = new HashMap<>();
        this.pendingInvites = new HashSet<>();
        this.blacklist = new HashSet<>();
        
        // Add leader to members
        this.members.put(leaderId, MemberRole.LEADER);
    }

    // Constructor used when loading from disk to preserve creation time
    public GuildData(String guildName, UUID leaderId, long createdAt) {
        this.guildName = guildName;
        this.leaderId = leaderId;
        this.createdAt = createdAt;
        this.members = new HashMap<>();
        this.pendingInvites = new HashSet<>();
        this.blacklist = new HashSet<>();
        this.members.put(leaderId, MemberRole.LEADER);
    }
    
    public String getGuildName() {
        return guildName;
    }
    
    public UUID getLeaderId() {
        return leaderId;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public Map<UUID, MemberRole> getMembers() {
        return members;
    }
    
    public Set<UUID> getPendingInvites() {
        return pendingInvites;
    }
    
    public Set<UUID> getBlacklist() {
        return blacklist;
    }
    
    public MemberRole getRole(UUID playerId) {
        return members.getOrDefault(playerId, null);
    }
    
    public void addMember(UUID playerId, MemberRole role) {
        members.put(playerId, role);
        pendingInvites.remove(playerId);
    }
    
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }
    
    public void promoteToSenior(UUID playerId) {
        if (members.containsKey(playerId) && members.get(playerId) == MemberRole.MEMBER) {
            members.put(playerId, MemberRole.SENIOR);
        }
    }

    public void demoteToMember(UUID playerId) {
        if (members.containsKey(playerId) && members.get(playerId) == MemberRole.SENIOR) {
            members.put(playerId, MemberRole.MEMBER);
        }
    }

    public void setRole(UUID playerId, MemberRole role) {
        if (members.containsKey(playerId)) {
            members.put(playerId, role);
        }
    }

    public void addToBlacklist(UUID playerId) {
        blacklist.add(playerId);
        members.remove(playerId);
    }
    
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public boolean canInvite(UUID playerId) {
        MemberRole role = getRole(playerId);
        return role == MemberRole.LEADER || role == MemberRole.SENIOR;
    }
    
    public boolean canManage(UUID playerId) {
        return getRole(playerId) == MemberRole.LEADER;
    }
}
