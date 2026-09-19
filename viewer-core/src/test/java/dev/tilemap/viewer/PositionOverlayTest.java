package dev.tilemap.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Style;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PositionOverlayTest {
    private static GeoJsonTileSource town() throws Exception {
        String fixtures = System.getProperty("tilemap.fixtures");
        Path file = (fixtures != null ? Path.of(fixtures) : Path.of("..", "fixtures")).resolve("town.geojson");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GeoJsonTileSource.parse(r);
        }
    }

    @Test
    void marksThePositionAndNothingElseChanges() throws Exception {
        Viewport vp = new Viewport(new LonLat(10.0, 50.0), 15, 80, 30);
        Capabilities caps = new Capabilities(Charset.BRAILLE, ColorDepth.TRUE);
        Style style = PositionOverlay.style(Styles.defaultStyle());
        PositionOverlay overlay = new PositionOverlay(town());

        String plain = Renderer.render(vp, Styles.defaultStyle(), caps, town()).toPlain();
        assertEquals(plain, Renderer.render(vp, style, caps, overlay).toPlain(), "no position, no change");

        overlay.setPosition(new LonLat(10.0, 50.0));
        Canvas marked = Renderer.render(vp, style, caps, overlay);
        // The view is centered on the position, so the marker lands in the middle cell.
        assertEquals(PositionOverlay.MARKER, marked.cell(40, 15).codePoint());
        int diffs = 0;
        for (int r = 0; r < 30; r++) {
            for (int c = 0; c < 80; c++) {
                if (marked.cell(c, r).codePoint() != plain.lines().toList().get(r).codePointAt(c)) diffs++;
            }
        }
        assertEquals(1, diffs, "only the marker cell differs");

        overlay.setPosition(new LonLat(11.0, 51.0));
        assertEquals(plain, Renderer.render(vp, style, caps, overlay).toPlain(), "a position off screen changes nothing");
    }

    @Test
    void asciiDegradesTheMarker() throws Exception {
        PositionOverlay overlay = new PositionOverlay(town());
        overlay.setPosition(new LonLat(10.0, 50.0));
        Canvas ascii = Renderer.render(new Viewport(new LonLat(10.0, 50.0), 15, 80, 30), PositionOverlay.style(Styles.defaultStyle()),
                new Capabilities(Charset.ASCII, ColorDepth.NONE), overlay);
        assertTrue(ascii.cell(40, 15).codePoint() < 0x7f);
        assertTrue(overlay.fetch(new TileId(15, 0, 0)).isEmpty(), "tiles far from the position stay absent");
    }
}
