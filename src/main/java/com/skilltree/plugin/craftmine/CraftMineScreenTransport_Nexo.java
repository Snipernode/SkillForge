package com.skilltree.plugin.craftmine;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class CraftMineScreenTransport_Nexo implements CraftMineScreenTransport, Listener {

    private static final List<String> TAB_CATEGORIES = Arrays.asList(
            "combat", "mining", "agility", "intellect", "farming", "fishing", "magic", "mastery"
    );
    private static final Material[] TAB_ICONS = new Material[] {
            Material.IRON_SWORD, Material.DIAMOND_PICKAXE, Material.FEATHER, Material.ENCHANTED_BOOK,
            Material.WHEAT, Material.FISHING_ROD, Material.BLAZE_POWDER, Material.NETHERITE_SWORD
    };

    private final SkillForgePlugin plugin;
    private final Map<UUID, SkillTreeViewState> openViews = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private Consumer<SkillTreeClickEvent> handler;
    private enum LinkPiece {
        HORIZONTAL, VERTICAL, CORNER_NE, CORNER_NW, CORNER_SE, CORNER_SW
    }

    public CraftMineScreenTransport_Nexo() {
        this(SkillForgePlugin.getInstance());
    }

    public CraftMineScreenTransport_Nexo(SkillForgePlugin plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    @Override
    public void open(Player player, SkillTreeViewState view) {
        if (player == null || view == null) return;
        String title = ChatColor.WHITE + "Skill Graph";
        String treeId = view.tree != null ? view.tree.id : "unknown";
        Inventory inv = Bukkit.createInventory(
                new SkillTreeGraphHolder(player.getUniqueId(), treeId),
                CraftMineLayout.GRID_W * CraftMineLayout.GRID_H,
                title
        );
        openViews.put(player.getUniqueId(), view);
        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @Override
    public void update(Player player, SkillTreeViewState view) {
        if (player == null || view == null || view.tree == null) return;

        Inventory inv = openInventories.get(player.getUniqueId());
        if (inv == null) {
            open(player, view);
            inv = openInventories.get(player.getUniqueId());
            if (inv == null) return;
        }

        // outer frame
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", null, false);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, bg);
        }

        // custom tree workspace (canopy + trunk tones).
        ItemStack canopy = makeItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.DARK_GREEN + " ", null, false);
        ItemStack canopyDeep = makeItem(Material.LIME_STAINED_GLASS_PANE, ChatColor.DARK_GREEN + " ", null, false);
        ItemStack trunk = makeItem(Material.BROWN_STAINED_GLASS_PANE, ChatColor.GOLD + " ", null, false);
        for (int y = 1; y < CraftMineLayout.GRID_H; y++) {
            for (int x = 0; x < CraftMineLayout.GRID_W; x++) {
                int slot = CraftMineLayout.toSlot(x, y);
                if (slot < 0 || slot >= inv.getSize()) continue;
                if (y <= 2) inv.setItem(slot, canopy);
                else if (y == 3) inv.setItem(slot, canopyDeep);
                else inv.setItem(slot, trunk);
            }
        }

        drawTabs(inv, view, player);

        List<SkillNode> nodes = new ArrayList<>(view.tree.nodes.values());
        Set<Integer> nodeSlots = new HashSet<>();
        for (SkillNode node : nodes) {
            int slot = nodeToSlot(view, node);
            if (slot >= 0 && slot < inv.getSize()) nodeSlots.add(slot);
        }

        // Draw links based on actual parent dependencies.
        for (SkillNode child : nodes) {
            if (child.parents == null || child.parents.isEmpty()) continue;
            for (String parentId : child.parents) {
                SkillNode parent = view.tree.get(parentId);
                if (parent == null) continue;
                drawConnector(inv, view, parent, child, nodeSlots, parent.unlocked);
            }
        }

        // nodes
        for (SkillNode node : nodes) {
            int slot = nodeToSlot(view, node);
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, makeNodeItem(view, node));
        }
    }

    @Override
    public void close(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Inventory tracked = openInventories.remove(uuid);
        openViews.remove(uuid);

        if (tracked != null && player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().equals(tracked)) {
            player.closeInventory();
        }
    }

    @Override
    public void onClick(Consumer<SkillTreeClickEvent> handler) {
        this.handler = handler;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SkillTreeGraphHolder)) return;
        Inventory tracked = openInventories.get(player.getUniqueId());
        if (tracked == null || !tracked.equals(top)) return;

        event.setCancelled(true);
        player.setItemOnCursor(null);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        SkillTreeViewState view = openViews.get(player.getUniqueId());
        if (view == null) return;

        if (rawSlot >= 0 && rawSlot < TAB_CATEGORIES.size()) {
            String category = TAB_CATEGORIES.get(rawSlot);
            if (plugin != null && plugin.getSkillGraphSystem() != null) {
                plugin.getSkillGraphSystem().open(player, category);
            }
            return;
        }
        if (rawSlot == 8) {
            player.closeInventory();
            return;
        }

        if (handler == null) return;
        handler.accept(new SkillTreeClickEvent(player, view, rawSlot, event.getClick()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Inventory tracked = openInventories.get(uuid);
        if (tracked == null) return;
        if (!tracked.equals(event.getInventory())) return;
        openInventories.remove(uuid);
        openViews.remove(uuid);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openInventories.remove(uuid);
        openViews.remove(uuid);
    }

    private void drawTabs(Inventory inv, SkillTreeViewState view, Player player) {
        String active = view.tree != null && view.tree.id != null ? view.tree.id.toLowerCase() : "";
        for (int i = 0; i < TAB_CATEGORIES.size(); i++) {
            String category = TAB_CATEGORIES.get(i);
            boolean selected = category.equals(active);
            String label = capitalize(category);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Open " + label + " tree");
            if (selected) lore.add(ChatColor.GREEN + "Selected");
            inv.setItem(i, makeItem(TAB_ICONS[i], (selected ? ChatColor.GREEN : ChatColor.WHITE) + label, lore, selected));
        }

        int unlocked = 0;
        int totalLevels = 0;
        if (plugin != null && plugin.getPlayerDataManager() != null) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            if (data != null && view.tree != null) {
                for (SkillNode node : view.tree.nodes.values()) {
                    int level = data.getSkillLevel(node.id);
                    if (level > 0) unlocked++;
                    totalLevels += level;
                }
            }
        }
        inv.setItem(8, makeItem(Material.BARRIER, ChatColor.RED + "Close",
                List.of(
                        ChatColor.GREEN + "Levels: " + totalLevels,
                        ChatColor.GRAY + "Unlocked Nodes: " + unlocked + "/" + (view.tree != null ? view.tree.nodes.size() : 0),
                        ChatColor.DARK_GRAY + "Click to close"
                ), false));
    }

    private int nodeToSlot(SkillTreeViewState view, SkillNode node) {
        int gridX = node.x - view.offsetX;
        int gridY = node.y - view.offsetY;
        return CraftMineLayout.toSlot(gridX, gridY);
    }

    private void drawConnector(Inventory inv, SkillTreeViewState view, SkillNode from, SkillNode to, Set<Integer> nodeSlots, boolean active) {
        if (from == null || to == null) return;
        int x1 = from.x - view.offsetX;
        int y1 = from.y - view.offsetY;
        int x2 = to.x - view.offsetX;
        int y2 = to.y - view.offsetY;

        if (y1 < 1 || y2 < 1) return;
        if (x1 < 0 || x1 >= CraftMineLayout.GRID_W || x2 < 0 || x2 >= CraftMineLayout.GRID_W) return;
        if (y1 >= CraftMineLayout.GRID_H || y2 >= CraftMineLayout.GRID_H) return;

        int stepX = Integer.compare(x2, x1);
        int stepY = Integer.compare(y2, y1);
        int cx = x1;
        int cy = y1;

        boolean changedX = false;
        while (cx != x2) {
            cx += stepX;
            changedX = true;
            placeConnector(inv, cx, cy, nodeSlots, connectorFor(LinkPiece.HORIZONTAL, active));
        }

        if (changedX && stepY != 0) {
            LinkPiece corner =
                    stepX > 0 && stepY > 0 ? LinkPiece.CORNER_SE :
                    stepX > 0 ? LinkPiece.CORNER_NE :
                    stepY > 0 ? LinkPiece.CORNER_SW : LinkPiece.CORNER_NW;
            placeConnector(inv, cx, cy, nodeSlots, connectorFor(corner, active));
        }

        while (cy != y2) {
            cy += stepY;
            placeConnector(inv, cx, cy, nodeSlots, connectorFor(LinkPiece.VERTICAL, active));
        }
    }

    private void placeConnector(Inventory inv, int x, int y, Set<Integer> nodeSlots, ItemStack connector) {
        int slot = CraftMineLayout.toSlot(x, y);
        if (slot < 0 || slot >= inv.getSize()) return;
        if (nodeSlots.contains(slot)) return;
        inv.setItem(slot, connector);
    }

    private ItemStack connectorFor(LinkPiece piece, boolean active) {
        return switch (piece) {
            case HORIZONTAL -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Link ↔", null, active);
            case VERTICAL -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Link ↕", null, active);
            case CORNER_NE -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Bend ↗", null, active);
            case CORNER_NW -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Bend ↖", null, active);
            case CORNER_SE -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Bend ↘", null, active);
            case CORNER_SW -> makeItem(Material.CHAIN, (active ? ChatColor.GREEN : ChatColor.GRAY) + "Chain Bend ↙", null, active);
        };
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private ItemStack makeNodeItem(SkillTreeViewState view, SkillNode node) {
        Material mat = node.icon != null ? node.icon : Material.PAPER;
        List<String> lore = new ArrayList<>();
        if (node.lore != null) lore.addAll(node.lore);

        if (node.unlocked) {
            lore.add(ChatColor.GREEN + "Unlocked");
        } else if (node.parents == null || node.parents.isEmpty()) {
            lore.add(ChatColor.YELLOW + "Click to upgrade");
        } else {
            lore.add(ChatColor.GRAY + "Requires:");
            for (String parentId : node.parents) {
                SkillNode parent = view.tree.get(parentId);
                boolean ready = parent != null && parent.unlocked;
                String pname = parent != null ? parent.name : parentId;
                lore.add((ready ? ChatColor.GREEN : ChatColor.RED) + "- " + pname);
            }
        }
        return makeItem(mat, (node.unlocked ? ChatColor.GREEN : ChatColor.GOLD) + node.name, lore, node.unlocked);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
