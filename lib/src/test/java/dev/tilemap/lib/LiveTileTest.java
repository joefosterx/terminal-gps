package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Cell;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileSource;
import dev.tilemap.core.Viewport;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Renders from OpenFreeMap's public tiles (OpenMapTiles schema, no key). Run with {@code gradlew :lib:test -Plive}. */
@EnabledIfSystemProperty(named = "tilemap.live", matches = "true")
class LiveTileTest {
    static final String OPENFREEMAP = "https://tiles.openfreemap.org/planet";

    @Test
    void rendersCentralLondon() throws Exception {
        TileSource src = TileSources.open(new SourceConfig.Url(OPENFREEMAP, null));
        assertEquals(14, src.maxZoom());
        Viewport vp = new Viewport(new LonLat(-0.1276, 51.5072), 15, 100, 35);
        Canvas canvas = Renderer.render(vp, Styles.defaultStyle(), Capabilities.DEFAULT, src);
        System.out.println(canvas.toPlain());

        long drawn = Arrays.stream(canvas.cells()).filter(c -> !c.equals(Cell.EMPTY)).count();
        assertTrue(drawn > canvas.cells().length / 4, "only " + drawn + " cells drawn");
    }
}
