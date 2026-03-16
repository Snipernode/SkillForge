package com.skilltree.plugin.craftmine;

import org.bukkit.Material;

import java.util.List;

public final class SkillNode {

    public final String id;
    public final String name;
    public final List<String> lore;
    public final Material icon;
    public final List<String> parents;
    public final int x;
    public final int y;

    public boolean unlocked = false;

    public SkillNode(String id, String name, List<String> lore,
                     Material icon, List<String> parents, int x, int y) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.icon = icon;
        this.parents = parents;
        this.x = x;
        this.y = y;
    }
}

