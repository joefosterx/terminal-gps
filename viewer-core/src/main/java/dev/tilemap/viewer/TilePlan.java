package dev.tilemap.viewer;

import dev.tilemap.core.Projection;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which tiles a viewer wants beyond the visible ones, and where a tile lands on the cell grid. */
public final class TilePlan {
    private TilePlan() {}

    /** The ring of tiles around the view and the parents of the visible tiles, so small pans and zoom-outs hit cache. */
    public static List<TileId> prefetch(Viewport vp, List<TileId> visible) {
        Set<TileId> out = new LinkedHashSet<>();
        if (!visible.isEmpty()) {
            TileId first = visible.get(0), last = visible.get(visible.size() - 1);
            int z = first.z(), n = 1 << z;
            for (int y = first.y() - 1; y <= last.y() + 1; y++) {
                for (int x = first.x() - 1; x <= last.x() + 1; x++) {
                    if (x >= 0 && y >= 0 && x < n && y < n) out.add(new TileId(z, x, y));
                }
            }
            if (z > 0) {
                for (TileId id : visible) out.add(new TileId(z - 1, id.x() / 2, id.y() / 2));
            }
        }
        visible.forEach(out::remove);
        return new ArrayList<>(out);
    }

    /** The map cells a tile covers, using the renderer's projection (a cell is 2 × 4 dots). */
    public static CellRect rect(Viewport vp, TileId id) {
        double world = Projection.worldDots(vp.zoom());
        double cx = Projection.mercX(vp.center().lon()), cy = Projection.mercY(vp.center().lat());
        double n = 1 << id.z();
        double x0 = ((id.x() / n - cx) * world + vp.cols()) / 2;
        double x1 = (((id.x() + 1) / n - cx) * world + vp.cols()) / 2;
        double scaleY = world * 2 * vp.cellAspect();
        double y0 = ((id.y() / n - cy) * scaleY + vp.rows() * 2) / 4;
        double y1 = (((id.y() + 1) / n - cy) * scaleY + vp.rows() * 2) / 4;
        return new CellRect((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
    }
}
