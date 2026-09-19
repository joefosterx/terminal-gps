package dev.tilemap.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlyphTableTest {
    @Test
    void everyGlyphTheCoreEmitsIsDrawnAsShapes() {
        for (int cp = 0x2800; cp <= 0x28FF; cp++) assertEquals(GlyphTable.Kind.BRAILLE, GlyphTable.kind(cp));
        for (char c : " ╵╶└╷│┌├╴┘─┴┐┤┬┼╹╺┗╻┃┏┣╸┛━┻┓┫┳╋║═╚╔╠╝╩╗╣╦╬".substring(1).toCharArray()) {
            assertEquals(GlyphTable.Kind.BOX, GlyphTable.kind(c), "U+" + Integer.toHexString(c));
        }
        for (char c : "▘▝▀▖▌▞▛▗▚▐▜▄▙▟█".toCharArray()) assertEquals(GlyphTable.Kind.QUADRANT, GlyphTable.kind(c));
        for (char c : "░▒▓".toCharArray()) assertEquals(GlyphTable.Kind.SHADE, GlyphTable.kind(c));
        for (int cp = 0x1FB00; cp <= 0x1FB3B; cp++) assertEquals(GlyphTable.Kind.SEXTANT, GlyphTable.kind(cp));
        assertEquals(GlyphTable.Kind.TEXT, GlyphTable.kind('A'));
        assertEquals(GlyphTable.Kind.TEXT, GlyphTable.kind('●'));
    }

    @Test
    void brailleDotsMapToRowsAndColumns() {
        assertEquals(0, GlyphTable.brailleMask(0x2800));
        assertEquals(0b1, GlyphTable.brailleMask('⠁'), "dot 1 is top-left");
        assertEquals(0b10, GlyphTable.brailleMask('⠈'), "dot 4 is top-right");
        assertEquals(0b01000000, GlyphTable.brailleMask('⡀'), "dot 7 is bottom-left");
        assertEquals(0b10000000, GlyphTable.brailleMask('⢀'), "dot 8 is bottom-right");
        assertEquals(0xff, GlyphTable.brailleMask('⣿'));
    }

    @Test
    void boxArmsFollowTheJoint() {
        int cross = GlyphTable.boxArms('┼');
        for (int d = 0; d < 4; d++) assertEquals(GlyphTable.LIGHT, GlyphTable.arm(cross, d));
        int corner = GlyphTable.boxArms('┏');
        assertEquals(GlyphTable.NONE, GlyphTable.arm(corner, 0));
        assertEquals(GlyphTable.HEAVY, GlyphTable.arm(corner, 1));
        assertEquals(GlyphTable.HEAVY, GlyphTable.arm(corner, 2));
        assertEquals(GlyphTable.NONE, GlyphTable.arm(corner, 3));
        int vertical = GlyphTable.boxArms('║');
        assertEquals(GlyphTable.DOUBLE, GlyphTable.arm(vertical, 0));
        assertEquals(GlyphTable.NONE, GlyphTable.arm(vertical, 1));
        assertEquals(GlyphTable.DOUBLE, GlyphTable.arm(vertical, 2));
        assertEquals(-1, GlyphTable.boxArms('x'));
    }

    @Test
    void sextantsSkipTheHalfBlocks() {
        assertEquals(1, GlyphTable.sextantMask(0x1FB00));
        assertEquals(0b010100, GlyphTable.sextantMask(0x1FB13));
        assertEquals(0b010110, GlyphTable.sextantMask(0x1FB14), "0b010101 is ▌, not in this block");
        assertEquals(0b111110, GlyphTable.sextantMask(0x1FB3B), "the block also leaves out █");
        assertTrue(GlyphTable.quadrantMask('▌') == 5);
    }
}
