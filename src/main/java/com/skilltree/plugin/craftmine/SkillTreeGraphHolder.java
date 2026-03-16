package com.skilltree.plugin.craftmine;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder for SkillForge graph-style skill tree inventories.
 * This gives us a dedicated GUI type signature instead of relying on title text.
 */
public final class SkillTreeGraphHolder implements InventoryHolder {

    private final UUID viewer;
    private final String treeId;

    public SkillTreeGraphHolder(UUID viewer, String treeId) {
        this.viewer = viewer;
        this.treeId = treeId == null ? "unknown" : treeId;
    }

    public UUID getViewer() {
        return viewer;
    }

    public String getTreeId() {
        return treeId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
