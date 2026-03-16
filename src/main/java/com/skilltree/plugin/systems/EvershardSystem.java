package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Wither;
import org.bukkit.entity.ElderGuardian;

public class EvershardSystem {
    
    private final SkillForgePlugin plugin;
    private final int xpToEsRatio;
    
    public EvershardSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.xpToEsRatio = plugin.getConfig().getInt("evershards.xp-to-es-ratio", 5);
    }
    
    public void grantEvershardsFromXP(Player player, int xpAmount) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        int totalXp = data.getXpRemainder() + xpAmount;
        int estoGrant = totalXp / xpToEsRatio;
        int newRemainder = totalXp % xpToEsRatio;
        
        if (estoGrant > 0) {
            data.addEvershards(estoGrant);
        }
        data.setXpRemainder(newRemainder);
    }
    
    public void grantBonusESFromMobKill(Player player, Entity killedEntity) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        int bonusES = 5;
        
        if (killedEntity instanceof EnderDragon || killedEntity instanceof Wither || killedEntity instanceof ElderGuardian) {
            bonusES = 100;
        } else if (killedEntity instanceof Boss) {
            bonusES = 50;
        } else {
            String entityType = killedEntity.getType().toString();
            if (entityType.contains("GUARDIAN")) {
                bonusES = 25;
            } else if (entityType.contains("BLAZE") || entityType.contains("GHAST") || entityType.contains("WARDEN")) {
                bonusES = 30;
            } else if (entityType.contains("SKELETON") || entityType.contains("ZOMBIE") || entityType.contains("CREEPER") || entityType.contains("ENDERMAN")) {
                bonusES = 5;
            } else if (entityType.contains("SPIDER") || entityType.contains("CAVE_SPIDER")) {
                bonusES = 5;
            } else if (entityType.contains("WITCH")) {
                bonusES = 10;
            } else if (entityType.contains("PIGLIN") || entityType.contains("ZOMBIFIED")) {
                bonusES = 5000;
            }
        }
        
        data.addEvershards(bonusES);
    }
    
    public int calculateSkillPointCost(PlayerData data) {
        int totalPurchased = data.getTotalSkillsPurchased();
        int baseCost = plugin.getConfig().getInt("skillpoints.base-cost", 5);
        int level10Cost = plugin.getConfig().getInt("skillpoints.level-10-cost", 25);
        
        if (totalPurchased <= 9) {
            if (totalPurchased == 0) return baseCost;
            if (totalPurchased == 9) return level10Cost;
            double increment = (level10Cost - baseCost) / 9.0;
            return baseCost + (int)(increment * totalPurchased);
        } else {
            int postTenIncrement = (level10Cost - baseCost) / 9;
            if (postTenIncrement == 0) postTenIncrement = 2;
            return level10Cost + (postTenIncrement * (totalPurchased - 9));
        }
    }
    
    public boolean purchaseSkillPoint(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int cost = calculateSkillPointCost(data);
        
        if (data.removeEvershards(cost)) {
            data.addSkillPoints(getSkillPointGain(data, 1));
            data.incrementTotalSkillsPurchased();
            return true;
        }
        return false;
    }

    public int getSkillPointGain(PlayerData data, int baseAmount) {
        if (baseAmount <= 0) return 0;
        double multiplier = 1.0;
        if (data != null && data.getGamemode() != null) {
            if (data.getGamemode() == PlayerData.Gamemode.PROTAGONIST) {
                multiplier = plugin.getConfig().getDouble("gamemodes.protagonist-sp-multiplier", 2.0);
            } else if (data.getGamemode() == PlayerData.Gamemode.THE_END) {
                multiplier = plugin.getConfig().getDouble("gamemodes.the-end-sp-multiplier", 3.0);
            }
        }
        multiplier = Math.max(1.0, multiplier);
        return Math.max(1, (int) Math.round(baseAmount * multiplier));
    }
}
