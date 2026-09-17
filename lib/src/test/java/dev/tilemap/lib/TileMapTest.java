package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileSource;
import dev.tilemap.core.Viewport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TileMapTest {
    private static final Path GOLDEN_DIR = Path.of("src", "test", "golden");
    private static final Capabilities TRUE_COLOR = new Capabilities(Charset.BRAILLE, ColorDepth.TRUE);

    private static MapRequest townRequest(Capabilities caps) throws IOException {
        return new MapRequest(new Area.Center(10.0005, 50.0005, 14.5), 40, 12, null, caps,
                new SourceConfig.GeoJson(Files.readAllBytes(TownTiles.fixture())));
    }

    static void assertGolden(String name, String actual) throws IOException {
        Path file = GOLDEN_DIR.resolve(name);
        if (Boolean.getBoolean("tilemap.updateGoldens")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(file)) throw new AssertionError("missing golden " + file + "; run gradlew :lib:test -PupdateGoldens");
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), actual, "golden " + name);
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void formatsTheFixture(Format fmt) throws Exception {
        assertGolden("town-" + fmt.name().toLowerCase() + "." + switch (fmt) {
            case HTML -> "html";
            case SVG -> "svg";
            case JSON -> "json";
            default -> "txt";
        }, TileMap.renderString(townRequest(TRUE_COLOR), fmt));
    }

    @Test
    void colorlessMarkupHasNoColors() throws Exception {
        MapRequest req = townRequest(new Capabilities(Charset.BRAILLE, ColorDepth.NONE));
        assertFalse(TileMap.renderString(req, Format.HTML).contains("color:"));
        assertFalse(TileMap.renderString(req, Format.SVG).contains("fill="));
        assertFalse(TileMap.renderString(req, Format.JSON).contains("\"#"));
    }

    @Test
    void htmlEscapesMarkup() {
        var cells = new dev.tilemap.core.Cell[] {
            new dev.tilemap.core.Cell('<', null, null, dev.tilemap.core.Attrs.NONE, -1),
            new dev.tilemap.core.Cell('&', null, null, dev.tilemap.core.Attrs.NONE, -1),
        };
        String html = OutputFormats.html(new dev.tilemap.core.Canvas(2, 1, cells), true);
        assertTrue(html.contains(">&lt;&amp;\n</pre>"), html);
    }

    @Test
    void nullStyleAndCapsUseDefaults() throws IOException {
        MapRequest req = new MapRequest(new Area.Center(0, 0, 1), 10, 5, null, null, SourceConfig.OPENFREEMAP);
        assertEquals(Styles.defaultStyle(), req.style());
        assertEquals(Capabilities.DEFAULT, req.caps());
    }

    @Test
    void bboxPicksTheLargestZoomThatFits() {
        Area.BBox box = new Area.BBox(2.29, 48.85, 2.31, 48.86);
        Viewport vp = box.viewport(120, 40);
        double world = Projection.worldDots(vp.zoom());
        double widthDots = (Projection.mercX(2.31) - Projection.mercX(2.29)) * world;
        double heightDots = (Projection.mercY(48.85) - Projection.mercY(48.86)) * world * 2 * vp.cellAspect();
        assertTrue(widthDots <= 240 + 1e-6 && heightDots <= 160 + 1e-6, widthDots + " x " + heightDots);
        assertTrue(Math.abs(widthDots - 240) < 1e-6 || Math.abs(heightDots - 160) < 1e-6, "box should touch an edge");
        assertEquals(2.30, vp.center().lon(), 1e-9);
        assertEquals(48.855, vp.center().lat(), 1e-4);

        assertThrows(IllegalArgumentException.class, () -> new Area.BBox(10, 0, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new Area.Center(0, 0, 30));
    }

    @Test
    void failedTilesGiveAPartialResultOrAStrictError() throws Exception {
        TileSource broken = id -> {
            throw new TileException("boom");
        };
        MapRequest req = townRequest(TRUE_COLOR);
        TileMap.Result result = TileMap.render(req, broken);
        assertFalse(result.complete());
        assertEquals("boom", result.failures().getFirst().message());
        assertEquals(req.cols(), result.canvas().cols());

        MapRequest unreachable = new MapRequest(req.area(), 4, 2, null, null, new SourceConfig.PmTiles(Path.of("does-not-exist.pmtiles")));
        assertThrows(TileMapException.class, () -> TileMap.renderCanvas(unreachable));
    }

    @Test
    void sourceStringsAreClassified() throws IOException {
        assertTrue(SourceConfig.fromString("https://x.example/{z}/{x}/{y}.pbf", "k") instanceof SourceConfig.Url u && "k".equals(u.key()));
        assertTrue(SourceConfig.fromString(TownTiles.fixture().toString(), null) instanceof SourceConfig.GeoJson);
        assertThrows(IllegalArgumentException.class, () -> SourceConfig.fromString("nope.pmtiles", null));
        assertThrows(IllegalArgumentException.class, () -> SourceConfig.fromString("build.gradle", null));
    }
}
