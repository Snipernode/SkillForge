package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.gui.GuiStyle;
import com.skilltree.plugin.managers.CrateKeyLedger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CrateBlockSystem implements Listener {
    private static final String PREVIEW_PREFIX = "Crate Rewards: ";
    private static final String ROLL_PREFIX = "Crate Roll: ";

    private final SkillForgePlugin plugin;
    private final CrateKeyLedger keyLedger;
    private final Random random = new Random();
    private final Map<String, String> placedCrates = new HashMap<>();
    private final Map<UUID, SpinSession> spinSessions = new HashMap<>();
    private final Set<String> warnedNexoIds = ConcurrentHashMap.newKeySet();

    private final NamespacedKey cratePlacerKey;
    private final NamespacedKey crateTypeKey;
    private final NamespacedKey crateKeyItemKey;
    private final NamespacedKey crateKeyTypeKey;

    public CrateBlockSystem(SkillForgePlugin plugin, CrateKeyLedger keyLedger) {
        this.plugin = plugin;
        this.keyLedger = keyLedger;
        this.cratePlacerKey = new NamespacedKey(plugin, "crate_placer");
        this.crateTypeKey = new NamespacedKey(plugin, "crate_type");
        this.crateKeyItemKey = new NamespacedKey(plugin, "crate_key_item");
        this.crateKeyTypeKey = new NamespacedKey(plugin, "crate_key_type");
        loadPlacedCrates();
    }

    public boolean isValidCrateType(String type) {
        if (type == null || type.isBlank()) return false;
        return plugin.getConfig().isConfigurationSection("crates.types." + type.toLowerCase(Locale.ROOT));
    }

    public ItemStack createCratePlacerItem(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        Material mat = getMaterial("crates.placer_item.material", Material.CHEST);
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getConfig().getString("crates.placer_item.name", "&6Crate Placer &7(%type%)");
        meta.setDisplayName(color(name.replace("%type%", normalized)));
        List<String> lore = plugin.getConfig().getStringList("crates.placer_item.lore");
        if (lore == null || lore.isEmpty()) {
            lore = Arrays.asList(
                    "&7Right-click a block face to place.",
                    "&7Type: &e%type%",
                    "&7Shift-right-click crate to edit."
            );
        }
        List<String> finalLore = new ArrayList<>();
        for (String line : lore) {
            finalLore.add(color(line.replace("%type%", normalized)));
        }
        meta.setLore(finalLore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(cratePlacerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(crateTypeKey, PersistentDataType.STRING, normalized);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKeyItem(String type, int amount) {
        String normalized = type.toLowerCase(Locale.ROOT);
        Material mat = getMaterial("crates.key_item.material", Material.TRIPWIRE_HOOK);
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getConfig().getString("crates.key_item.name", "&e%type% Crate Key");
        meta.setDisplayName(color(name.replace("%type%", normalized)));
        List<String> lore = plugin.getConfig().getStringList("crates.key_item.lore");
        if (lore == null || lore.isEmpty()) {
            lore = Arrays.asList(
                    "&7Use on a placed crate.",
                    "&7Type: &e%type%"
            );
        }
        List<String> finalLore = new ArrayList<>();
        for (String line : lore) {
            finalLore.add(color(line.replace("%type%", normalized)));
        }
        meta.setLore(finalLore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(crateKeyItemKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(crateKeyTypeKey, PersistentDataType.STRING, normalized);
        item.setItemMeta(meta);
        return item;
    }

    public long giveKeyItems(Player player, String type, long amount) {
        if (player == null || type == null || amount <= 0) return 0L;
        String normalized = type.toLowerCase(Locale.ROOT);
        long remaining = amount;
        while (remaining > 0) {
            int stackSize = (int) Math.min(64L, remaining);
            ItemStack keyStack = createKeyItem(normalized, stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(keyStack);
            if (!overflow.isEmpty()) {
                for (ItemStack extra : overflow.values()) {
                    if (extra == null || extra.getType() == Material.AIR) continue;
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
            remaining -= stackSize;
        }
        return amount;
    }

    public long getTotalKeyCount(Player player, String type) {
        if (player == null || type == null) return 0L;
        String normalized = type.toLowerCase(Locale.ROOT);
        long itemKeys = countKeyItems(player, normalized);
        long ledgerKeys = keyLedger.getKeys(player.getUniqueId(), normalized);
        return itemKeys + ledgerKeys;
    }

    public boolean consumeOneKey(Player player, String type) {
        if (player == null || type == null) return false;
        String normalized = type.toLowerCase(Locale.ROOT);

        if (removeOneKeyItem(player, normalized)) {
            return true;
        }
        return keyLedger.removeKeys(player.getUniqueId(), normalized, 1L) >= 0;
    }

    public List<ItemStack> getRewards(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("crates.types." + normalized);
        if (section == null) return out;

        List<?> rawItems = section.getList("rewards_items");
        if (rawItems != null) {
            for (Object o : rawItems) {
                if (o instanceof ItemStack stack && stack.getType() != Material.AIR) {
                    out.add(stack.clone());
                }
            }
        }
        if (!out.isEmpty()) return out;

        List<String> legacy = section.getStringList("rewards");
        for (String raw : legacy) {
            ItemStack parsed = parseLegacyReward(raw);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    public void openRewardsPreview(Player player, String type) {
        if (player == null || type == null) return;
        List<ItemStack> rewards = getRewards(type);
        if (rewards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This crate has no rewards configured.");
            return;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        Inventory inv = Bukkit.createInventory(null, 54, GuiStyle.title(PREVIEW_PREFIX + normalized));
        double chance = rewards.isEmpty() ? 0.0 : (100.0 / rewards.size());
        int slot = 0;
        for (ItemStack reward : rewards) {
            if (slot >= 45) break;
            ItemStack show = reward.clone();
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ChatColor.GRAY + "Chance: " + String.format(Locale.US, "%.2f%%", chance));
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inv.setItem(slot++, show);
        }

        long keys = getTotalKeyCount(player, normalized);
        inv.setItem(49, GuiStyle.item(Material.TRIPWIRE_HOOK, ChatColor.GOLD + "Your Keys: " + keys,
                Arrays.asList(ChatColor.GRAY + "Left-click the crate block to roll.",
                        ChatColor.GRAY + "Click STOP in roll GUI to claim.")));
        inv.setItem(53, GuiStyle.item(Material.BARRIER, ChatColor.RED + "Close", Collections.emptyList()));
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        player.openInventory(inv);
    }

    public void openRollGui(Player player, String type) {
        if (player == null || type == null) return;
        List<ItemStack> rewards = getRewards(type);
        if (rewards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This crate has no rewards configured.");
            return;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        long keys = getTotalKeyCount(player, normalized);
        if (keys <= 0) {
            player.sendMessage(ChatColor.RED + "You need a " + normalized + " crate key.");
            return;
        }

        stopSpin(player, false);
        Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title(ROLL_PREFIX + normalized));
        inv.setItem(22, GuiStyle.item(Material.LEVER, ChatColor.YELLOW + "Stop Rotation",
                Collections.singletonList(ChatColor.GRAY + "Click to stop and claim.")));
        inv.setItem(26, GuiStyle.item(Material.BARRIER, ChatColor.RED + "Close",
                Collections.singletonList(ChatColor.GRAY + "Close without opening.")));
        GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
        player.openInventory(inv);

        SpinSession session = new SpinSession(normalized, rewards, inv);
        int interval = Math.max(1, plugin.getConfig().getInt("crates.spin.tick_interval", 2));
        int autoStopTicks = Math.max(40, plugin.getConfig().getInt("crates.spin.auto_stop_ticks", 200));

        session.task = new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopSpin(player, false);
                    cancel();
                    return;
                }
                if (elapsed >= autoStopTicks) {
                    stopSpin(player, true);
                    cancel();
                    return;
                }
                rollDisplay(session);
                elapsed += interval;
            }
        };
        spinSessions.put(player.getUniqueId(), session);
        session.task.runTaskTimer(plugin, 0L, interval);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        Action action = event.getAction();

        if ((action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) && isCratePlacer(hand)) {
            if (!player.hasPermission("skillforge.crate.admin")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return;
            }
            String type = getCratePlacerType(hand);
            if (type == null || !isValidCrateType(type)) {
                player.sendMessage(ChatColor.RED + "Invalid crate type in placer.");
                return;
            }
            if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
                player.sendMessage(ChatColor.YELLOW + "Right-click a block face to place the crate.");
                return;
            }
            event.setCancelled(true);
            placeCrateFromPlacer(player, event.getClickedBlock(), event.getBlockFace(), hand, type);
            return;
        }

        if (event.getClickedBlock() == null) return;
        String crateType = getPlacedCrateType(event.getClickedBlock());
        if (crateType == null) return;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (player.isSneaking() && player.hasPermission("skillforge.crate.admin")) {
                if (plugin.getCrateLootTableGUI() == null) {
                    player.sendMessage(ChatColor.RED + "Crate editor GUI unavailable.");
                    return;
                }
                plugin.getCrateLootTableGUI().openEditor(player, crateType);
                return;
            }
            openRewardsPreview(player, crateType);
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            openRollGui(player, crateType);
        }
    }

    @EventHandler
    public void onCrateBreak(BlockBreakEvent event) {
        if (event == null || event.getBlock() == null || event.getPlayer() == null) return;
        String crateType = getPlacedCrateType(event.getBlock());
        if (crateType == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("skillforge.crate.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break crate blocks.");
            return;
        }
        if (!player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Sneak + break to remove placed crate.");
            return;
        }
        removePlacedCrate(event.getBlock());
        player.sendMessage(ChatColor.RED + "Removed crate: " + crateType);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String plainTitle = plainTitle(event.getView().getTitle());
        if (plainTitle.contains(PREVIEW_PREFIX.toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
            if (event.getRawSlot() == 53) {
                player.closeInventory();
            }
            return;
        }

        if (plainTitle.contains(ROLL_PREFIX.toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                stopSpin(player, true);
            } else if (event.getRawSlot() == 26) {
                stopSpin(player, false);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String plainTitle = plainTitle(event.getView().getTitle());
        if (plainTitle.contains(PREVIEW_PREFIX.toLowerCase(Locale.ROOT))
                || plainTitle.contains(ROLL_PREFIX.toLowerCase(Locale.ROOT))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String plainTitle = plainTitle(event.getView().getTitle());
        if (!plainTitle.contains(ROLL_PREFIX.toLowerCase(Locale.ROOT))) return;
        stopSpin(player, false);
    }

    private void placeCrateFromPlacer(Player player, Block clicked, BlockFace face, ItemStack hand, String type) {
        if (face == null) {
            player.sendMessage(ChatColor.RED + "Could not place crate on that face.");
            return;
        }
        Block target;
        target = clicked.getRelative(face);
        if (!target.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Target block must be empty.");
            return;
        }
        Material crateMaterial = getMaterial("crates.types." + type + ".block_material",
                getMaterial("crates.block_material", Material.ENDER_CHEST));
        target.setType(crateMaterial);
        setPlacedCrate(target, type);
        player.sendMessage(ChatColor.GREEN + "Placed " + type + " crate.");

        if (player.getGameMode() != GameMode.CREATIVE && hand != null) {
            int amount = hand.getAmount();
            if (amount <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(amount - 1);
                player.getInventory().setItemInMainHand(hand);
            }
        }
    }

    private void rollDisplay(SpinSession session) {
        if (session == null || session.inventory == null || session.rewards.isEmpty()) return;
        ItemStack left = randomReward(session.rewards);
        ItemStack mid = randomReward(session.rewards);
        ItemStack right = randomReward(session.rewards);
        session.inventory.setItem(12, left);
        session.inventory.setItem(13, mid);
        session.inventory.setItem(14, right);
        session.currentReward = mid;
    }

    private ItemStack randomReward(List<ItemStack> rewards) {
        return rewards.get(random.nextInt(rewards.size())).clone();
    }

    private void stopSpin(Player player, boolean claimReward) {
        if (player == null) return;
        SpinSession session = spinSessions.remove(player.getUniqueId());
        if (session == null) return;
        if (session.task != null) {
            session.task.cancel();
        }
        if (!claimReward) return;

        if (!consumeOneKey(player, session.type)) {
            player.sendMessage(ChatColor.RED + "You do not have a " + session.type + " crate key.");
            return;
        }

        ItemStack reward = session.currentReward != null ? session.currentReward.clone() : randomReward(session.rewards);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
        if (!overflow.isEmpty()) {
            for (ItemStack extra : overflow.values()) {
                if (extra != null && extra.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
        }

        String itemName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(reward.getItemMeta().getDisplayName())
                : reward.getType().name();
        player.sendMessage(ChatColor.GREEN + "Crate opened: " + reward.getAmount() + "x " + itemName + ".");
        player.closeInventory();
    }

    private boolean isCratePlacer(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cratePlacerKey, PersistentDataType.BYTE);
    }

    private String getCratePlacerType(ItemStack item) {
        if (!isCratePlacer(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(crateTypeKey, PersistentDataType.STRING);
    }

    private boolean isKeyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(crateKeyItemKey, PersistentDataType.BYTE);
    }

    private String getKeyItemType(ItemStack item) {
        if (!isKeyItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(crateKeyTypeKey, PersistentDataType.STRING);
    }

    private long countKeyItems(Player player, String type) {
        if (player == null || type == null) return 0L;
        long count = 0L;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!isKeyItem(stack)) continue;
            String keyType = getKeyItemType(stack);
            if (keyType == null || !keyType.equalsIgnoreCase(type)) continue;
            count += stack.getAmount();
        }
        return count;
    }

    private boolean removeOneKeyItem(Player player, String type) {
        if (player == null || type == null) return false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!isKeyItem(stack)) continue;
            String keyType = getKeyItemType(stack);
            if (keyType == null || !keyType.equalsIgnoreCase(type)) continue;
            int amount = stack.getAmount();
            if (amount <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(amount - 1);
                player.getInventory().setItem(slot, stack);
            }
            return true;
        }
        return false;
    }

    private String getPlacedCrateType(Block block) {
        if (block == null || block.getWorld() == null) return null;
        return placedCrates.get(locKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private void setPlacedCrate(Block block, String type) {
        if (block == null || block.getWorld() == null || type == null) return;
        placedCrates.put(locKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()), type.toLowerCase(Locale.ROOT));
        savePlacedCrates();
    }

    private void removePlacedCrate(Block block) {
        if (block == null || block.getWorld() == null) return;
        placedCrates.remove(locKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        savePlacedCrates();
    }

    private void loadPlacedCrates() {
        placedCrates.clear();
        List<String> entries = plugin.getConfig().getStringList("crates.placed_entries");
        if (entries == null) return;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split(";", 5);
            if (parts.length < 5) continue;
            String world = parts[0];
            int x = parseInt(parts[1], Integer.MIN_VALUE);
            int y = parseInt(parts[2], Integer.MIN_VALUE);
            int z = parseInt(parts[3], Integer.MIN_VALUE);
            String type = parts[4].toLowerCase(Locale.ROOT);
            if (world.isBlank() || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) continue;
            if (!isValidCrateType(type)) continue;
            placedCrates.put(locKey(world, x, y, z), type);
        }
    }

    private void savePlacedCrates() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : placedCrates.entrySet()) {
            out.add(entry.getKey() + ";" + entry.getValue());
        }
        plugin.getConfig().set("crates.placed_entries", out);
        plugin.saveConfig();
    }

    private ItemStack parseLegacyReward(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        if (parts.length >= 2) {
            String prefix = parts[0].trim();
            if (prefix.equalsIgnoreCase("NEXO") || prefix.equalsIgnoreCase("NEXO_ITEM")) {
                String nexoId = parts[1].trim();
                int amount = parts.length >= 3 ? parseInt(parts[2].trim(), 1) : 1;
                ItemStack nexoItem = resolveNexoItem(nexoId, Math.max(1, amount));
                if (nexoItem != null && nexoItem.getType() != Material.AIR) {
                    return nexoItem;
                }
                warnNexoOnce(nexoId, "crate reward id could not be resolved");
                return null;
            }
        }

        Material mat = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if (mat == null) return null;
        int amount = 1;
        if (parts.length >= 2) {
            amount = parseInt(parts[1].trim(), 1);
        }
        return new ItemStack(mat, Math.max(1, amount));
    }

    private ItemStack resolveNexoItem(String id, int amount) {
        if (id == null || id.isBlank()) return null;
        Plugin nexo = findPluginIgnoreCase("Nexo");
        if (nexo == null || !nexo.isEnabled()) {
            return null;
        }

        Object raw = invokeNexoFactory("com.nexomc.nexo.api.NexoItems", id)
                .orElseGet(() -> invokeNexoFactory("com.nexomc.nexo.api.items.NexoItems", id)
                        .orElseGet(() -> invokeNexoFactory("com.nexomc.nexo.api.NexoItemsAPI", id)
                                .orElse(null)));
        ItemStack stack = toItemStack(raw, 0);
        if (stack == null || stack.getType() == Material.AIR) return null;
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    private Plugin findPluginIgnoreCase(String name) {
        Plugin exact = Bukkit.getPluginManager().getPlugin(name);
        if (exact != null) return exact;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private Optional<Object> invokeNexoFactory(String className, String id) {
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

    private ItemStack toItemStack(Object raw, int depth) {
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

    private void warnNexoOnce(String id, String reason) {
        String token = id + "|" + reason;
        if (warnedNexoIds.add(token)) {
            plugin.getLogger().warning("[Crates] Nexo item '" + id + "' skipped (" + reason + ").");
        }
    }

    private Material getMaterial(String path, Material fallback) {
        String raw = plugin.getConfig().getString(path, fallback.name());
        Material parsed = raw != null ? Material.matchMaterial(raw) : null;
        return parsed != null ? parsed : fallback;
    }

    private String locKey(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private String plainTitle(String raw) {
        String stripped = ChatColor.stripColor(raw);
        if (stripped == null) stripped = raw;
        if (stripped == null) stripped = "";
        return stripped.toLowerCase(Locale.ROOT);
    }

    private int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    private static final class SpinSession {
        private final String type;
        private final List<ItemStack> rewards;
        private final Inventory inventory;
        private BukkitRunnable task;
        private ItemStack currentReward;

        private SpinSession(String type, List<ItemStack> rewards, Inventory inventory) {
            this.type = type;
            this.rewards = rewards;
            this.inventory = inventory;
        }
    }
}
