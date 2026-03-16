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

import java.util.*;

public class SkillUpgradeGUI implements Listener {
    private final SkillForgePlugin plugin;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, SkillPageContext> openContexts = new HashMap<>();
    private static final int PAGE_SIZE = SkillTreeLayout.FALLBACK_TREE_SLOTS.length;
    private static final int SLOT_PREV = 36;
    private static final int SLOT_CATEGORY = 39;
    private static final int SLOT_INFO = 40;
    private static final int SLOT_CLOSE = 41;
    private static final int SLOT_NEXT = 44;

    public SkillUpgradeGUI(SkillForgePlugin plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player) {
        open(player, "all", 1);
    }

    public void open(Player player, String category, int page) {
        if (player == null || plugin == null) return;
        String safeCategory = (category == null || category.isEmpty()) ? "all" : category.toLowerCase();
        List<SkillTreeSystem.SkillNode> nodes = resolveNodes(safeCategory);
        if (nodes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No skills found for category: " + safeCategory);
            return;
        }

        int totalPages = Math.max(1, (nodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(1, Math.min(page, totalPages));
        String title = GuiStyle.title("Skill Upgrade - " + formatCategory(safeCategory) + " (" + safePage + "/" + totalPages + ")");

        Inventory inv = Bukkit.createInventory(null, 45, title);
        fillBackground(inv);
        placeHeader(inv, player);
        placeNavigation(inv, safeCategory, safePage, totalPages);
        trackInventory(player, inv, safeCategory, safePage, nodes, totalPages);
        SkillPageContext ctx = openContexts.get(player.getUniqueId());
        placeSkillNodes(inv, player, nodes, safeCategory, safePage, ctx);
        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv) {
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            background.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, background.clone());
        }
    }

