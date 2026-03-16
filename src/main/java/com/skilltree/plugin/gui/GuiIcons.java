package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiIcons {
    private GuiIcons() {}
    private static final Set<String> warnedNexoIds = ConcurrentHashMap.newKeySet();

    public static ItemStack icon(SkillForgePlugin plugin, String key, Material fallbackMaterial, String name, List<String> lore) {
        ItemStack item = resolveNexoIcon(plugin, key);
        if (item == null) {
            Material material = resolveMaterial(plugin, key, fallbackMaterial);
            item = new ItemStack(material);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);

        String modelPath = plugin.getConfig().getString("gui.icons." + key + ".item-model", "");
        if (modelPath != null && !modelPath.isBlank()) {
            NamespacedKey itemModel = NamespacedKey.fromString(modelPath.trim());
            if (itemModel != null) {
                meta.setItemModel(itemModel);
            }
        }

        int customModelData = plugin.getConfig().getInt("gui.icons." + key + ".custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack resolveNexoIcon(SkillForgePlugin plugin, String key) {
        String id = plugin.getConfig().getString("gui.icons." + key + ".nexo-id", "");
        if (id == null || id.isBlank()) return null;

        Plugin nexo = findPluginIgnoreCase("Nexo");
        if (nexo == null || !nexo.isEnabled()) {
            warnNexoOnce(plugin, id, "Nexo plugin not found/enabled");
            return null;
        }

        Object raw = invokeNexoFactory("com.nexomc.nexo.api.NexoItems", id)
                .orElseGet(() -> invokeNexoFactory("com.nexomc.nexo.api.items.NexoItems", id)
                        .orElseGet(() -> invokeNexoFactory("com.nexomc.nexo.api.NexoItemsAPI", id)
                                .orElse(null)));
        ItemStack stack = toItemStack(raw, 0);
        if (stack == null || stack.getType() == Material.AIR) {
            warnNexoOnce(plugin, id, "item id could not be resolved");
            return null;
        }
        return stack;
    }

    private static Plugin findPluginIgnoreCase(String name) {
        Plugin exact = Bukkit.getPluginManager().getPlugin(name);
        if (exact != null) return exact;
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) return plugin;
        }
        return null;
    }

    private static Optional<Object> invokeNexoFactory(String className, String id) {
        try {
            Class<?> clazz = Class.forName(className);
            for (String methodName : new String[]{"itemFromId", "getItemById", "getItem", "fromId"}) {
                try {
                    Method m = clazz.getMethod(methodName, String.class);
                    return Optional.ofNullable(m.invoke(null, id));
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static ItemStack toItemStack(Object raw, int depth) {
        if (raw == null || depth > 4) return null;
        if (raw instanceof ItemStack stack) return stack.clone();
        if (raw instanceof Optional<?> optional) return optional.map(v -> toItemStack(v, depth + 1)).orElse(null);

        for (String accessor : new String[]{"build", "getItemStack", "asItemStack", "getItem", "toItemStack"}) {
            try {
                Method m = raw.getClass().getMethod(accessor);
                Object nested = m.invoke(raw);
                ItemStack result = toItemStack(nested, depth + 1);
                if (result != null) return result;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void warnNexoOnce(SkillForgePlugin plugin, String id, String reason) {
        String token = id + "|" + reason;
        if (warnedNexoIds.add(token)) {
            plugin.getLogger().warning("[GuiIcons] Nexo icon '" + id + "' fallback used (" + reason + ").");
        }
    }

    private static Material resolveMaterial(SkillForgePlugin plugin, String key, Material fallbackMaterial) {
        String configured = plugin.getConfig().getString("gui.icons." + key + ".material");
        if (configured == null || configured.isBlank()) {
            return fallbackMaterial;
        }
        Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallbackMaterial : material;
    }
}
