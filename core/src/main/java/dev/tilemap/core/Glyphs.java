package dev.tilemap.core;

/**
 * Glyph tables. Dot masks here use grid order, bit {@code row * 2 + col} of a 2×4 cell; box masks use
 * {@link CellBuffer#N}/{@code E}/{@code S}/{@code W}.
 */
final class Glyphs {
    /** Box-drawing joints indexed by N|E|S|W mask. Dead ends use half lines; double lines have none. */
    private static final String LIGHT = " ╵╶└╷│┌├╴┘─┴┐┤┬┼";
    private static final String HEAVY = " ╹╺┗╻┃┏┣╸┛━┻┓┫┳╋";
    private static final String DOUBLE = " ║═╚║║╔╠═╝═╩╗╣╦╬";

    /** Quadrant blocks indexed by UL=1, UR=2, LL=4, LR=8. */
    private static final String QUADRANTS = " ▘▝▀▖▌▞▛▗▚▐▜▄▙▟█";

    private Glyphs() {}

    static int box(int mask, Weight weight) {
        String table = switch (weight) {
            case LIGHT -> LIGHT;
            case HEAVY -> HEAVY;
            case DOUBLE -> DOUBLE;
        };
        return table.charAt(mask & 0xf);
    }

    /** {@code -} or {@code =} (heavy) for east-west runs, {@code |} for north-south, {@code +} for anything that turns. */
    static int asciiBox(int mask, Weight weight) {
        if (mask == 0) return ' ';
        boolean ns = (mask & (CellBuffer.N | CellBuffer.S)) != 0;
        boolean ew = (mask & (CellBuffer.E | CellBuffer.W)) != 0;
        if (ns && ew) return '+';
        if (ns) return '|';
        return weight == Weight.LIGHT ? '-' : '=';
    }

    static int braille(int grid) {
        int mask = 0;
        for (int i = 0; i < 8; i++) {
            if ((grid & 1 << i) != 0) mask |= Braille.bit(i % 2, i / 2);
        }
        return Braille.glyph(mask);
    }

    /** A quadrant is lit when either of its two dots is, so one-dot lines stay visible. */
    static int block(int grid) {
        int q = 0;
        if ((grid & 0b0000_0101) != 0) q |= 1;
        if ((grid & 0b0000_1010) != 0) q |= 2;
        if ((grid & 0b0101_0000) != 0) q |= 4;
        if ((grid & 0b1010_0000) != 0) q |= 8;
        return QUADRANTS.charAt(q);
    }

    /** Coverage out of 8 dots, thresholded to four densities. */
    static int shade(int grid) {
        int n = Integer.bitCount(grid & 0xff);
        if (n == 0) return ' ';
        if (n <= 2) return '░';
        if (n <= 5) return '▒';
        if (n <= 7) return '▓';
        return '█';
    }

    /** Full cell {@code #}; otherwise {@code '} for dots only in the top half, {@code .} bottom, {@code :} both. */
    static int asciiDots(int grid) {
        grid &= 0xff;
        if (grid == 0) return ' ';
        if (grid == 0xff) return '#';
        boolean top = (grid & 0x0f) != 0;
        boolean bottom = (grid & 0xf0) != 0;
        return top && bottom ? ':' : top ? '\'' : '.';
    }

    static int asciiMarker(int codePoint) {
        return switch (codePoint) {
            case '●', '•', '○' -> 'o';
            case '▲', '△' -> '^';
            case '⌂' -> 'H';
            case '■', '□' -> '#';
            default -> codePoint > 0x20 && codePoint < 0x7f ? codePoint : '*';
        };
    }
}
