package dev.tilemap.core;

import java.util.Objects;

/**
 * What to draw: a center, a fractional zoom, a size in cells, and the width:height ratio of one
 * terminal cell (0.5 for a typical monospace font).
 */
public record Viewport(LonLat center, double zoom, int cols, int rows, double cellAspect) {
    public static final double DEFAULT_CELL_ASPECT = 0.5;

    public Viewport {
        Objects.requireNonNull(center, "center");
        if (cols <= 0 || rows <= 0) throw new IllegalArgumentException("viewport must be at least 1x1");
        if (!(cellAspect > 0)) throw new IllegalArgumentException("cellAspect must be positive");
        if (!(zoom >= 0 && zoom <= Projection.MAX_ZOOM)) throw new IllegalArgumentException("zoom out of range: " + zoom);
    }

    public Viewport(LonLat center, double zoom, int cols, int rows) {
        this(center, zoom, cols, rows, DEFAULT_CELL_ASPECT);
    }

    /** Integer zoom of the tiles that back this viewport. */
    public int tileZoom() {
        return (int) Math.floor(zoom);
    }
}
