package dev.tilemap.core;

import java.util.Arrays;
import java.util.Objects;

/** The render result: {@code cols × rows} cells in row-major order. Contains no escape codes. */
public record Canvas(int cols, int rows, Cell[] cells) {
    public Canvas {
        Objects.requireNonNull(cells, "cells");
        if (cols <= 0 || rows <= 0 || cells.length != cols * rows) {
            throw new IllegalArgumentException("expected " + cols + "x" + rows + " cells, got " + cells.length);
        }
    }

    public Cell cell(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) throw new IndexOutOfBoundsException(col + "," + row);
        return cells[row * cols + col];
    }

    /** Glyphs only, one line per row, each terminated by {@code \n}. */
    public String toPlain() {
        StringBuilder sb = new StringBuilder(rows * (cols + 1));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) sb.appendCodePoint(cells[r * cols + c].codePoint());
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Glyphs plus SGR sequences quantized to {@code caps.color()}. Each row that sets any attribute ends with a
     * reset, so rows can be printed independently. No cursor movement and no diffing.
     */
    public String toAnsi(Capabilities caps) {
        StringBuilder sb = new StringBuilder(rows * (cols + 1) * 4);
        for (int r = 0; r < rows; r++) {
            String current = "";
            for (int c = 0; c < cols; c++) {
                Cell cell = cells[r * cols + c];
                String sgr = Ansi.sgr(cell, caps.color());
                if (!sgr.equals(current)) {
                    sb.append(sgr.isEmpty() ? Ansi.RESET : sgr);
                    current = sgr;
                }
                sb.appendCodePoint(cell.codePoint());
            }
            if (!current.isEmpty()) sb.append(Ansi.RESET);
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Canvas other && cols == other.cols && rows == other.rows && Arrays.equals(cells, other.cells);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * cols + rows) + Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "Canvas[" + cols + "x" + rows + "]";
    }
}
