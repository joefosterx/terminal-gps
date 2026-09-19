package dev.tilemap.viewer;

/** A rectangle of map cells, {@code [col0, col1) × [row0, row1)}; may extend past the map. */
public record CellRect(int col0, int row0, int col1, int row1) {}
