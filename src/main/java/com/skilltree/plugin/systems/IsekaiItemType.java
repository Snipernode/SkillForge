package com.skilltree.plugin.systems;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum IsekaiItemType {
    SOULBLADE("soulblade", "Soulblade", Material.NETHERITE_SWORD,
            Arrays.asList("A blade that grows with its bearer.", "Favored by relentless duelists."),
            Arrays.asList("active_ember_wave", "active_soul_dash")),
    STARBOW("starbow", "Starbow", Material.BOW,
            Arrays.asList("A bow that calls starfire.", "Strikes true across any field."),
            Arrays.asList("active_arcane_burst", "active_void_grasp")),
    STORMLANCE("stormlance", "Stormlance", Material.TRIDENT,
            Arrays.asList("A lance that rides the thunder.", "Enemies falter at its call."),
            Arrays.asList("active_time_dilation", "active_void_grasp")),
    SUNHAMMER("sunhammer", "Sunhammer", Material.NETHERITE_AXE,
            Arrays.asList("A hammer forged for sundering shields.", "Heavy, radiant, decisive."),
            Arrays.asList("active_ember_wave", "active_arcane_burst")),
    WARDSTAFF("wardstaff", "Wardstaff", Material.BLAZE_ROD,
            Arrays.asList("A staff etched with living runes.", "Bestows protection and control."),
            Arrays.asList("active_time_dilation", "active_soul_dash")),
    SHADEDAGGER("shadedagger", "Shade Dagger", Material.NETHERITE_SWORD,
            Arrays.asList("A silent edge for swift strikes.", "Rewards precision and speed."),
            Arrays.asList("active_soul_dash", "active_void_grasp"));

    private final String key;
    private final String displayName;
    private final Material material;
    private final List<String> description;
    private final List<String> activePool;

    IsekaiItemType(String key, String displayName, Material material, List<String> description, List<String> activePool) {
        this.key = key;
        this.displayName = displayName;
        this.material = material;
        this.description = description;
        this.activePool = activePool;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public List<String> getDescription() {
        return description == null ? Collections.emptyList() : description;
    }

    public List<String> getActivePool() {
        return activePool == null ? Collections.emptyList() : activePool;
    }

    public static IsekaiItemType fromKey(String key) {
        if (key == null) return null;
        for (IsekaiItemType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
