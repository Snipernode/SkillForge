package com.skilltree.plugin.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.skilltree.plugin.SkillForgePlugin;

public class PlayerData {
    
    public enum Gamemode {
        NONE,
        CASUAL,
        ROGUELITE,
        ROGUELIKE,
        HARDCORE,
        PROTAGONIST,
        THE_END
    }
    
    private final UUID playerId;
    private int evershards;
    private int skillPoints;
    private int xpRemainder;
    private Map<String, Integer> skillLevels;
    private Set<String> unlockedCosmetics;
    private String activeCosmetic;
    private int totalSkillsPurchased;
    
    private boolean nekrotic;
    private int nekrosisDay;
    private int deathPoints;
    
    private Gamemode gamemode;
    private double stamina;
    private double maxStamina;
    private double thirst;
    private double maxThirst;
    
    private Map<Integer, String> abilitySlots;
    private Map<String, Long> abilityCooldowns;
    
    private String innateSkillId1;
    private int innateSkillLevel;
    private long innateSkillCooldown;
    
    private Map<String, Integer> questProgress;
    private Map<String, Integer> masteryPoints;
    
    // Isekai item data
    private String isekaiItemId;
    private String isekaiItemType;
    private List<String> isekaiAbilities;
    private int isekaiItemLevel;
    private long isekaiLastSeenAt;
    
    private long playtimeMillis;
    private long lastLoginTime;
    private String lastKnownName;
    private int debateRating;
    private int debateWins;
    private int debateLosses;
    private int debateCurrentStreak;
    private int debateBestStreak;
    private long debateLastAt;

    // New fields for Gamemode Logic
    private String kingdom;
    private boolean hardcoreDead;
    
    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        this.evershards = 0;
        this.skillPoints = 0;
        this.xpRemainder = 0;
        this.skillLevels = new HashMap<>();
        this.unlockedCosmetics = new HashSet<>();
        this.activeCosmetic = null;
        this.totalSkillsPurchased = 0;
        this.nekrotic = false;
        this.nekrosisDay = 0;
        this.deathPoints = 0;
        this.gamemode = Gamemode.NONE;
        this.maxStamina = 100.0;
        this.stamina = 100.0;
        this.maxThirst = 100.0;
        this.thirst = 100.0;
        this.abilitySlots = new HashMap<>();
        this.abilityCooldowns = new HashMap<>();
        this.innateSkillId1 = null;
        this.innateSkillLevel = 0;
        this.innateSkillCooldown = 0;
        this.questProgress = new HashMap<>();
        this.masteryPoints = new HashMap<>();
        this.isekaiItemId = null;
        this.isekaiItemType = null;
        this.isekaiAbilities = new ArrayList<>();
        this.isekaiItemLevel = 1;
        this.isekaiLastSeenAt = 0L;
        this.playtimeMillis = 0;
        this.lastLoginTime = 0;
        this.lastKnownName = null;
        this.debateRating = 0;
        this.debateWins = 0;
        this.debateLosses = 0;
        this.debateCurrentStreak = 0;
        this.debateBestStreak = 0;
        this.debateLastAt = 0L;
        this.kingdom = null;
        this.hardcoreDead = false;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public int getEvershards() {
        return evershards;
    }
    
    public void setEvershards(int evershards) {
        this.evershards = Math.max(0, evershards);
    }
    
    public void addEvershards(int amount) {
        this.evershards += amount;
    }
    
    public boolean removeEvershards(int amount) {
        if (evershards >= amount) {
            evershards -= amount;
            return true;
        }
        return false;
    }
    
    public int getSkillPoints() {
        return skillPoints;
    }
    
    public void setSkillPoints(int skillPoints) {
        this.skillPoints = Math.max(0, skillPoints);
    }
    
    public void addSkillPoints(int amount) {
        this.skillPoints += amount;
    }
    
    public boolean removeSkillPoints(int amount) {
        if (skillPoints >= amount) {
            skillPoints -= amount;
            return true;
        }
        return false;
    }
    
    public int getSkillLevel(String skillName) {
        return skillLevels.getOrDefault(skillName, 0);
    }
    
    public void setSkillLevel(String skillName, int level) {
        skillLevels.put(skillName, level);
    }
    
    public void incrementSkillLevel(String skillName) {
        skillLevels.put(skillName, getSkillLevel(skillName) + 1);
    }
    
    public Map<String, Integer> getAllSkillLevels() {
        return new HashMap<>(skillLevels);
    }
    
    // FIX: Added method to properly clear the internal map
    public void clearAllSkills() {
        skillLevels.clear();
    }
    
    public boolean hasCosmeticUnlocked(String cosmeticId) {
        return unlockedCosmetics.contains(cosmeticId);
    }
    
