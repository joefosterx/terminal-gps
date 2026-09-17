package dev.tilemap.core;

/**
 * The braille block, U+2800–U+28FF. Dots are numbered in columns (1-2-3 left, 4-5-6 right) with the
 * fourth row (7, 8) added afterwards, so the bit for a dot is not simply {@code row * 2 + col}.
 */
public final class Braille {
    public static final int BASE = 0x2800;

    /** {@code BITS[row][col]} for a 2-wide, 4-tall cell. */
    private static final int[][] BITS = {
        {0x01, 0x08},
        {0x02, 0x10},
        {0x04, 0x20},
        {0x40, 0x80},
    };

    private Braille() {}

    public static int bit(int col, int row) {
        return BITS[row][col];
    }

    public static int glyph(int mask) {
        if ((mask & ~0xff) != 0) throw new IllegalArgumentException("mask out of range: " + mask);
        return BASE + mask;
    }
}
