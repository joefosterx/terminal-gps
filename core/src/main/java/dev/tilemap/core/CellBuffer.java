package dev.tilemap.core;

import java.util.Arrays;

/**
 * Per-cell rasters for the strategies that bypass dots: road connectivity and point markers.
 *
 * <p>Connectivity bits are shared by all box-line layers, so a minor road ending on a major road turns the major
 * road's cell into a T-junction. Each cell also remembers the highest line layer and marker layer that touched it.
 */
final class CellBuffer {
    static final int N = 1, E = 2, S = 4, W = 8;

    private final int cols;
    private final int rows;
    private final byte[] conn;
    private final short[] lineLayer;
    private final short[] markerLayer;
    private final int[] marker;

    CellBuffer(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.conn = new byte[cols * rows];
        this.lineLayer = new short[cols * rows];
        this.markerLayer = new short[cols * rows];
        this.marker = new int[cols * rows];
        clear();
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    void clear() {
        Arrays.fill(conn, (byte) 0);
        Arrays.fill(lineLayer, DotBuffer.EMPTY);
        Arrays.fill(markerLayer, DotBuffer.EMPTY);
        Arrays.fill(marker, 0);
    }

    /** Joins two 4-adjacent cells; either may be off-grid, in which case only the other is marked. */
    void connect(int ax, int ay, int bx, int by, short layer) {
        int dir = bx > ax ? E : bx < ax ? W : by > ay ? S : N;
        mark(ax, ay, dir, layer);
        mark(bx, by, opposite(dir), layer);
    }

    void marker(int x, int y, int codePoint, short layer) {
        if (!inside(x, y)) return;
        int i = y * cols + x;
        if (layer > markerLayer[i]) {
            markerLayer[i] = layer;
            marker[i] = codePoint;
        }
    }

    int conn(int x, int y) {
        return conn[y * cols + x];
    }

    short lineLayer(int x, int y) {
        return lineLayer[y * cols + x];
    }

    short markerLayer(int x, int y) {
        return markerLayer[y * cols + x];
    }

    int marker(int x, int y) {
        return marker[y * cols + x];
    }

    private void mark(int x, int y, int bit, short layer) {
        if (!inside(x, y)) return;
        int i = y * cols + x;
        conn[i] |= (byte) bit;
        if (layer > lineLayer[i]) lineLayer[i] = layer;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < cols && y < rows;
    }

    private static int opposite(int dir) {
        return switch (dir) {
            case N -> S;
            case S -> N;
            case E -> W;
            default -> E;
        };
    }
}
