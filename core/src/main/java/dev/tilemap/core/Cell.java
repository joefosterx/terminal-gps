package dev.tilemap.core;

import java.util.Objects;

/**
 * One terminal cell. {@code fg}/{@code bg} are null for "terminal default"; {@code layer} is the style layer
 * that owns the cell, or -1. A code point rather than a {@code char}, because sextants are outside the BMP.
 */
public record Cell(int codePoint, Rgb fg, Rgb bg, Attrs attrs, int layer) {
    public static final Cell EMPTY = new Cell(' ', null, null, Attrs.NONE, -1);

    public Cell {
        Objects.requireNonNull(attrs, "attrs");
        if (!Character.isValidCodePoint(codePoint)) throw new IllegalArgumentException("bad code point " + codePoint);
    }
}
