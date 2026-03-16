package com.skilltree.plugin.craftmine;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public final class SkillTreeClickEvent {

    public final Player player;
    public final SkillTreeViewState view;
    public final int slot;
    public final ClickType click;

    public SkillTreeClickEvent(Player player, SkillTreeViewState view,
                               int slot, ClickType click) {
        this.player = player;
        this.view = view;
        this.slot = slot;
        this.click = click;
    }
}

