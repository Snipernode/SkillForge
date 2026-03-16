package com.skilltree.plugin.gui;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ShopItemRegistry {
    private static final List<ShopItem> ITEMS = new ArrayList<>();

    static {
        // Overworld
        register(Material.COAL, "Coal", 1, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.IRON_INGOT, "Iron Ingot", 5, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.GOLD_INGOT, "Gold Ingot", 8, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.DIAMOND, "Diamond", 500000, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.EMERALD, "Emerald", 40, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.LAPIS_LAZULI, "Lapis Lazuli", 3, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.REDSTONE, "Redstone", 2, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.COPPER_INGOT, "Copper Ingot", 2, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.RAW_IRON, "Raw Iron", 4, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.RAW_GOLD, "Raw Gold", 7, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.OAK_LOG, "Oak Log", 1, ShopCategory.OVERWORLD_RESOURCES);
        register(Material.COBBLESTONE, "Cobblestone", 1, ShopCategory.OVERWORLD_RESOURCES);

        // Nether
        register(Material.NETHERRACK, "Netherrack", 1, ShopCategory.NETHER_RESOURCES);
        register(Material.SOUL_SAND, "Soul Sand", 2, ShopCategory.NETHER_RESOURCES);
        register(Material.GLOWSTONE_DUST, "Glowstone Dust", 4, ShopCategory.NETHER_RESOURCES);
        register(Material.QUARTZ, "Nether Quartz", 5, ShopCategory.NETHER_RESOURCES);
        register(Material.ANCIENT_DEBRIS, "Ancient Debris", 250, ShopCategory.NETHER_RESOURCES);
        register(Material.MAGMA_BLOCK, "Magma Block", 3, ShopCategory.NETHER_RESOURCES);
        register(Material.NETHER_BRICK, "Nether Brick", 2, ShopCategory.NETHER_RESOURCES);

        // Seeds & Farming
        register(Material.WHEAT_SEEDS, "Wheat Seeds", 1, ShopCategory.SEEDS_AND_FARMING);
        register(Material.PUMPKIN_SEEDS, "Pumpkin Seeds", 2, ShopCategory.SEEDS_AND_FARMING);
        register(Material.MELON_SEEDS, "Melon Seeds", 2, ShopCategory.SEEDS_AND_FARMING);
        register(Material.BEETROOT_SEEDS, "Beetroot Seeds", 1, ShopCategory.SEEDS_AND_FARMING);
        register(Material.CARROT, "Carrot", 2, ShopCategory.SEEDS_AND_FARMING);
        register(Material.POTATO, "Potato", 2, ShopCategory.SEEDS_AND_FARMING);
        register(Material.SUGAR_CANE, "Sugar Cane", 3, ShopCategory.SEEDS_AND_FARMING);
        register(Material.BAMBOO, "Bamboo", 2, ShopCategory.SEEDS_AND_FARMING);
    }

    private static void register(Material m, String name, int cost, ShopCategory cat) {
        ITEMS.add(new ShopItem(m, name, cost, cat));
    }

    public static List<ShopItem> getItemsByCategory(ShopCategory category) {
        return ITEMS.stream().filter(i -> i.category() == category).collect(Collectors.toList());
    }

    private ShopItemRegistry() {}
}