    public void unlockCosmetic(String cosmeticId) {
        unlockedCosmetics.add(cosmeticId);
    }
    
    public Set<String> getUnlockedCosmetics() {
        return new HashSet<>(unlockedCosmetics);
    }
    
    public int getTotalSkillsPurchased() {
        return totalSkillsPurchased;
    }
    
    public void incrementTotalSkillsPurchased() {
        this.totalSkillsPurchased++;
    }
    
    public void setTotalSkillsPurchased(int count) {
        this.totalSkillsPurchased = count;
    }
    
    public int getXpRemainder() {
        return xpRemainder;
    }
    
    public void setXpRemainder(int xpRemainder) {
        this.xpRemainder = Math.max(0, xpRemainder);
    }
    
    public void addXpRemainder(int amount) {
        this.xpRemainder += amount;
    }
    
    public Gamemode getGamemode() {
        return gamemode;
    }
    
    public void setGamemode(Gamemode gamemode) {
        this.gamemode = gamemode;
    }
    
    public boolean hasSelectedGamemode() {
        return gamemode != Gamemode.NONE;
    }
    
    public double getStamina() {
        return stamina;
    }
    
    public void setStamina(double stamina) {
        this.stamina = Math.max(0, Math.min(stamina, maxStamina));
    }
    
    public void drainStamina(double amount) {
        this.stamina = Math.max(0, this.stamina - amount);
    }
    
    public void regenStamina(double amount) {
        this.stamina = Math.min(maxStamina, this.stamina + amount);
    }
    
    public double getMaxStamina() {
        return maxStamina;
    }
    
    public void setMaxStamina(double maxStamina) {
        this.maxStamina = maxStamina;
        if (this.stamina > maxStamina) {
            this.stamina = maxStamina;
        }
    }
    
    public double getThirst() {
        return thirst;
    }
    
    public void setThirst(double thirst) {
        this.thirst = Math.max(0, Math.min(thirst, maxThirst));
    }
    
    public void drainThirst(double amount) {
        this.thirst = Math.max(0, this.thirst - amount);
    }
    
    public void restoreThirst(double amount) {
        this.thirst = Math.min(maxThirst, this.thirst + amount);
    }
    
    public double getMaxThirst() {
        return maxThirst;
    }
    
    public void setMaxThirst(double maxThirst) {
        this.maxThirst = maxThirst;
        if (this.thirst > maxThirst) {
            this.thirst = maxThirst;
        }
    }
    
    public String getActiveCosmetic() {
        return activeCosmetic;
    }
    
    public void setActiveCosmetic(String cosmeticId) {
        if (cosmeticId != null && !hasCosmeticUnlocked(cosmeticId)) {
            return;
        }
        this.activeCosmetic = cosmeticId;
    }
    
        // Add this field to PlayerData class
    private String innateSkillId;
    
    // ... existing fields ...

    // Add getter and setter
    public String getInnateSkillId1() {
        return innateSkillId1;
    }

    public void setInnateSkillId1(String skillId) {
        this.innateSkillId1 = skillId;
    }

    public void recalculateStaminaAndThirst(double maxStaminaConfig, double maxThirstConfig) {
        int agilityTotal = getSkillLevel("agility_speed") + getSkillLevel("agility_dodge") + 
                           getSkillLevel("agility_jump") + getSkillLevel("agility_acrobatics") + 
                           getSkillLevel("agility_parkour") + getSkillLevel("agility_climbing");
        
        int intellectTotal = getSkillLevel("intellect_wisdom") + getSkillLevel("intellect_enchanting") + 
                             getSkillLevel("intellect_brewing") + getSkillLevel("intellect_arcana") + 
                             getSkillLevel("intellect_alchemy") + getSkillLevel("intellect_scholarship");
        
        double staminaScale = 1.0 + (agilityTotal * 0.05);
        double thirstScale = 1.0 + (intellectTotal * 0.05);
        
        double newMaxStamina = 100.0 * staminaScale;
        double newMaxThirst = 100.0 * thirstScale;
        
        newMaxStamina = Math.min(newMaxStamina, maxStaminaConfig);
        newMaxThirst = Math.min(newMaxThirst, maxThirstConfig);
        
        setMaxStamina(newMaxStamina);
        setMaxThirst(newMaxThirst);
    }
    
    public void bindAbility(int slot, String skillId) {
        abilitySlots.put(slot, skillId);
    }
    
    public String getAbilityAtSlot(int slot) {
        return abilitySlots.getOrDefault(slot, null);
    }
    
    public void unbindAbility(int slot) {
        abilitySlots.remove(slot);
    }
    
    public Map<Integer, String> getAllAbilityBindings() {
        return new HashMap<>(abilitySlots);
    }
    
    public boolean isAbilityOnCooldown(String skillId) {
        Long cooldownUntil = abilityCooldowns.getOrDefault(skillId, 0L);
        return System.currentTimeMillis() < cooldownUntil;
    }
    
