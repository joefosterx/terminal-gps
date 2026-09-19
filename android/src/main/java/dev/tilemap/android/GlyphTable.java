package dev.tilemap.android;

import java.util.HashMap;
import java.util.Map;

/**
 * What a map glyph is made of, so the grid view can draw braille, box-drawing, block and sextant cells as shapes
 * instead of trusting a font to have them. Pure lookup, no Android classes, so it is unit-tested on the JVM.
 */
final class GlyphTable {
    enum Kind { BRAILLE, BOX, QUADRANT, SHADE, SEXTANT, TEXT }

    /** Line weights for {@link #boxArms}. */
    static final int NONE = 0, LIGHT = 1, HEAVY = 2, DOUBLE = 3;

    // The same tables the core's Glyphs class draws from; index bits are N=1, E=2, S=4, W=8.
    private static final String LIGHT_TABLE = " ╵╶└╷│┌├╴┘─┴┐┤┬┼";
    private static final String HEAVY_TABLE = " ╹╺┗╻┃┏┣╸┛━┻┓┫┳╋";
    private static final Map<Integer, Integer> BOX = new HashMap<>();

    static {
        for (int i = 1; i < 16; i++) {
            BOX.put((int) LIGHT_TABLE.charAt(i), arms(i, LIGHT));
            BOX.put((int) HEAVY_TABLE.charAt(i), arms(i, HEAVY));
        }
        // Double lines: the core's table reuses ║ and ═ for stubs, so list the true shapes explicitly.
        String doubles = "║═╚╔╠╝╩╗╣╦╬";
        int[] masks = {1 | 4, 2 | 8, 1 | 2, 2 | 4, 1 | 2 | 4, 1 | 8, 1 | 2 | 8, 4 | 8, 1 | 4 | 8, 2 | 4 | 8, 15};
        for (int i = 0; i < doubles.length(); i++) BOX.put((int) doubles.charAt(i), arms(masks[i], DOUBLE));
    }

    private GlyphTable() {}

    static Kind kind(int cp) {
        if (cp >= 0x2800 && cp <= 0x28FF) return Kind.BRAILLE;
        if (BOX.containsKey(cp)) return Kind.BOX;
        if (cp == '░' || cp == '▒' || cp == '▓') return Kind.SHADE;
        if (cp >= 0x2580 && cp <= 0x259F && quadrantMask(cp) >= 0) return Kind.QUADRANT;
        if (cp >= 0x1FB00 && cp <= 0x1FB3B) return Kind.SEXTANT;
        return Kind.TEXT;
    }

    /** Braille dots as a 2×4 grid mask: bit {@code row * 2 + col}, row 0 at the top. */
    static int brailleMask(int cp) {
        int bits = cp - 0x2800, mask = 0;
        // Unicode braille bit order: dots 1-3 down the left column, 4-6 down the right, then 7 (left) and 8 (right).
        int[] rowOf = {0, 1, 2, 0, 1, 2, 3, 3}, colOf = {0, 0, 0, 1, 1, 1, 0, 1};
        for (int b = 0; b < 8; b++) {
            if ((bits & (1 << b)) != 0) mask |= 1 << (rowOf[b] * 2 + colOf[b]);
        }
        return mask;
    }

    /** Arm weights packed as {@code N | E << 2 | S << 4 | W << 6}, or -1 for a glyph that is not a box line. */
    static int boxArms(int cp) {
        Integer arms = BOX.get(cp);
        return arms == null ? -1 : arms;
    }

    static int arm(int arms, int direction) {
        return (arms >> (direction * 2)) & 3;
    }

    private static int arms(int mask, int weight) {
        int arms = 0;
        for (int d = 0; d < 4; d++) {
            if ((mask & (1 << d)) != 0) arms |= weight << (d * 2);
        }
        return arms;
    }

    /** Quadrant blocks as a 2×2 mask (bit0 top-left, bit1 top-right, bit2 bottom-left, bit3 bottom-right), or -1. */
    static int quadrantMask(int cp) {
        return switch (cp) {
            case '▘' -> 1;
            case '▝' -> 2;
            case '▀' -> 3;
            case '▖' -> 4;
            case '▌' -> 5;
            case '▞' -> 6;
            case '▛' -> 7;
            case '▗' -> 8;
            case '▚' -> 9;
            case '▐' -> 10;
            case '▜' -> 11;
            case '▄' -> 12;
            case '▙' -> 13;
            case '▟' -> 14;
            case '█' -> 15;
            default -> -1;
        };
    }

    /** Coverage of a shade glyph in quarters: 1 for ░, 2 for ▒, 3 for ▓. */
    static int shadeLevel(int cp) {
        return cp == '░' ? 1 : cp == '▒' ? 2 : 3;
    }

    /** Sextants as a 2×3 mask: bit {@code row * 2 + col}. The block skips the two half-block shapes. */
    static int sextantMask(int cp) {
        int index = cp - 0x1FB00 + 1;
        if (index >= 0b010101) index++;
        if (index >= 0b101010) index++;
        return index;
    }
}
