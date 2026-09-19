package dev.tilemap.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.LonLat;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.util.List;
import org.junit.jupiter.api.Test;

class TilePlanTest {
    @Test
    void tileRectMatchesRendererProjection() {
        // At zoom 2 centered on 0,0 in a 20 x 5 view, tile 2/2/2 starts at dot (20, 10): cell (10, 2.5).
        Viewport vp = new Viewport(new LonLat(0, 0), 2, 20, 5);
        assertEquals(new CellRect(10, 2, 138, 67), TilePlan.rect(vp, new TileId(2, 2, 2)));
    }

    @Test
    void prefetchIsTheRingAndParents() {
        List<TileId> visible = List.of(new TileId(3, 4, 4), new TileId(3, 5, 4));
        List<TileId> ring = TilePlan.prefetch(new Viewport(new LonLat(0, 0), 3, 10, 10), visible);
        assertEquals(10 + 1, ring.size()); // the 4 x 3 block around them minus the 2 visible, plus parent 2/2/2
        assertTrue(ring.contains(new TileId(2, 2, 2)));
        assertTrue(ring.stream().noneMatch(visible::contains));
    }
}
