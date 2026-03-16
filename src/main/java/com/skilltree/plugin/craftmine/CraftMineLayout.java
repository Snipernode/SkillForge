package com.skilltree.plugin.craftmine;

public final class CraftMineLayout {

    private CraftMineLayout() {}

    public static final int GRID_W = 9;
    public static final int GRID_H = 6;

    public static int toSlot(int x, int y) {
        if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) {
            return -1;
        }
        return (y * GRID_W) + x;
    }

    public static int[] fromSlot(int slot) {
        if (slot < 0 || slot >= GRID_W * GRID_H) {
            return new int[]{-1, -1};
        }
        int x = slot % GRID_W;
        int y = slot / GRID_W;
        return new int[]{x, y};
    }
}
