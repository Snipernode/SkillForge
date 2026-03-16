package com.skilltree.plugin.systems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.Axis;

import java.util.ArrayList;
import java.util.List;

public final class GuildHallBuilder {

    public static final int DEFAULT_HALL_SIZE = 100;
    public static final int MIN_HALL_SIZE = 50;
    public static final int MAX_HALL_SIZE = 100;

    private GuildHallBuilder() {}

    public enum GuildHallType {
        HUNTERS("Hunters Guild", Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG, Material.MOSS_BLOCK, Material.GREEN_CONCRETE),
        MERCHANT("Merchant Guild", Material.SMOOTH_SANDSTONE, Material.BIRCH_LOG, Material.CUT_COPPER, Material.GOLD_BLOCK),
        ADVENTURER("Adventurer Guild", Material.STONE_BRICKS, Material.COBBLED_DEEPSLATE, Material.OAK_PLANKS, Material.LAPIS_BLOCK),
        THIEVES("Thieves Guild", Material.DEEPSLATE_BRICKS, Material.BLACKSTONE, Material.DARK_OAK_PLANKS, Material.POLISHED_BLACKSTONE);

        private final String displayName;
        private final Material wall;
        private final Material frame;
        private final Material floor;
        private final Material emblem;

        GuildHallType(String displayName, Material wall, Material frame, Material floor, Material emblem) {
            this.displayName = displayName;
            this.wall = wall;
            this.frame = frame;
            this.floor = floor;
            this.emblem = emblem;
        }

        public String displayName() {
            return displayName;
        }

        public Material wall() {
            return wall;
        }

        public Material frame() {
            return frame;
        }

        public Material floor() {
            return floor;
        }

        public Material emblem() {
            return emblem;
        }

        public static GuildHallType fromInput(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return switch (raw.trim().toLowerCase()) {
                case "hunter", "hunters", "hunters_guild" -> HUNTERS;
                case "merchant", "merchants", "merchant_guild" -> MERCHANT;
                case "adventurer", "adventurers", "adventurer_guild" -> ADVENTURER;
                case "thief", "thieves", "thieves_guild" -> THIEVES;
                default -> null;
            };
        }
    }

    public static void buildHall(Location center, GuildHallType type) {
        buildHall(center, type, DEFAULT_HALL_SIZE, BlockFace.SOUTH);
    }

    public static void buildHall(Location center, GuildHallType type, int requestedSize) {
        buildHall(center, type, requestedSize, BlockFace.SOUTH);
    }

    public static void buildHall(Location center, GuildHallType type, int requestedSize, BlockFace facing) {
        if (center == null || type == null || center.getWorld() == null) return;
        World world = center.getWorld();

        int size = normalizeSize(requestedSize);
        BlockFace normalizedFacing = normalizeFacing(facing);
        int half = size / 2;
        int minX = center.getBlockX() - half;
        int minZ = center.getBlockZ() - half;
        int maxX = minX + size - 1;
        int maxZ = minZ + size - 1;
        int y = Math.max(world.getMinHeight() + 2, center.getBlockY() - 1);
        int structureTopY = y + scale(40, size);

        if (type == GuildHallType.MERCHANT) {
            buildMerchantHall(world, minX, minZ, maxX, maxZ, y, type, size);
        } else if (type == GuildHallType.ADVENTURER) {
            buildAdventurerHall(world, minX, minZ, maxX, maxZ, y, type, size);
        } else if (type == GuildHallType.THIEVES) {
            buildThievesHall(world, minX, minZ, maxX, maxZ, y, type, size);
        } else {
            // Clear and prep the selected build plot.
            fill(world, minX, y, minZ, maxX, y + scale(24, size), maxZ, Material.AIR);
            fill(world, minX, y - 1, minZ, maxX, y - 1, maxZ, Material.STONE_BRICKS);
            fill(world, minX + 1, y, minZ + 1, maxX - 1, y, maxZ - 1, type.floor());

            int hallMinX = minX + scale(8, size);
            int hallMaxX = maxX - scale(8, size);
            int hallMinZ = minZ + scale(11, size);
            int hallMaxZ = maxZ - scale(9, size);
            int wallTop = y + scale(9, size);
            int roofY = wallTop + 1;

            // Outer shell + frame.
            fillHollow(world, hallMinX, y + 1, hallMinZ, hallMaxX, wallTop, hallMaxZ, type.wall(), type.frame());

            // Roof + beams.
            fill(world, hallMinX, roofY, hallMinZ, hallMaxX, roofY, hallMaxZ, type.frame());
            for (int x = hallMinX + scale(2, size); x <= hallMaxX - scale(2, size); x += Math.max(3, scale(3, size))) {
                fill(world, x, roofY + 1, hallMinZ + 1, x, roofY + 1, hallMaxZ - 1, type.frame());
            }

            // Interior floor.
            fill(world, hallMinX + 1, y + 1, hallMinZ + 1, hallMaxX - 1, y + 1, hallMaxZ - 1, type.floor());

            // Windows.
            addWindowBand(world, hallMinX + 1, hallMaxX - 1, y + scale(4, size), hallMinZ, hallMaxZ, Material.GLASS_PANE);

            // Front door (south face).
            int doorX = (hallMinX + hallMaxX) / 2;
            world.getBlockAt(doorX, y + 2, hallMaxZ).setType(Material.AIR, false);
            world.getBlockAt(doorX, y + 3, hallMaxZ).setType(Material.AIR, false);
            setStairs(world, doorX, y + 1, hallMaxZ, Material.STONE_BRICK_STAIRS, BlockFace.SOUTH);
            world.getBlockAt(doorX - 1, y + 2, hallMaxZ).setType(type.frame(), false);
            world.getBlockAt(doorX + 1, y + 2, hallMaxZ).setType(type.frame(), false);

            // Hunters hall interior upgrade.
            decorateHuntersInterior(world, hallMinX, hallMaxX, hallMinZ, hallMaxZ, y, wallTop, size, doorX);

            // Front emblem wall (large) and sign.
            int emblemZ = hallMaxZ + scale(4, size);
            fill(world, doorX - 6, y + 1, emblemZ, doorX + 6, y + 11, emblemZ, type.frame());
            fill(world, doorX - 5, y + 2, emblemZ, doorX + 5, y + 10, emblemZ, type.wall());
            drawEmblem(world, doorX, y + 2, emblemZ, type);
            placeSign(world, doorX, y + 1, emblemZ - 1, type.displayName(), size + "x" + size + " Hall");

            // Front path.
            fill(world, doorX - scale(2, size), y + 1, hallMaxZ + 1, doorX + scale(2, size), y + 1, maxZ - 1, Material.STONE_BRICK_SLAB);
        }

        if (normalizedFacing != BlockFace.SOUTH) {
            rotateArea(world, minX, y - 1, minZ, maxX, structureTopY, maxZ, turnsFromSouth(normalizedFacing));
        }
    }

