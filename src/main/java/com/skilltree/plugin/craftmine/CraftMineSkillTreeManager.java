package com.skilltree.plugin.craftmine;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftMineSkillTreeManager {

    private final Map<UUID, SkillTreeViewState> open = new HashMap<>();
    private final CraftMineScreenTransport transport;

    public CraftMineSkillTreeManager(CraftMineScreenTransport transport) {
        this.transport = transport;
        this.transport.onClick(this::handleClick);
    }

    public void open(Player p, SkillTree tree) {
        SkillTreeViewState view = new SkillTreeViewState(p.getUniqueId(), tree);
        open.put(p.getUniqueId(), view);
        transport.open(p, view);
        transport.update(p, view);
    }

    public void close(Player p) {
        open.remove(p.getUniqueId());
        transport.close(p);
    }

    private void handleClick(SkillTreeClickEvent e) {
        SkillTreeViewState view = e.view;
        Player p = e.player;

        SkillNode node = mapSlotToNode(view, e.slot);
        if (node == null) return;

        if (!canUnlock(view, node)) return;

        if (!applySkillEffects(p, view, node)) return;

        node.unlocked = true;
        playUnlockAnimation(p, node);

        transport.update(p, view);
    }

    private SkillNode mapSlotToNode(SkillTreeViewState view, int slot) {
        int[] grid = CraftMineLayout.fromSlot(slot);
        if (grid[0] < 0 || grid[1] < 0) return null;

        int worldX = grid[0] + view.offsetX;
        int worldY = grid[1] + view.offsetY;

        for (SkillNode node : view.tree.nodes.values()) {
            if (node.x == worldX && node.y == worldY) {
                return node;
            }
        }
        return null;
    }

    private boolean canUnlock(SkillTreeViewState view, SkillNode node) {
        if (node.parents == null || node.parents.isEmpty()) return true;
        for (String parent : node.parents) {
            SkillNode p = view.tree.get(parent);
            if (p == null || !p.unlocked) return false;
        }
        return true;
    }

    private boolean applySkillEffects(Player player, SkillTreeViewState view, SkillNode node) {
        SkillForgePlugin plugin = SkillForgePlugin.getInstance();
        if (plugin == null || plugin.getSkillTreeSystem() == null || plugin.getPlayerDataManager() == null) {
            player.sendMessage(ChatColor.RED + "Skill system is unavailable.");
            return false;
        }

        com.skilltree.plugin.systems.SkillTreeSystem.UpgradeResult result =
                plugin.getSkillTreeSystem().tryUpgradeSkill(player, node.id);
        if (result != com.skilltree.plugin.systems.SkillTreeSystem.UpgradeResult.SUCCESS) {
            player.sendMessage(ChatColor.RED + plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, node.id, result));
            return false;
        }

        int level = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(node.id);
        node.unlocked = level > 0;
        refreshNodeLevelLore(node, level);
        player.sendMessage(ChatColor.GREEN + "Upgraded " + node.name + " to level " + level + ".");
        return true;
    }

    private void refreshNodeLevelLore(SkillNode node, int level) {
        if (node.lore == null) return;
        List<String> lore = node.lore;
        lore.removeIf(line -> {
            if (line == null) return false;
            String clean = ChatColor.stripColor(line);
            return clean != null && clean.toLowerCase().startsWith("level:");
        });
        SkillForgePlugin plugin = SkillForgePlugin.getInstance();
        int max = 100;
        if (plugin != null && plugin.getSkillTreeSystem() != null) {
            com.skilltree.plugin.systems.SkillTreeSystem.SkillNode s = plugin.getSkillTreeSystem().getAllSkillNodes().get(node.id);
            if (s != null) max = s.getMaxLevel();
        }
        lore.add(ChatColor.GRAY + "Level: " + level + "/" + max);
    }

    private void playUnlockAnimation(Player player, SkillNode node) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
    }
}
