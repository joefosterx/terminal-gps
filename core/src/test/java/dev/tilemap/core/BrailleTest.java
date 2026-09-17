package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BrailleTest {
    @Test
    void mapsDotPositionsToUnicodeBits() {
        assertEquals("⠁", Character.toString(Braille.glyph(Braille.bit(0, 0))));
        assertEquals("⠈", Character.toString(Braille.glyph(Braille.bit(1, 0))));
        assertEquals("⠄", Character.toString(Braille.glyph(Braille.bit(0, 2))));
        assertEquals("⡀", Character.toString(Braille.glyph(Braille.bit(0, 3))));
        assertEquals("⢀", Character.toString(Braille.glyph(Braille.bit(1, 3))));
    }

    @Test
    void allBitsIsAFullCell() {
        int mask = 0;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 2; col++) mask |= Braille.bit(col, row);
        assertEquals(0xff, mask);
        assertEquals("⣿", Character.toString(Braille.glyph(mask)));
    }

    @Test
    void rejectsOutOfRangeMasks() {
        assertThrows(IllegalArgumentException.class, () -> Braille.glyph(0x100));
    }
}