    private static void buildAdventurerHall(World world, int minX, int minZ, int maxX, int maxZ, int y, GuildHallType type, int size) {
        Material plaster = Material.SMOOTH_SANDSTONE;
        Material timber = Material.DARK_OAK_LOG;
        Material trim = Material.DARK_OAK_PLANKS;
        Material roof = Material.BLUE_TERRACOTTA;
        Material glass = Material.WHITE_STAINED_GLASS;
        Material floor = Material.SPRUCE_PLANKS;
        Material balcony = Material.DARK_OAK_PLANKS;
        Material rail = Material.DARK_OAK_FENCE;
        Material rug = Material.RED_CARPET;

        // Plot prep.
        fill(world, minX, y, minZ, maxX, y + scale(28, size), maxZ, Material.AIR);
        fill(world, minX, y - 1, minZ, maxX, y - 1, maxZ, Material.STONE_BRICKS);
        fill(world, minX + 1, y, minZ + 1, maxX - 1, y, maxZ - 1, Material.POLISHED_ANDESITE);

        int hallMinX = minX + scale(6, size);
        int hallMaxX = maxX - scale(6, size);
        int hallMinZ = minZ + scale(12, size);
        int hallBackZ = maxZ - scale(11, size);
        int hallFrontZ = hallBackZ + scale(5, size); // central front bay extends forward
        int wallTop = y + scale(10, size);
        int doorX = (hallMinX + hallMaxX) / 2;

        // Main shell.
        fillHollow(world, hallMinX, y + 1, hallMinZ, hallMaxX, wallTop, hallBackZ, plaster, timber);

        // Central entrance bay (anime guild facade vibe).
        fillHollow(world, doorX - scale(5, size), y + 1, hallBackZ, doorX + scale(5, size), wallTop - 1, hallFrontZ, plaster, timber);

        // Timber patterning on facade and walls.
        for (int x = hallMinX + scale(3, size); x <= hallMaxX - scale(3, size); x += Math.max(3, scale(4, size))) {
            fill(world, x, y + 2, hallBackZ, x, wallTop - 1, hallBackZ, timber);
            fill(world, x, y + 2, hallMinZ, x, wallTop - 1, hallMinZ, timber);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallBackZ - scale(3, size); z += Math.max(3, scale(4, size))) {
            fill(world, hallMinX, y + 2, z, hallMinX, wallTop - 1, z, timber);
            fill(world, hallMaxX, y + 2, z, hallMaxX, wallTop - 1, z, timber);
        }
        fill(world, hallMinX, y + scale(5, size), hallMinZ, hallMaxX, y + scale(5, size), hallBackZ, timber);
        fill(world, hallMinX, y + scale(8, size), hallMinZ, hallMaxX, y + scale(8, size), hallBackZ, timber);

        // Windows.
        for (int x = hallMinX + scale(2, size); x <= hallMaxX - scale(2, size); x += Math.max(3, scale(4, size))) {
            if (Math.abs(x - doorX) <= scale(6, size)) continue;
            addTallWindow(world, x, y + scale(3, size), y + scale(7, size), hallBackZ, glass);
            addTallWindow(world, x, y + scale(3, size), y + scale(7, size), hallMinZ, glass);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallBackZ - scale(3, size); z += Math.max(4, scale(5, size))) {
            addTallWindow(world, hallMinX, y + scale(3, size), y + scale(7, size), z, glass);
            addTallWindow(world, hallMaxX, y + scale(3, size), y + scale(7, size), z, glass);
        }
        addTallWindow(world, doorX - 1, y + scale(4, size), y + scale(8, size), hallFrontZ, glass);
        addTallWindow(world, doorX + 1, y + scale(4, size), y + scale(8, size), hallFrontZ, glass);

        // Entrance.
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(doorX + dx, y + 2, hallFrontZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + 3, hallFrontZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + scale(4, size), hallFrontZ).setType(Material.AIR, false);
            setStairs(world, doorX + dx, y + 1, hallFrontZ, Material.STONE_BRICK_STAIRS, BlockFace.SOUTH);
        }
        fill(world, doorX - scale(2, size), y + scale(5, size), hallFrontZ, doorX + scale(3, size), y + scale(5, size), hallFrontZ, timber);

        // Blue roof (stepped gable look).
        int roofMinX = hallMinX - 1;
        int roofMaxX = hallMaxX + 1;
        int roofMinZ = hallMinZ - 1;
        int roofMaxZ = hallFrontZ + 1;
        for (int layer = 0; layer <= scale(6, size); layer++) {
            int z1 = roofMinZ + layer;
            int z2 = roofMaxZ - layer;
            if (z1 > z2) break;
            fill(world, roofMinX, wallTop + 1 + layer, z1, roofMaxX, wallTop + 1 + layer, z2, roof);
        }
        fill(world, roofMinX + scale(2, size), wallTop + scale(8, size), (roofMinZ + roofMaxZ) / 2, roofMaxX - scale(2, size), wallTop + scale(8, size), (roofMinZ + roofMaxZ) / 2, trim);

        // Interior floor and rug.
        fill(world, hallMinX + 1, y + 1, hallMinZ + 1, hallMaxX - 1, y + 1, hallFrontZ - 1, floor);
        fill(world, doorX, y + 2, hallMinZ + scale(2, size), doorX + Math.max(1, scale(1, size)), y + 2, hallFrontZ - scale(2, size), rug);

        // Upper balcony ring and rails.
        int balY = y + scale(6, size);
        fill(world, hallMinX + scale(2, size), balY, hallMinZ + scale(2, size), hallMinX + scale(6, size), balY, hallFrontZ - scale(2, size), balcony);
        fill(world, hallMaxX - scale(6, size), balY, hallMinZ + scale(2, size), hallMaxX - scale(2, size), balY, hallFrontZ - scale(2, size), balcony);
        fill(world, hallMinX + scale(2, size), balY, hallMinZ + scale(2, size), hallMaxX - scale(2, size), balY, hallMinZ + scale(5, size), balcony);
        fill(world, hallMinX + scale(7, size), balY + 1, hallMinZ + scale(2, size), hallMinX + scale(7, size), balY + 1, hallFrontZ - scale(2, size), rail);
        fill(world, hallMaxX - scale(7, size), balY + 1, hallMinZ + scale(2, size), hallMaxX - scale(7, size), balY + 1, hallFrontZ - scale(2, size), rail);

        // Twin stair flights toward balcony.
        for (int step = 0; step <= scale(4, size); step++) {
            setStairs(world, hallMinX + scale(8, size) + step, y + 2 + step, hallFrontZ - scale(3, size), Material.SPRUCE_STAIRS, BlockFace.WEST);
            setStairs(world, hallMaxX - scale(8, size) - step, y + 2 + step, hallFrontZ - scale(3, size), Material.SPRUCE_STAIRS, BlockFace.EAST);
            world.getBlockAt(hallMinX + scale(8, size) + step, y + 1 + step, hallFrontZ - scale(3, size)).setType(Material.DARK_OAK_PLANKS, false);
            world.getBlockAt(hallMaxX - scale(8, size) - step, y + 1 + step, hallFrontZ - scale(3, size)).setType(Material.DARK_OAK_PLANKS, false);
        }

        // Back dais / board wall.
        fill(world, doorX - scale(3, size), y + 2, hallMinZ + scale(2, size), doorX + scale(4, size), y + scale(5, size), hallMinZ + scale(3, size), type.frame());
        fill(world, doorX - scale(2, size), y + 3, hallMinZ + scale(2, size), doorX + scale(3, size), y + scale(4, size), hallMinZ + scale(2, size), Material.WHITE_WOOL);

