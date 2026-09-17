package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import org.junit.jupiter.api.Test;

class CanvasTest {
    private static final String ESC = String.valueOf((char) 27);
    private static final Rgb ORANGE = new Rgb(217, 138, 28);
    private static final Rgb BLUE = new Rgb(170, 211, 223);

    private static Canvas sample() {
        Cell road = new Cell('⣿', ORANGE, BLUE, new Attrs(true, false), 1);
        return new Canvas(3, 2, new Cell[] {road, road, Cell.EMPTY, Cell.EMPTY, Cell.EMPTY, Cell.EMPTY});
    }

    @Test
    void plainIsGlyphsOnly() {
        assertEquals("⣿⣿ \n   \n", sample().toPlain());
    }

    @Test
    void ansiTrueColor() {
        assertEquals(ESC + "[0;1;38;2;217;138;28;48;2;170;211;223m⣿⣿" + ESC + "[0m \n   \n",
                sample().toAnsi(new Capabilities(Charset.BRAILLE, ColorDepth.TRUE)));
    }

    @Test
    void ansiWithoutColorKeepsAttributes() {
        assertEquals(ESC + "[0;1m⣿⣿" + ESC + "[0m \n   \n",
                sample().toAnsi(new Capabilities(Charset.BRAILLE, ColorDepth.NONE)));
    }

    @Test
    void quantizesTo256ColorCube() {
        assertEquals(16, Ansi.to256(new Rgb(0, 0, 0)));
        assertEquals(231, Ansi.to256(new Rgb(255, 255, 255)));
        assertEquals(16 + 36 * 4 + 6 * 2 + 0, Ansi.to256(ORANGE)); // 215, 135, 0
    }

    @Test
    void quantizesTo16Colors() {
        assertEquals(0, Ansi.to16(new Rgb(10, 10, 10)));
        assertEquals(9, Ansi.to16(new Rgb(250, 20, 20)));
        assertEquals(ESC + "[0;34m", Ansi.sgr(new Cell('x', new Rgb(0, 0, 230), null, Attrs.NONE, 0), ColorDepth.C16));
        assertEquals(ESC + "[0;104m", Ansi.sgr(new Cell('x', null, new Rgb(90, 90, 250), Attrs.NONE, 0), ColorDepth.C16));
    }

    @Test
    void cellsAreAddressedByColumnAndRow() {
        assertEquals(Cell.EMPTY, sample().cell(2, 0));
        assertEquals('⣿', sample().cell(1, 0).codePoint());
    }
}
