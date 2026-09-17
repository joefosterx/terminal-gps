package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ViewTransformTest {
    @Test
    void projectionRoundTrips() {
        assertEquals(10.0, Projection.lon(Projection.mercX(10.0)), 1e-9);
        assertEquals(50.0, Projection.lat(Projection.mercY(50.0)), 1e-9);
        assertEquals(0.5, Projection.mercY(0), 1e-12);
    }

    @Test
    void centerMapsToMiddleOfDotGrid() {
        ViewTransform t = ViewTransform.of(new Viewport(new LonLat(10, 50), 14.3, 80, 30));
        assertEquals(80, t.dotX(Projection.mercX(10)), 1e-6);
        assertEquals(60, t.dotY(Projection.mercY(50)), 1e-6);
    }

    @Test
    void cellAspectStretchesVertically() {
        ViewTransform square = ViewTransform.of(new Viewport(new LonLat(0, 0), 10, 10, 10, 0.5));
        ViewTransform tall = ViewTransform.of(new Viewport(new LonLat(0, 0), 10, 10, 10, 0.25));
        assertEquals(square.scaleX(), square.scaleY(), 1e-9);
        assertEquals(square.scaleY() / 2, tall.scaleY(), 1e-9);
    }

    @Test
    void wholeWorldAtZoomZeroIsOneTile() {
        assertEquals(List.of(new TileId(0, 0, 0)), Renderer.tilesFor(new Viewport(new LonLat(0, 0), 0.5, 200, 60)));
    }

    @Test
    void tilesAreRowMajorAndCoverTheViewport() {
        // Centered exactly on a tile corner at z=2, a small viewport touches the four surrounding tiles.
        List<TileId> tiles = Renderer.tilesFor(new Viewport(new LonLat(0, 0), 2, 4, 4));
        assertEquals(List.of(new TileId(2, 1, 1), new TileId(2, 2, 1), new TileId(2, 1, 2), new TileId(2, 2, 2)), tiles);
    }

    @Test
    void tileRangeIsClampedAtTheWorldEdge() {
        List<TileId> tiles = Renderer.tilesFor(new Viewport(new LonLat(-179.9, 84), 3, 100, 100));
        assertEquals(new TileId(3, 0, 0), tiles.get(0));
        for (TileId id : tiles) assertEquals(3, id.z());
    }
}
