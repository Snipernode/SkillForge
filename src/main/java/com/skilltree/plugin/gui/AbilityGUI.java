package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.SkillTreeSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AbilityGUI implements Listener {
    private final SkillForgePlugin plugin;
    private final Player player;
    private Inventory inventory;

    // NEW: track currently selected skill per player (so we don't put items on cursor)
    private final HashMap<UUID, String> selectedSkill = new HashMap<>();

    public AbilityGUI(SkillForgePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        // EXPANDED inventory to fit many skills
        inventory = Bukkit.createInventory(null, 54, GuiStyle.title("Ability Management"));

        // ability slots (kept same positions)
        int[] abilitySlots = new int[]{10, 12, 14, 16};
        for (int i = 0; i < abilitySlots.length; i++) {
            ItemStack slot = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = slot.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Ability Slot " + (i + 1));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Empty");
                lore.add("");
                lore.add(ChatColor.AQUA + "Click with a Skill Item to bind!");
                meta.setLore(lore);
                slot.setItemMeta(meta);
            }
            inventory.setItem(abilitySlots[i], slot);
        }

        // collect active skills from SkillTreeSystem + personal + registry
        List<LocalSkill> skills = new ArrayList<>();
        String[] cats = new String[] {"combat","mining","agility","intellect","farming","fishing","magic"};
        try {
            for (String cat : cats) {
                List<SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getSkillsByCategory(cat);
                if (nodes == null) continue;
                for (SkillTreeSystem.SkillNode n : nodes) {
                    if (n == null) continue;
                    if (!isPassiveSkillNode(n)) {
                        skills.add(new LocalSkill(n.getId(), n.getName(), n));
                    }
                }
                // add registry skills for category
                List<LocalSkill> reg = getRegistrySkillsByCategory(cat);
                if (reg != null) {
                    for (LocalSkill ls : reg) {
                        if (ls != null && ls.id != null) {
                            // avoid duplicates
                            boolean dup = false;
                            for (LocalSkill ex : skills) if (ex.id.equalsIgnoreCase(ls.id)) { dup = true; break; }
                            if (!dup && !isPassiveLocalSkill(ls)) skills.add(ls);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // personal generated skills
        try {
            Map<String,Integer> gen = plugin.getGeneratedSkillsFor(player);
            if (gen != null) {
                for (Map.Entry<String,Integer> e : gen.entrySet()) {
                    skills.add(0, new LocalSkill(e.getKey(), "Personal: " + e.getKey(), null));
                }
            }
        } catch (Throwable ignored) {}

        // place skill items into inventory skipping ability slots
        int maxSlots = inventory.getSize();
        for (int slotIndex = 0, sIdx = 0; slotIndex < maxSlots && sIdx < skills.size(); slotIndex++) {
            boolean isAbilitySlot = false;
            for (int a : abilitySlots) if (a == slotIndex) { isAbilitySlot = true; break; }
            if (isAbilitySlot) continue;
            // reserve last row for navigation if needed; but we simply fill up
            LocalSkill ls = skills.get(sIdx++);
            ItemStack item = createSkillItem(ls.id, ls.name != null ? ls.name : ls.id);
            inventory.setItem(slotIndex, item);
        }

        // Header + help
        ItemStack header = GuiStyle.item(Material.ENCHANTED_BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Adventurer's Grimoire",
                List.of(ChatColor.GRAY + "Select a skill, then bind it to a slot.",
                        ChatColor.DARK_GRAY + "Bindings are stored per player."));
        inventory.setItem(4, header);
        ItemStack help = GuiStyle.item(Material.NAME_TAG, ChatColor.YELLOW + "Training Notes",
                List.of(ChatColor.GRAY + "Click a skill to select it.",
                        ChatColor.GRAY + "Click a slot to bind.",
                        ChatColor.GRAY + "Shift-clicking does nothing here."));
        inventory.setItem(49, help);

        // Soft background fill for any unused slots
        GuiStyle.fillEmpty(inventory, GuiStyle.fillerPane());

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true); // always cancel to prevent moving/taking items

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // If clicked item is a skill entry, select it for the player (do NOT put on cursor)
        if (isSkillItem(clicked)) {
            String skillId = extractSkillId(clicked);
            if (skillId == null) skillId = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            selectedSkill.put(player.getUniqueId(), skillId.toLowerCase());
            player.sendMessage(ChatColor.YELLOW + "Selected skill: " + skillId + ". Click an ability slot to bind.");
            return;
        }

        // If clicked is an ability slot, attempt to bind selected skill (do not remove GUI items)
        ItemMeta clickedMeta = clicked.getItemMeta();
        String clickedName = clickedMeta != null && clickedMeta.hasDisplayName() ? clickedMeta.getDisplayName() : "";
        boolean isAbilitySlot = clicked.getType() == Material.GRAY_STAINED_GLASS_PANE || clickedName.contains("Ability Slot") || clicked.getType() == Material.LIME_STAINED_GLASS_PANE;
        if (isAbilitySlot) {
            String sel = selectedSkill.get(player.getUniqueId());
            if (sel == null) {
                player.sendMessage(ChatColor.RED + "No skill selected. Click a skill in the GUI first.");
                return;
            }
            // Bind: update slot display and type (keep item in GUI)
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(Material.LIME_STAINED_GLASS_PANE);
            }
            meta.setDisplayName(ChatColor.GREEN + sel);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Bound Skill: " + sel);
            lore.add("");
            lore.add(ChatColor.AQUA + "Use your bound ability via the ability system.");
            meta.setLore(lore);
            clicked.setItemMeta(meta);
            clicked.setType(Material.LIME_STAINED_GLASS_PANE);
            selectedSkill.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Skill " + sel + " bound to slot.");
            return;
        }

        // otherwise ignore (GUI is read-only)
    }

    // clear selection when player closes the GUI
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        UUID uid = event.getPlayer().getUniqueId();
        selectedSkill.remove(uid);
    }

    // detect if an ItemStack represents a skill entry (lore contains "Skill ID:")
    private boolean isSkillItem(ItemStack it) {
        if (it == null) return false;
        ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        List<String> lore = m.getLore();
        if (lore == null) return false;
        for (String L : lore) {
            if (L != null && L.toLowerCase().contains("skill id")) return true;
        }
        return false;
    }

    // extract skill id from an ItemStack's lore (line containing "Skill ID:")
    private String extractSkillId(ItemStack it) {
        if (it == null) return null;
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        List<String> lore = m.getLore();
        if (lore == null) return null;
        for (String L : lore) {
            if (L == null) continue;
            String low = ChatColor.stripColor(L).toLowerCase();
            int i = low.indexOf("skill id:");
            if (i >= 0) {
                return L.substring(i + "Skill ID:".length()).trim();
            }
        }
        return null;
    }

    // NEW small holder for skills inside this GUI
    private static class LocalSkill {
        String id;
        String name;
        Object raw;
        LocalSkill(String id, String name, Object raw) { this.id = id; this.name = name; this.raw = raw; }
    }

    // NEW: create display item for a skill
    private ItemStack createSkillItem(String id, String displayName) {
        Material mat = Material.BLAZE_POWDER;
        if (id == null) id = "unknown_skill";
        String lid = id.toLowerCase();
        if (lid.contains("combat")) mat = Material.IRON_SWORD;
        else if (lid.contains("mining")) mat = Material.DIAMOND_PICKAXE;
        else if (lid.contains("fishing")) mat = Material.FISHING_ROD;
        else if (lid.contains("farming")) mat = Material.WHEAT;
        else if (lid.contains("agility")) mat = Material.FEATHER;
        else if (lid.contains("intellect") || lid.contains("magic")) mat = Material.ENCHANTED_BOOK;
        else if (lid.contains("meteor")) mat = Material.BLAZE_POWDER;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.GOLD + (displayName != null ? displayName : id));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Skill ID: " + id);
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Active");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select this skill.");
            lore.add(ChatColor.DARK_GRAY + "Then click an ability slot.");
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    // NEW: best-effort passive detection for SkillNode
    private boolean isPassiveSkillNode(SkillTreeSystem.SkillNode node) {
        if (node == null) return false;
        try {
            Method m = node.getClass().getMethod("isPassive");
            Object r = m.invoke(node);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Exception ignored) {}
        try {
            Method m = node.getClass().getMethod("getType");
            Object r = m.invoke(node);
            if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
            if (r != null && r.toString().toLowerCase().contains("passive")) return true;
        } catch (Exception ignored) {}
        try {
            String id = node.getId();
            if (id != null) {
                id = id.toLowerCase();
                return id.startsWith("passive_") || id.contains("_passive");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isPassiveLocalSkill(LocalSkill ls) {
        if (ls == null || ls.raw == null) return false;
        Object raw = ls.raw;
        try {
            Method m = raw.getClass().getMethod("isPassive");
            Object r = m.invoke(raw);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Exception ignored) {}
        try {
            Method m = raw.getClass().getMethod("getType");
            Object r = m.invoke(raw);
            if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
            if (r != null && r.toString().toLowerCase().contains("passive")) return true;
        } catch (Exception ignored) {}
        try {
            Field f = raw.getClass().getField("type");
            Object r = f.get(raw);
            if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
        } catch (Exception ignored) {}
        return ls.id != null && (ls.id.toLowerCase().startsWith("passive_") || ls.id.toLowerCase().contains("_passive"));
    }

    // NEW: registry category fetch (best-effort via reflection)
    private List<LocalSkill> getRegistrySkillsByCategory(String category) {
        List<LocalSkill> out = new ArrayList<>();
        try {
            Method gm = plugin.getClass().getMethod("getSkillRegistry");
            Object registry = gm.invoke(plugin);
            if (registry == null) return out;
            try {
                Method catM = registry.getClass().getMethod("getSkillsByCategory", String.class);
                Object res = catM.invoke(registry, category);
                if (res instanceof Iterable) {
                    for (Object s : (Iterable<?>) res) {
                        String id = tryGetString(s, new String[]{"getId","getIdentifier","id"});
                        String nm = tryGetString(s, new String[]{"getName","getDisplayName","name"});
                        if (id != null) out.add(new LocalSkill(id, nm, s));
                    }
                    return out;
                }
            } catch (NoSuchMethodException ignore) {}
            String[] allNames = new String[] {"getAllSkills","getSkills","values","all","list"};
            for (String name : allNames) {
                try {
                    Method am = registry.getClass().getMethod(name);
                    Object all = am.invoke(registry);
                    if (all instanceof Map) {
                        for (Object v : ((Map<?,?>) all).values()) {
                            String cat = tryGetString(v, new String[]{"getCategory","category","getGroup"});
                            if (cat != null && cat.equalsIgnoreCase(category)) {
                                String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
                                String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
                                if (id != null) out.add(new LocalSkill(id, nm, v));
                            }
                        }
                    } else if (all instanceof Iterable) {
                        for (Object v : (Iterable<?>) all) {
                            String cat = tryGetString(v, new String[]{"getCategory","category","getGroup"});
                            if (cat != null && cat.equalsIgnoreCase(category)) {
                                String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
                                String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
                                if (id != null) out.add(new LocalSkill(id, nm, v));
                            }
                        }
                    }
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    // small reflective helper used above
    private String tryGetString(Object obj, String[] names) {
        if (obj == null) return null;
        for (String n : names) {
            try {
                Method m = obj.getClass().getMethod(n);
                Object r = m.invoke(obj);
                if (r instanceof String) return (String) r;
            } catch (Exception ignored) {}
            try {
                Field f = obj.getClass().getField(n);
                Object r = f.get(obj);
                if (r instanceof String) return (String) r;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