    private void placeHeader(Inventory inv, Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int skillPoints = data != null ? data.getSkillPoints() : 0;

        ItemStack header = GuiStyle.item(Material.NETHER_STAR, ChatColor.AQUA + "Upgrade Nexus",
                Arrays.asList(ChatColor.GRAY + "Select a node to invest your skill points.",
                        ChatColor.DARK_GRAY + "Progress is synced with the tree."));
        inv.setItem(4, header);

        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Skill Points: " + skillPoints);
            infoMeta.setLore(Arrays.asList(ChatColor.GRAY + "Use nodes to grow your power.",
                    ChatColor.GRAY + "Each click spends the node's cost."));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, info);
    }

    private void placeNavigation(Inventory inv, String category, int page, int totalPages) {
        ItemStack prev = GuiStyle.item(Material.ARROW, ChatColor.YELLOW + "Previous Page",
                Collections.singletonList(ChatColor.GRAY + "Page " + Math.max(1, page - 1)));
        ItemStack next = GuiStyle.item(Material.ARROW, ChatColor.YELLOW + "Next Page",
                Collections.singletonList(ChatColor.GRAY + "Page " + Math.min(totalPages, page + 1)));
        ItemStack close = GuiStyle.item(Material.BARRIER, ChatColor.RED + "Close", null);
        ItemStack cat = GuiStyle.item(Material.BOOK, ChatColor.AQUA + "Category: " + formatCategory(category),
                Collections.singletonList(ChatColor.GRAY + "Use /skillpanel skills <category> to switch."));

        inv.setItem(SLOT_PREV, prev);
        inv.setItem(SLOT_NEXT, next);
        inv.setItem(SLOT_CLOSE, close);
        inv.setItem(SLOT_CATEGORY, cat);
    }

    private void placeSkillNodes(Inventory inv, Player player, List<SkillTreeSystem.SkillNode> nodes, String category, int page, SkillPageContext ctx) {
        if (ctx == null) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        Map<String, SkillTreeSystem.SkillNode> allNodes = plugin.getSkillTreeSystem().getAllSkillNodes();
        Map<String, Integer> skillToSlot = new HashMap<>();
        for (Map.Entry<Integer, String> e : ctx.slotToSkill.entrySet()) {
            skillToSlot.put(e.getValue(), e.getKey());
        }

        for (Map.Entry<Integer, String> e : ctx.slotToSkill.entrySet()) {
            int slot = e.getKey();
            String skillId = e.getValue();
            SkillTreeSystem.SkillNode node = allNodes.get(skillId);
            if (node == null) continue;
            int current = data != null ? data.getSkillLevel(skillId) : 0;
            boolean unlocked = current > 0;
            boolean maxed = current >= node.getMaxLevel();
            int tier = computeTier(current);
            Material mat = resolveTierMaterial(node, current, tier, unlocked);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            meta.setDisplayName((unlocked ? ChatColor.GREEN : ChatColor.DARK_GRAY) + node.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + node.getDescription());
            lore.add("");
            lore.add(ChatColor.AQUA + "Level: " + ChatColor.WHITE + current + ChatColor.GRAY + "/" + node.getMaxLevel());
            lore.add(ChatColor.GRAY + "Tier: " + ChatColor.YELLOW + (tier + 1) + ChatColor.GRAY + "/5"
                    + ChatColor.DARK_GRAY + " (+" + SkillTreeSystem.TIER_LEVEL_SIZE + " levels per tier)");
            lore.add(ChatColor.YELLOW + "Cost: " + node.getCostPerLevel() + " SP");
            appendRequirementLore(lore, node, data);
            lore.add("");
            if (maxed) {
                lore.add(ChatColor.GRAY + "Maxed out");
            } else {
                List<SkillTreeSystem.SkillRequirement> unmet = plugin.getSkillTreeSystem().getUnmetRequirements(player, skillId);
                lore.add(unmet.isEmpty() ? ChatColor.GOLD + "Click to upgrade" : ChatColor.RED + "Requirements not met");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        drawRequirementLinks(inv, allNodes, skillToSlot);
    }

    private void trackInventory(Player player, Inventory inv, String category, int page,
                                List<SkillTreeSystem.SkillNode> nodes, int totalPages) {
        if (player == null || inv == null) return;
        openInventories.put(player.getUniqueId(), inv);
        SkillPageContext ctx = new SkillPageContext(category, page, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(nodes.size(), start + PAGE_SIZE);
        List<SkillTreeSystem.SkillNode> slice = new ArrayList<>();
        for (int i = start; i < end; i++) {
            SkillTreeSystem.SkillNode node = nodes.get(i);
            if (node == null) continue;
            slice.add(node);
        }
        Map<String, Integer> preferred = SkillTreeLayout.getCategorySlotMap(category);
        Set<Integer> used = new HashSet<>();

        // Put mapped nodes first to keep the visual tree shape.
        for (SkillTreeSystem.SkillNode node : slice) {
            Integer mappedSlot = preferred.get(node.getId());
            if (mappedSlot == null) continue;
            if (mappedSlot < 0 || mappedSlot >= SLOT_PREV) continue;
            if (used.add(mappedSlot)) {
                ctx.slotToSkill.put(mappedSlot, node.getId());
            }
        }

        // Put any remaining nodes in fallback slots.
        for (SkillTreeSystem.SkillNode node : slice) {
            if (ctx.slotToSkill.containsValue(node.getId())) continue;
            for (int slot : SkillTreeLayout.FALLBACK_TREE_SLOTS) {
                if (slot < 0 || slot >= SLOT_PREV) continue;
                if (used.add(slot)) {
                    ctx.slotToSkill.put(slot, node.getId());
                    break;
                }
            }
        }
        openContexts.put(player.getUniqueId(), ctx);
    }

    private boolean isTrackedInventory(Player player, Inventory top) {
        if (player == null || top == null) return false;
        Inventory tracked = openInventories.get(player.getUniqueId());
        return tracked != null && tracked.equals(top);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isTrackedInventory(player, event.getView().getTopInventory())) return;

        event.setCancelled(true);
        safeRestoreSlot(event, player);
        int slot = event.getSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            openInventories.remove(player.getUniqueId());
            openContexts.remove(player.getUniqueId());
            return;
        }

        SkillPageContext ctx = openContexts.get(player.getUniqueId());
        if (ctx == null) return;

        if (slot == SLOT_PREV) {
            if (ctx.page > 1) open(player, ctx.category, ctx.page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            if (ctx.page < ctx.totalPages) open(player, ctx.category, ctx.page + 1);
            return;
        }

        String skillId = ctx.slotToSkill.get(slot);
        if (skillId == null) return;

        SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
        if (node == null) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;
        int current = data.getSkillLevel(skillId);
        if (current >= node.getMaxLevel()) {
            player.sendMessage(ChatColor.GRAY + node.getName() + " is already at max level.");
            return;
        }

        SkillTreeSystem.UpgradeResult result = plugin.getSkillTreeSystem().tryUpgradeSkill(player, skillId);
        if (result != SkillTreeSystem.UpgradeResult.SUCCESS) {
            String reason = plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, skillId, result);
            player.sendMessage(ChatColor.RED + reason);
            open(player, ctx.category, ctx.page);
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Upgraded " + node.getName() + " to level " + data.getSkillLevel(skillId) + ".");
        open(player, ctx.category, ctx.page);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Inventory tracked = openInventories.get(player.getUniqueId());
        if (tracked != null && tracked.equals(top)) {
            openInventories.remove(player.getUniqueId());
            openContexts.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isTrackedInventory(player, event.getView().getTopInventory())) return;
        event.setCancelled(true);
        player.setItemOnCursor(null);
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

    private List<SkillTreeSystem.SkillNode> resolveNodes(String category) {
        List<SkillTreeSystem.SkillNode> nodes;
        if ("all".equalsIgnoreCase(category)) {
            nodes = new ArrayList<>(plugin.getSkillTreeSystem().getAllSkillNodes().values());
            Map<String, SkillTreeSystem.SkillNode> all = plugin.getSkillTreeSystem().getAllSkillNodes();
            nodes.sort(Comparator
                    .comparing(SkillTreeSystem.SkillNode::getCategory, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(n -> dependencyDepth(n, all))
                    .thenComparing(SkillTreeSystem.SkillNode::getName));
        } else {
            nodes = plugin.getSkillTreeSystem().getSkillsByCategory(category);
        }
        if (nodes == null) return Collections.emptyList();
        return nodes;
    }

    private int dependencyDepth(SkillTreeSystem.SkillNode node, Map<String, SkillTreeSystem.SkillNode> all) {
        if (node == null || all == null) return 0;
        Set<String> seen = new HashSet<>();
        SkillTreeSystem.SkillNode cursor = node;
        int depth = 0;
        while (cursor != null && cursor.getRequirements() != null && !cursor.getRequirements().isEmpty()) {
            SkillTreeSystem.SkillRequirement req = cursor.getRequirements().get(0);
            if (req == null || req.getRequiredSkillId() == null || req.getRequiredSkillId().isBlank()) break;
            if (!seen.add(req.getRequiredSkillId())) break;
            depth++;
            cursor = all.get(req.getRequiredSkillId());
        }
        return depth;
    }

    private String formatCategory(String category) {
        if (category == null || category.isEmpty()) return "All";
        String c = category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase();
        return "all".equalsIgnoreCase(category) ? "All Skills" : c;
    }

    private Material materialForCategory(String category) {
        if (category == null) return Material.EMERALD;
        switch (category.toLowerCase()) {
            case "combat":
                return Material.IRON_SWORD;
            case "mining":
                return Material.DIAMOND_PICKAXE;
            case "agility":
                return Material.FEATHER;
            case "intellect":
                return Material.ENCHANTED_BOOK;
            case "farming":
                return Material.WHEAT;
            case "fishing":
                return Material.FISHING_ROD;
            case "magic":
                return Material.BLAZE_POWDER;
            case "mastery":
                return Material.NETHERITE_SWORD;
            default:
                return Material.EMERALD;
        }
    }

    private void drawRequirementLinks(Inventory inv, Map<String, SkillTreeSystem.SkillNode> allNodes, Map<String, Integer> skillToSlot) {
        if (inv == null || allNodes == null || skillToSlot == null || skillToSlot.isEmpty()) return;
        ItemStack link = GuiStyle.pane(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");
        Set<Integer> occupied = new HashSet<>(skillToSlot.values());

        for (Map.Entry<String, Integer> entry : skillToSlot.entrySet()) {
            SkillTreeSystem.SkillNode child = allNodes.get(entry.getKey());
            if (child == null || child.getRequirements().isEmpty()) continue;
            int childSlot = entry.getValue();
            for (SkillTreeSystem.SkillRequirement req : child.getRequirements()) {
                if (req == null) continue;
                Integer parentSlot = skillToSlot.get(req.getRequiredSkillId());
                if (parentSlot == null) continue;
                drawPath(inv, parentSlot, childSlot, occupied, link);
            }
        }
    }

    private void drawPath(Inventory inv, int from, int to, Set<Integer> occupied, ItemStack link) {
        int r = from / 9;
        int c = from % 9;
        int tr = to / 9;
        int tc = to % 9;

        while (r != tr) {
            r += Integer.compare(tr, r);
            int slot = r * 9 + c;
            placeLink(inv, slot, occupied, link);
        }
        while (c != tc) {
            c += Integer.compare(tc, c);
            int slot = r * 9 + c;
            placeLink(inv, slot, occupied, link);
        }
    }

    private void placeLink(Inventory inv, int slot, Set<Integer> occupied, ItemStack link) {
        if (slot < 0 || slot >= SLOT_PREV) return;
        if (occupied.contains(slot)) return;
        inv.setItem(slot, link);
    }

    private Material resolveTierMaterial(SkillTreeSystem.SkillNode node, int level, int tier, boolean unlocked) {
        if (!unlocked) return Material.GRAY_DYE;
        String id = node.getId() == null ? "" : node.getId().toLowerCase(Locale.ROOT);
        String category = node.getCategory() == null ? "" : node.getCategory().toLowerCase(Locale.ROOT);
        int t = Math.max(0, Math.min(4, tier));

        if (category.equals("fishing")) {
            return Material.FISHING_ROD;
        }
        if (id.contains("brick")) {
            Material[] mats = new Material[] {
                    Material.BRICKS,
                    Material.STONE_BRICKS,
                    Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS,
                    Material.CHISELED_STONE_BRICKS
            };
            return mats[t];
        }
        Material[] mats;
        switch (category) {
            case "combat":
            case "mastery":
                mats = new Material[] {
                        Material.WOODEN_SWORD,
                        Material.STONE_SWORD,
                        Material.IRON_SWORD,
                        Material.DIAMOND_SWORD,
                        Material.NETHERITE_SWORD
                };
                break;
            case "mining":
                mats = new Material[] {
                        Material.WOODEN_PICKAXE,
                        Material.STONE_PICKAXE,
                        Material.IRON_PICKAXE,
                        Material.DIAMOND_PICKAXE,
                        Material.NETHERITE_PICKAXE
                };
                break;
            case "agility":
                mats = new Material[] {
                        Material.LEATHER_BOOTS,
                        Material.CHAINMAIL_BOOTS,
                        Material.IRON_BOOTS,
                        Material.DIAMOND_BOOTS,
                        Material.NETHERITE_BOOTS
                };
                break;
            case "intellect":
                mats = new Material[] {
                        Material.BOOK,
                        Material.WRITABLE_BOOK,
                        Material.ENCHANTED_BOOK,
                        Material.ENDER_EYE,
                        Material.NETHER_STAR
                };
                break;
            case "farming":
                mats = new Material[] {
                        Material.WOODEN_HOE,
                        Material.STONE_HOE,
                        Material.IRON_HOE,
                        Material.DIAMOND_HOE,
                        Material.NETHERITE_HOE
                };
                break;
            case "magic":
                mats = new Material[] {
                        Material.STICK,
                        Material.BLAZE_ROD,
                        Material.AMETHYST_SHARD,
                        Material.END_ROD,
                        Material.NETHER_STAR
                };
                break;
            default:
                mats = new Material[] {
                        materialForCategory(category),
                        materialForCategory(category),
                        materialForCategory(category),
                        materialForCategory(category),
                        materialForCategory(category)
                };
                break;
        }
        return mats[t];
    }

    private int computeTier(int level) {
        if (level <= 0) return 0;
        return Math.max(0, Math.min(4, (level - 1) / SkillTreeSystem.TIER_LEVEL_SIZE));
    }

    private void appendRequirementLore(List<String> lore, SkillTreeSystem.SkillNode node, PlayerData data) {
        if (lore == null || node == null) return;
        List<SkillTreeSystem.SkillRequirement> requirements = node.getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            lore.add(ChatColor.DARK_GREEN + "Root Skill");
            return;
        }
        lore.add(ChatColor.GRAY + "Requirements:");
        Map<String, SkillTreeSystem.SkillNode> all = plugin.getSkillTreeSystem().getAllSkillNodes();
        for (SkillTreeSystem.SkillRequirement req : requirements) {
            if (req == null) continue;
            int have = data != null ? data.getSkillLevel(req.getRequiredSkillId()) : 0;
            boolean met = have >= req.getRequiredLevel();
            SkillTreeSystem.SkillNode reqNode = all.get(req.getRequiredSkillId());
            String reqName = reqNode != null ? reqNode.getName() : req.getRequiredSkillId();
            lore.add((met ? ChatColor.GREEN : ChatColor.RED)
                    + "- " + reqName + " " + have + "/" + req.getRequiredLevel());
        }
    }

    private static class SkillPageContext {
        private final String category;
        private final int page;
        private final int totalPages;
        private final Map<Integer, String> slotToSkill = new HashMap<>();

        private SkillPageContext(String category, int page, int totalPages) {
            this.category = category;
            this.page = page;
            this.totalPages = totalPages;
        }
    }
}
