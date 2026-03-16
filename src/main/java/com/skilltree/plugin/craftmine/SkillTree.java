package com.skilltree.plugin.craftmine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillTree {

    public final String id;
    public final String title;
    public final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillTree(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public void add(SkillNode node) {
        nodes.put(node.id, node);
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }
}

