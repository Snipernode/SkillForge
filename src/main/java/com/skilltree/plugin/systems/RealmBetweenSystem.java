package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RealmBetweenSystem {
    private final SkillForgePlugin plugin;
    private final Map<UUID, LockedSkill> lockedSkills;
    private final Map<UUID, RevivalBeacon> activeBeacons;
    
    public RealmBetweenSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.lockedSkills = new HashMap<>();
        this.activeBeacons = new HashMap<>();
    }
    
    public boolean createRevivalBeacon(Player creator, String targetPlayerName, String skillName) {
        // Verify player has the skill
        if (!plugin.getSkillTreeSystem().hasSkill(creator, skillName)) {
            creator.sendMessage("§cYou don't have the skill " + skillName + "!");
            return false;
        }
        
        // Check if skill is already locked
        if (isSkillLocked(creator, skillName)) {
            creator.sendMessage("§cThis skill is already locked!");
            return false;
        }
        
        // Create revival beacon
        RevivalBeacon beacon = new RevivalBeacon(creator, targetPlayerName, skillName);
        activeBeacons.put(creator.getUniqueId(), beacon);
        
        // Lock the skill
        lockSkill(creator, skillName);
        
        // Start beacon timeout
        startBeaconTimeout(beacon);
        
        creator.sendMessage("§aRevival beacon created! Skill " + skillName + " has been locked.");
        return true;
    }
    
    private void lockSkill(Player player, String skillName) {
        UUID playerId = player.getUniqueId();
        lockedSkills.put(playerId, new LockedSkill(skillName, System.currentTimeMillis()));
        
        // Lock the skill in SkillTreeSystem
        plugin.getSkillTreeSystem().lockSkill(player, skillName);
    }
    
    public boolean isSkillLocked(Player player, String skillName) {
        UUID playerId = player.getUniqueId();
        LockedSkill locked = lockedSkills.get(playerId);
        return locked != null && locked.getSkillName().equals(skillName);
    }
    
    public boolean unlockSkill(Player admin, Player target) {
        if (!admin.hasPermission("skillforge.admin.unlock")) {
            admin.sendMessage("§cYou don't have permission to unlock skills!");
            return false;
        }
        
        UUID targetId = target.getUniqueId();
        LockedSkill locked = lockedSkills.get(targetId);
        if (locked == null) {
            admin.sendMessage("§cThis player has no locked skills!");
            return false;
        }
        
        // Unlock the skill
        plugin.getSkillTreeSystem().unlockSkill(target, locked.getSkillName());
        lockedSkills.remove(targetId);
        
        admin.sendMessage("§aSuccessfully unlocked skill " + locked.getSkillName() + " for " + target.getName());
        target.sendMessage("§aYour skill " + locked.getSkillName() + " has been unlocked!");
        return true;
    }
    
    private void startBeaconTimeout(RevivalBeacon beacon) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBeacons.containsKey(beacon.getCreatorId())) {
                    activeBeacons.remove(beacon.getCreatorId());
                    plugin.getServer().getPlayer(beacon.getCreatorId())
                        .sendMessage("§cYour revival beacon has expired!");
                }
            }
        }.runTaskLater(plugin, 20L * 60L * 10); // 10 minutes
    }
    
    private static class LockedSkill {
        private final String skillName;
        private final long lockTime;
        
        public LockedSkill(String skillName, long lockTime) {
            this.skillName = skillName;
            this.lockTime = lockTime;
        }
        
        public String getSkillName() { return skillName; }
        public long getLockTime() { return lockTime; }
    }
    
    private static class RevivalBeacon {
        private final UUID creatorId;
        private final String targetPlayerName;
        private final String lockedSkill;
        private final long creationTime;
        
        public RevivalBeacon(Player creator, String targetPlayerName, String lockedSkill) {
            this.creatorId = creator.getUniqueId();
            this.targetPlayerName = targetPlayerName;
            this.lockedSkill = lockedSkill;
            this.creationTime = System.currentTimeMillis();
        }
        
        public UUID getCreatorId() { return creatorId; }
        public String getTargetPlayerName() { return targetPlayerName; }
        public String getLockedSkill() { return lockedSkill; }
        public long getCreationTime() { return creationTime; }
    }
}