        // Side banners.
        fill(world, hallMinX - 1, y + 3, hallBackZ - scale(6, size), hallMinX - 1, y + scale(8, size), hallBackZ - scale(4, size), Material.BLUE_WOOL);
        fill(world, hallMaxX + 1, y + 3, hallBackZ - scale(6, size), hallMaxX + 1, y + scale(8, size), hallBackZ - scale(4, size), Material.BLUE_WOOL);

        // Adventurer interior upgrade.
        decorateAdventurerInterior(world, hallMinX, hallMaxX, hallMinZ, hallFrontZ, y, wallTop, size, doorX);

        // Emblem + sign.
        int emblemZ = hallFrontZ + scale(4, size);
        fill(world, doorX - 6, y + 1, emblemZ, doorX + 6, y + 11, emblemZ, type.frame());
        fill(world, doorX - 5, y + 2, emblemZ, doorX + 5, y + 10, emblemZ, plaster);
        drawEmblem(world, doorX, y + 2, emblemZ, type);
        placeSign(world, doorX, y + 1, emblemZ - 1, type.displayName(), "Grand Hall");

        // Front path.
        fill(world, doorX - scale(2, size), y + 1, hallFrontZ + 1, doorX + scale(3, size), y + 1, maxZ - 1, Material.STONE_BRICK_SLAB);
    }

    private static void buildMerchantHall(World world, int minX, int minZ, int maxX, int maxZ, int y, GuildHallType type, int size) {
        Material stoneBase = Material.STONE_BRICKS;
        Material timber = Material.SPRUCE_LOG;
        Material plaster = Material.SMOOTH_SANDSTONE;
        Material roof = Material.DARK_OAK_PLANKS;
        Material roofStair = Material.DARK_OAK_STAIRS;
        Material glass = Material.LIGHT_GRAY_STAINED_GLASS;
        Material trim = Material.DARK_OAK_PLANKS;

        // Plot prep.
        fill(world, minX, y, minZ, maxX, y + scale(26, size), maxZ, Material.AIR);
        fill(world, minX, y - 1, minZ, maxX, y - 1, maxZ, Material.STONE_BRICKS);
        fill(world, minX + 1, y, minZ + 1, maxX - 1, y, maxZ - 1, Material.POLISHED_ANDESITE);

        int hallMinX = minX + scale(7, size);
        int hallMaxX = maxX - scale(7, size);
        int hallMinZ = minZ + scale(12, size);
        int hallMaxZ = maxZ - scale(10, size);
        int wallTop = y + scale(10, size);
        int doorX = (hallMinX + hallMaxX) / 2;

        // Main shell.
        fillHollow(world, hallMinX, y + 1, hallMinZ, hallMaxX, wallTop, hallMaxZ, plaster, timber);

        // Stone ground floor belt.
        for (int yy = y + 1; yy <= y + scale(4, size); yy++) {
            for (int x = hallMinX; x <= hallMaxX; x++) {
                world.getBlockAt(x, yy, hallMinZ).setType(stoneBase, false);
                world.getBlockAt(x, yy, hallMaxZ).setType(stoneBase, false);
            }
            for (int z = hallMinZ; z <= hallMaxZ; z++) {
                world.getBlockAt(hallMinX, yy, z).setType(stoneBase, false);
                world.getBlockAt(hallMaxX, yy, z).setType(stoneBase, false);
            }
        }

        // Timber columns + horizontal beams.
        for (int x = hallMinX + scale(3, size); x <= hallMaxX - scale(3, size); x += Math.max(3, scale(4, size))) {
            fill(world, x, y + 2, hallMinZ, x, wallTop - 1, hallMinZ, timber);
            fill(world, x, y + 2, hallMaxZ, x, wallTop - 1, hallMaxZ, timber);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallMaxZ - scale(3, size); z += Math.max(3, scale(4, size))) {
            fill(world, hallMinX, y + 2, z, hallMinX, wallTop - 1, z, timber);
            fill(world, hallMaxX, y + 2, z, hallMaxX, wallTop - 1, z, timber);
        }
        fill(world, hallMinX, y + scale(5, size), hallMinZ, hallMaxX, y + scale(5, size), hallMaxZ, trim);
        fill(world, hallMinX, y + scale(8, size), hallMinZ, hallMaxX, y + scale(8, size), hallMaxZ, trim);

        // Front porch / awning.
        int porchDepth = scale(2, size);
        int porchHalf = scale(4, size);
        int porchZ = hallMaxZ + porchDepth;
        fill(world, doorX - porchHalf, y + 1, hallMaxZ + 1, doorX + porchHalf, y + 1, porchZ, Material.SPRUCE_PLANKS);
        fill(world, doorX - porchHalf, y + scale(5, size), hallMaxZ + 1, doorX + porchHalf, y + scale(5, size), porchZ, trim);
        fill(world, doorX - porchHalf, y + 2, porchZ, doorX - porchHalf, y + scale(4, size), porchZ, timber);
        fill(world, doorX + porchHalf, y + 2, porchZ, doorX + porchHalf, y + scale(4, size), porchZ, timber);

        // Entrance.
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(doorX + dx, y + 2, hallMaxZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + 3, hallMaxZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + scale(4, size), hallMaxZ).setType(Material.AIR, false);
        }
        setStairs(world, doorX, y + 1, hallMaxZ, Material.STONE_BRICK_STAIRS, BlockFace.SOUTH);
        setStairs(world, doorX + 1, y + 1, hallMaxZ, Material.STONE_BRICK_STAIRS, BlockFace.SOUTH);

        // Windows.
        for (int x = hallMinX + scale(2, size); x <= hallMaxX - scale(2, size); x += Math.max(3, scale(4, size))) {
            if (Math.abs(x - doorX) <= scale(3, size)) continue;
            addTallWindow(world, x, y + scale(3, size), y + scale(7, size), hallMinZ, glass);
            addTallWindow(world, x, y + scale(3, size), y + scale(7, size), hallMaxZ, glass);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallMaxZ - scale(3, size); z += Math.max(3, scale(4, size))) {
            addTallWindow(world, hallMinX, y + scale(3, size), y + scale(7, size), z, glass);
            addTallWindow(world, hallMaxX, y + scale(3, size), y + scale(7, size), z, glass);
        }

        // Sloped roof with long ridge.
        int roofMinX = hallMinX - 1;
        int roofMaxX = hallMaxX + 1;
        int roofMinZ = hallMinZ - 1;
        int roofMaxZ = hallMaxZ + 1;
        for (int layer = 0; layer <= scale(6, size); layer++) {
            int z1 = roofMinZ + layer;
            int z2 = roofMaxZ - layer;
            if (z1 > z2) break;
            fill(world, roofMinX, wallTop + 1 + layer, z1, roofMaxX, wallTop + 1 + layer, z2, roof);
            for (int x = roofMinX; x <= roofMaxX; x++) {
                setStairs(world, x, wallTop + 1 + layer, z1, roofStair, BlockFace.NORTH);
                setStairs(world, x, wallTop + 1 + layer, z2, roofStair, BlockFace.SOUTH);
            }
        }
        fill(world, roofMinX + scale(2, size), wallTop + scale(8, size), (roofMinZ + roofMaxZ) / 2, roofMaxX - scale(2, size), wallTop + scale(8, size), (roofMinZ + roofMaxZ) / 2, trim);

        // Interior floor + merchant hall vibe.
        fill(world, hallMinX + 1, y + 1, hallMinZ + 1, hallMaxX - 1, y + 1, hallMaxZ - 1, Material.SMOOTH_STONE);
        fill(world, doorX, y + 2, hallMinZ + scale(2, size), doorX + Math.max(1, scale(1, size)), y + 2, hallMaxZ - scale(2, size), Material.RED_CARPET);
        fill(world, doorX - Math.max(1, scale(1, size)), y + 2, hallMinZ + scale(2, size), doorX - Math.max(1, scale(1, size)), y + 2, hallMaxZ - scale(2, size), Material.YELLOW_CARPET);
        fill(world, doorX + Math.max(2, scale(2, size)), y + 2, hallMinZ + scale(2, size), doorX + Math.max(2, scale(2, size)), y + 2, hallMaxZ - scale(2, size), Material.YELLOW_CARPET);

        // Wall decorations (banner blocks, storage, counters).
        fill(world, hallMinX + scale(2, size), y + 3, hallMinZ + 1, hallMinX + scale(2, size), y + scale(8, size), hallMinZ + 1, Material.GREEN_WOOL);
        fill(world, hallMinX + scale(5, size), y + 3, hallMinZ + 1, hallMinX + scale(5, size), y + scale(8, size), hallMinZ + 1, Material.GREEN_WOOL);
        fill(world, hallMaxX - scale(5, size), y + 3, hallMinZ + 1, hallMaxX - scale(5, size), y + scale(8, size), hallMinZ + 1, Material.RED_WOOL);
        fill(world, hallMaxX - scale(2, size), y + 3, hallMinZ + 1, hallMaxX - scale(2, size), y + scale(8, size), hallMinZ + 1, Material.BLACK_WOOL);

        fill(world, hallMinX + scale(2, size), y + 2, hallMinZ + scale(2, size), hallMinX + scale(5, size), y + 2, hallMinZ + scale(3, size), Material.DARK_OAK_PLANKS);
        world.getBlockAt(hallMinX + scale(2, size), y + 2, hallMinZ + scale(4, size)).setType(Material.CHEST, false);
        world.getBlockAt(hallMinX + scale(4, size), y + 2, hallMinZ + scale(4, size)).setType(Material.BARREL, false);
        fill(world, hallMaxX - scale(5, size), y + 2, hallMinZ + scale(2, size), hallMaxX - scale(2, size), y + 2, hallMinZ + scale(3, size), Material.DARK_OAK_PLANKS);
        world.getBlockAt(hallMaxX - scale(4, size), y + 2, hallMinZ + scale(4, size)).setType(Material.CHEST, false);
        world.getBlockAt(hallMaxX - scale(2, size), y + 2, hallMinZ + scale(4, size)).setType(Material.BARREL, false);

        // Merchant interior upgrade.
        decorateMerchantInterior(world, hallMinX, hallMaxX, hallMinZ, hallMaxZ, y, wallTop, size, doorX);

        // Emblem wall + sign.
        int emblemZ = hallMaxZ + scale(5, size);
        fill(world, doorX - 6, y + 1, emblemZ, doorX + 6, y + 11, emblemZ, type.frame());
        fill(world, doorX - 5, y + 2, emblemZ, doorX + 5, y + 10, emblemZ, plaster);
        drawEmblem(world, doorX, y + 2, emblemZ, type);
        placeSign(world, doorX, y + 1, emblemZ - 1, type.displayName(), "Trade Hall");

        // Front path.
        fill(world, doorX - scale(2, size), y + 1, hallMaxZ + 1, doorX + scale(2, size), y + 1, maxZ - 1, Material.STONE_BRICK_SLAB);
    }

    private static void buildThievesHall(World world, int minX, int minZ, int maxX, int maxZ, int y, GuildHallType type, int size) {
        Material wall = Material.TUFF_BRICKS;
        Material frame = Material.POLISHED_DEEPSLATE;
        Material trim = Material.POLISHED_BLACKSTONE_BRICKS;
        Material roof = Material.DEEPSLATE_TILES;
        Material roofStair = Material.DEEPSLATE_TILE_STAIRS;
        Material glass = Material.GRAY_STAINED_GLASS;
        Material floor = Material.DARK_OAK_PLANKS;
        Material rail = Material.DARK_OAK_FENCE;

        // Plot prep.
        fill(world, minX, y, minZ, maxX, y + scale(32, size), maxZ, Material.AIR);
        fill(world, minX, y - 1, minZ, maxX, y - 1, maxZ, Material.COBBLED_DEEPSLATE);
        fill(world, minX + 1, y, minZ + 1, maxX - 1, y, maxZ - 1, Material.POLISHED_DEEPSLATE);

        int hallMinX = minX + scale(7, size);
        int hallMaxX = maxX - scale(7, size);
        int hallMinZ = minZ + scale(13, size);
        int hallMaxZ = maxZ - scale(10, size);
        int wallTop = y + scale(10, size);
        int doorX = (hallMinX + hallMaxX) / 2;

        // Main shell and asymmetric side annexes.
        fillHollow(world, hallMinX, y + 1, hallMinZ, hallMaxX, wallTop, hallMaxZ, wall, frame);
        fillHollow(world, hallMinX - scale(5, size), y + 1, hallMaxZ - scale(9, size), hallMinX + scale(3, size), y + scale(8, size), hallMaxZ + scale(2, size), wall, frame);
        fillHollow(world, hallMaxX - scale(3, size), y + 1, hallMinZ + scale(2, size), hallMaxX + scale(5, size), y + scale(7, size), hallMinZ + scale(12, size), wall, frame);

        // Extra framing and buttresses for a rough guild hideout look.
        for (int x = hallMinX + scale(3, size); x <= hallMaxX - scale(3, size); x += Math.max(3, scale(4, size))) {
            fill(world, x, y + 2, hallMinZ, x, wallTop - 1, hallMinZ, frame);
            fill(world, x, y + 2, hallMaxZ, x, wallTop - 1, hallMaxZ, frame);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallMaxZ - scale(3, size); z += Math.max(3, scale(4, size))) {
            fill(world, hallMinX, y + 2, z, hallMinX, wallTop - 1, z, frame);
            fill(world, hallMaxX, y + 2, z, hallMaxX, wallTop - 1, z, frame);
        }
        fill(world, hallMinX, y + scale(5, size), hallMinZ, hallMaxX, y + scale(5, size), hallMaxZ, trim);
        fill(world, hallMinX, y + scale(8, size), hallMinZ, hallMaxX, y + scale(8, size), hallMaxZ, trim);

        // Entry cut.
        for (int dx = 0; dx <= 1; dx++) {
            world.getBlockAt(doorX + dx, y + 2, hallMaxZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + 3, hallMaxZ).setType(Material.AIR, false);
            world.getBlockAt(doorX + dx, y + scale(4, size), hallMaxZ).setType(Material.AIR, false);
            setStairs(world, doorX + dx, y + 1, hallMaxZ, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.SOUTH);
        }
        fill(world, doorX - scale(3, size), y + 1, hallMaxZ + 1, doorX + scale(4, size), y + 1, hallMaxZ + scale(3, size), Material.POLISHED_BLACKSTONE_BRICKS);
        fill(world, doorX - scale(4, size), y + 2, hallMaxZ + scale(3, size), doorX - scale(4, size), y + scale(5, size), hallMaxZ + scale(3, size), frame);
        fill(world, doorX + scale(5, size), y + 2, hallMaxZ + scale(3, size), doorX + scale(5, size), y + scale(5, size), hallMaxZ + scale(3, size), frame);
        fill(world, doorX - scale(4, size), y + scale(6, size), hallMaxZ + 1, doorX + scale(5, size), y + scale(6, size), hallMaxZ + scale(3, size), trim);

        // Narrow windows.
        for (int x = hallMinX + scale(2, size); x <= hallMaxX - scale(2, size); x += Math.max(4, scale(5, size))) {
            if (Math.abs(x - doorX) <= scale(4, size)) continue;
            addTallWindow(world, x, y + scale(4, size), y + scale(7, size), hallMinZ, glass);
            addTallWindow(world, x, y + scale(4, size), y + scale(7, size), hallMaxZ, glass);
        }
        for (int z = hallMinZ + scale(3, size); z <= hallMaxZ - scale(3, size); z += Math.max(4, scale(5, size))) {
            addTallWindow(world, hallMinX, y + scale(4, size), y + scale(7, size), z, glass);
            addTallWindow(world, hallMaxX, y + scale(4, size), y + scale(7, size), z, glass);
        }

        // Main steep roof.
        int roofMinX = hallMinX - 1;
        int roofMaxX = hallMaxX + 1;
        for (int layer = 0; layer <= scale(7, size); layer++) {
            int x1 = roofMinX + layer;
            int x2 = roofMaxX - layer;
            if (x1 > x2) break;
            fill(world, x1, wallTop + 1 + layer, hallMinZ - 1, x2, wallTop + 1 + layer, hallMaxZ + 1, roof);
            for (int z = hallMinZ - 1; z <= hallMaxZ + 1; z++) {
                setStairs(world, x1, wallTop + 1 + layer, z, roofStair, BlockFace.WEST);
                setStairs(world, x2, wallTop + 1 + layer, z, roofStair, BlockFace.EAST);
            }
        }

        // Left cross-gable.
        int wingMinX = hallMinX - scale(5, size);
        int wingMaxX = hallMinX + scale(3, size);
        int wingMidZ = hallMaxZ - scale(3, size);
        for (int layer = 0; layer <= scale(4, size); layer++) {
            int z1 = wingMidZ - scale(3, size) + layer;
            int z2 = wingMidZ + scale(3, size) - layer;
            if (z1 > z2) break;
            fill(world, wingMinX - 1, y + 9 + layer, z1, wingMaxX + 1, y + 9 + layer, z2, roof);
        }

        // Right watch tower.
        int towerMinX = hallMaxX - scale(3, size);
        int towerMaxX = hallMaxX + scale(3, size);
        int towerMinZ = hallMinZ + scale(4, size);
        int towerMaxZ = hallMinZ + scale(10, size);
        fillHollow(world, towerMinX, y + 1, towerMinZ, towerMaxX, y + scale(15, size), towerMaxZ, wall, frame);
        for (int layer = 0; layer <= scale(3, size); layer++) {
            fill(world, towerMinX + layer, y + scale(16, size) + layer, towerMinZ + layer, towerMaxX - layer, y + scale(16, size) + layer, towerMaxZ - layer, roof);
        }
        addTallWindow(world, (towerMinX + towerMaxX) / 2, y + scale(6, size), y + scale(10, size), towerMinZ, glass);
        addTallWindow(world, (towerMinX + towerMaxX) / 2, y + scale(6, size), y + scale(10, size), towerMaxZ, glass);

        // Interior floor and mezzanine.
        fill(world, hallMinX + 1, y + 1, hallMinZ + 1, hallMaxX - 1, y + 1, hallMaxZ - 1, floor);
        fill(world, hallMinX + scale(2, size), y + 2, hallMinZ + scale(2, size), hallMaxX - scale(2, size), y + 2, hallMinZ + scale(3, size), Material.POLISHED_BLACKSTONE_BRICKS);
        fill(world, hallMinX + scale(2, size), y + 2, hallMaxZ - scale(3, size), hallMaxX - scale(2, size), y + 2, hallMaxZ - scale(2, size), Material.POLISHED_BLACKSTONE_BRICKS);
        fill(world, hallMinX + scale(2, size), y + scale(5, size), hallMinZ + scale(2, size), hallMinX + scale(5, size), y + scale(5, size), hallMaxZ - scale(2, size), Material.SPRUCE_PLANKS);
        fill(world, hallMaxX - scale(5, size), y + scale(5, size), hallMinZ + scale(2, size), hallMaxX - scale(2, size), y + scale(5, size), hallMaxZ - scale(2, size), Material.SPRUCE_PLANKS);
        fill(world, hallMinX + scale(6, size), y + scale(5, size), hallMinZ + scale(2, size), hallMaxX - scale(6, size), y + scale(5, size), hallMinZ + scale(4, size), Material.SPRUCE_PLANKS);
        fill(world, hallMinX + scale(5, size), y + scale(6, size), hallMinZ + scale(2, size), hallMinX + scale(5, size), y + scale(6, size), hallMaxZ - scale(2, size), rail);
        fill(world, hallMaxX - scale(5, size), y + scale(6, size), hallMinZ + scale(2, size), hallMaxX - scale(5, size), y + scale(6, size), hallMaxZ - scale(2, size), rail);

        // Stair runs up to mezzanine.
        for (int step = 0; step <= scale(4, size); step++) {
            setStairs(world, hallMinX + scale(7, size) + step, y + 2 + step, hallMaxZ - scale(4, size), Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
            world.getBlockAt(hallMinX + scale(7, size) + step, y + 1 + step, hallMaxZ - scale(4, size)).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            setStairs(world, hallMaxX - scale(7, size) - step, y + 2 + step, hallMaxZ - scale(4, size), Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);
            world.getBlockAt(hallMaxX - scale(7, size) - step, y + 1 + step, hallMaxZ - scale(4, size)).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        }

        // Crates, barrels, and clutter.
        for (int x = hallMinX + scale(3, size); x <= hallMaxX - scale(3, size); x += Math.max(4, scale(5, size))) {
            world.getBlockAt(x, y + 2, hallMinZ + scale(5, size)).setType(Material.BARREL, false);
            world.getBlockAt(x, y + 2, hallMaxZ - scale(5, size)).setType(Material.CHEST, false);
            world.getBlockAt(x, y + 2, hallMinZ + scale(6, size)).setType(Material.SPRUCE_TRAPDOOR, false);
            world.getBlockAt(x, y + 2, hallMaxZ - scale(6, size)).setType(Material.SPRUCE_TRAPDOOR, false);
        }
        fill(world, doorX - Math.max(1, scale(1, size)), y + 2, hallMinZ + scale(6, size), doorX + scale(2, size), y + 2, hallMinZ + scale(8, size), Material.POLISHED_BLACKSTONE_BRICKS);
        world.getBlockAt(doorX, y + 3, hallMinZ + scale(7, size)).setType(Material.ANVIL, false);

        // Hanging chains and monochrome canopy strips.
        for (int x = hallMinX + scale(4, size); x <= hallMaxX - scale(4, size); x += Math.max(3, scale(4, size))) {
            world.getBlockAt(x, y + scale(10, size), hallMinZ + scale(6, size)).setType(Material.CHAIN, false);
            world.getBlockAt(x, y + scale(9, size), hallMinZ + scale(6, size)).setType(Material.CHAIN, false);
            world.getBlockAt(x, y + scale(8, size), hallMinZ + scale(6, size)).setType(Material.BLACK_WOOL, false);
        }

        // Thieves interior upgrade.
        decorateThievesInterior(world, hallMinX, hallMaxX, hallMinZ, hallMaxZ, y, wallTop, size, doorX);

        // Emblem wall + sign.
        int emblemZ = hallMaxZ + scale(6, size);
        fill(world, doorX - 6, y + 1, emblemZ, doorX + 6, y + 11, emblemZ, frame);
        fill(world, doorX - 5, y + 2, emblemZ, doorX + 5, y + 10, emblemZ, wall);
        drawEmblem(world, doorX, y + 2, emblemZ, type);
        placeSign(world, doorX, y + 1, emblemZ - 1, type.displayName(), "Shadow Hall");

        // Front path.
        fill(world, doorX - scale(2, size), y + 1, hallMaxZ + 1, doorX + scale(2, size), y + 1, maxZ - 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
    }

    public static void buildAll(Location center) {
        buildAll(center, DEFAULT_HALL_SIZE);
    }

    public static void buildAll(Location center, int requestedSize) {
        buildAll(center, requestedSize, BlockFace.SOUTH);
    }

    public static void buildAll(Location center, int requestedSize, BlockFace facing) {
        if (center == null || center.getWorld() == null) return;
        int size = normalizeSize(requestedSize);
        int spacing = size + 24;
        BlockFace normalized = normalizeFacing(facing);
        buildHall(center.clone().add(-spacing, 0, -spacing), GuildHallType.HUNTERS, size, normalized);
        buildHall(center.clone().add(spacing, 0, -spacing), GuildHallType.MERCHANT, size, normalized);
        buildHall(center.clone().add(-spacing, 0, spacing), GuildHallType.ADVENTURER, size, normalized);
        buildHall(center.clone().add(spacing, 0, spacing), GuildHallType.THIEVES, size, normalized);
    }

    public static void clearGuildArea(Location center, int radius) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int r = Math.max(25, Math.min(300, radius));
        int minX = center.getBlockX() - r;
        int maxX = center.getBlockX() + r;
        int minZ = center.getBlockZ() - r;
        int maxZ = center.getBlockZ() + r;
        int minY = Math.max(world.getMinHeight() + 1, center.getBlockY() - 5);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + 65);
        fill(world, minX, minY, minZ, maxX, maxY, maxZ, Material.AIR);
    }

    public static int normalizeSize(int requestedSize) {
        return Math.max(MIN_HALL_SIZE, Math.min(MAX_HALL_SIZE, requestedSize));
    }

    private static int scale(int baseValueAt50, int size) {
        return Math.max(1, (int) Math.round(baseValueAt50 * (size / 50.0)));
    }

    private static void decorateHuntersInterior(World world, int hallMinX, int hallMaxX, int hallMinZ, int hallMaxZ, int y, int wallTop, int size, int doorX) {
        int aisleHalf = Math.max(1, scale(1, size));
        fill(world, doorX - aisleHalf, y + 2, hallMinZ + scale(2, size), doorX + aisleHalf, y + 2, hallMaxZ - scale(2, size), Material.MOSS_CARPET);

        int benchDepth = Math.max(1, scale(1, size));
        int benchStep = Math.max(4, scale(6, size));
        for (int z = hallMinZ + scale(3, size); z <= hallMaxZ - scale(4, size); z += benchStep) {
            fill(world, hallMinX + scale(2, size), y + 2, z, hallMinX + scale(5, size), y + 2, z + benchDepth, Material.DARK_OAK_SLAB);
            fill(world, hallMaxX - scale(5, size), y + 2, z, hallMaxX - scale(2, size), y + 2, z + benchDepth, Material.DARK_OAK_SLAB);
        }

        int cx = (hallMinX + hallMaxX) / 2;
        int cz = (hallMinZ + hallMaxZ) / 2;
        fill(world, cx - scale(2, size), y + 1, cz - scale(2, size), cx + scale(2, size), y + 1, cz + scale(2, size), Material.POLISHED_ANDESITE);
        world.getBlockAt(cx, y + 2, cz).setType(Material.CAMPFIRE, false);
        world.getBlockAt(cx - 1, y + 2, cz).setType(Material.BARREL, false);
        world.getBlockAt(cx + 1, y + 2, cz).setType(Material.BARREL, false);

        fill(world, cx - scale(4, size), y + 3, hallMinZ + 1, cx + scale(4, size), y + 3, hallMinZ + 1, Material.DARK_OAK_LOG);
        world.getBlockAt(cx - scale(3, size), y + 4, hallMinZ + 1).setType(Material.TARGET, false);
        world.getBlockAt(cx, y + 4, hallMinZ + 1).setType(Material.TARGET, false);
        world.getBlockAt(cx + scale(3, size), y + 4, hallMinZ + 1).setType(Material.TARGET, false);

        placeSign(world, doorX, y + 2, hallMaxZ - scale(2, size), "Reception", "FancyNPC Here");
        hangLantern(world, cx, wallTop, cz, Math.max(2, scale(3, size)), Material.LANTERN);
    }

    private static void decorateAdventurerInterior(World world, int hallMinX, int hallMaxX, int hallMinZ, int hallFrontZ, int y, int wallTop, int size, int doorX) {
        int receptionZ = hallFrontZ - scale(4, size);
        fill(world, doorX - scale(2, size), y + 2, receptionZ, doorX + scale(2, size), y + 2, receptionZ + Math.max(1, scale(1, size)), Material.DARK_OAK_PLANKS);
        placeSign(world, doorX, y + 3, receptionZ, "Reception", "FancyNPC Here");

        int tableSpan = Math.max(2, scale(2, size));
        int centerZ = (hallMinZ + hallFrontZ) / 2;
        fill(world, doorX - tableSpan, y + 2, centerZ - 1, doorX + tableSpan, y + 2, centerZ + 1, Material.SPRUCE_PLANKS);
        fill(world, doorX - tableSpan, y + 2, centerZ + scale(7, size), doorX + tableSpan, y + 2, centerZ + scale(9, size), Material.SPRUCE_PLANKS);

        int chairOffset = tableSpan + 2;
        fill(world, doorX - chairOffset, y + 2, centerZ - 1, doorX - chairOffset, y + 2, centerZ + 1, Material.SPRUCE_SLAB);
        fill(world, doorX + chairOffset, y + 2, centerZ - 1, doorX + chairOffset, y + 2, centerZ + 1, Material.SPRUCE_SLAB);
        fill(world, doorX - chairOffset, y + 2, centerZ + scale(7, size), doorX - chairOffset, y + 2, centerZ + scale(9, size), Material.SPRUCE_SLAB);
        fill(world, doorX + chairOffset, y + 2, centerZ + scale(7, size), doorX + chairOffset, y + 2, centerZ + scale(9, size), Material.SPRUCE_SLAB);

        fill(world, hallMinX + scale(2, size), y + 2, hallFrontZ - scale(8, size), hallMinX + scale(4, size), y + 3, hallFrontZ - scale(6, size), Material.CHISELED_BOOKSHELF);
        fill(world, hallMaxX - scale(4, size), y + 2, hallFrontZ - scale(8, size), hallMaxX - scale(2, size), y + 3, hallFrontZ - scale(6, size), Material.CHISELED_BOOKSHELF);
        world.getBlockAt(hallMinX + scale(3, size), y + 2, hallFrontZ - scale(9, size)).setType(Material.CARTOGRAPHY_TABLE, false);
        world.getBlockAt(hallMaxX - scale(3, size), y + 2, hallFrontZ - scale(9, size)).setType(Material.CARTOGRAPHY_TABLE, false);

        hangLantern(world, doorX - scale(5, size), wallTop, centerZ, Math.max(2, scale(3, size)), Material.LANTERN);
        hangLantern(world, doorX + scale(5, size), wallTop, centerZ, Math.max(2, scale(3, size)), Material.LANTERN);
    }

    private static void decorateMerchantInterior(World world, int hallMinX, int hallMaxX, int hallMinZ, int hallMaxZ, int y, int wallTop, int size, int doorX) {
        int receptionZ = hallMaxZ - scale(4, size);
        fill(world, doorX - scale(3, size), y + 2, receptionZ, doorX + scale(3, size), y + 3, receptionZ + Math.max(1, scale(1, size)), Material.SPRUCE_PLANKS);
        placeSign(world, doorX, y + 4, receptionZ, "Reception", "FancyNPC Here");

        int marketStep = Math.max(6, scale(8, size));
        int startZ = hallMinZ + scale(7, size);
        for (int z = startZ; z <= hallMaxZ - scale(7, size); z += marketStep) {
            fill(world, hallMinX + scale(3, size), y + 2, z, hallMinX + scale(6, size), y + 2, z + scale(2, size), Material.SPRUCE_PLANKS);
            fill(world, hallMaxX - scale(6, size), y + 2, z, hallMaxX - scale(3, size), y + 2, z + scale(2, size), Material.SPRUCE_PLANKS);
            fill(world, hallMinX + scale(3, size), y + scale(5, size), z, hallMinX + scale(6, size), y + scale(5, size), z + scale(2, size), Material.RED_WOOL);
            fill(world, hallMaxX - scale(6, size), y + scale(5, size), z, hallMaxX - scale(3, size), y + scale(5, size), z + scale(2, size), Material.GREEN_WOOL);
            world.getBlockAt(hallMinX + scale(4, size), y + 3, z + 1).setType(Material.BARREL, false);
            world.getBlockAt(hallMaxX - scale(4, size), y + 3, z + 1).setType(Material.CHEST, false);
        }

        fill(world, doorX - scale(6, size), y + 2, hallMinZ + 1, doorX + scale(6, size), y + 4, hallMinZ + scale(2, size), Material.DARK_OAK_PLANKS);
        fill(world, doorX - scale(5, size), y + 5, hallMinZ + 1, doorX + scale(5, size), y + 6, hallMinZ + scale(2, size), Material.BARREL);
        world.getBlockAt(doorX, y + 5, hallMinZ + scale(3, size)).setType(Material.LECTERN, false);

        hangLantern(world, doorX, wallTop, hallMinZ + scale(6, size), Math.max(2, scale(3, size)), Material.LANTERN);
        hangLantern(world, doorX, wallTop, hallMaxZ - scale(6, size), Math.max(2, scale(3, size)), Material.LANTERN);
    }

    private static void decorateThievesInterior(World world, int hallMinX, int hallMaxX, int hallMinZ, int hallMaxZ, int y, int wallTop, int size, int doorX) {
        int receptionZ = hallMaxZ - scale(4, size);
        fill(world, doorX - scale(3, size), y + 2, receptionZ, doorX + scale(3, size), y + 3, receptionZ + Math.max(1, scale(1, size)), Material.POLISHED_BLACKSTONE_BRICKS);
        placeSign(world, doorX, y + 4, receptionZ, "Reception", "FancyNPC Here");

        int centerZ = (hallMinZ + hallMaxZ) / 2;
        fill(world, doorX - scale(3, size), y + 2, centerZ - scale(2, size), doorX + scale(3, size), y + 2, centerZ + scale(2, size), Material.POLISHED_BLACKSTONE_BRICKS);
        world.getBlockAt(doorX, y + 3, centerZ).setType(Material.CRAFTING_TABLE, false);
        world.getBlockAt(doorX - 1, y + 3, centerZ).setType(Material.WHITE_CARPET, false);
        world.getBlockAt(doorX + 1, y + 3, centerZ).setType(Material.WHITE_CARPET, false);

        int drapeStep = Math.max(5, scale(7, size));
        for (int z = hallMinZ + scale(5, size); z <= hallMaxZ - scale(5, size); z += drapeStep) {
            world.getBlockAt(hallMinX + scale(3, size), wallTop, z).setType(Material.CHAIN, false);
            world.getBlockAt(hallMinX + scale(3, size), wallTop - 1, z).setType(Material.GRAY_WOOL, false);
            world.getBlockAt(hallMaxX - scale(3, size), wallTop, z).setType(Material.CHAIN, false);
            world.getBlockAt(hallMaxX - scale(3, size), wallTop - 1, z).setType(Material.GRAY_WOOL, false);
        }

        fill(world, hallMaxX - scale(7, size), y + 2, hallMaxZ - scale(7, size), hallMaxX - scale(5, size), y + 4, hallMaxZ - scale(5, size), Material.IRON_BLOCK);
        world.getBlockAt(hallMaxX - scale(6, size), y + 3, hallMaxZ - scale(8, size)).setType(Material.IRON_DOOR, false);
        world.getBlockAt(hallMaxX - scale(6, size), y + 2, hallMaxZ - scale(8, size)).setType(Material.IRON_DOOR, false);

        hangLantern(world, doorX - scale(5, size), wallTop, centerZ, Math.max(2, scale(3, size)), Material.SOUL_LANTERN);
        hangLantern(world, doorX + scale(5, size), wallTop, centerZ, Math.max(2, scale(3, size)), Material.SOUL_LANTERN);
    }

    private static void hangLantern(World world, int x, int yTop, int z, int chainLength, Material lanternType) {
        int len = Math.max(1, chainLength);
        for (int i = 0; i < len; i++) {
            world.getBlockAt(x, yTop - i, z).setType(Material.CHAIN, false);
        }
        world.getBlockAt(x, yTop - len, z).setType(lanternType, false);
    }

    private static void fill(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Material material) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private static void fillHollow(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Material wall, Material frame) {
        // Fill shell with wall.
        fill(world, minX, minY, minZ, maxX, maxY, maxZ, wall);
        // Hollow interior.
        fill(world, minX + 1, minY + 1, minZ + 1, maxX - 1, maxY - 1, maxZ - 1, Material.AIR);

        // Frame columns.
        fill(world, minX, minY, minZ, minX, maxY, minZ, frame);
        fill(world, maxX, minY, minZ, maxX, maxY, minZ, frame);
        fill(world, minX, minY, maxZ, minX, maxY, maxZ, frame);
        fill(world, maxX, minY, maxZ, maxX, maxY, maxZ, frame);
    }

    private static void addWindowBand(World world, int minX, int maxX, int y, int minZ, int maxZ, Material glass) {
        for (int x = minX + 2; x <= maxX - 2; x += 3) {
            world.getBlockAt(x, y, minZ).setType(glass, false);
            world.getBlockAt(x, y + 1, minZ).setType(glass, false);
            world.getBlockAt(x, y, maxZ).setType(glass, false);
            world.getBlockAt(x, y + 1, maxZ).setType(glass, false);
        }
        for (int z = minZ + 2; z <= maxZ - 2; z += 3) {
            world.getBlockAt(minX, y, z).setType(glass, false);
            world.getBlockAt(minX, y + 1, z).setType(glass, false);
            world.getBlockAt(maxX, y, z).setType(glass, false);
            world.getBlockAt(maxX, y + 1, z).setType(glass, false);
        }
    }

    private static void addTallWindow(World world, int x, int yMin, int yMax, int z, Material glass) {
        for (int y = yMin; y <= yMax; y++) {
            world.getBlockAt(x, y, z).setType(glass, false);
        }
    }

    private static void drawEmblem(World world, int cx, int y, int z, GuildHallType type) {
        Material emblem = type.emblem();
        switch (type) {
            case HUNTERS -> {
                // Crossed marks.
                for (int i = 0; i < 9; i++) {
                    world.getBlockAt(cx - 4 + i, y + i, z).setType(emblem, false);
                    world.getBlockAt(cx + 4 - i, y + i, z).setType(emblem, false);
                }
            }
            case MERCHANT -> {
                // Diamond.
                for (int i = 0; i <= 4; i++) {
                    setH(world, cx, y + i, z, i, emblem);
                }
                for (int i = 3; i >= 0; i--) {
                    setH(world, cx, y + (8 - i), z, i, emblem);
                }
            }
            case ADVENTURER -> {
                // Compass/star.
                for (int i = -4; i <= 4; i++) {
                    world.getBlockAt(cx + i, y + 4, z).setType(emblem, false);
                    world.getBlockAt(cx, y + 4 + i, z).setType(emblem, false);
                }
                world.getBlockAt(cx - 3, y + 7, z).setType(emblem, false);
                world.getBlockAt(cx + 3, y + 7, z).setType(emblem, false);
                world.getBlockAt(cx - 3, y + 1, z).setType(emblem, false);
                world.getBlockAt(cx + 3, y + 1, z).setType(emblem, false);
            }
            case THIEVES -> {
                // Mask/eye motif.
                for (int i = -4; i <= 4; i++) {
                    world.getBlockAt(cx + i, y + 7, z).setType(emblem, false);
                    if (Math.abs(i) <= 2) {
                        world.getBlockAt(cx + i, y + 1, z).setType(emblem, false);
                    }
                }
                for (int i = 0; i <= 5; i++) {
                    world.getBlockAt(cx - 4, y + 2 + i, z).setType(emblem, false);
                    world.getBlockAt(cx + 4, y + 2 + i, z).setType(emblem, false);
                }
                world.getBlockAt(cx - 2, y + 4, z).setType(Material.LIGHT_GRAY_CONCRETE, false);
                world.getBlockAt(cx + 2, y + 4, z).setType(Material.LIGHT_GRAY_CONCRETE, false);
            }
        }
    }

    private static void setH(World world, int cx, int y, int z, int radius, Material material) {
        for (int i = -radius; i <= radius; i++) {
            world.getBlockAt(cx + i, y, z).setType(material, false);
        }
    }

    private static void setStairs(World world, int x, int y, int z, Material material, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        BlockData data = block.getBlockData();
        if (data instanceof Stairs stairs) {
            BlockFace normalizedFacing = normalizeFacing(facing);
            if (stairs.getFaces().contains(normalizedFacing)) {
                stairs.setFacing(normalizedFacing);
            }
            stairs.setHalf(Bisected.Half.BOTTOM);
            stairs.setShape(Stairs.Shape.STRAIGHT);
            block.setBlockData(stairs, false);
        }
    }

    private static BlockFace normalizeFacing(BlockFace facing) {
        if (facing == null) return BlockFace.SOUTH;
        return switch (facing) {
            case NORTH, EAST, SOUTH, WEST -> facing;
            default -> BlockFace.SOUTH;
        };
    }

    private static int turnsFromSouth(BlockFace desiredFacing) {
        return switch (normalizeFacing(desiredFacing)) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static void rotateArea(World world, int x1, int y1, int z1, int x2, int y2, int z2, int clockwiseTurns) {
        int turns = ((clockwiseTurns % 4) + 4) % 4;
        if (turns == 0) return;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        List<BlockSnapshot> snapshots = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    snapshots.add(new BlockSnapshot(x, y, z, block.getType(), block.getBlockData().clone()));
                }
            }
        }

        fill(world, minX, minY, minZ, maxX, maxY, maxZ, Material.AIR);

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        for (BlockSnapshot snapshot : snapshots) {
            int relX = snapshot.x() - minX;
            int relZ = snapshot.z() - minZ;
            int nx;
            int nz;
            switch (turns) {
                case 1 -> {
                    nx = minX + relZ;
                    nz = minZ + (width - 1 - relX);
                }
                case 2 -> {
                    nx = minX + (width - 1 - relX);
                    nz = minZ + (depth - 1 - relZ);
                }
                case 3 -> {
                    nx = minX + (depth - 1 - relZ);
                    nz = minZ + relX;
                }
                default -> {
                    nx = snapshot.x();
                    nz = snapshot.z();
                }
            }

            Block target = world.getBlockAt(nx, snapshot.y(), nz);
            target.setType(snapshot.type(), false);
            target.setBlockData(rotateBlockData(snapshot.data(), turns), false);
        }
    }

    private static BlockData rotateBlockData(BlockData original, int turns) {
        BlockData data = original.clone();
        for (int i = 0; i < turns; i++) {
            if (data instanceof Directional directional) {
                BlockFace current = directional.getFacing();
                BlockFace rotated = rotateHorizontalFaceClockwise(current);
                if (directional.getFaces().contains(rotated)) {
                    directional.setFacing(rotated);
                }
            }
            if (data instanceof Rotatable rotatable) {
                BlockFace current = rotatable.getRotation();
                rotatable.setRotation(rotateHorizontalFaceClockwise(current));
            }
            if (data instanceof Orientable orientable) {
                Axis axis = orientable.getAxis();
                if (axis == Axis.X) orientable.setAxis(Axis.Z);
                else if (axis == Axis.Z) orientable.setAxis(Axis.X);
            }
        }
        return data;
    }

    private static BlockFace rotateHorizontalFaceClockwise(BlockFace face) {
        if (face == null) return BlockFace.SOUTH;
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case NORTH_EAST -> BlockFace.SOUTH_EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH_EAST -> BlockFace.SOUTH_WEST;
            case SOUTH -> BlockFace.WEST;
            case SOUTH_WEST -> BlockFace.NORTH_WEST;
            case WEST -> BlockFace.NORTH;
            case NORTH_WEST -> BlockFace.NORTH_EAST;
            default -> face;
        };
    }

    private record BlockSnapshot(int x, int y, int z, Material type, BlockData data) {}

    private static void placeSign(World world, int x, int y, int z, String line1, String line2) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            sign.setLine(0, line1);
            sign.setLine(1, line2);
            sign.update(true, false);
        }
    }
}
