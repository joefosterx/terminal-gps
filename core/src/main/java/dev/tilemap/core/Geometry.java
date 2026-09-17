package dev.tilemap.core;

import java.util.List;

/**
 * Tile-local geometry. Coordinates are flat {@code [x0, y0, x1, y1, ...]} arrays so the raster loops
 * do not allocate; the arrays must not be mutated after construction.
 */
public sealed interface Geometry {
    /** One or more positions. */
    record Point(double[] coords) implements Geometry {}

    /** One or more polylines. */
    record Line(List<double[]> parts) implements Geometry {
        public Line {
            parts = List.copyOf(parts);
        }
    }

    /** Rings filled with the even-odd rule, so holes and multipolygons need no extra structure. */
    record Polygon(List<double[]> rings) implements Geometry {
        public Polygon {
            rings = List.copyOf(rings);
        }
    }
}
