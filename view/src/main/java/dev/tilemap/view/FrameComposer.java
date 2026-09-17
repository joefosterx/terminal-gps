package dev.tilemap.view;

import dev.tilemap.core.Attrs;
import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Cell;
import dev.tilemap.core.Rgb;
import java.util.List;

/** Assembles a full-screen frame: the map, placeholders for missing tiles, the status bar, and any overlay. */
final class FrameComposer {
    static final Rgb BAR_FG = new Rgb(0x10, 0x10, 0x10);
    static final Rgb BAR_BG = new Rgb(0xd0, 0xd0, 0xd0);
    static final Rgb OVERLAY_FG = new Rgb(0xe8, 0xe8, 0xe8);
    static final Rgb OVERLAY_BG = new Rgb(0x28, 0x28, 0x30);
    private static final Attrs DIM = new Attrs(false, true);

    /** A rectangle of map cells, {@code [col0, col1) × [row0, row1)}. */
    record Rect(int col0, int row0, int col1, int row1) {}

    enum Placement { CENTER, LEFT, RIGHT }

    /** A boxed list of lines drawn over the map. */
    record Overlay(List<String> lines, Placement placement) {
        static final Overlay NONE = new Overlay(List.of(), Placement.CENTER);
    }

    private FrameComposer() {}

    /**
     * @param map the rendered map, {@code cols × (rows - 1)}
     * @param missing areas whose tiles have not loaded; their empty cells get a placeholder shade
     * @param overlay a box of lines, or {@link Overlay#NONE}
     * @param cursor the inspect cursor as {@code {col, row}}, or null
     */
    static Cell[] compose(Canvas map, int cols, int rows, List<Rect> missing, String statusLeft, String statusRight,
                          Overlay overlay, int[] cursor, Charset charset) {
        Cell[] frame = new Cell[cols * rows];
        boolean unicode = charset.compareTo(Charset.BOX) >= 0;
        int mapRows = Math.min(rows - 1, map.rows());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                frame[r * cols + c] = r < mapRows && c < map.cols() ? map.cell(c, r) : Cell.EMPTY;
            }
        }

        Cell placeholder = new Cell(unicode ? '░' : '.', null, null, DIM, -1);
        for (Rect rect : missing) {
            for (int r = Math.max(0, rect.row0()); r < Math.min(mapRows, rect.row1()); r++) {
                for (int c = Math.max(0, rect.col0()); c < Math.min(cols, rect.col1()); c++) {
                    if (frame[r * cols + c].equals(Cell.EMPTY)) frame[r * cols + c] = placeholder;
                }
            }
        }

        if (cursor != null && cursor[0] >= 0 && cursor[0] < cols && cursor[1] >= 0 && cursor[1] < mapRows) {
            Cell under = frame[cursor[1] * cols + cursor[0]];
            int glyph = under.codePoint() == ' ' || under.codePoint() == placeholder.codePoint() ? '+' : under.codePoint();
            frame[cursor[1] * cols + cursor[0]] = new Cell(glyph, null, null, new Attrs(true, false, true), under.layer());
        }
        if (!overlay.lines().isEmpty()) box(frame, cols, mapRows, overlay.lines(), overlay.placement(), unicode);
        statusBar(frame, cols, rows - 1, statusLeft, statusRight);
        return frame;
    }

    private static void statusBar(Cell[] frame, int cols, int row, String left, String right) {
        int[] l = left.codePoints().toArray();
        int[] rt = right.codePoints().toArray();
        int rightStart = Math.max(0, cols - rt.length);
        int leftRoom = rt.length + 1 <= cols ? rightStart - 1 : cols;
        Attrs attrs = Attrs.NONE;
        for (int c = 0; c < cols; c++) {
            int cp = ' ';
            if (c < l.length && c < leftRoom) {
                cp = (c == leftRoom - 1 && l.length > leftRoom) ? '…' : l[c];
            } else if (c >= rightStart && rt.length + 1 <= cols) {
                cp = rt[c - rightStart];
            }
            frame[row * cols + c] = new Cell(cp, BAR_FG, BAR_BG, attrs, -1);
        }
    }

    private static void box(Cell[] frame, int cols, int rows, List<String> lines, Placement placement, boolean unicode) {
        int inner = lines.stream().mapToInt(s -> s.codePointCount(0, s.length())).max().orElse(0) + 2;
        int width = Math.min(cols, inner + 2), height = Math.min(rows, lines.size() + 2);
        int left = switch (placement) {
            case CENTER -> (cols - width) / 2;
            case LEFT -> 0;
            case RIGHT -> cols - width;
        };
        int top = placement == Placement.CENTER ? (rows - height) / 2 : 0;
        char h = unicode ? '─' : '-', v = unicode ? '│' : '|';
        char tl = unicode ? '┌' : '+', tr = unicode ? '┐' : '+', bl = unicode ? '└' : '+', br = unicode ? '┘' : '+';
        for (int r = 0; r < height; r++) {
            int[] text = r > 0 && r - 1 < lines.size() ? lines.get(r - 1).codePoints().toArray() : new int[0];
            for (int c = 0; c < width; c++) {
                int cp;
                boolean topEdge = r == 0, bottomEdge = r == height - 1, leftEdge = c == 0, rightEdge = c == width - 1;
                if (topEdge && leftEdge) cp = tl;
                else if (topEdge && rightEdge) cp = tr;
                else if (bottomEdge && leftEdge) cp = bl;
                else if (bottomEdge && rightEdge) cp = br;
                else if (topEdge || bottomEdge) cp = h;
                else if (leftEdge || rightEdge) cp = v;
                else cp = c - 2 >= 0 && c - 2 < text.length ? text[c - 2] : ' ';
                frame[(top + r) * cols + left + c] = new Cell(cp, OVERLAY_FG, OVERLAY_BG, Attrs.NONE, -1);
            }
        }
    }
}
