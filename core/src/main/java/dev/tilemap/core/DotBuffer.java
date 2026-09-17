package dev.tilemap.core;

import java.util.Arrays;

/**
 * Sub-cell raster: 2×4 dots per cell. Each dot stores the index of the highest-priority style layer that
 * painted it, or -1. Because the higher index always wins, the result is independent of drawing order.
 */
public final class DotBuffer {
    public static final short EMPTY = -1;

    private final int width;
    private final int height;
    private final short[] owner;

    public DotBuffer(int cols, int rows) {
        this.width = cols * 2;
        this.height = rows * 4;
        this.owner = new short[width * height];
        clear();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void clear() {
        Arrays.fill(owner, EMPTY);
    }

    /** Claims a dot for {@code layer} unless a higher layer already owns it. Out-of-range dots are ignored. */
    public void set(int x, int y, short layer) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int i = y * width + x;
        if (layer > owner[i]) owner[i] = layer;
    }

    public short get(int x, int y) {
        return owner[y * width + x];
    }
}
