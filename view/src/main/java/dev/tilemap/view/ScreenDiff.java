package dev.tilemap.view;

import dev.tilemap.core.Ansi;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import java.util.Objects;

/**
 * Encodes a frame as the escape sequences that turn the previous frame into it: cursor moves and SGR changes only
 * for cells that differ. The first frame, and any frame after {@link #invalidate()}, a resize or a color depth
 * change, clears the screen and draws every non-blank cell. The bottom-right cell is never written, so terminals
 * that wrap eagerly do not scroll.
 */
final class ScreenDiff {
    private Cell[] previous;
    private int cols;
    private int rows;
    private ColorDepth depth;

    void invalidate() {
        previous = null;
    }

    String render(Cell[] frame, int cols, int rows, ColorDepth depth) {
        if (frame.length != cols * rows) throw new IllegalArgumentException("frame is not " + cols + "x" + rows);
        boolean full = previous == null || cols != this.cols || rows != this.rows || depth != this.depth;
        StringBuilder sb = new StringBuilder(full ? cols * rows * 4 : 256);
        if (full) sb.append(Ansi.RESET).append("\u001b[2J");

        String current = full ? Ansi.RESET : null;
        int cursorRow = -1, cursorCol = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == rows - 1 && c == cols - 1) continue;
                int i = r * cols + c;
                Cell cell = frame[i];
                if (full ? cell.equals(Cell.EMPTY) : Objects.equals(cell, previous[i])) continue;
                if (r != cursorRow || c != cursorCol) sb.append("\u001b[").append(r + 1).append(';').append(c + 1).append('H');
                String sgr = Ansi.sgr(cell, depth);
                String wanted = sgr.isEmpty() ? Ansi.RESET : sgr;
                if (!wanted.equals(current)) {
                    sb.append(wanted);
                    current = wanted;
                }
                sb.appendCodePoint(cell.codePoint());
                cursorRow = r;
                cursorCol = c + 1;
            }
        }
        if (current != null && !current.equals(Ansi.RESET)) sb.append(Ansi.RESET);

        previous = frame.clone();
        this.cols = cols;
        this.rows = rows;
        this.depth = depth;
        return sb.toString();
    }
}
