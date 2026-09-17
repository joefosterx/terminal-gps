package dev.tilemap.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tilemap.core.Attrs;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import dev.tilemap.core.Rgb;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ScreenDiffTest {
    private static final String ESC = String.valueOf((char) 27);
    private static final Cell RED_X = new Cell('x', new Rgb(255, 0, 0), null, Attrs.NONE, 0);

    private static Cell[] blank(int n) {
        Cell[] cells = new Cell[n];
        Arrays.fill(cells, Cell.EMPTY);
        return cells;
    }

    private static Cell plain(char ch) {
        return new Cell(ch, null, null, Attrs.NONE, -1);
    }

    @Test
    void firstFrameClearsAndSkipsBlanks() {
        Cell[] frame = blank(6);
        frame[1] = plain('a');
        frame[2] = plain('b');
        assertEquals(ESC + "[0m" + ESC + "[2J" + ESC + "[1;2Hab", new ScreenDiff().render(frame, 3, 2, ColorDepth.TRUE));
    }

    @Test
    void laterFramesWriteOnlyChangedCells() {
        ScreenDiff diff = new ScreenDiff();
        Cell[] first = blank(9);
        diff.render(first, 3, 3, ColorDepth.TRUE);

        Cell[] second = first.clone();
        second[4] = RED_X;
        second[5] = RED_X;
        assertEquals(ESC + "[2;2H" + ESC + "[0;38;2;255;0;0mxx" + ESC + "[0m", diff.render(second, 3, 3, ColorDepth.TRUE));
        assertEquals("", diff.render(second.clone(), 3, 3, ColorDepth.TRUE));
    }

    @Test
    void bottomRightCellIsNeverWritten() {
        ScreenDiff diff = new ScreenDiff();
        diff.render(blank(4), 2, 2, ColorDepth.NONE);
        Cell[] next = blank(4);
        next[3] = plain('z');
        assertEquals("", diff.render(next, 2, 2, ColorDepth.NONE));
    }

    @Test
    void resizeColorChangeAndInvalidateRepaint() {
        ScreenDiff diff = new ScreenDiff();
        Cell[] frame = blank(4);
        frame[0] = plain('a');
        diff.render(frame, 2, 2, ColorDepth.TRUE);
        String repaint = ESC + "[0m" + ESC + "[2J" + ESC + "[1;1Ha";
        assertEquals(repaint, diff.render(frame, 2, 2, ColorDepth.C256));
        diff.invalidate();
        assertEquals(repaint, diff.render(frame, 2, 2, ColorDepth.C256));
        Cell[] wide = blank(6);
        wide[0] = plain('a');
        assertEquals(repaint, diff.render(wide, 3, 2, ColorDepth.C256));
    }
}
