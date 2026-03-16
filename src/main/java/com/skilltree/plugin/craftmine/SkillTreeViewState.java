package com.skilltree.plugin.craftmine;

import java.util.UUID;

public final class SkillTreeViewState {

    public final UUID playerId;
    public final SkillTree tree;

    public int offsetX = 0;
    public int offsetY = 0;

    public SkillTreeViewState(UUID playerId, SkillTree tree) {
        this.playerId = playerId;
        this.tree = tree;
    }
}

