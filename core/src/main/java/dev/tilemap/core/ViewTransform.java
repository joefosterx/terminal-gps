package dev.tilemap.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps normalized Mercator coordinates to the viewport's dot grid ({@code cols*2 × rows*4}).
 *
 * <p>A dot is {@code 1/2} cell wide and {@code 1/4} cell tall, so with a cell aspect of 0.5 dots are square.
 * For any other aspect the vertical scale is stretched by {@code 2 * cellAspect}, which keeps north-south
 * distances true on screen.
 */
record ViewTransform(double centerX, double centerY, double scaleX, double scaleY, int width, int height) {

    static ViewTransform of(Viewport vp) {
        double world = Projection.worldDots(vp.zoom());
        return new ViewTransform(
                Projection.mercX(vp.center().lon()),
                Projection.mercY(vp.center().lat()),
                world,
                world * 2 * vp.cellAspect(),
                vp.cols() * 2,
                vp.rows() * 4);
    }

    double dotX(double mercX) {
        return (mercX - centerX) * scaleX + width / 2.0;
    }

    double dotY(double mercY) {
        return (mercY - centerY) * scaleY + height / 2.0;
    }

    double mercX(double dotX) {
        return (dotX - width / 2.0) / scaleX + centerX;
    }

    double mercY(double dotY) {
        return (dotY - height / 2.0) / scaleY + centerY;
    }

    /** Tiles at {@code z} intersecting the viewport, row-major from the north-west. No antimeridian wrap. */
    List<TileId> tiles(int z) {
        int n = 1 << z;
        int x0 = clampTile(Math.floor(mercX(0) * n), n);
        int x1 = clampTile(Math.ceil(mercX(width) * n) - 1, n);
        int y0 = clampTile(Math.floor(mercY(0) * n), n);
        int y1 = clampTile(Math.ceil(mercY(height) * n) - 1, n);
        List<TileId> ids = new ArrayList<>((x1 - x0 + 1) * (y1 - y0 + 1));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) ids.add(new TileId(z, x, y));
        }
        return ids;
    }

    private static int clampTile(double v, int n) {
        return (int) Math.max(0, Math.min(n - 1, v));
    }
}
