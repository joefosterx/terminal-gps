package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoJsonTileSourceTest {
    private static final String ONE_SQUARE = """
            {"type": "FeatureCollection", "features": [
              {"type": "Feature", "properties": {"layer": "water", "name": "Pond", "depth": 3, "note": null},
               "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [90, 0], [90, 45], [0, 45], [0, 0]]]}}
            ]}
            """;

    @Test
    void placesFeaturesInTileLocalCoordinates() throws Exception {
        GeoJsonTileSource src = GeoJsonTileSource.parse(new StringReader(ONE_SQUARE));
        Tile tile = src.fetch(new TileId(1, 1, 0)).orElseThrow();
        assertEquals(1, tile.layers().size());
        Layer layer = tile.layers().get(0);
        assertEquals("water", layer.name());

        Feature f = layer.features().get(0);
        assertEquals("Pond", f.tags().get("name"));
        assertEquals("3", f.tags().get("depth"));
        assertTrue(!f.tags().containsKey("layer") && !f.tags().containsKey("note"));

        double[] ring = assertInstanceOf(Geometry.Polygon.class, f.geom()).rings().get(0);
        assertEquals(0, ring[0], 1e-9);                   // lon 0 is the tile's west edge
        assertEquals(Tile.EXTENT, ring[1], 1e-9);         // lat 0 is its south edge
        assertEquals(Tile.EXTENT / 2.0, ring[2], 1e-9);   // lon 90 is half way across
    }

    @Test
    void tilesWithoutFeaturesAreEmpty() throws Exception {
        GeoJsonTileSource src = GeoJsonTileSource.parse(new StringReader(ONE_SQUARE));
        assertTrue(src.fetch(new TileId(2, 0, 0)).isEmpty());
    }

    @Test
    void readsTheTownFixture() throws Exception {
        TileId id = Renderer.tilesFor(new Viewport(Fixtures.TOWN_CENTER, 12, 10, 10)).get(0);
        List<String> layers = Fixtures.town().fetch(id).orElseThrow().layers().stream().map(Layer::name).toList();
        assertEquals(List.of("water", "transportation", "landcover", "building", "place", "poi"), layers);
    }

    @Test
    void rejectsUnsupportedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> GeoJsonTileSource.parse(new StringReader("""
                {"type": "FeatureCollection", "features": [
                  {"type": "Feature", "properties": {}, "geometry": {"type": "GeometryCollection", "geometries": []}}]}
                """)));
    }
}
