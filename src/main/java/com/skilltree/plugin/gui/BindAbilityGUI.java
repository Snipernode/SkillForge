package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.SkillTreeSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BindAbilityGUI implements Listener {
    
    private final SkillForgePlugin plugin;
    private static final int CHEST_SIZE = 54;
    private static final Map<String, Material> SKILL_MATERIALS = new HashMap<>();
    private static final Map<UUID, String> selectedSkills = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    
    static {
        // --- COMBAT ---
        SKILL_MATERIALS.put("combat_whirlwind", Material.DIAMOND_SWORD);
        SKILL_MATERIALS.put("combat_laststand", Material.SHIELD);
        SKILL_MATERIALS.put("combat_execute", Material.IRON_AXE);
        SKILL_MATERIALS.put("combat_shieldbash", Material.IRON_SHOVEL);
        SKILL_MATERIALS.put("combat_battlecry", Material.DRAGON_HEAD);
        SKILL_MATERIALS.put("combat_charge", Material.LEATHER_BOOTS);
        SKILL_MATERIALS.put("berserker_bloodrage", Material.REDSTONE_BLOCK);
        SKILL_MATERIALS.put("berserker_rage", Material.BLAZE_POWDER);
        SKILL_MATERIALS.put("berserker_shout", Material.WITHER_ROSE);
        SKILL_MATERIALS.put("paladin_holylight", Material.GOLDEN_APPLE);
        SKILL_MATERIALS.put("paladin_divineshield", Material.TOTEM_OF_UNDYING);
        SKILL_MATERIALS.put("paladin_hammer", Material.GOLDEN_HOE);
        SKILL_MATERIALS.put("defender_taunt", Material.IRON_CHESTPLATE);
        SKILL_MATERIALS.put("defender_shieldwall", Material.SHIELD);
        SKILL_MATERIALS.put("defender_lastgasp", Material.SPECTRAL_ARROW);

        // --- DUELIST ---
        SKILL_MATERIALS.put("duelist_parry", Material.IRON_SWORD);
        SKILL_MATERIALS.put("duelist_riposte", Material.GOLDEN_SWORD);
        SKILL_MATERIALS.put("duelist_precision", Material.SPECTRAL_ARROW);

        // --- AGILITY ---
        SKILL_MATERIALS.put("agility_shadowstep", Material.ENDER_PEARL);
        SKILL_MATERIALS.put("agility_adrenaline", Material.SUGAR);
        SKILL_MATERIALS.put("agility_dash", Material.RABBIT_FOOT);
        SKILL_MATERIALS.put("agility_evasion", Material.PHANTOM_MEMBRANE);
        SKILL_MATERIALS.put("agility_smokebomb", Material.GUNPOWDER);
        SKILL_MATERIALS.put("agility_dodge", Material.FEATHER);
        SKILL_MATERIALS.put("agility_climb", Material.LEATHER);
        SKILL_MATERIALS.put("agility_parkour", Material.FEATHER);
        SKILL_MATERIALS.put("agility_acrobatics", Material.RABBIT_HIDE);
        SKILL_MATERIALS.put("agility_grapple", Material.TRIPWIRE_HOOK);
        SKILL_MATERIALS.put("agility_stealth", Material.POTION);
        SKILL_MATERIALS.put("agility_mirrorimage", Material.ARMOR_STAND);
        SKILL_MATERIALS.put("agility_phantom_step", Material.ENDER_EYE);
        SKILL_MATERIALS.put("agility_whirlwind_slice", Material.NETHERITE_SWORD);

        // --- INTELLECT ---
        SKILL_MATERIALS.put("intellect_frostbolt", Material.SNOWBALL);
        SKILL_MATERIALS.put("intellect_mindblast", Material.AMETHYST_SHARD);
        SKILL_MATERIALS.put("intellect_mindshield", Material.LODESTONE);
        SKILL_MATERIALS.put("intellect_amplify", Material.REDSTONE);

        // --- MINING ---
        SKILL_MATERIALS.put("mining_veinminer", Material.DIAMOND_PICKAXE);
        SKILL_MATERIALS.put("mining_seismic_strike", Material.TNT);
        SKILL_MATERIALS.put("mining_fortune_strike", Material.GLOWSTONE_DUST);

        // --- ARCHERY ---
        SKILL_MATERIALS.put("archery_multishot", Material.BOW);
        SKILL_MATERIALS.put("archery_venomous_arrow", Material.SPIDER_EYE);
        SKILL_MATERIALS.put("archery_ice_arrow", Material.PACKED_ICE);
        SKILL_MATERIALS.put("archery_fire_arrow", Material.BLAZE_POWDER);
        SKILL_MATERIALS.put("archery_volley", Material.ARROW);
        SKILL_MATERIALS.put("archery_piercing_shot", Material.SPECTRAL_ARROW);
        SKILL_MATERIALS.put("archery_kinetic_shot", Material.FIREWORK_ROCKET);
        SKILL_MATERIALS.put("archery_rain_of_arrows", Material.CROSSBOW);

        // --- NATURE / HUNTER ---
        SKILL_MATERIALS.put("nature_root", Material.OAK_SAPLING);
        SKILL_MATERIALS.put("nature_regrowth", Material.GOLDEN_CARROT);
        SKILL_MATERIALS.put("summon_wolf", Material.WOLF_SPAWN_EGG);
        SKILL_MATERIALS.put("summon_beastrage", Material.BONE);
        SKILL_MATERIALS.put("hunter_mark", Material.COMPASS);
        SKILL_MATERIALS.put("hunter_bloodhound", Material.COAL);
        SKILL_MATERIALS.put("nature_call_lightning", Material.LIGHTNING_ROD);
        SKILL_MATERIALS.put("summon_bear", Material.POLAR_BEAR_SPAWN_EGG);
        SKILL_MATERIALS.put("nature_earthquake", Material.GRASS_BLOCK);

        // --- UTILITY / MISC ---
        SKILL_MATERIALS.put("util_recall", Material.CAMPFIRE);
        SKILL_MATERIALS.put("util_safefall", Material.HAY_BLOCK);
        SKILL_MATERIALS.put("builder_scaffold", Material.BRICK);
        SKILL_MATERIALS.put("builder_wall", Material.STONE_BRICKS);
        SKILL_MATERIALS.put("chef_feast", Material.COOKED_BEEF);
        SKILL_MATERIALS.put("alchemy_brew", Material.GLASS_BOTTLE);
        SKILL_MATERIALS.put("alchemy_transmute", Material.COBBLESTONE);
        SKILL_MATERIALS.put("fish_grapple", Material.FISHING_ROD);
        SKILL_MATERIALS.put("fish_harpoon", Material.TRIDENT);
        SKILL_MATERIALS.put("bard_speed", Material.NOTE_BLOCK);
        SKILL_MATERIALS.put("bard_healing", Material.GOLDEN_APPLE);
        SKILL_MATERIALS.put("bard_resistance", Material.IRON_INGOT);
        SKILL_MATERIALS.put("bard_discord", Material.MUSIC_DISC_11);

        // --- MAGIC ---
        SKILL_MATERIALS.put("magic_meteor", Material.MAGMA_BLOCK);
        SKILL_MATERIALS.put("magic_frostmova", Material.BLUE_ICE);
        SKILL_MATERIALS.put("magic_chainlightning", Material.LIGHTNING_ROD);

        // --- BRICK (PLAYSTYLE) ---
        SKILL_MATERIALS.put("mastery_brick_bulwark", Material.BRICKS);
        SKILL_MATERIALS.put("mastery_brick_brawler", Material.CRACKED_STONE_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_duelist", Material.CHISELED_STONE_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_ranger", Material.MOSSY_STONE_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_mage", Material.POLISHED_DEEPSLATE);
        SKILL_MATERIALS.put("mastery_brick_harvester", Material.MUD_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_miner", Material.DEEPSLATE_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_explorer", Material.NETHER_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_support", Material.PRISMARINE_BRICKS);
        SKILL_MATERIALS.put("mastery_brick_controller", Material.RED_NETHER_BRICKS);

        // --- FARMING ---
        SKILL_MATERIALS.put("farming_bountyharvest", Material.GOLDEN_HOE);

        // --- FISHING ---
        SKILL_MATERIALS.put("fishing_deeptreasure", Material.FISHING_ROD);

        // --- UI ELEMENTS ---
        SKILL_MATERIALS.put("more_flare", Material.BLAZE_ROD);
    }
    
    public BindAbilityGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void openForPlayer(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        Inventory inv = Bukkit.createInventory(null, CHEST_SIZE, GuiStyle.title("Bind Skills"));
        
        Map<Integer, String> bindings = data.getAllAbilityBindings();
        List<String> bindableSkills = getBindableSkills();
        
        // Add skill items
        int slotIndex = 0;
        for (String skillId : bindableSkills) {
            if (slotIndex >= CHEST_SIZE - 10) break; // Reserve last row for slots + More Flare
            
            // Skip "more_flare" for the main list, we handle it separately
            if (skillId.equals("more_flare")) continue;

            SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
            
            // Check if player has unlocked the skill (Level > 0)
            boolean isUnlocked = data.getSkillLevel(skillId) > 0;
            
            ItemStack skillItem;
            ItemMeta meta;
            
            if (isUnlocked) {
                // Show actual skill
                Material material = getMaterialForSkill(skillId);
                skillItem = new ItemStack(material);
                meta = skillItem.getItemMeta();
                if (meta != null) {
                    String name = node != null ? node.getName() : skillId;
                    String desc = node != null ? node.getDescription() : "No description available.";
                    meta.setDisplayName(ChatColor.GOLD + name);
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + desc);
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Left-click to select");
                    lore.add(ChatColor.YELLOW + "Click a slot below to bind");
                    meta.setLore(lore);
                    skillItem.setItemMeta(meta);
                }
            } else {
                // Show locked skill
                skillItem = new ItemStack(Material.BRICKS);
                meta = skillItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.DARK_GRAY + "Locked Brick");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.RED + "Locked");
                    lore.add(ChatColor.GRAY + "Unlock this skill in the Skill Tree.");
                    meta.setLore(lore);
                    skillItem.setItemMeta(meta);
                }
            }
            
            inv.setItem(slotIndex, skillItem);
            slotIndex++;
        }
        
        // Add binding slots (last 9 slots)
        int startSlot = CHEST_SIZE - 9;
        for (int i = 1; i <= 9; i++) {
            String boundAbility = bindings.getOrDefault(i, null);
            ItemStack slot = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = slot.getItemMeta();
            
            if (boundAbility != null) {
                slot.setType(Material.LIME_STAINED_GLASS_PANE);
                SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(boundAbility);
                String skillName = node != null ? node.getName() : boundAbility;
                meta.setDisplayName(ChatColor.GREEN + "Slot " + i + ": " + skillName);
            } else {
                meta.setDisplayName(ChatColor.GRAY + "Slot " + i + " (Empty)");
            }
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Click to bind selected skill");
            lore.add(ChatColor.YELLOW + "Right-click to unbind");
            meta.setLore(lore);
            slot.setItemMeta(meta);
            inv.setItem(startSlot + i - 1, slot);
        }

        // Add "More Flare" item
        ItemStack flareItem = new ItemStack(Material.BLAZE_ROD);
        ItemMeta flareMeta = flareItem.getItemMeta();
        if (flareMeta != null) {
            flareMeta.setDisplayName(ChatColor.GOLD + "More Flare");
            List<String> flareLore = new ArrayList<>();
            flareLore.add(ChatColor.GRAY + "Refresh the bindings list");
            flareMeta.setLore(flareLore);
            flareItem.setItemMeta(flareMeta);
        }
        // Place it in the slot before the binding slots
        inv.setItem(CHEST_SIZE - 10, flareItem);

        // Header + background
        ItemStack header = GuiIcons.icon(plugin, "bind", Material.BOOK, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Rune Binding Table",
                List.of(ChatColor.GRAY + "Select a skill, then bind it below.",
                        ChatColor.DARK_GRAY + "Right-click a slot to clear."));
        inv.setItem(4, header);
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());

        trackInventory(player, inv);
        player.openInventory(inv);
    }
    
    private List<String> getBindableSkills() {
        Set<String> bindable = new LinkedHashSet<>();
        bindable.add("more_flare");

        try {
            com.skilltree.plugin.systems.SkillRegistry registry = plugin.getSkillRegistry();
            if (registry != null) {
                for (String id : registry.getRegisteredSkillIds()) {
                    if (id == null || id.trim().isEmpty()) continue;
                    bindable.add(id);
                }
            }
        } catch (Throwable ignored) {}

        Map<String, SkillTreeSystem.SkillNode> allNodes = Collections.emptyMap();
        if (plugin.getSkillTreeSystem() != null) {
            allNodes = plugin.getSkillTreeSystem().getAllSkillNodes();
            for (String id : allNodes.keySet()) {
                if (id == null || id.trim().isEmpty()) continue;
                bindable.add(id);
            }
        }

        List<String> sorted = new ArrayList<>(bindable);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private Material getMaterialForSkill(String skillId) {
        if (skillId == null) return Material.PAPER;
        Material m = SKILL_MATERIALS.get(skillId);
        if (m != null) return m;
        try {
            SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
            if (node != null) {
                String cat = node.getCategory();
                if (cat != null) {
                    String c = cat.toLowerCase();
                    if (c.contains("combat")) return Material.IRON_SWORD;
                    if (c.contains("agility")) return Material.FEATHER;
                    if (c.contains("magic") || c.contains("intellect")) return Material.ENCHANTED_BOOK;
                    if (c.contains("mining")) return Material.DIAMOND_PICKAXE;
                    if (c.contains("farming")) return Material.GOLDEN_HOE;
                    if (c.contains("fishing")) return Material.FISHING_ROD;
                    if (c.contains("mastery")) return Material.NETHERITE_SWORD;
                }
            }
        } catch (Throwable ignored) {}
        return Material.PAPER;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;

        event.setCancelled(true);
        safeRestoreSlot(event, player);
        UUID playerUuid = player.getUniqueId();
        int slot = event.getSlot();
        int startSlot = CHEST_SIZE - 9;

        if (slot < startSlot) {
            ItemStack clickedItem = event.getInventory().getItem(slot);
            if (clickedItem != null && clickedItem.hasItemMeta()) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String itemName = ChatColor.stripColor(meta.getDisplayName());
                    if (itemName.equals("More Flare")) {
                        openForPlayer(player);
                        return;
                    }
                    if (clickedItem.getType() == Material.BRICKS) {
                        player.sendMessage(ChatColor.RED + "You have not unlocked this skill yet!");
                        return;
                    }

                    String skillId = findSkillIdByName(itemName);
                    if (skillId != null) {
                        selectedSkills.put(playerUuid, skillId);
                        player.sendMessage(ChatColor.GOLD + "Selected: " + itemName + ChatColor.GRAY + " (Click a slot to bind)");
                    }
                }
            }
            return;
        }

        if (slot >= startSlot && slot < CHEST_SIZE) {
            int bindSlot = slot - startSlot + 1;
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

            if (event.isRightClick()) {
                data.unbindAbility(bindSlot);
                player.sendMessage(ChatColor.YELLOW + "Unbound slot " + bindSlot);
            } else {
                String selectedSkill = selectedSkills.get(playerUuid);
                if (selectedSkill != null) {
                    SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(selectedSkill);
                    String skillName = node != null ? node.getName() : selectedSkill;
                    data.bindAbility(bindSlot, selectedSkill);
                    player.sendMessage(ChatColor.GREEN + "Bound " + skillName + " to slot " + bindSlot);
                } else {
                    player.sendMessage(ChatColor.RED + "Select a skill first!");
                }
            }

            openForPlayer(player);
        }
    }

    private void safeRestoreSlot(InventoryClickEvent event, Player player) {
        try {
            Inventory top = event.getView().getTopInventory();
            int raw = event.getRawSlot();
            if (raw >= 0 && raw < top.getSize()) {
                ItemStack curr = event.getCurrentItem();
                top.setItem(raw, curr);
            }
        } catch (Throwable ignored) {}
        player.setItemOnCursor(null);
    }
    
    private void trackInventory(Player player, Inventory inv) {
        if (player == null || inv == null) return;
        openInventories.put(player.getUniqueId(), inv);
    }

    private boolean isTrackedInventory(Player player, Inventory target) {
        if (player == null || target == null) return false;
        Inventory tracked = openInventories.get(player.getUniqueId());
        return tracked != null && tracked.equals(target);
    }

    private String findSkillIdByName(String displayName) {
        if (displayName == null) return null;
        Map<String, SkillTreeSystem.SkillNode> allSkills = plugin.getSkillTreeSystem().getAllSkillNodes();

        for (Map.Entry<String, SkillTreeSystem.SkillNode> entry : allSkills.entrySet()) {
            SkillTreeSystem.SkillNode node = entry.getValue();
            if (node == null) continue;
            if (node.getName().equalsIgnoreCase(displayName)) {
                return entry.getKey();
            }
        }

        try {
            com.skilltree.plugin.systems.SkillRegistry registry = plugin.getSkillRegistry();
            if (registry != null) {
                for (String id : registry.getRegisteredSkillIds()) {
                    if (id != null && id.equalsIgnoreCase(displayName)) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Inventory tracked = openInventories.get(player.getUniqueId());
        if (tracked != null && tracked.equals(top)) {
            openInventories.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isTrackedInventory(player, top)) return;
        event.setCancelled(true);
        player.setItemOnCursor(null);
    }
}
