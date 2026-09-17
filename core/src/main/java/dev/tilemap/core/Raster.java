package dev.tilemap.core;

import java.util.Arrays;
import java.util.List;

/** Scan conversion into a {@link DotBuffer}. All coordinates here are in dot space. */
final class Raster {
    private Raster() {}

    /**
     * Even-odd fill of a set of closed rings. A dot is inside when its center is, so shared edges between
     * adjacent polygons neither overlap nor leave gaps.
     */
    static void fillPolygon(DotBuffer buf, List<double[]> rings, short layer) {
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int edges = 0;
        for (double[] ring : rings) {
            for (int i = 1; i < ring.length; i += 2) {
                minY = Math.min(minY, ring[i]);
                maxY = Math.max(maxY, ring[i]);
            }
            edges += ring.length / 2;
        }
        int y0 = Math.max(0, (int) Math.floor(minY));
        int y1 = Math.min(buf.height() - 1, (int) Math.ceil(maxY));
        double[] xs = new double[Math.max(2, edges)];

        for (int y = y0; y <= y1; y++) {
            double sy = y + 0.5;
            int count = 0;
            for (double[] ring : rings) {
                int points = ring.length / 2;
                for (int p = 0; p < points; p++) {
                    int q = (p + 1) % points;
                    double ax = ring[2 * p], ay = ring[2 * p + 1];
                    double bx = ring[2 * q], by = ring[2 * q + 1];
                    if ((ay <= sy) != (by <= sy)) {
                        xs[count++] = ax + (sy - ay) * (bx - ax) / (by - ay);
                    }
                }
            }
            Arrays.sort(xs, 0, count);
            for (int k = 0; k + 1 < count; k += 2) {
                // Dots whose center x + 0.5 lies in [xs[k], xs[k+1]).
                int from = Math.max(0, (int) Math.ceil(xs[k] - 0.5));
                int to = Math.min(buf.width() - 1, (int) Math.ceil(xs[k + 1] - 0.5) - 1);
                for (int x = from; x <= to; x++) buf.set(x, y, layer);
            }
        }
    }

    /** Draws a polyline; {@code closed} adds the segment from the last point back to the first. */
    static void strokeLine(DotBuffer buf, double[] pts, boolean closed, int width, short layer) {
        int points = pts.length / 2;
        int segments = closed ? points : points - 1;
        if (points == 1) {
            stamp(buf, (int) Math.floor(pts[0]), (int) Math.floor(pts[1]), width, layer);
            return;
        }
        for (int s = 0; s < segments; s++) {
            int e = (s + 1) % points;
            segment(buf, pts[2 * s], pts[2 * s + 1], pts[2 * e], pts[2 * e + 1], width, layer);
        }
    }

    static void point(DotBuffer buf, double x, double y, int width, short layer) {
        stamp(buf, (int) Math.floor(x), (int) Math.floor(y), width, layer);
    }

    /** Bresenham between two dot-space points, after clipping to the buffer plus a brush-sized margin. */
    static void segment(DotBuffer buf, double ax, double ay, double bx, double by, int width, short layer) {
        double margin = width + 1;
        double[] c = clip(ax, ay, bx, by, -margin, -margin, buf.width() + margin, buf.height() + margin);
        if (c == null) return;
        int x0 = (int) Math.floor(c[0]), y0 = (int) Math.floor(c[1]);
        int x1 = (int) Math.floor(c[2]), y1 = (int) Math.floor(c[3]);
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            stamp(buf, x0, y0, width, layer);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /**
     * Traces a dot-space polyline through cells, connecting each pair of consecutive cells it crosses. Segments
     * are clipped one cell beyond the grid so roads leaving the screen still point off-screen.
     */
    static void traceCells(CellBuffer cells, double[] pts, boolean closed, short layer) {
        int points = pts.length / 2;
        int segments = closed ? points : points - 1;
        for (int s = 0; s < segments; s++) {
            int e = (s + 1) % points;
            double[] c = clip(pts[2 * s] / 2, pts[2 * s + 1] / 4, pts[2 * e] / 2, pts[2 * e + 1] / 4,
                    -1, -1, cells.cols() + 1, cells.rows() + 1);
            if (c != null) walkCells(cells, c[0], c[1], c[2], c[3], layer);
        }
    }

    /**
     * Amanatides–Woo grid traversal: visits every cell the segment passes through, one 4-neighbor step at a time.
     * A segment passing exactly through a cell corner steps horizontally first.
     */
    private static void walkCells(CellBuffer cells, double x0, double y0, double x1, double y1, short layer) {
        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0);
        int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1);
        double dx = x1 - x0, dy = y1 - y0;
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1;
        double tDeltaX = dx != 0 ? Math.abs(1 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = dy != 0 ? Math.abs(1 / dy) : Double.POSITIVE_INFINITY;
        double tMaxX = dx > 0 ? (cx + 1 - x0) / dx : dx < 0 ? (x0 - cx) / -dx : Double.POSITIVE_INFINITY;
        double tMaxY = dy > 0 ? (cy + 1 - y0) / dy : dy < 0 ? (y0 - cy) / -dy : Double.POSITIVE_INFINITY;
        int steps = Math.abs(ex - cx) + Math.abs(ey - cy);
        for (int i = 0; i < steps; i++) {
            if (tMaxX <= tMaxY) {
                cells.connect(cx, cy, cx + stepX, cy, layer);
                cx += stepX;
                tMaxX += tDeltaX;
            } else {
                cells.connect(cx, cy, cx, cy + stepY, layer);
                cy += stepY;
                tMaxY += tDeltaY;
            }
        }
    }

    /** A square brush: width 1 is a single dot, width 2 covers the dot and its right/lower neighbors. */
    private static void stamp(DotBuffer buf, int x, int y, int width, short layer) {
        int lo = -(width - 1) / 2;
        int hi = width / 2;
        for (int oy = lo; oy <= hi; oy++) {
            for (int ox = lo; ox <= hi; ox++) buf.set(x + ox, y + oy, layer);
        }
    }

    /** Liang–Barsky clip; returns {@code [ax, ay, bx, by]} or null when the segment misses the box. */
    static double[] clip(double ax, double ay, double bx, double by, double minX, double minY, double maxX, double maxY) {
        double dx = bx - ax, dy = by - ay;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {ax - minX, maxX - ax, ay - minY, maxY - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return null;
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) {
                    if (t > t1) return null;
                    t0 = Math.max(t0, t);
                } else {
                    if (t < t0) return null;
                    t1 = Math.min(t1, t);
                }
            }
        }
        return new double[] {ax + t0 * dx, ay + t0 * dy, ax + t1 * dx, ay + t1 * dy};
    }
}
