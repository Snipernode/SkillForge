package com.skilltree.plugin.craftmine;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface CraftMineScreenTransport {

    void open(Player player, SkillTreeViewState view);

    void update(Player player, SkillTreeViewState view);

    void close(Player player);

    void onClick(Consumer<SkillTreeClickEvent> handler);
}

