package dev.tilemap.core;

import dev.tilemap.core.Capabilities.ColorDepth;

/** SGR encoding and color quantization. */
final class Ansi {
    static final String RESET = "\u001b[0m";

    /** Channel levels of the xterm 6×6×6 cube (indexes 16–231). */
    private static final int[] CUBE = {0, 95, 135, 175, 215, 255};

    /** The xterm defaults for the 16 basic colors, in SGR order: 30–37 then 90–97. */
    private static final Rgb[] BASIC = {
        new Rgb(0, 0, 0), new Rgb(205, 0, 0), new Rgb(0, 205, 0), new Rgb(205, 205, 0),
        new Rgb(0, 0, 238), new Rgb(205, 0, 205), new Rgb(0, 205, 205), new Rgb(229, 229, 229),
        new Rgb(127, 127, 127), new Rgb(255, 0, 0), new Rgb(0, 255, 0), new Rgb(255, 255, 0),
        new Rgb(92, 92, 255), new Rgb(255, 0, 255), new Rgb(0, 255, 255), new Rgb(255, 255, 255),
    };

    private Ansi() {}

    /** The full SGR sequence that sets this cell's style from a reset state, or "" if it has none. */
    static String sgr(Cell cell, ColorDepth depth) {
        StringBuilder p = new StringBuilder();
        if (cell.attrs().bold()) p.append(";1");
        if (cell.attrs().dim()) p.append(";2");
        if (cell.fg() != null) appendColor(p, cell.fg(), depth, false);
        if (cell.bg() != null) appendColor(p, cell.bg(), depth, true);
        return p.isEmpty() ? "" : "\u001b[0" + p + "m";
    }

    private static void appendColor(StringBuilder p, Rgb c, ColorDepth depth, boolean bg) {
        switch (depth) {
            case NONE -> {}
            case TRUE -> p.append(bg ? ";48;2;" : ";38;2;").append(c.r()).append(';').append(c.g()).append(';').append(c.b());
            case C256 -> p.append(bg ? ";48;5;" : ";38;5;").append(to256(c));
            case C16 -> {
                int i = to16(c);
                int code = (i < 8 ? 30 + i : 90 + i - 8) + (bg ? 10 : 0);
                p.append(';').append(code);
            }
        }
    }

    /** Nearest color in the 6×6×6 cube. Per-channel nearest is the Euclidean nearest because the cube is separable. */
    static int to256(Rgb c) {
        return 16 + 36 * nearestLevel(c.r()) + 6 * nearestLevel(c.g()) + nearestLevel(c.b());
    }

    /** Nearest of the 16 basic colors by squared RGB distance; ties go to the lower index. */
    static int to16(Rgb c) {
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < BASIC.length; i++) {
            int dr = c.r() - BASIC[i].r(), dg = c.g() - BASIC[i].g(), db = c.b() - BASIC[i].b();
            int d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestLevel(int v) {
        int best = 0;
        for (int i = 1; i < CUBE.length; i++) {
            if (Math.abs(CUBE[i] - v) < Math.abs(CUBE[best] - v)) best = i;
        }
        return best;
    }
}
