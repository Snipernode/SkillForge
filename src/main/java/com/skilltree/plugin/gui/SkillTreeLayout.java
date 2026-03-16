package com.skilltree.plugin.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SkillTreeLayout {
    private SkillTreeLayout() {}

    // Top 5 rows of a 6-row chest inventory.
    public static final int[] FALLBACK_TREE_SLOTS = new int[] {
            22,
            13, 15,
            12, 14, 16,
            21, 23, 25,
            20, 24, 28, 30, 32, 34,
            11, 17, 19, 26, 27, 29, 31, 33, 35, 10, 18
    };

    private static final Map<String, Map<String, Integer>> CATEGORY_SLOTS = new HashMap<>();

    static {
        // combat
        put("combat", "combat_endurance", 22);
        put("combat", "combat_strength", 13);
        put("combat", "combat_defense", 15);
        put("combat", "combat_critical", 12);
        put("combat", "combat_precision", 14);
        put("combat", "combat_parry", 16);
        put("combat", "combat_regeneration", 21);
        put("combat", "combat_whirlwind", 23);
        put("combat", "combat_execute", 25);
        put("combat", "combat_laststand", 30);
        put("combat", "beserker", 34);

        // mining
        put("mining", "mining_efficiency", 22);
        put("mining", "mining_stoneworking", 13);
        put("mining", "mining_durability", 15);
        put("mining", "mining_fortune", 21);
        put("mining", "mining_prospector", 23);
        put("mining", "mining_excavation", 25);
        put("mining", "mining_explosive", 30);
        put("mining", "mining_gemdiscovery", 34);

        // agility
        put("agility", "agility_speed", 22);
        put("agility", "agility_jump", 13);
        put("agility", "agility_dodge", 15);
        put("agility", "agility_acrobatics", 21);
        put("agility", "agility_parkour", 23);
        put("agility", "agility_shadowstep", 25);
        put("agility", "agility_climbing", 30);
        put("agility", "agility_blink", 32);
        put("agility", "agility_timeshift", 34);

        // intellect
        put("intellect", "intellect_wisdom", 22);
        put("intellect", "intellect_scholarship", 13);
        put("intellect", "intellect_enchanting", 21);
        put("intellect", "intellect_brewing", 23);
        put("intellect", "intellect_arcana", 30);
        put("intellect", "intellect_alchemy", 32);
        put("intellect", "intellect_mindblast", 24);
        put("intellect", "intellect_mindshield", 26);
        put("intellect", "intellect_amplify", 34);

        // farming
        put("farming", "farming_growth", 22);
        put("farming", "farming_harvest", 13);
        put("farming", "farming_irrigation", 15);
        put("farming", "farming_breeding", 21);
        put("farming", "farming_pestcontrol", 23);
        put("farming", "farming_bountyharvest", 30);
        put("farming", "farming_summongolem", 34);

        // fishing
        put("fishing", "fishing_luck", 22);
        put("fishing", "fishing_speed", 13);
        put("fishing", "fishing_rare", 21);
        put("fishing", "fishing_underwater", 23);
        put("fishing", "fishing_deeptreasure", 30);
        put("fishing", "fishing_summonwhale", 32);

        // magic
        put("magic", "magic_fireball", 22);
        put("magic", "magic_frostbolt", 13);
        put("magic", "magic_spellcraft", 15);
        put("magic", "magic_manapool", 21);
        put("magic", "magic_manashield", 23);
        put("magic", "magic_chainlightning", 25);
        put("magic", "magic_invisibility", 30);
        put("magic", "magic_meteor", 32);
        put("magic", "magic_timefreeze", 34);

        // mastery
        put("mastery", "mastery_swords", 22);
        put("mastery", "mastery_axes", 13);
        put("mastery", "mastery_bows", 15);
        put("mastery", "mastery_flail", 21);
        put("mastery", "mastery_firearms", 23);
        put("mastery", "mastery_brick_bulwark", 24);
        put("mastery", "mastery_brick_brawler", 25);
        put("mastery", "mastery_brick_duelist", 26);
        put("mastery", "mastery_brick_ranger", 30);
        put("mastery", "mastery_brick_mage", 31);
        put("mastery", "mastery_brick_harvester", 32);
        put("mastery", "mastery_brick_miner", 33);
        put("mastery", "mastery_brick_explorer", 34);
        put("mastery", "mastery_brick_support", 35);
        put("mastery", "mastery_brick_controller", 29);
    }

    public static Map<String, Integer> getCategorySlotMap(String category) {
        if (category == null) return Collections.emptyMap();
        Map<String, Integer> map = CATEGORY_SLOTS.get(category.toLowerCase(Locale.ROOT));
        if (map == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(map);
    }

    private static void put(String category, String skillId, int slot) {
        CATEGORY_SLOTS.computeIfAbsent(category, k -> new HashMap<>())
                .put(skillId, slot);
    }
}