    public void setAbilityCooldown(String skillId, long durationMillis) {
        abilityCooldowns.put(skillId, System.currentTimeMillis() + durationMillis);
    }
    
    public long getAbilityCooldownRemaining(String skillId) {
        Long cooldownUntil = abilityCooldowns.getOrDefault(skillId, 0L);
        long remaining = cooldownUntil - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    public String getInnateSkillId() {
        return innateSkillId1;
    }
    
    public void setInnateSkillId(String skillId) {
        this.innateSkillId1 = skillId;
    }
    
    public int getInnateSkillLevel() {
        return innateSkillLevel;
    }
    
    public void setInnateSkillLevel(int level) {
        this.innateSkillLevel = Math.max(0, level);
    }
    
    public void incrementInnateSkillLevel() {
        this.innateSkillLevel++;
    }
    
    public boolean isInnateSkillOnCooldown() {
        return System.currentTimeMillis() < innateSkillCooldown;
    }
    
    public void setInnateSkillCooldown(long durationMillis) {
        this.innateSkillCooldown = System.currentTimeMillis() + durationMillis;
    }
    
    public long getInnateSkillCooldownRemaining() {
        long remaining = innateSkillCooldown - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    public void addQuestProgress(String questId, int amount) {
        int current = questProgress.getOrDefault(questId.toLowerCase(), 0);
        questProgress.put(questId.toLowerCase(), current + amount);
    }
    
    public int getQuestProgress(String questId) {
        return questProgress.getOrDefault(questId.toLowerCase(), 0);
    }
    
    public void setQuestProgress(String questId, int progress) {
        questProgress.put(questId.toLowerCase(), Math.max(0, progress));
    }
    
    public Map<String, Integer> getAllQuestProgress() {
        return new HashMap<>(questProgress);
    }

    public int getMasteryPoints(String masteryId) {
        if (masteryId == null) return 0;
        return masteryPoints.getOrDefault(masteryId, 0);
    }

    public void setMasteryPoints(String masteryId, int points) {
        if (masteryId == null) return;
        masteryPoints.put(masteryId, Math.max(0, points));
    }

    public void addMasteryPoints(String masteryId, int amount) {
        if (masteryId == null) return;
        masteryPoints.put(masteryId, Math.max(0, getMasteryPoints(masteryId) + amount));
    }

    public Map<String, Integer> getAllMasteryPoints() {
        return new HashMap<>(masteryPoints);
    }
    
    public String getIsekaiItemId() {
        return isekaiItemId;
    }
    
    public void setIsekaiItemId(String isekaiItemId) {
        this.isekaiItemId = isekaiItemId;
    }
    
    public String getIsekaiItemType() {
        return isekaiItemType;
    }
    
    public void setIsekaiItemType(String isekaiItemType) {
        this.isekaiItemType = isekaiItemType;
    }
    
    public List<String> getIsekaiAbilities() {
        return new ArrayList<>(isekaiAbilities);
    }
    
    public void setIsekaiAbilities(List<String> abilities) {
        this.isekaiAbilities = abilities == null ? new ArrayList<>() : new ArrayList<>(abilities);
    }
    
    public int getIsekaiItemLevel() {
        return isekaiItemLevel;
    }
    
    public void setIsekaiItemLevel(int isekaiItemLevel) {
        this.isekaiItemLevel = Math.max(1, isekaiItemLevel);
    }
    
    public long getIsekaiLastSeenAt() {
        return isekaiLastSeenAt;
    }
    
    public void setIsekaiLastSeenAt(long isekaiLastSeenAt) {
        this.isekaiLastSeenAt = Math.max(0L, isekaiLastSeenAt);
    }
    
    public boolean hasIsekaiItem() {
        return isekaiItemId != null && !isekaiItemId.isEmpty();
    }

    public long getPlaytimeMillis() {
        return playtimeMillis;
    }
    
    public void setPlaytimeMillis(long millis) {
        this.playtimeMillis = millis;
    }
    
    public void addPlaytime(long millis) {
        this.playtimeMillis += millis;
    }
    
    public long getLastLoginTime() {
        return lastLoginTime;
    }
    
    public void setLastLoginTime(long time) {
        this.lastLoginTime = time;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        if (lastKnownName == null) {
            this.lastKnownName = null;
            return;
        }
        String clean = lastKnownName.trim();
        this.lastKnownName = clean.isEmpty() ? null : clean;
    }

    public int getDebateRating() {
        return debateRating;
    }

    public void setDebateRating(int debateRating) {
        this.debateRating = Math.max(0, debateRating);
    }

    public void addDebateRating(int amount) {
        this.debateRating = Math.max(0, this.debateRating + amount);
    }

    public int getDebateWins() {
        return debateWins;
    }

    public void setDebateWins(int debateWins) {
        this.debateWins = Math.max(0, debateWins);
    }

    public void incrementDebateWins() {
        this.debateWins++;
    }

    public int getDebateLosses() {
        return debateLosses;
    }

    public void setDebateLosses(int debateLosses) {
        this.debateLosses = Math.max(0, debateLosses);
    }

    public void incrementDebateLosses() {
        this.debateLosses++;
    }

    public int getDebateCurrentStreak() {
        return debateCurrentStreak;
    }

    public void setDebateCurrentStreak(int debateCurrentStreak) {
        this.debateCurrentStreak = Math.max(0, debateCurrentStreak);
        if (this.debateCurrentStreak > this.debateBestStreak) {
            this.debateBestStreak = this.debateCurrentStreak;
        }
    }

    public void incrementDebateCurrentStreak() {
        setDebateCurrentStreak(this.debateCurrentStreak + 1);
    }

    public void resetDebateCurrentStreak() {
        this.debateCurrentStreak = 0;
    }

    public int getDebateBestStreak() {
        return debateBestStreak;
    }

    public void setDebateBestStreak(int debateBestStreak) {
        this.debateBestStreak = Math.max(0, debateBestStreak);
    }

    public long getDebateLastAt() {
        return debateLastAt;
    }

    public void setDebateLastAt(long debateLastAt) {
        this.debateLastAt = Math.max(0L, debateLastAt);
    }

    public boolean isNekrotic() { 
        return nekrotic; 
    }
    
    public void setNekrotic(boolean nekrotic) { 
        this.nekrotic = nekrotic; 
    }
    
    public int getNekrosisDay() { 
        return nekrosisDay; 
    }
    
    public void setNekrosisDay(int day) { 
        this.nekrosisDay = day; 
    }
    
    public int getDeathPoints() { 
        return deathPoints; 
    }
    
    public void setDeathPoints(int points) { 
        this.deathPoints = points; 
    }
    
    public void addDeathPoints(int amount) { 
        this.deathPoints += amount; 
    }

    // New Getters and Setters
    public String getKingdom() {
        return kingdom;
    }

    public void setKingdom(String kingdom) {
        this.kingdom = kingdom;
    }

    public boolean isHardcoreDead() {
        return hardcoreDead;
    }

    public void setHardcoreDead(boolean hardcoreDead) {
        this.hardcoreDead = hardcoreDead;
    }

    /**
     * Creates and returns an Inventory GUI displaying the player's skill levels.
     * 
     * @param plugin The main plugin instance for accessing configs and creating inventories.
     * @return An Inventory populated with the player's skills.
     */
    public Inventory getSkillLevels(SkillForgePlugin plugin) {
        // Create a new inventory with 54 slots (6 rows)
        String title = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Your Skills";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Iterate through all known skills in the config
        // Assuming your skills are defined in config under "skills.items"
        if (plugin.getConfig().contains("skills.items")) {
            for (String skillId : plugin.getConfig().getConfigurationSection("skills.items").getKeys(false)) {
                int level = getSkillLevel(skillId);
                
                // Get the display item for this skill from the config
                // This assumes you have a helper method or logic to get the item stack
                // For this example, we'll create a basic item stack
                ItemStack item = getSkillDisplayItem(plugin, skillId, level);
                
                // Add the item to the inventory
                // Note: In a real implementation, you'd want to calculate specific slots
                // based on categories or a layout, rather than just filling them sequentially.
                inv.addItem(item);
            }
        }
        
        // Fill empty slots with glass panes (optional, for aesthetics)
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }

        return inv;
    }

    /**
     * Helper method to create an ItemStack representing a skill.
     */
    private ItemStack getSkillDisplayItem(SkillForgePlugin plugin, String skillId, int level) {
        // Default fallback item
        Material material = Material.ENCHANTED_BOOK;
        String name = ChatColor.YELLOW + skillId;
        List<String> lore = new ArrayList<>();

        // Try to load details from config
        String path = "skills.items." + skillId;
        if (plugin.getConfig().contains(path)) {
            String matName = plugin.getConfig().getString(path + ".material");
            if (matName != null) {
                try {
                    material = Material.valueOf(matName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Keep default material if config is wrong
                }
            }
            
            String configName = plugin.getConfig().getString(path + ".name");
            if (configName != null) {
                // FIX: Corrected syntax error here (&' -> &)
                name = ChatColor.translateAlternateColorCodes('&', configName);
            }
        }

        // Add level to lore
        lore.add("");
        lore.add(ChatColor.GRAY + "Current Level: " + ChatColor.GREEN + level);
        lore.add(ChatColor.GRAY + "Next Level: " + ChatColor.YELLOW + (level + 1));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }
}
