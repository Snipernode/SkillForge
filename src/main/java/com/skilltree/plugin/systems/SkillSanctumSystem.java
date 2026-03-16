package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillSanctumSystem implements Listener {

    private static final List<String> CATEGORIES = List.of(
            "combat", "mining", "agility", "intellect", "farming", "fishing", "magic", "mastery"
    );

    private static final int CELL_SPACING = 96;
    private static final int ROOM_RADIUS = 16;
    private static final int ROOM_HEIGHT = 12;
    private static final int AVAILABLE_PAGE_SIZE = 6;
    private static final int SANCTUM_BUILD_VERSION = 2;

    private final SkillForgePlugin plugin;
    private final Map<UUID, SanctumSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> builtCells = ConcurrentHashMap.newKeySet();
    private final Map<String, DustInfo> dustByCategory = new HashMap<>();

    public SkillSanctumSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        initializeDust();
        startParticleLoop();
    }

    public void open(Player player) {
        open(player, "combat");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("skill_sanctum.enabled", true);
    }

    public void open(Player player, String rawCategory) {
        if (player == null) return;
        if (!isEnabled()) {
            player.sendMessage(ChatColor.RED + "The Skill Sanctum is disabled.");
            return;
        }

        if ("exit".equalsIgnoreCase(rawCategory) || "leave".equalsIgnoreCase(rawCategory)) {
            returnPlayer(player);
            return;
        }

        World world = ensureWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "The Skill Sanctum is unavailable right now.");
            return;
        }

        String category = normalizeCategory(rawCategory);
        SanctumSession session = sessions.computeIfAbsent(player.getUniqueId(), uuid -> new SanctumSession(uuid));
        session.category = category;
        session.cellOrigin = getCellOrigin(world, player.getUniqueId());

        if (!isInsideSanctum(player.getLocation())) {
            session.returnLocation = player.getLocation().clone();
            session.returnGameMode = player.getGameMode();
        }

        buildChamberIfNeeded(session.cellOrigin);
        renderSession(player, session);

        Location spawn = getSpawnLocation(session.cellOrigin);
        if (!sameBlock(player.getLocation(), spawn)) {
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.sendTitle(ChatColor.GREEN + "Skill Sanctum", ChatColor.YELLOW + "Choose your next upgrade path", 10, 40, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
    }

    public void returnPlayer(Player player) {
        if (player == null) return;
        SanctumSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        Location destination = session.returnLocation;
        if (destination == null || destination.getWorld() == null) {
            destination = Bukkit.getWorlds().isEmpty()
                    ? player.getLocation()
                    : Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        player.teleport(destination);
        if (session.returnGameMode != null) {
            player.setGameMode(session.returnGameMode);
        }
        player.sendMessage(ChatColor.YELLOW + "You leave the Skill Sanctum.");
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 1.0f);
    }

    public List<String> getCategories() {
        return CATEGORIES;
    }

    private void initializeDust() {
        dustByCategory.put("combat", new DustInfo(Color.fromRGB(196, 54, 54), Material.RED_NETHER_BRICKS, Particle.CRIT));
        dustByCategory.put("mining", new DustInfo(Color.fromRGB(214, 144, 61), Material.DEEPSLATE_TILES, Particle.BLOCK_CRUMBLE));
        dustByCategory.put("agility", new DustInfo(Color.fromRGB(99, 222, 202), Material.OXIDIZED_CUT_COPPER, Particle.CLOUD));
        dustByCategory.put("intellect", new DustInfo(Color.fromRGB(145, 108, 220), Material.BOOKSHELF, Particle.ENCHANT));
        dustByCategory.put("farming", new DustInfo(Color.fromRGB(102, 191, 92), Material.MOSS_BLOCK, Particle.HAPPY_VILLAGER));
        dustByCategory.put("fishing", new DustInfo(Color.fromRGB(78, 146, 214), Material.PRISMARINE_BRICKS, Particle.BUBBLE_POP));
        dustByCategory.put("magic", new DustInfo(Color.fromRGB(215, 88, 206), Material.AMETHYST_BLOCK, Particle.END_ROD));
        dustByCategory.put("mastery", new DustInfo(Color.fromRGB(230, 187, 84), Material.POLISHED_BLACKSTONE_BRICKS, Particle.TOTEM_OF_UNDYING));
    }

    private void startParticleLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (SanctumSession session : sessions.values()) {
                    Player player = Bukkit.getPlayer(session.playerId);
                    if (player == null || !player.isOnline()) continue;
                    if (!isInsideSanctum(player.getLocation())) continue;
                    spawnSessionParticles(player, session);
                }
            }
        }.runTaskTimer(plugin, 10L, 12L);
    }

    private void spawnSessionParticles(Player player, SanctumSession session) {
        World world = player.getWorld();
        for (PedestalVisual visual : session.visuals) {
            if (visual == null || visual.location == null) continue;
            Location loc = visual.location.clone().add(0.5, 1.15, 0.5);
            switch (visual.state) {
                case ROOT -> world.spawnParticle(Particle.END_ROD, loc, 3, 0.18, 0.08, 0.18, 0.01);
                case MAXED -> world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 2, 0.2, 0.12, 0.2, 0.01);
                case CATEGORY -> spawnCategoryParticle(world, loc, visual.category, true);
                case AVAILABLE -> spawnCategoryParticle(world, loc, visual.category, false);
                case EXIT -> world.spawnParticle(Particle.PORTAL, loc, 6, 0.25, 0.2, 0.25, 0.02);
                case PAGE -> world.spawnParticle(Particle.ENCHANT, loc, 4, 0.2, 0.12, 0.2, 0.02);
            }
        }
    }

    private void spawnCategoryParticle(World world, Location loc, String category, boolean intense) {
        DustInfo dust = dustByCategory.getOrDefault(category, dustByCategory.get("mastery"));
        if (dust == null) return;
        world.spawnParticle(Particle.DUST, loc, intense ? 6 : 4, 0.2, 0.12, 0.2, 0.0,
                new Particle.DustOptions(dust.color, intense ? 1.4f : 1.0f));
        if (dust.accent == Particle.BLOCK_CRUMBLE) {
            world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 3, 0.18, 0.1, 0.18, dust.material.createBlockData());
            return;
        }
        world.spawnParticle(dust.accent, loc, intense ? 4 : 2, 0.18, 0.1, 0.18, 0.01);
    }

    private World ensureWorld() {
        String worldName = plugin.getConfig().getString("skill_sanctum.world", "skill_sanctum");
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            applyWorldRules(world);
            return world;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.environment(World.Environment.NORMAL);
        world = creator.createWorld();
        if (world != null) {
            applyWorldRules(world);
        }
        return world;
    }

    private void applyWorldRules(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setStorm(false);
        world.setTime(18000L);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "combat";
        String normalized = category.toLowerCase(Locale.ROOT).trim();
        return CATEGORIES.contains(normalized) ? normalized : "combat";
    }

    private Location getCellOrigin(World world, UUID playerId) {
        long hashA = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        long hashB = Long.rotateLeft(playerId.getMostSignificantBits(), 17) ^ Long.rotateRight(playerId.getLeastSignificantBits(), 9);
        int gridX = Math.floorMod((int) hashA, 2048) - 1024;
        int gridZ = Math.floorMod((int) hashB, 2048) - 1024;

        int baseY = plugin.getConfig().getInt("skill_sanctum.base_y", 180);
        return new Location(world, gridX * CELL_SPACING, baseY, gridZ * CELL_SPACING);
    }

    private Location getSpawnLocation(Location origin) {
        return origin.clone().add(0.5, 1.0, 11.5).setDirection(origin.clone().add(0.5, 2.0, 0.5).toVector()
                .subtract(origin.clone().add(0.5, 1.0, 11.5).toVector()));
    }

    private void buildChamberIfNeeded(Location origin) {
        if (origin == null || origin.getWorld() == null) return;
        String key = chamberKey(origin);
        if (!builtCells.add(key)) return;

        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int x = -ROOM_RADIUS; x <= ROOM_RADIUS; x++) {
            for (int z = -ROOM_RADIUS; z <= ROOM_RADIUS; z++) {
                for (int y = 0; y <= ROOM_HEIGHT; y++) {
                    Block block = world.getBlockAt(ox + x, oy + y, oz + z);
                    block.setType(Material.AIR, false);
                }
            }
        }

        for (int x = -ROOM_RADIUS; x <= ROOM_RADIUS; x++) {
            for (int z = -ROOM_RADIUS; z <= ROOM_RADIUS; z++) {
                int dist = Math.max(Math.abs(x), Math.abs(z));
                Material floor = dist >= ROOM_RADIUS - 1
                        ? Material.ROOTED_DIRT
                        : ((Math.abs(x) + Math.abs(z)) % 3 == 0 ? Material.MOSS_BLOCK : Material.PALE_OAK_PLANKS);
                world.getBlockAt(ox + x, oy, oz + z).setType(floor, false);
                if (dist == ROOM_RADIUS) {
                    for (int y = 1; y <= ROOM_HEIGHT; y++) {
                        Material wall = y >= ROOM_HEIGHT - 1 ? Material.AZALEA_LEAVES : Material.STRIPPED_DARK_OAK_LOG;
                        world.getBlockAt(ox + x, oy + y, oz + z).setType(wall, false);
                    }
                }
            }
        }

        for (int x = -ROOM_RADIUS; x <= ROOM_RADIUS; x++) {
            for (int z = -ROOM_RADIUS; z <= ROOM_RADIUS; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) >= ROOM_RADIUS) continue;
                Material roof = (Math.abs(x) + Math.abs(z)) % 5 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES;
                world.getBlockAt(ox + x, oy + ROOM_HEIGHT, oz + z).setType(roof, false);
                world.getBlockAt(ox + x, oy + ROOM_HEIGHT + 1, oz + z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
        }

        buildHeartwoodRibs(world, origin);
        buildFloorRoots(world, origin);
        applyQuadrantGrovePalettes(world, origin);
        buildGlowBerryLanterns(world, origin);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(ox + x, oy, oz + z).setType(Material.MOSS_CARPET, false);
            }
        }

        buildPedestal(world, origin.clone().add(0, 0, 14), Material.RESPAWN_ANCHOR, Material.POLISHED_BLACKSTONE_BRICKS);
        buildPedestal(world, origin.clone().add(-7, 0, 14), Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS);
        buildPedestal(world, origin.clone().add(7, 0, 14), Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS);

        int[][] categoryOffsets = new int[][]{
                {-10, -10}, {-3, -10}, {4, -10}, {11, -10},
                {-10, -3}, {-3, -3}, {4, -3}, {11, -3}
        };
        for (int i = 0; i < categoryOffsets.length && i < CATEGORIES.size(); i++) {
            String category = CATEGORIES.get(i);
            DustInfo dust = dustByCategory.getOrDefault(category, dustByCategory.get("mastery"));
            buildPedestal(world, origin.clone().add(categoryOffsets[i][0], 0, categoryOffsets[i][1]), dust.material, Material.PALE_OAK_WOOD);
        }

        int[][] optionOffsets = new int[][]{
                {-10, 4}, {-4, 4}, {2, 4},
                {-10, 10}, {-4, 10}, {2, 10}
        };
        for (int[] offset : optionOffsets) {
            buildPedestal(world, origin.clone().add(offset[0], 0, offset[1]), Material.MOSSY_STONE_BRICKS, Material.PALE_OAK_WOOD);
        }
    }

    private void buildPedestal(World world, Location base, Material top, Material pillar) {
        int x = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();

        world.getBlockAt(x, y, z).setType(Material.PALE_OAK_WOOD, false);
        world.getBlockAt(x, y + 1, z).setType(pillar, false);
        world.getBlockAt(x, y + 2, z).setType(top, false);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                world.getBlockAt(x + dx, y, z + dz).setType(Material.PALE_MOSS_BLOCK, false);
            }
        }
    }

    private void buildHeartwoodRibs(World world, Location origin) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        int[] ribXs = new int[] {-12, -6, 0, 6, 12};
        for (int xOffset : ribXs) {
            for (int y = 1; y <= 8; y++) {
                world.getBlockAt(ox + xOffset, oy + y, oz - 14).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                world.getBlockAt(ox + xOffset, oy + y, oz + 14).setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
            for (int z = -14; z <= 14; z++) {
                if ((z + xOffset) % 3 == 0) {
                    world.getBlockAt(ox + xOffset, oy + 9, oz + z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                }
            }
        }

        int[] ribZs = new int[] {-12, -6, 0, 6, 12};
        for (int zOffset : ribZs) {
            for (int y = 1; y <= 8; y++) {
                world.getBlockAt(ox - 14, oy + y, oz + zOffset).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                world.getBlockAt(ox + 14, oy + y, oz + zOffset).setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
            for (int x = -14; x <= 14; x++) {
                if ((x - zOffset) % 3 == 0) {
                    world.getBlockAt(ox + x, oy + 8, oz + zOffset).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                }
            }
        }

        for (int y = 1; y <= 10; y++) {
            int radius = Math.max(1, 4 - (y / 3));
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        world.getBlockAt(ox + x, oy + y, oz + z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                    }
                }
            }
        }
    }

    private void buildFloorRoots(World world, Location origin) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int z = -12; z <= 12; z++) {
            if (z == 0) continue;
            Material mat = (Math.abs(z) % 4 == 0) ? Material.MANGROVE_ROOTS : Material.MUDDY_MANGROVE_ROOTS;
            world.getBlockAt(ox - 1, oy, oz + z).setType(mat, false);
            world.getBlockAt(ox + 1, oy, oz + z).setType(mat, false);
        }
        for (int x = -12; x <= 12; x++) {
            if (x == 0) continue;
            Material mat = (Math.abs(x) % 4 == 0) ? Material.MANGROVE_ROOTS : Material.MUDDY_MANGROVE_ROOTS;
            world.getBlockAt(ox + x, oy, oz - 1).setType(mat, false);
            world.getBlockAt(ox + x, oy, oz + 1).setType(mat, false);
        }
    }

    private void buildGlowBerryLanterns(World world, Location origin) {
        int[][] berryPoints = new int[][] {
                {-11, -11, 4},
                {-5, -4, 3},
                {0, -10, 5},
                {6, -5, 4},
                {11, -11, 3},
                {-9, 9, 4},
                {-2, 11, 3},
                {5, 10, 5},
                {11, 8, 4}
        };

        for (int[] point : berryPoints) {
            placeGlowBerryCluster(world, origin.clone().add(point[0], ROOM_HEIGHT - 1, point[1]), point[2]);
        }
    }

    private void applyQuadrantGrovePalettes(World world, Location origin) {
        applyQuadrantPalette(world, origin, -14, -2, -14, -2, Material.CHERRY_WOOD, Material.CHERRY_LEAVES, Material.CRIMSON_HYPHAE);
        applyQuadrantPalette(world, origin, 2, 14, -14, -2, Material.BIRCH_WOOD, Material.FLOWERING_AZALEA_LEAVES, Material.BAMBOO_BLOCK);
        applyQuadrantPalette(world, origin, -14, -2, 2, 14, Material.JUNGLE_WOOD, Material.OAK_LEAVES, Material.MANGROVE_ROOTS);
        applyQuadrantPalette(world, origin, 2, 14, 2, 14, Material.SPRUCE_WOOD, Material.DARK_OAK_LEAVES, Material.WARPED_WART_BLOCK);
    }

    private void applyQuadrantPalette(World world, Location origin, int minX, int maxX, int minZ, int maxZ,
                                      Material wood, Material leaves, Material accent) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if ((Math.abs(x) + Math.abs(z)) % 4 == 0) {
                    world.getBlockAt(ox + x, oy, oz + z).setType(accent, false);
                }
                if (Math.max(Math.abs(x), Math.abs(z)) >= ROOM_RADIUS - 1) {
                    world.getBlockAt(ox + x, oy + 5, oz + z).setType(leaves, false);
                    world.getBlockAt(ox + x, oy + 6, oz + z).setType(wood, false);
                }
            }
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        for (int y = 1; y <= 7; y++) {
            world.getBlockAt(ox + centerX, oy + y, oz + centerZ).setType(wood, false);
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) > 3) continue;
                world.getBlockAt(ox + centerX + x, oy + 7, oz + centerZ + z).setType(leaves, false);
                world.getBlockAt(ox + centerX + x, oy + 8, oz + centerZ + z).setType(wood, false);
            }
        }
    }

    private void placeGlowBerryCluster(World world, Location top, int length) {
        int x = top.getBlockX();
        int y = top.getBlockY();
        int z = top.getBlockZ();
        world.getBlockAt(x, y + 1, z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
        for (int i = 0; i < length; i++) {
            Material vine = (i == length - 1) ? Material.CAVE_VINES : Material.CAVE_VINES_PLANT;
            Block block = world.getBlockAt(x, y - i, z);
            block.setBlockData(Bukkit.createBlockData(vine.getKey().toString() + "[berries=true]"), false);
        }
    }

    private void renderSession(Player player, SanctumSession session) {
        clearDisplays(session);
        session.actions.clear();
        session.visuals.clear();

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        Location origin = session.cellOrigin;
        if (origin == null) return;

        spawnText(session, origin.clone().add(0.5, 9.2, -12.5),
                ChatColor.GREEN + "Skill Sanctum\n"
                        + ChatColor.GOLD + capitalize(session.category) + ChatColor.GRAY + " Path"
                        + "\n" + ChatColor.YELLOW + data.getSkillPoints() + ChatColor.GRAY + " Skill Points Available",
                240);

        renderCategoryPedestals(player, session, origin);
        renderOptionPedestals(player, session, origin, data);
        renderUtilityPedestals(session, origin);
        renderStatusWall(player, session, origin, data);
    }

    private void renderCategoryPedestals(Player player, SanctumSession session, Location origin) {
        int[][] categoryOffsets = new int[][]{
                {-10, -10}, {-3, -10}, {4, -10}, {11, -10},
                {-10, -3}, {-3, -3}, {4, -3}, {11, -3}
        };

        for (int i = 0; i < categoryOffsets.length && i < CATEGORIES.size(); i++) {
            String category = CATEGORIES.get(i);
            Location pedestal = origin.clone().add(categoryOffsets[i][0], 2, categoryOffsets[i][1]);
            DustInfo dust = dustByCategory.getOrDefault(category, dustByCategory.get("mastery"));
            pedestal.getBlock().setType(category.equals(session.category) ? Material.GOLD_BLOCK : dust.material, false);
            spawnIcon(session, pedestal.clone().add(0.5, 1.25, 0.5), categoryIcon(category, category.equals(session.category), false));
            String title = (category.equals(session.category) ? ChatColor.GOLD + "▣ " : ChatColor.YELLOW + "• ")
                    + capitalize(category);
            String subtitle = category.equals(session.category)
                    ? ChatColor.GREEN + "Active Path"
                    : ChatColor.GRAY + "Right-click to focus";
            spawnText(session, pedestal.clone().add(0.5, 1.6, 0.5), title + "\n" + subtitle, 180);

            Block keyBlock = pedestal.getBlock();
            session.actions.put(blockKey(keyBlock), "category:" + category);
            session.visuals.add(new PedestalVisual(keyBlock.getLocation(), category, PedestalState.CATEGORY));
        }

        spawnText(session, origin.clone().add(11.5, 7.0, -13.0),
                ChatColor.AQUA + "Category Grove\n"
                        + ChatColor.GRAY + "Each altar shifts the available branch.\n"
                        + ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/skillforge <category>" + ChatColor.GRAY + " too.",
                180);
    }

    private void renderOptionPedestals(Player player, SanctumSession session, Location origin, PlayerData data) {
        List<SkillTreeSystem.SkillNode> all = plugin.getSkillTreeSystem().getSkillsByCategory(session.category);
        List<SkillTreeSystem.SkillNode> available = new ArrayList<>();
        List<SkillTreeSystem.SkillNode> completed = new ArrayList<>();

        for (SkillTreeSystem.SkillNode node : all) {
            int level = data.getSkillLevel(node.getId());
            if (level >= node.getMaxLevel()) {
                completed.add(node);
                continue;
            }
            if (plugin.getSkillTreeSystem().getUnmetRequirements(player, node.getId()).isEmpty()) {
                available.add(node);
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil(available.size() / (double) AVAILABLE_PAGE_SIZE));
        if (session.page >= totalPages) session.page = totalPages - 1;
        if (session.page < 0) session.page = 0;

        int start = session.page * AVAILABLE_PAGE_SIZE;
        int end = Math.min(start + AVAILABLE_PAGE_SIZE, available.size());
        List<SkillTreeSystem.SkillNode> page = start >= end ? Collections.emptyList() : available.subList(start, end);

        int[][] optionOffsets = new int[][]{
                {-10, 4}, {-4, 4}, {2, 4},
                {-10, 10}, {-4, 10}, {2, 10}
        };

        renderCurrentPathBranches(origin, session.category, page.size(), optionOffsets);

        for (int i = 0; i < optionOffsets.length; i++) {
            Location pedestal = origin.clone().add(optionOffsets[i][0], 2, optionOffsets[i][1]);
            Block block = pedestal.getBlock();
            if (i >= page.size()) {
                block.setType(Material.MOSSY_STONE_BRICKS, false);
                spawnText(session, pedestal.clone().add(0.5, 1.35, 0.5),
                        ChatColor.DARK_GRAY + "Empty Branch", 150);
                continue;
            }

            SkillTreeSystem.SkillNode node = page.get(i);
            int level = data.getSkillLevel(node.getId());
            boolean root = node.getRequirements().isEmpty();
            block.setType(resolveSkillPedestalMaterial(session.category, root, level >= node.getMaxLevel()), false);
            spawnIcon(session, pedestal.clone().add(0.5, 1.25, 0.5), skillIcon(node, root, false));
            String text = ChatColor.GREEN + node.getName()
                    + "\n" + ChatColor.GRAY + "Lvl " + level + "/" + node.getMaxLevel()
                    + "  " + ChatColor.YELLOW + node.getCostPerLevel() + " SP"
                    + "\n" + ChatColor.AQUA + "Right-click to attune";
            spawnText(session, pedestal.clone().add(0.5, 1.55, 0.5), text, 200);
            session.actions.put(blockKey(block), "skill:" + node.getId());
            session.visuals.add(new PedestalVisual(block.getLocation(), session.category, root ? PedestalState.ROOT : PedestalState.AVAILABLE));
        }

        if (page.isEmpty()) {
            spawnText(session, origin.clone().add(-2.5, 4.8, 7.5),
                    ChatColor.RED + "No available upgrades in " + capitalize(session.category)
                            + "\n" + ChatColor.GRAY + "Spend points elsewhere or unlock prerequisites.",
                    220);
        }

        StringBuilder completedWall = new StringBuilder();
        completedWall.append(ChatColor.GOLD).append("Completed Branches\n");
        if (completed.isEmpty()) {
            completedWall.append(ChatColor.GRAY).append("Nothing maxed here yet.");
        } else {
            int shown = 0;
            for (SkillTreeSystem.SkillNode node : completed) {
                completedWall.append(ChatColor.YELLOW).append("• ").append(node.getName()).append("\n");
                if (shown < 3) {
                    Location trophyLoc = origin.clone().add(8.5 + (shown * 2), 3.2, 4.5);
                    spawnIcon(session, trophyLoc, skillIcon(node, false, true));
                    session.visuals.add(new PedestalVisual(trophyLoc.clone().subtract(0.5, 0.7, 0.5), session.category, PedestalState.MAXED));
                }
                shown++;
                if (shown >= 8) break;
            }
            if (completed.size() > shown) {
                completedWall.append(ChatColor.DARK_GRAY).append("+").append(completed.size() - shown).append(" more");
            }
        }
        spawnText(session, origin.clone().add(10.5, 5.2, 6.5), completedWall.toString(), 220);

        StringBuilder guide = new StringBuilder();
        guide.append(ChatColor.GREEN).append("Path Reading\n")
                .append(ChatColor.YELLOW).append("End Rod").append(ChatColor.GRAY).append(" = root/start\n")
                .append(ChatColor.YELLOW).append("Class particle").append(ChatColor.GRAY).append(" = available\n")
                .append(ChatColor.YELLOW).append("Totem burst").append(ChatColor.GRAY).append(" = mastered\n")
                .append(ChatColor.YELLOW).append("Current page: ").append(session.page + 1).append("/").append(totalPages);
        spawnText(session, origin.clone().add(-12.5, 5.2, 6.5), guide.toString(), 220);
    }

    private void renderUtilityPedestals(SanctumSession session, Location origin) {
        Block exitBlock = origin.clone().add(0, 2, 14).getBlock();
        exitBlock.setType(Material.RESPAWN_ANCHOR, false);
        session.actions.put(blockKey(exitBlock), "exit");
        session.visuals.add(new PedestalVisual(exitBlock.getLocation(), session.category, PedestalState.EXIT));
        spawnIcon(session, exitBlock.getLocation().clone().add(0.5, 1.15, 0.5), new ItemStack(Material.ENDER_EYE));
        spawnText(session, exitBlock.getLocation().clone().add(0.5, 1.6, 0.5),
                ChatColor.RED + "Leave Sanctum\n" + ChatColor.GRAY + "Return to where you came from", 180);

        Block prevBlock = origin.clone().add(-7, 2, 14).getBlock();
        Block nextBlock = origin.clone().add(7, 2, 14).getBlock();
        prevBlock.setType(Material.CHISELED_STONE_BRICKS, false);
        nextBlock.setType(Material.CHISELED_STONE_BRICKS, false);
        session.actions.put(blockKey(prevBlock), "page:-1");
        session.actions.put(blockKey(nextBlock), "page:+1");
        session.visuals.add(new PedestalVisual(prevBlock.getLocation(), session.category, PedestalState.PAGE));
        session.visuals.add(new PedestalVisual(nextBlock.getLocation(), session.category, PedestalState.PAGE));
        spawnIcon(session, prevBlock.getLocation().clone().add(0.5, 1.1, 0.5), new ItemStack(Material.ARROW));
        spawnIcon(session, nextBlock.getLocation().clone().add(0.5, 1.1, 0.5), new ItemStack(Material.SPECTRAL_ARROW));
        spawnText(session, prevBlock.getLocation().clone().add(0.5, 1.45, 0.5),
                ChatColor.YELLOW + "Previous\n" + ChatColor.GRAY + "Earlier upgrades", 140);
        spawnText(session, nextBlock.getLocation().clone().add(0.5, 1.45, 0.5),
                ChatColor.YELLOW + "Next\n" + ChatColor.GRAY + "Later upgrades", 140);
    }

    private void renderStatusWall(Player player, SanctumSession session, Location origin, PlayerData data) {
        Collection<SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getAllSkillNodes().values();
        int unlocked = 0;
        int total = 0;
        for (SkillTreeSystem.SkillNode node : nodes) {
            if (!session.category.equalsIgnoreCase(node.getCategory())) continue;
            total++;
            if (data.getSkillLevel(node.getId()) > 0) unlocked++;
        }

        List<SkillTreeSystem.SkillNode> available = new ArrayList<>();
        for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getSkillsByCategory(session.category)) {
            int level = data.getSkillLevel(node.getId());
            if (level >= node.getMaxLevel()) continue;
            if (plugin.getSkillTreeSystem().getUnmetRequirements(player, node.getId()).isEmpty()) {
                available.add(node);
            }
        }

        String text = ChatColor.AQUA + "Sanctum Status"
                + "\n" + ChatColor.GRAY + "Focused Path: " + ChatColor.GOLD + capitalize(session.category)
                + "\n" + ChatColor.GRAY + "Unlocked in Path: " + ChatColor.GREEN + unlocked + "/" + total
                + "\n" + ChatColor.GRAY + "Upgrades Ready: " + ChatColor.YELLOW + available.size()
                + "\n" + ChatColor.GRAY + "Tip: roots open branches; maxed skills finish them.";
        spawnText(session, origin.clone().add(0.5, 7.0, 12.0), text, 240);
    }

    private void spawnText(SanctumSession session, Location location, String text, int width) {
        if (session == null || location == null || location.getWorld() == null) return;
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, td -> configureTextDisplay(td, text, width));
        String tag = tagFor(session.playerId);
        display.addScoreboardTag(tag);
        session.displayEntityIds.add(display.getUniqueId());
    }

    private void spawnIcon(SanctumSession session, Location location, ItemStack icon) {
        if (session == null || location == null || location.getWorld() == null || icon == null || icon.getType().isAir()) return;
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, id -> {
            id.setItemStack(icon);
            id.setBillboard(Display.Billboard.CENTER);
            id.setPersistent(false);
            id.setInvulnerable(true);
            id.setGlowing(false);
        });
        String tag = tagFor(session.playerId);
        display.addScoreboardTag(tag);
        session.displayEntityIds.add(display.getUniqueId());
    }

    private void configureTextDisplay(TextDisplay td, String text, int width) {
        td.setText(text);
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setLineWidth(width);
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setDefaultBackground(false);
        td.setPersistent(false);
    }

    private void clearDisplays(SanctumSession session) {
        if (session == null || session.cellOrigin == null || session.cellOrigin.getWorld() == null) return;
        World world = session.cellOrigin.getWorld();
        String tag = tagFor(session.playerId);
        Location origin = session.cellOrigin;

        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(origin.clone().add(0.5, 5.0, 0.5), ROOM_RADIUS + 4, ROOM_HEIGHT + 4, ROOM_RADIUS + 4)) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
        session.displayEntityIds.clear();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        SanctumSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!isInsideSanctum(player.getLocation())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        String action = session.actions.get(blockKey(clicked));
        if (action == null) return;

        event.setCancelled(true);

        if (action.startsWith("category:")) {
            session.category = normalizeCategory(action.substring("category:".length()));
            session.page = 0;
            renderSession(player, session);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.8f, 1.25f);
            return;
        }

        if (action.startsWith("skill:")) {
            String skillId = action.substring("skill:".length());
            SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
            if (node == null) {
                player.sendMessage(ChatColor.RED + "That branch no longer exists.");
                return;
            }

            int before = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(skillId);
            SkillTreeSystem.UpgradeResult result = plugin.getSkillTreeSystem().tryUpgradeSkill(player, skillId);
            if (result == SkillTreeSystem.UpgradeResult.SUCCESS) {
                player.sendMessage(ChatColor.GREEN + "Attuned " + node.getName() + " to level " + (before + 1) + ".");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.15f);
            } else {
                player.sendMessage(ChatColor.RED + plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, skillId, result));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            }
            renderSession(player, session);
            return;
        }

        if (action.startsWith("page:")) {
            int delta = Integer.parseInt(action.substring("page:".length()));
            session.page += delta;
            if (session.page < 0) session.page = 0;
            renderSession(player, session);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
            return;
        }

        if ("exit".equals(action)) {
            returnPlayer(player);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInsideSanctum(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInsideSanctum(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isInsideSanctum(player.getLocation())) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isInsideSanctum(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isInsideSanctum(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        SanctumSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Location to = event.getTo();
        if (to != null && isInsideSanctum(to)) return;
        if (isInsideSanctum(event.getFrom())) {
            sessions.remove(player.getUniqueId());
            if (session.returnGameMode != null) {
                player.setGameMode(session.returnGameMode);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private boolean isInsideSanctum(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("skill_sanctum.world", "skill_sanctum");
        return worldName.equalsIgnoreCase(location.getWorld().getName());
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private String chamberKey(Location origin) {
        return SANCTUM_BUILD_VERSION + ":" + origin.getWorld().getName() + ":" + origin.getBlockX() + ":" + origin.getBlockY() + ":" + origin.getBlockZ();
    }

    private String tagFor(UUID playerId) {
        return "sf_sanctum_" + playerId.toString().substring(0, 8);
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return "Combat";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void renderCurrentPathBranches(Location origin, String category, int optionCount, int[][] optionOffsets) {
        if (origin == null || origin.getWorld() == null) return;
        clearDynamicBranches(origin);
        World world = origin.getWorld();
        BranchPalette palette = branchPalette(category);
        int[] categoryOffset = categoryOffset(category);

        Location trunk = origin.clone().add(0, 6, 0);
        Location categoryAnchor = origin.clone().add(categoryOffset[0], 6, categoryOffset[1]);
        drawBranchLine(world, trunk, categoryAnchor, palette);
        world.getBlockAt(categoryAnchor).setType(Material.CHAIN, false);

        for (int i = 0; i < optionCount && i < optionOffsets.length; i++) {
            Location optionAnchor = origin.clone().add(optionOffsets[i][0], 5, optionOffsets[i][1]);
            drawBranchLine(world, categoryAnchor, optionAnchor, palette);
            world.getBlockAt(optionAnchor).setType(Material.CHAIN, false);
        }
    }

    private void drawBranchLine(World world, Location from, Location to, BranchPalette palette) {
        if (world == null || from == null || to == null || palette == null) return;
        int x1 = from.getBlockX();
        int y1 = from.getBlockY();
        int z1 = from.getBlockZ();
        int x2 = to.getBlockX();
        int y2 = to.getBlockY();
        int z2 = to.getBlockZ();

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        steps = Math.max(steps, Math.abs(y2 - y1));
        steps = Math.max(steps, 1);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + ((x2 - x1) * t));
            int y = (int) Math.round(y1 + ((y2 - y1) * t));
            int z = (int) Math.round(z1 + ((z2 - z1) * t));
            world.getBlockAt(x, y, z).setType(palette.wood, false);
        }
    }

    private void clearDynamicBranches(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (int x = -14; x <= 14; x++) {
            for (int y = 4; y <= 8; y++) {
                for (int z = -14; z <= 14; z++) {
                    Material type = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z).getType();
                    if (type == Material.STRIPPED_CHERRY_WOOD || type == Material.STRIPPED_BIRCH_WOOD || type == Material.STRIPPED_JUNGLE_WOOD
                            || type == Material.STRIPPED_SPRUCE_WOOD || type == Material.STRIPPED_PALE_OAK_WOOD
                            || type == Material.CHAIN) {
                        world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private int[] categoryOffset(String category) {
        return switch (normalizeCategory(category)) {
            case "combat" -> new int[]{-10, -10};
            case "mining" -> new int[]{-3, -10};
            case "agility" -> new int[]{4, -10};
            case "intellect" -> new int[]{11, -10};
            case "farming" -> new int[]{-10, -3};
            case "fishing" -> new int[]{-3, -3};
            case "magic" -> new int[]{4, -3};
            case "mastery" -> new int[]{11, -3};
            default -> new int[]{-10, -10};
        };
    }

    private BranchPalette branchPalette(String category) {
        return switch (normalizeCategory(category)) {
            case "combat" -> new BranchPalette(Material.STRIPPED_CHERRY_WOOD, Material.CHERRY_LEAVES);
            case "mining" -> new BranchPalette(Material.STRIPPED_SPRUCE_WOOD, Material.DARK_OAK_LEAVES);
            case "agility" -> new BranchPalette(Material.STRIPPED_BIRCH_WOOD, Material.FLOWERING_AZALEA_LEAVES);
            case "intellect" -> new BranchPalette(Material.STRIPPED_BIRCH_WOOD, Material.AZALEA_LEAVES);
            case "farming" -> new BranchPalette(Material.STRIPPED_JUNGLE_WOOD, Material.OAK_LEAVES);
            case "fishing" -> new BranchPalette(Material.STRIPPED_JUNGLE_WOOD, Material.MANGROVE_LEAVES);
            case "magic" -> new BranchPalette(Material.STRIPPED_SPRUCE_WOOD, Material.AZALEA_LEAVES);
            case "mastery" -> new BranchPalette(Material.STRIPPED_PALE_OAK_WOOD, Material.CHERRY_LEAVES);
            default -> new BranchPalette(Material.STRIPPED_SPRUCE_WOOD, Material.OAK_LEAVES);
        };
    }

    private Material resolveSkillPedestalMaterial(String category, boolean root, boolean maxed) {
        if (maxed) return Material.BEACON;
        if (root) return Material.EMERALD_BLOCK;
        DustInfo dust = dustByCategory.getOrDefault(category, dustByCategory.get("mastery"));
        return dust != null ? dust.material : Material.MOSSY_STONE_BRICKS;
    }

    private ItemStack categoryIcon(String category, boolean active, boolean maxed) {
        Material material;
        switch (category == null ? "" : category.toLowerCase(Locale.ROOT)) {
            case "combat" -> material = maxed ? Material.NETHERITE_SWORD : (active ? Material.DIAMOND_SWORD : Material.IRON_SWORD);
            case "mining" -> material = maxed ? Material.NETHERITE_PICKAXE : (active ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE);
            case "agility" -> material = maxed ? Material.ELYTRA : (active ? Material.WIND_CHARGE : Material.FEATHER);
            case "intellect" -> material = maxed ? Material.ENCHANTED_BOOK : (active ? Material.KNOWLEDGE_BOOK : Material.BOOK);
            case "farming" -> material = maxed ? Material.GOLDEN_CARROT : (active ? Material.WHEAT : Material.WHEAT_SEEDS);
            case "fishing" -> material = maxed ? Material.NAUTILUS_SHELL : (active ? Material.FISHING_ROD : Material.COD_BUCKET);
            case "magic" -> material = maxed ? Material.NETHER_STAR : (active ? Material.BLAZE_ROD : Material.AMETHYST_SHARD);
            case "mastery" -> material = maxed ? Material.BEACON : (active ? Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE : Material.BRICKS);
            default -> material = Material.BOOK;
        }
        return new ItemStack(material);
    }

    private ItemStack skillIcon(SkillTreeSystem.SkillNode node, boolean root, boolean maxed) {
        if (node == null) return new ItemStack(Material.BOOK);
        if (maxed) {
            return categoryIcon(node.getCategory(), false, true);
        }
        if (root) {
            return switch (normalizeCategory(node.getCategory())) {
                case "combat" -> new ItemStack(Material.WOODEN_SWORD);
                case "mining" -> new ItemStack(Material.WOODEN_PICKAXE);
                case "agility" -> new ItemStack(Material.RABBIT_FOOT);
                case "intellect" -> new ItemStack(Material.WRITABLE_BOOK);
                case "farming" -> new ItemStack(Material.OAK_SAPLING);
                case "fishing" -> new ItemStack(Material.TROPICAL_FISH_BUCKET);
                case "magic" -> new ItemStack(Material.ENDER_EYE);
                case "mastery" -> new ItemStack(Material.BRICK);
                default -> new ItemStack(Material.BOOK);
            };
        }
        String category = normalizeCategory(node.getCategory());
        String id = node.getId() == null ? "" : node.getId().toLowerCase(Locale.ROOT);
        return switch (category) {
            case "combat" -> new ItemStack(id.contains("parry") ? Material.SHIELD : Material.IRON_SWORD);
            case "mining" -> new ItemStack(id.contains("fortune") ? Material.EMERALD_ORE : Material.IRON_PICKAXE);
            case "agility" -> new ItemStack(id.contains("blink") || id.contains("shadow") ? Material.ENDER_PEARL : Material.FEATHER);
            case "intellect" -> new ItemStack(id.contains("alchemy") ? Material.BREWING_STAND : Material.ENCHANTED_BOOK);
            case "farming" -> new ItemStack(id.contains("breeding") ? Material.HAY_BLOCK : Material.WHEAT);
            case "fishing" -> new ItemStack(id.contains("treasure") ? Material.HEART_OF_THE_SEA : Material.FISHING_ROD);
            case "magic" -> new ItemStack(id.contains("fire") ? Material.FIRE_CHARGE : id.contains("frost") ? Material.SNOWBALL : Material.BLAZE_POWDER);
            case "mastery" -> new ItemStack(id.contains("brick") ? Material.BRICKS : Material.NETHERITE_SWORD);
            default -> new ItemStack(Material.BOOK);
        };
    }

    private static final class SanctumSession {
        private final UUID playerId;
        private String category = "combat";
        private int page = 0;
        private Location returnLocation;
        private GameMode returnGameMode;
        private Location cellOrigin;
        private final Map<String, String> actions = new LinkedHashMap<>();
        private final List<UUID> displayEntityIds = new ArrayList<>();
        private final List<PedestalVisual> visuals = new ArrayList<>();

        private SanctumSession(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private enum PedestalState {
        CATEGORY,
        AVAILABLE,
        ROOT,
        MAXED,
        EXIT,
        PAGE
    }

    private static final class PedestalVisual {
        private final Location location;
        private final String category;
        private final PedestalState state;

        private PedestalVisual(Location location, String category, PedestalState state) {
            this.location = location;
            this.category = category;
            this.state = state;
        }
    }

    private static final class DustInfo {
        private final Color color;
        private final Material material;
        private final Particle accent;

        private DustInfo(Color color, Material material, Particle accent) {
            this.color = color;
            this.material = material;
            this.accent = accent;
        }
    }

    private static final class BranchPalette {
        private final Material wood;
        private final Material leaves;

        private BranchPalette(Material wood, Material leaves) {
            this.wood = wood;
            this.leaves = leaves;
        }
    }
}
