package dev.tilemap.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The label pass: collects candidates while tiles are drawn, then places them on the finished cells.
 *
 * <p>Candidates are placed by priority, then distance from the viewport center. A placement is accepted when its
 * cells hold no other label, none of them belongs to a {@link StyleLayer#protect() protected} layer, and the cells
 * immediately left and right of the text hold no other label. Each distinct text is placed at most once, so a
 * street split across tiles gets one label. Label text uses the terminal's default colors.
 *
 * <ul>
 *   <li>Points try right of the anchor (leaving one cell), left, above, then below.
 *   <li>Lines try their longest horizontal run, then the point placements around the middle of their longest run
 *       (vertical streets end up beside themselves), then a nearby free spot, then a truncated horizontal run.
 *   <li>Areas try the nearest free spot around the center of their visible bounding box.
 * </ul>
 */
final class Labels {
    private static final Attrs BOLD = new Attrs(true, false);
    private static final int SEARCH_ROWS = 3;

    enum Kind { POINT, LINE, AREA }

    /** A straight run of cells: {@code len} cells from (col, row) going right, or down if not horizontal. */
    record Run(int col, int row, int len, boolean horizontal) {}

    record Candidate(int[] text, int priority, boolean bold, Kind kind, double ax, double ay, Run hrun, int seq) {
        String key() {
            return new String(text, 0, text.length);
        }
    }

    private final int cols;
    private final int rows;
    private final List<Candidate> candidates = new ArrayList<>();

    Labels(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
    }

    List<Candidate> candidates() {
        return candidates;
    }

    /** Adds a candidate for a feature whose geometry is already in dot space. */
    void add(LabelRule rule, Geometry dots, String rawText) {
        int[] text = clean(rawText, rule.maxWidth());
        if (text == null) return;
        switch (dots) {
            case Geometry.Point p -> {
                for (int i = 0; i + 1 < p.coords().length; i += 2) {
                    double cx = p.coords()[i] / 2, cy = p.coords()[i + 1] / 4;
                    if (cx >= 0 && cy >= 0 && cx < cols && cy < rows) {
                        add(new Candidate(text, rule.priority(), rule.bold(), Kind.POINT, Math.floor(cx), Math.floor(cy), null, candidates.size()));
                    }
                }
            }
            case Geometry.Line l -> {
                Run best = null, bestH = null;
                for (double[] part : l.parts()) {
                    List<int[]> path = path(part, false);
                    for (Run run : runs(path)) {
                        if (run.horizontal && (bestH == null || run.len > bestH.len)) bestH = run;
                        if (best == null || run.len > best.len || (run.len == best.len && run.horizontal && !best.horizontal)) best = run;
                    }
                }
                if (best == null) return;
                double ax = best.horizontal ? best.col + best.len / 2 : best.col;
                double ay = best.horizontal ? best.row : best.row + best.len / 2;
                add(new Candidate(text, rule.priority(), rule.bold(), Kind.LINE, ax, ay, bestH, candidates.size()));
            }
            case Geometry.Polygon p -> {
                double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                for (double[] ring : p.rings()) {
                    for (int i = 0; i + 1 < ring.length; i += 2) {
                        minX = Math.min(minX, ring[i] / 2);
                        maxX = Math.max(maxX, ring[i] / 2);
                        minY = Math.min(minY, ring[i + 1] / 4);
                        maxY = Math.max(maxY, ring[i + 1] / 4);
                    }
                }
                minX = Math.max(minX, 0);
                minY = Math.max(minY, 0);
                maxX = Math.min(maxX, cols);
                maxY = Math.min(maxY, rows);
                if (minX >= maxX || minY >= maxY) return;
                add(new Candidate(text, rule.priority(), rule.bold(), Kind.AREA,
                        Math.floor((minX + maxX) / 2), Math.floor((minY + maxY) / 2), null, candidates.size()));
            }
        }
    }

    private void add(Candidate c) {
        candidates.add(c);
    }

    /** Writes labels into {@code cells} (row-major, {@code cols × rows}). */
    void place(Cell[] cells, List<StyleLayer> layers) {
        boolean[] taken = new boolean[cells.length];
        double cx = cols / 2.0, cy = rows / 2.0;
        List<Candidate> order = new ArrayList<>(candidates);
        order.sort(Comparator.comparingInt((Candidate c) -> -c.priority)
                .thenComparingDouble(c -> sq(c.ax + 0.5 - cx) + sq(2 * (c.ay + 0.5 - cy)))
                .thenComparing(Candidate::key)
                .thenComparingInt(Candidate::seq));
        Set<String> placed = new HashSet<>();
        Placer placer = new Placer(cells, layers, taken);
        for (Candidate c : order) {
            if (placed.contains(c.key())) continue;
            if (placer.place(c)) placed.add(c.key());
        }
    }

    private final class Placer {
        private final Cell[] cells;
        private final List<StyleLayer> layers;
        private final boolean[] taken;

        Placer(Cell[] cells, List<StyleLayer> layers, boolean[] taken) {
            this.cells = cells;
            this.layers = layers;
            this.taken = taken;
        }

        boolean place(Candidate c) {
            int ax = (int) c.ax, ay = (int) c.ay;
            return switch (c.kind) {
                case POINT -> beside(c, ax, ay);
                case LINE -> onRun(c.hrun, c.text, c.bold) || beside(c, ax, ay) || nearby(c, ax, ay) || onRunTruncated(c);
                case AREA -> nearby(c, ax, ay);
            };
        }

        private boolean onRun(Run run, int[] text, boolean bold) {
            if (run == null || run.len < text.length) return false;
            return tryAt(run.row, run.col + (run.len - text.length) / 2, text, bold);
        }

        private boolean onRunTruncated(Candidate c) {
            if (c.hrun == null || c.hrun.len < LabelRule.MIN_CHARS) return false;
            return tryAt(c.hrun.row, c.hrun.col, truncate(c.text, c.hrun.len), c.bold);
        }

        /**
         * Right, left, above, below with the full text; failing that, right or left truncated at the canvas edge
         * if at least 4 characters fit.
         */
        private boolean beside(Candidate c, int ax, int ay) {
            int[] t = c.text;
            int start = Math.max(0, Math.min(cols - t.length, ax - t.length / 2));
            if (tryAt(ay, ax + 2, t, c.bold) || tryAt(ay, ax - 1 - t.length, t, c.bold)
                    || tryAt(ay - 1, start, t, c.bold) || tryAt(ay + 1, start, t, c.bold)) {
                return true;
            }
            int rightRoom = cols - (ax + 2);
            if (rightRoom >= LabelRule.MIN_CHARS && rightRoom < t.length && tryAt(ay, ax + 2, truncate(t, rightRoom), c.bold)) return true;
            int leftRoom = ax - 1;
            if (leftRoom >= LabelRule.MIN_CHARS && leftRoom < t.length) {
                int[] lt = truncate(t, leftRoom);
                return tryAt(ay, ax - 1 - lt.length, lt, c.bold);
            }
            return false;
        }

        /** Rows 0, -1, +1, ... ±3 from the anchor, and on each row start positions increasingly far from centered. */
        private boolean nearby(Candidate c, int ax, int ay) {
            int[] t = c.text;
            int centered = ax - t.length / 2;
            for (int dr = 0; dr <= SEARCH_ROWS; dr++) {
                for (int sign : dr == 0 ? new int[] {1} : new int[] {-1, 1}) {
                    int row = ay + sign * dr;
                    for (int dc = 0; dc <= t.length + 1; dc++) {
                        if (tryAt(row, centered + dc, t, c.bold)) return true;
                        if (dc > 0 && tryAt(row, centered - dc, t, c.bold)) return true;
                    }
                }
            }
            return false;
        }

        private boolean tryAt(int row, int start, int[] text, boolean bold) {
            if (row < 0 || row >= rows || start < 0 || start + text.length > cols) return false;
            int base = row * cols;
            for (int i = 0; i < text.length; i++) {
                int idx = base + start + i;
                if (taken[idx]) return false;
                int layer = cells[idx].layer();
                if (layer >= 0 && layers.get(layer).protect()) return false;
            }
            if (start > 0 && taken[base + start - 1]) return false;
            if (start + text.length < cols && taken[base + start + text.length]) return false;
            Attrs attrs = bold ? BOLD : Attrs.NONE;
            for (int i = 0; i < text.length; i++) {
                cells[base + start + i] = new Cell(text[i], null, null, attrs, Cell.LABEL_LAYER);
                taken[base + start + i] = true;
            }
            return true;
        }
    }

    /** The cells a dot-space polyline passes through, in order, including out-of-grid cells. */
    private List<int[]> path(double[] dots, boolean closed) {
        List<int[]> path = new ArrayList<>();
        Raster.walkPolyline(dots, closed, cols, rows, (fx, fy, tx, ty) -> {
            // No List.getLast(): the core must stay loadable on Android, which lacks SequencedCollection.
            int[] last = path.isEmpty() ? null : path.get(path.size() - 1);
            if (last == null || last[0] != fx || last[1] != fy) path.add(new int[] {fx, fy});
            path.add(new int[] {tx, ty});
        });
        return path;
    }

    /** Maximal straight runs of in-grid cells along a path. */
    private List<Run> runs(List<int[]> path) {
        List<Run> out = new ArrayList<>();
        int i = 0;
        while (i < path.size()) {
            int[] start = path.get(i);
            if (!inside(start)) {
                i++;
                continue;
            }
            // Extend horizontally and vertically from this cell; keep whichever is longer.
            int h = extent(path, i, true), v = extent(path, i, false);
            if (h >= v) {
                int minCol = start[0], maxCol = start[0];
                for (int k = i; k < i + h; k++) {
                    minCol = Math.min(minCol, path.get(k)[0]);
                    maxCol = Math.max(maxCol, path.get(k)[0]);
                }
                out.add(new Run(minCol, start[1], maxCol - minCol + 1, true));
                i += Math.max(1, h - 1);
            } else {
                int minRow = start[1], maxRow = start[1];
                for (int k = i; k < i + v; k++) {
                    minRow = Math.min(minRow, path.get(k)[1]);
                    maxRow = Math.max(maxRow, path.get(k)[1]);
                }
                out.add(new Run(start[0], minRow, maxRow - minRow + 1, false));
                i += Math.max(1, v - 1);
            }
        }
        return out;
    }

    /** How many consecutive in-grid path cells from {@code i} stay in one row (or column). */
    private int extent(List<int[]> path, int i, boolean horizontal) {
        int n = 1;
        int[] first = path.get(i);
        while (i + n < path.size()) {
            int[] next = path.get(i + n), prev = path.get(i + n - 1);
            if (!inside(next)) break;
            boolean straight = horizontal
                    ? next[1] == first[1] && Math.abs(next[0] - prev[0]) == 1
                    : next[0] == first[0] && Math.abs(next[1] - prev[1]) == 1;
            if (!straight) break;
            n++;
        }
        return n;
    }

    private boolean inside(int[] cell) {
        return cell[0] >= 0 && cell[1] >= 0 && cell[0] < cols && cell[1] < rows;
    }

    /**
     * NFC-normalized, whitespace-collapsed code points, truncated to {@code maxWidth}. Returns null for empty text
     * and for text with double-width or zero-width characters, which cannot be aligned to cells yet.
     */
    static int[] clean(String raw, int maxWidth) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
        if (s.isEmpty()) return null;
        int[] cps = s.codePoints().toArray();
        for (int cp : cps) {
            if (Character.isISOControl(cp) || isWide(cp) || isZeroWidth(cp)) return null;
        }
        return truncate(cps, maxWidth);
    }

    static int[] truncate(int[] text, int width) {
        if (text.length <= width) return text;
        int[] out = java.util.Arrays.copyOf(text, width);
        out[width - 1] = '…';
        return out;
    }

    private static boolean isZeroWidth(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT;
    }

    /** East Asian wide and fullwidth ranges, plus emoji. */
    static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F) || (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F)
                || (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0xFE30 && cp <= 0xFE4F)
                || (cp >= 0xFF00 && cp <= 0xFF60) || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF) || (cp >= 0x20000 && cp <= 0x3FFFD);
    }

    private static double sq(double v) {
        return v * v;
    }
}
