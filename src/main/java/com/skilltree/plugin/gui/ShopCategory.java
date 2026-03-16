package com.skilltree.plugin.gui;

public enum ShopCategory {
    OVERWORLD_RESOURCES("§aOverworld Resources"),
    NETHER_RESOURCES("§cNether Resources"),
    SEEDS_AND_FARMING("§eSeeds & Farming"),
    MISC("§7Miscellaneous");

    private final String displayName;

    ShopCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
