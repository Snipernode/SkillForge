package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.craftmine.CraftMineScreenTransport_Nexo;
import com.skilltree.plugin.craftmine.CraftMineSkillTreeManager;
import com.skilltree.plugin.craftmine.SkillNode;
import com.skilltree.plugin.craftmine.SkillTree;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SkillGraphSystem {

    private static final List<String> CATEGORIES = Arrays.asList(
            "combat", "mining", "agility", "intellect", "farming", "fishing", "magic", "mastery"
    );

    private final SkillForgePlugin plugin;
    private final CraftMineSkillTreeManager manager;

    public SkillGraphSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.manager = new CraftMineSkillTreeManager(new CraftMineScreenTransport_Nexo(plugin));
    }

    public void open(Player player) {
        open(player, "combat");
    }

    public void open(Player player, String category) {
        if (player == null) return;
        String normalized = normalizeCategory(category);
        SkillTree tree = buildTree(player, normalized);
        manager.open(player, tree);
    }

    public List<String> getCategories() {
        return CATEGORIES;
    }

    private String normalizeCategory(String category) {
        if (category == null) return "combat";
        String normalized = category.toLowerCase(Locale.ROOT).trim();
        return CATEGORIES.contains(normalized) ? normalized : "combat";
    }

    private SkillTree buildTree(Player player, String category) {
        String title = "SkillForge " + Character.toUpperCase(category.charAt(0)) + category.substring(1);
        SkillTree tree = new SkillTree(category, title);
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        List<SkillTreeSystem.SkillNode> skills = plugin.getSkillTreeSystem().getSkillsByCategory(category);
        if (skills == null) return tree;

        Map<String, int[]> treeCoords = computeTreeCoordinates(skills);
        final int[][] fallback = new int[][] {
                {4, 5},
                {2, 4}, {6, 4},
                {1, 3}, {3, 3}, {5, 3}, {7, 3},
                {0, 2}, {2, 2}, {4, 2}, {6, 2}, {8, 2},
                {1, 1}, {3, 1}, {5, 1}, {7, 1}
        };

        for (int i = 0; i < skills.size(); i++) {
            SkillTreeSystem.SkillNode skill = skills.get(i);
            int level = data.getSkillLevel(skill.getId());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + skill.getDescription());
            lore.add(ChatColor.GRAY + "Level: " + level + "/" + skill.getMaxLevel());
            lore.add(ChatColor.GRAY + "Cost: " + skill.getCostPerLevel() + " SP");
            if (!skill.getRequirements().isEmpty()) {
                lore.add(ChatColor.GRAY + "Requirements:");
                for (SkillTreeSystem.SkillRequirement req : skill.getRequirements()) {
                    if (req == null) continue;
                    int have = data.getSkillLevel(req.getRequiredSkillId());
                    SkillTreeSystem.SkillNode reqNode = plugin.getSkillTreeSystem().getAllSkillNodes().get(req.getRequiredSkillId());
                    String reqName = reqNode != null ? reqNode.getName() : req.getRequiredSkillId();
                    lore.add((have >= req.getRequiredLevel() ? ChatColor.GREEN : ChatColor.RED)
                            + "- " + reqName + " " + have + "/" + req.getRequiredLevel());
                }
            } else {
                lore.add(ChatColor.DARK_GREEN + "Root Skill");
            }
            lore.add(ChatColor.YELLOW + "Click to upgrade.");

            List<String> parents = new ArrayList<>();
            for (SkillTreeSystem.SkillRequirement req : skill.getRequirements()) {
                if (req != null && req.getRequiredSkillId() != null && !req.getRequiredSkillId().isBlank()) {
                    parents.add(req.getRequiredSkillId());
                }
            }

            int gridX;
            int gridY;
            int[] mapped = treeCoords.get(skill.getId());
            if (mapped != null && mapped.length >= 2) {
                gridX = mapped[0];
                gridY = mapped[1];
            } else {
                if (i >= fallback.length) break;
                gridX = fallback[i][0];
                gridY = fallback[i][1];
            }

            SkillNode node = new SkillNode(
                    skill.getId(),
                    skill.getName(),
                    lore,
                    iconFor(skill),
                    parents,
                    gridX,
                    gridY
            );
            node.unlocked = level > 0;
            tree.add(node);
        }
        return tree;
    }

    /**
     * Builds a real dependency tree layout for the 9x6 graph grid.
     * Row 0 is tabs, rows 1..5 are the graph space.
     * Roots are pushed toward the bottom (tree trunk), children branch upward.
     */
    private Map<String, int[]> computeTreeCoordinates(List<SkillTreeSystem.SkillNode> skills) {
        if (skills == null || skills.isEmpty()) return Collections.emptyMap();

        Map<String, SkillTreeSystem.SkillNode> byId = new HashMap<>();
        for (SkillTreeSystem.SkillNode node : skills) {
            if (node == null || node.getId() == null) continue;
            byId.put(node.getId(), node);
        }
        if (byId.isEmpty()) return Collections.emptyMap();

        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String id : byId.keySet()) {
            children.put(id, new ArrayList<>());
            indegree.put(id, 0);
        }

        for (SkillTreeSystem.SkillNode node : byId.values()) {
            List<SkillTreeSystem.SkillRequirement> reqs = node.getRequirements();
            if (reqs == null) continue;
            for (SkillTreeSystem.SkillRequirement req : reqs) {
                if (req == null || req.getRequiredSkillId() == null) continue;
                String parentId = req.getRequiredSkillId();
                if (!byId.containsKey(parentId)) continue;
                children.get(parentId).add(node.getId());
                indegree.put(node.getId(), indegree.getOrDefault(node.getId(), 0) + 1);
            }
        }

        for (List<String> list : children.values()) {
            list.sort(Comparator.naturalOrder());
        }

        List<String> roots = new ArrayList<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() <= 0) roots.add(e.getKey());
        }
        roots.sort(Comparator.naturalOrder());
        if (roots.isEmpty()) {
            roots.addAll(byId.keySet());
            roots.sort(Comparator.naturalOrder());
        }

        Map<String, Integer> depth = new HashMap<>();
        Set<String> frontier = new LinkedHashSet<>(roots);
        for (String id : roots) depth.put(id, 0);

        // Longest path depth on DAG-like graphs (safe if cycles exist).
        for (int guard = 0; guard < byId.size() * 3 && !frontier.isEmpty(); guard++) {
            Set<String> next = new LinkedHashSet<>();
            for (String parent : frontier) {
                int d = depth.getOrDefault(parent, 0);
                for (String child : children.getOrDefault(parent, List.of())) {
                    int cand = d + 1;
                    if (cand > depth.getOrDefault(child, -1)) {
                        depth.put(child, cand);
                        next.add(child);
                    }
                }
            }
            frontier = next;
        }
        for (String id : byId.keySet()) depth.putIfAbsent(id, 0);

        int maxDepth = 0;
        for (int d : depth.values()) maxDepth = Math.max(maxDepth, d);

        Map<String, Double> rawX = new HashMap<>();
        Set<String> placed = new HashSet<>();
        int[] leafCursor = new int[] {0};
        for (String root : roots) {
            computeRawX(root, children, rawX, placed, leafCursor, new HashSet<>());
        }
        List<String> leftovers = new ArrayList<>(byId.keySet());
        leftovers.removeAll(placed);
        leftovers.sort(Comparator.naturalOrder());
        for (String id : leftovers) {
            computeRawX(id, children, rawX, placed, leafCursor, new HashSet<>());
        }

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (double v : rawX.values()) {
            minX = Math.min(minX, v);
            maxX = Math.max(maxX, v);
        }
        if (minX == Double.MAX_VALUE) {
            minX = 0;
            maxX = 0;
        }

        Map<Integer, Set<Integer>> usedByRow = new HashMap<>();
        Map<String, int[]> coords = new HashMap<>();
        for (SkillTreeSystem.SkillNode node : skills) {
            if (node == null || node.getId() == null) continue;
            String id = node.getId();
            double raw = rawX.getOrDefault(id, 0.0);
            int x;
            if (Math.abs(maxX - minX) < 0.0001) x = 4;
            else x = (int) Math.round(((raw - minX) / (maxX - minX)) * 8.0);
            x = clamp(x, 0, 8);

            int d = depth.getOrDefault(id, 0);
            int y;
            if (maxDepth <= 0) y = 3;
            else {
                double ratio = (double) d / (double) maxDepth;
                y = 5 - (int) Math.round(ratio * 4.0);
            }
            y = clamp(y, 1, 5);

            Set<Integer> used = usedByRow.computeIfAbsent(y, k -> new HashSet<>());
            if (used.contains(x)) {
                x = nearestFreeColumn(x, used);
            }
            used.add(x);
            coords.put(id, new int[] {x, y});
        }
        return coords;
    }

    private void computeRawX(
            String id,
            Map<String, List<String>> children,
            Map<String, Double> rawX,
            Set<String> placed,
            int[] leafCursor,
            Set<String> stack
    ) {
        if (id == null) return;
        if (rawX.containsKey(id)) return;
        if (!stack.add(id)) {
            // Cycle guard.
            rawX.put(id, (double) leafCursor[0]++);
            placed.add(id);
            return;
        }

        List<String> kids = children.getOrDefault(id, List.of());
        if (kids.isEmpty()) {
            rawX.put(id, (double) leafCursor[0]++);
            placed.add(id);
            stack.remove(id);
            return;
        }

        double sum = 0.0;
        int count = 0;
        for (String child : kids) {
            computeRawX(child, children, rawX, placed, leafCursor, stack);
            sum += rawX.getOrDefault(child, (double) leafCursor[0]);
            count++;
        }

        rawX.put(id, count <= 0 ? (double) leafCursor[0]++ : (sum / (double) count));
        placed.add(id);
        stack.remove(id);
    }

    private int nearestFreeColumn(int preferred, Set<Integer> used) {
        if (preferred < 0) preferred = 0;
        if (preferred > 8) preferred = 8;
        if (!used.contains(preferred)) return preferred;
        for (int offset = 1; offset < 9; offset++) {
            int left = preferred - offset;
            int right = preferred + offset;
            if (left >= 0 && !used.contains(left)) return left;
            if (right <= 8 && !used.contains(right)) return right;
        }
        return preferred;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private Material iconFor(SkillTreeSystem.SkillNode node) {
        if (node == null) return Material.BOOK;
        String id = node.getId() == null ? "" : node.getId().toLowerCase(Locale.ROOT);
        String category = node.getCategory() == null ? "" : node.getCategory().toLowerCase(Locale.ROOT);

        if (id.contains("brick")) return Material.BRICKS;
        if (category.equals("combat")) return Material.IRON_SWORD;
        if (category.equals("mining")) return Material.DIAMOND_PICKAXE;
        if (category.equals("agility")) return Material.FEATHER;
        if (category.equals("intellect")) return Material.ENCHANTED_BOOK;
        if (category.equals("farming")) return Material.WHEAT;
        if (category.equals("fishing")) return Material.FISHING_ROD;
        if (category.equals("magic")) return Material.BLAZE_POWDER;
        if (category.equals("mastery")) return Material.NETHERITE_SWORD;
        return Material.BOOK;
    }
}
