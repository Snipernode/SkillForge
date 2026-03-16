package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.SkillTreeSystem.SkillNode;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GuildCardSystem {
    
    private final SkillForgePlugin plugin;
    private final PlaystyleCalculator playstyleCalculator;
    
    public GuildCardSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.playstyleCalculator = new PlaystyleCalculator(plugin);
    }
    
    /**
     * Create and give a guild card to a player
     * Card is a name tag with player head and skill bars
     */
    public void giveGuildCard(Player player) {
        ItemStack card = createGuildCard(player);
        player.getInventory().addItem(card);
        player.sendMessage(ChatColor.GOLD + "📇 Guild Card created!");
    }
    
    /**
     * Create a guild card item with player head and skill bars
     */
    private ItemStack createGuildCard(Player player) {
        ItemStack card = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = card.getItemMeta();
        
        if (meta == null) {
            meta = plugin.getServer().getItemFactory().getItemMeta(Material.NAME_TAG);
        }
        
        // Set display name to player name
        meta.setDisplayName(ChatColor.GOLD + player.getName() + "'s Guild Card");
        
        // Get player data for skill info
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        // Create lore with player head indicator and skill bars
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.LIGHT_PURPLE + "👤 " + player.getName());
        lore.add(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━");
        
        // Add Playstyle (Class)
        addPlaystyleInfo(data, lore);
        
        // Add skill category bars
        lore.add("");
        lore.add(ChatColor.RED + "Combat: " + getSkillBar(data, "combat"));
        lore.add(ChatColor.YELLOW + "Mining: " + getSkillBar(data, "mining"));
        lore.add(ChatColor.AQUA + "Agility: " + getSkillBar(data, "agility"));
        lore.add(ChatColor.GREEN + "Intellect: " + getSkillBar(data, "intellect"));
        lore.add(ChatColor.DARK_GREEN + "Farming: " + getSkillBar(data, "farming"));
        lore.add(ChatColor.BLUE + "Fishing: " + getSkillBar(data, "fishing"));
        lore.add(ChatColor.LIGHT_PURPLE + "Magic: " + getSkillBar(data, "magic"));
        lore.add(ChatColor.DARK_GRAY + "Mastery: " + getSkillBar(data, "mastery"));
        
        // Add everpoints and innate info
        lore.add("");
        lore.add(ChatColor.GOLD + "💰 Evershards: " + ChatColor.YELLOW + data.getEvershards());
        if (data.getInnateSkillId() != null) {
            lore.add(ChatColor.LIGHT_PURPLE + "✨ Innate Skill Lvl: " + ChatColor.YELLOW + data.getInnateSkillLevel());
        }
        
        lore.add("");
        lore.add(ChatColor.GRAY + "Player Bound Guild Card");
        
        meta.setLore(lore);
        card.setItemMeta(meta);
        
        // Set custom model data for identification
        meta.setCustomModelData(19191);
        card.setItemMeta(meta);
        
        return card;
    }
    
    /**
     * Adds the calculated playstyle to the lore list.
     */
    private void addPlaystyleInfo(PlayerData targetData, List<String> lore) {
        if (targetData == null || lore == null) return;

        // Get calculated playstyle
        String playstyle = playstyleCalculator.determinePlaystyle(targetData);
        
        // Add playstyle line to the lore
        lore.add(ChatColor.WHITE + "Class: " + ChatColor.GOLD + playstyle);
    }
    
    /**
     * Create a visual skill bar (10 blocks, filled based on level)
     */
    private String getSkillBar(PlayerData data, String category) {
        int totalSkills = getTotalSkillsInCategory(category);
        // Avoid division by zero if category is empty
        if (totalSkills == 0) return ChatColor.DARK_GRAY + "[No Skills]";
        
        int acquiredSkills = countAcquiredSkillsInCategory(data, category);
        
        int filledBlocks = (int) Math.ceil((acquiredSkills / (double) totalSkills) * 10);
        filledBlocks = Math.min(filledBlocks, 10);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filledBlocks) {
                bar.append(ChatColor.GREEN).append("▰");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("▱");
            }
        }
        
        bar.append(ChatColor.GRAY).append(" ").append(acquiredSkills).append("/").append(totalSkills);
        return bar.toString();
    }
    
    /**
     * Dynamically calculates total number of skills in a category
     * based on currently loaded skillNodes map.
     */
    private int getTotalSkillsInCategory(String category) {
        if (category == null) return 0;
        
        String targetCategory = category.toLowerCase();
        int count = 0;
        
        // Iterate through all loaded nodes
        for (SkillNode node : plugin.getSkillTreeSystem().values()) {
            if (node.getCategory().equalsIgnoreCase(targetCategory)) {
                count++;
            }
        }
        
        return count;
    }

    private int countAcquiredSkillsInCategory(PlayerData data, String category) {
        int count = 0;
        String[] skillIds = getSkillIdsForCategory(category);
        
        if (skillIds != null) {
            for (String skillId : skillIds) {
                if (data.getSkillLevel(skillId) > 0) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Returns a list of skill IDs for a specific category.
     * Updated to match the SkillRegistry implementation.
     */
    private String[] getSkillIdsForCategory(String category) {
        return switch(category) {
            case "combat" -> new String[]{
                "combat_whirlwind", "combat_laststand", "combat_execute", "combat_shieldbash",
                "combat_battlecry", "combat_charge", "berserker_bloodrage", "berserker_rage",
                "berserker_shout", "paladin_holylight", "paladin_divineshield", "paladin_hammer",
                "defender_taunt", "defender_shieldwall", "defender_lastgasp", "duelist_parry",
                "duelist_riposte", "duelist_precision"
            };
            case "mining" -> new String[]{
                "mining_veinminer", "mining_seismic_strike", "mining_fortune_strike"
            };
            case "agility" -> new String[]{
                "agility_shadowstep", "agility_adrenaline", "agility_dash", "agility_evasion",
                "agility_smokebomb", "agility_dodge", "agility_climb", "agility_parkour",
                "agility_acrobatics", "agility_grapple", "agility_stealth", "agility_mirrorimage",
                "agility_phantom_step", "agility_whirlwind_slice"
            };
            case "intellect" -> new String[]{
                "intellect_frostbolt", "intellect_mindblast", "intellect_mindshield", "intellect_amplify"
            };
            case "farming" -> new String[]{
                "farming_bountyharvest"
            };
            case "fishing" -> new String[]{
                "fishing_deeptreasure", "fish_grapple", "fish_harpoon"
            };
            case "magic" -> new String[]{
                "magic_frostbolt", "magic_meteor", "magic_frostmova", "magic_chainlightning"
            };
            case "mastery" -> new String[]{
                "archery_multishot", "archery_venomous_arrow", "archery_ice_arrow", "archery_fire_arrow",
                "archery_volley", "archery_piercing_shot", "archery_kinetic_shot", "archery_rain_of_arrows"
            };
            default -> new String[]{};
        };
    }
}
