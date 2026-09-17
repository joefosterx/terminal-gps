package dev.tilemap.core;

/**
 * Web Mercator in normalized world units: x and y both run 0–1, origin at the north-west corner.
 * At zoom {@code z} the world is {@code TILE_DOTS * 2^z} dots wide.
 */
public final class Projection {
    public static final int MAX_ZOOM = 24;
    /** Dots per tile edge; one dot corresponds to one pixel of a classic 256 px raster tile. */
    public static final int TILE_DOTS = 256;
    public static final double MAX_LAT = 85.0511287798066;

    private Projection() {}

    public static double mercX(double lon) {
        return (lon + 180.0) / 360.0;
    }

    public static double mercY(double lat) {
        double clamped = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
        double s = Math.sin(Math.toRadians(clamped));
        return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
    }

    public static double lon(double mercX) {
        return mercX * 360.0 - 180.0;
    }

    public static double lat(double mercY) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * mercY))));
    }

    public static double worldDots(double zoom) {
        return TILE_DOTS * Math.pow(2, zoom);
    }
}
