package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class RasterTest {
    private static final short L = 3;

    /** Buffer rows as strings, '#' for owned dots. */
    private static String dump(DotBuffer buf) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < buf.height(); y++) {
            for (int x = 0; x < buf.width(); x++) sb.append(buf.get(x, y) >= 0 ? '#' : '.');
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void fillsDotsWhoseCentersAreInside() {
        DotBuffer buf = new DotBuffer(4, 1); // 8 x 4 dots
        Raster.fillPolygon(buf, List.of(new double[] {1, 1, 6, 1, 6, 3, 1, 3}), L);
        assertEquals("""
                ........
                .#####..
                .#####..
                ........
                """, dump(buf));
    }

    @Test
    void evenOddLeavesHoles() {
        DotBuffer buf = new DotBuffer(4, 2); // 8 x 8 dots
        double[] outer = {0, 0, 8, 0, 8, 8, 0, 8};
        double[] hole = {2, 2, 6, 2, 6, 6, 2, 6};
        Raster.fillPolygon(buf, List.of(outer, hole), L);
        assertEquals("""
                ########
                ########
                ##....##
                ##....##
                ##....##
                ##....##
                ########
                ########
                """, dump(buf));
    }

    @Test
    void clipsPolygonsLargerThanTheBuffer() {
        DotBuffer buf = new DotBuffer(2, 1);
        Raster.fillPolygon(buf, List.of(new double[] {-100, -100, 100, -100, 100, 100, -100, 100}), L);
        assertEquals("####\n####\n####\n####\n", dump(buf));
    }

    @Test
    void drawsDiagonalLines() {
        DotBuffer buf = new DotBuffer(2, 1);
        Raster.strokeLine(buf, new double[] {0.5, 0.5, 3.5, 3.5}, false, 1, L);
        assertEquals("#...\n.#..\n..#.\n...#\n", dump(buf));
    }

    @Test
    void heavyLinesAreTwoDotsWide() {
        DotBuffer buf = new DotBuffer(3, 1);
        Raster.strokeLine(buf, new double[] {0, 1, 5, 1}, false, Weight.HEAVY.dots(), L);
        assertEquals("......\n######\n######\n......\n", dump(buf));
    }

    @Test
    void closedLinesJoinLastToFirst() {
        DotBuffer buf = new DotBuffer(2, 1);
        Raster.strokeLine(buf, new double[] {0, 0, 3, 0, 3, 3, 0, 3}, true, 1, L);
        assertEquals("####\n#..#\n#..#\n####\n", dump(buf));
    }

    @Test
    void higherLayersWinRegardlessOfOrder() {
        DotBuffer buf = new DotBuffer(1, 1);
        buf.set(0, 0, (short) 5);
        buf.set(0, 0, (short) 2);
        buf.set(1, 0, (short) 2);
        buf.set(1, 0, (short) 5);
        assertEquals(5, buf.get(0, 0));
        assertEquals(5, buf.get(1, 0));
    }

    /** Cell rows drawn with light box glyphs. */
    private static String dump(CellBuffer cells) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < cells.rows(); y++) {
            for (int x = 0; x < cells.cols(); x++) sb.appendCodePoint(Glyphs.box(cells.conn(x, y), Weight.LIGHT));
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void tracedLinesHaveEndCaps() {
        CellBuffer cells = new CellBuffer(3, 1);
        Raster.traceCells(cells, new double[] {1, 2, 5, 2}, false, L);
        assertEquals("╶─╴\n", dump(cells));
        assertEquals(L, cells.lineLayer(1, 0));
    }

    @Test
    void crossingLinesMakeAJunction() {
        CellBuffer cells = new CellBuffer(3, 3); // 6 x 12 dots
        Raster.traceCells(cells, new double[] {-20, 6, 20, 6}, false, L);
        Raster.traceCells(cells, new double[] {3, -40, 3, 40}, false, L);
        assertEquals(" │ \n─┼─\n │ \n", dump(cells));
    }

    @Test
    void tJunctionWhereARoadEnds() {
        CellBuffer cells = new CellBuffer(3, 2);
        Raster.traceCells(cells, new double[] {-20, 2, 20, 2}, false, L);
        Raster.traceCells(cells, new double[] {3, 2, 3, 6}, false, L);
        assertEquals("─┬─\n ╵ \n", dump(cells));
    }

    @Test
    void diagonalsStaircaseHorizontalFirst() {
        CellBuffer cells = new CellBuffer(3, 3);
        Raster.traceCells(cells, new double[] {1, 2, 5, 10}, false, L);
        assertEquals("╶┐ \n └┐\n  ╵\n", dump(cells));
    }

    @Test
    void closedRingsTraceAllSides() {
        CellBuffer cells = new CellBuffer(3, 3);
        Raster.traceCells(cells, new double[] {1, 2, 5, 2, 5, 10, 1, 10}, true, L);
        assertEquals("┌─┐\n│ │\n└─┘\n", dump(cells));
    }

    @Test
    void clipsSegmentsToBox() {
        assertArrayEquals(new double[] {0, 5, 10, 5}, Raster.clip(-10, 5, 20, 5, 0, 0, 10, 10), 1e-9);
        assertNull(Raster.clip(-10, -5, 20, -5, 0, 0, 10, 10));
    }

    @Test
    void farAwaySegmentsDoNotHang() {
        DotBuffer buf = new DotBuffer(2, 1);
        Raster.strokeLine(buf, new double[] {-1e9, 1, 1e9, 1}, false, 1, L);
        assertEquals("....\n####\n....\n....\n", dump(buf));
    }
}
