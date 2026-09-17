package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Styles;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapRequestJsonTest {
    @TempDir
    Path dir;

    @Test
    void minimalRequestUsesDefaults() throws Exception {
        MapRequest req = MapRequestJson.read(new StringReader("""
                {"area": {"center": [-0.1276, 51.5072], "zoom": 14}, "size": [120, 40]}
                """), dir);
        assertEquals(new Area.Center(-0.1276, 51.5072, 14), req.area());
        assertEquals(120, req.cols());
        assertEquals(40, req.rows());
        assertEquals(Styles.defaultStyle(), req.style());
        assertEquals(Capabilities.DEFAULT, req.caps());
        assertEquals(SourceConfig.OPENFREEMAP, req.source());
    }

    @Test
    void fullRequestResolvesRelativePaths() throws Exception {
        Files.copy(TownTiles.fixture(), dir.resolve("town.geojson"));
        Files.writeString(dir.resolve("mono.json"), """
                {"layers": [{"id": "w", "source": "water", "paint": {"kind": "fill", "fg": "#ffffff"}}]}
                """);
        Path file = dir.resolve("req.json");
        Files.writeString(file, """
                {"area": {"bbox": [9.99, 49.99, 10.01, 50.01]}, "size": [60, 20], "style": "mono.json",
                 "charset": "box", "color": "true", "source": "town.geojson"}
                """);
        MapRequest req = MapRequestJson.read(file);
        assertInstanceOf(Area.BBox.class, req.area());
        assertEquals(new Capabilities(Charset.BOX, ColorDepth.TRUE), req.caps());
        assertEquals("w", req.style().layers().getFirst().id());
        assertInstanceOf(SourceConfig.GeoJson.class, req.source());
        TileMap.renderCanvas(req);
    }

    @Test
    void inlineStylesAreAccepted() throws Exception {
        MapRequest req = MapRequestJson.read(new StringReader("""
                {"area": {"center": [0, 0], "zoom": 1}, "size": [10, 5],
                 "style": {"layers": [{"id": "x", "source": "water", "paint": {"kind": "fill", "fg": "#000000"}}]}}
                """), dir);
        assertEquals("x", req.style().layers().getFirst().id());
    }

    @Test
    void reportsMistakes() {
        for (String bad : new String[] {
            "[]",
            "{\"size\": [10, 5]}",
            "{\"area\": {\"center\": [0, 0]}, \"size\": [10, 5]}",
            "{\"area\": {\"center\": [0, 0], \"zoom\": 1}, \"size\": [10]}",
            "{\"area\": {\"center\": [0, 0], \"zoom\": 1}, \"size\": [10, 5], \"charset\": \"klingon\"}",
            "{\"area\": {\"center\": [0, 0], \"zoom\": 1}, \"size\": [10, 5], \"style\": \"nope\"}",
        }) {
            assertThrows(IllegalArgumentException.class, () -> MapRequestJson.read(new StringReader(bad), dir), bad);
        }
    }
}
