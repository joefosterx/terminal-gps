package dev.tilemap.android;

import dev.tilemap.core.Ansi;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Rgb;

/**
 * Cell colors as ARGB ints at a color depth. True color is the style's RGB; 256 and 16 go through the core's
 * quantizers and back out through the xterm palette, so "retro colors" on screen match what a terminal shows.
 */
final class Palette {
    private static final int[] XTERM16 = {
        0xff000000, 0xffcd0000, 0xff00cd00, 0xffcdcd00, 0xff0000ee, 0xffcd00cd, 0xff00cdcd, 0xffe5e5e5,
        0xff7f7f7f, 0xffff0000, 0xff00ff00, 0xffffff00, 0xff5c5cff, 0xffff00ff, 0xff00ffff, 0xffffffff,
    };
    private static final int[] CUBE = {0, 95, 135, 175, 215, 255};

    private Palette() {}

    static int argb(Rgb c, ColorDepth depth) {
        return switch (depth) {
            case TRUE, NONE -> 0xff000000 | c.r() << 16 | c.g() << 8 | c.b();
            case C256 -> xterm256(Ansi.to256(c));
            case C16 -> XTERM16[Ansi.to16(c) & 15];
        };
    }

    static int xterm256(int index) {
        if (index < 16) return XTERM16[index];
        if (index >= 232) {
            int v = 8 + 10 * (index - 232);
            return 0xff000000 | v << 16 | v << 8 | v;
        }
        int i = index - 16;
        return 0xff000000 | CUBE[i / 36] << 16 | CUBE[i / 6 % 6] << 8 | CUBE[i % 6];
    }
}
