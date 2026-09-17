package dev.tilemap.core;

import static dev.tilemap.core.CellBuffer.E;
import static dev.tilemap.core.CellBuffer.N;
import static dev.tilemap.core.CellBuffer.S;
import static dev.tilemap.core.CellBuffer.W;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlyphsTest {
    private static String s(int codePoint) {
        return Character.toString(codePoint);
    }

    @Test
    void boxJointsByWeight() {
        assertEquals("┼", s(Glyphs.box(N | E | S | W, Weight.LIGHT)));
        assertEquals("┴", s(Glyphs.box(N | E | W, Weight.LIGHT)));
        assertEquals("╴", s(Glyphs.box(W, Weight.LIGHT)));
        assertEquals("┃", s(Glyphs.box(N | S, Weight.HEAVY)));
        assertEquals("┏", s(Glyphs.box(E | S, Weight.HEAVY)));
        assertEquals("┫", s(Glyphs.box(N | S | W, Weight.HEAVY)));
        assertEquals("╔", s(Glyphs.box(E | S, Weight.DOUBLE)));
        assertEquals("═", s(Glyphs.box(E, Weight.DOUBLE)));
    }

    @Test
    void asciiBox() {
        assertEquals(" ", s(Glyphs.asciiBox(0, Weight.LIGHT)));
        assertEquals("|", s(Glyphs.asciiBox(N | S, Weight.HEAVY)));
        assertEquals("-", s(Glyphs.asciiBox(E | W, Weight.LIGHT)));
        assertEquals("=", s(Glyphs.asciiBox(E, Weight.HEAVY)));
        assertEquals("+", s(Glyphs.asciiBox(N | E, Weight.LIGHT)));
    }

    @Test
    void brailleFromGridOrder() {
        assertEquals("⣿", s(Glyphs.braille(0xff)));
        assertEquals("⠁", s(Glyphs.braille(1)));        // row 0, col 0
        assertEquals("⡀", s(Glyphs.braille(1 << 6)));   // row 3, col 0
        assertEquals("⠒", s(Glyphs.braille(0b1100)));   // row 1, both columns
    }

    @Test
    void quadrantBlocks() {
        assertEquals(" ", s(Glyphs.block(0)));
        assertEquals("▘", s(Glyphs.block(1)));
        assertEquals("▀", s(Glyphs.block(0b0000_1111)));
        assertEquals("▌", s(Glyphs.block(0b0101_0101)));
        assertEquals("▗", s(Glyphs.block(0b1000_0000)));
        assertEquals("█", s(Glyphs.block(0xff)));
    }

    @Test
    void shadeThresholds() {
        assertEquals("░", s(Glyphs.shade(0b1)));
        assertEquals("▒", s(Glyphs.shade(0b1111)));
        assertEquals("▓", s(Glyphs.shade(0b0111_1111)));
        assertEquals("█", s(Glyphs.shade(0xff)));
    }

    @Test
    void asciiDots() {
        assertEquals("#", s(Glyphs.asciiDots(0xff)));
        assertEquals("'", s(Glyphs.asciiDots(0b0000_0011)));
        assertEquals(".", s(Glyphs.asciiDots(0b1100_0000)));
        assertEquals(":", s(Glyphs.asciiDots(0b0001_0001)));
    }

    @Test
    void asciiMarkers() {
        assertEquals("o", s(Glyphs.asciiMarker('●')));
        assertEquals("^", s(Glyphs.asciiMarker('▲')));
        assertEquals("H", s(Glyphs.asciiMarker('⌂')));
        assertEquals("@", s(Glyphs.asciiMarker('@')));
        assertEquals("*", s(Glyphs.asciiMarker('★')));
    }
}
