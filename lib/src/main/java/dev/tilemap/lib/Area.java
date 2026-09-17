package dev.tilemap.lib;

import dev.tilemap.core.LonLat;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Viewport;

/** The part of the world a {@link MapRequest} shows. */
public sealed interface Area {
    /** Resolves this area to a viewport of the given size. */
    Viewport viewport(int cols, int rows);

    /**
     * A bounding box in degrees. It is shown centered, at the largest (fractional) zoom where the whole box fits.
     * Boxes crossing the antimeridian are not supported.
     */
    record BBox(double west, double south, double east, double north) implements Area {
        public BBox {
            if (!(west < east)) throw new IllegalArgumentException("bbox west must be less than east");
            if (!(south < north)) throw new IllegalArgumentException("bbox south must be less than north");
            if (west < -180 || east > 180 || south < -90 || north > 90) throw new IllegalArgumentException("bbox out of range");
        }

        @Override
        public Viewport viewport(int cols, int rows) {
            double x0 = Projection.mercX(west), x1 = Projection.mercX(east);
            double y0 = Projection.mercY(north), y1 = Projection.mercY(south);
            double aspect = Viewport.DEFAULT_CELL_ASPECT;
            // Width in dots at zoom z is dx * TILE_DOTS * 2^z; height is dy * TILE_DOTS * 2^z * 2 * aspect.
            double zx = log2(cols * 2 / ((x1 - x0) * Projection.TILE_DOTS));
            double zy = log2(rows * 4 / ((y1 - y0) * Projection.TILE_DOTS * 2 * aspect));
            double zoom = Math.max(0, Math.min(Projection.MAX_ZOOM, Math.min(zx, zy)));
            LonLat center = new LonLat(Projection.lon((x0 + x1) / 2), Projection.lat((y0 + y1) / 2));
            return new Viewport(center, zoom, cols, rows, aspect);
        }

        private static double log2(double v) {
            return Math.log(v) / Math.log(2);
        }
    }

    /** A center and zoom, passed through unchanged. */
    record Center(double lon, double lat, double zoom) implements Area {
        public Center {
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90) throw new IllegalArgumentException("center out of range");
            if (!(zoom >= 0 && zoom <= Projection.MAX_ZOOM)) {
                throw new IllegalArgumentException("zoom must be between 0 and " + Projection.MAX_ZOOM);
            }
        }

        @Override
        public Viewport viewport(int cols, int rows) {
            return new Viewport(new LonLat(lon, lat), zoom, cols, rows);
        }
    }
}
