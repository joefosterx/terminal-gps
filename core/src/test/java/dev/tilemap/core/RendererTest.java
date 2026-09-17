package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Uses a 4×2 viewport (8×8 dots) centered on the corner shared by tiles 2/1/1, 2/2/1, 2/1/2 and 2/2/2. One tile
 * unit is 1/16 dot, and the top-left of tile 2/2/2 sits at dot (4, 4).
 */
class RendererTest {
    private static final Viewport VIEW = new Viewport(new LonLat(0, 0), 2, 4, 2);
    private static final Capabilities ASCII = new Capabilities(Charset.ASCII, ColorDepth.NONE);

    private static Paint paint(PaintKind kind, String fg, String bg, Weight weight, GlyphStrategy strategy) {
        return new Paint(kind, Rgb.fromHex(fg), bg == null ? null : Rgb.fromHex(bg), weight, strategy);
    }

    private static StyleLayer layer(String source, Paint paint) {
        return new StyleLayer(source, source, Map.of(), 0, 25, paint);
    }

    private static Capabilities charset(Charset charset) {
        return new Capabilities(charset, ColorDepth.TRUE);
    }

    private static Feature feature(Geometry g) {
        return new Feature(g, Map.of());
    }

    /** Every tile holds a full-tile square in "area" and a horizontal line 24 units below its top in "road". */
    private static final java.util.function.Function<TileId, Optional<Tile>> SQUARE_AND_LINE_FETCH = id -> Optional.of(new Tile(id, List.of(
            new Layer("area", List.of(feature(new Geometry.Polygon(List.of(new double[] {0, 0, 4096, 0, 4096, 4096, 0, 4096}))))),
            new Layer("road", List.of(feature(new Geometry.Line(List.of(new double[] {0, 24, 4096, 24}))))))));
    private static final TileSource SQUARE_AND_LINE = SQUARE_AND_LINE_FETCH::apply;

    /** A horizontal "major" road through cell row 1 and a vertical "minor" road through cell column 2. */
    private static final TileSource CROSSING = id -> Optional.of(new Tile(id, List.of(
            new Layer("major", List.of(feature(new Geometry.Line(List.of(new double[] {0, 24, 4096, 24}))))),
            new Layer("minor", List.of(feature(new Geometry.Line(List.of(new double[] {24, 0, 24, 4096}))))))));

    private static final Style FILL_AND_LINE = new Style(List.of(
            layer("area", paint(PaintKind.FILL, "#00ff00", "#003300", Weight.LIGHT, GlyphStrategy.BRAILLE)),
            layer("road", paint(PaintKind.LINE, "#ffaa00", null, Weight.LIGHT, GlyphStrategy.BRAILLE))));

    private static final Style ROADS = new Style(List.of(
            layer("minor", paint(PaintKind.LINE, "#888888", null, Weight.LIGHT, GlyphStrategy.BOX_LINE)),
            layer("major", paint(PaintKind.LINE, "#ffaa00", null, Weight.HEAVY, GlyphStrategy.BOX_LINE))));

    @Test
    void emptySourceRendersBlankCanvas() throws RenderException {
        Canvas c = Renderer.render(VIEW, Styles.defaultStyle(), Capabilities.DEFAULT, EmptyTileSource.INSTANCE);
        assertEquals("    \n    \n", c.toPlain());
    }

    @Test
    void fillsTakeBackgroundAndLinesDrawOnTop() throws RenderException {
        Canvas c = Renderer.render(VIEW, FILL_AND_LINE, Capabilities.DEFAULT, SQUARE_AND_LINE);

        assertEquals("⣿⣿⣿⣿\n⠒⠒⠒⠒\n", c.toPlain());
        Cell road = c.cell(0, 1);
        assertEquals(1, road.layer());
        assertEquals(Rgb.fromHex("#ffaa00"), road.fg());
        assertEquals(Rgb.fromHex("#003300"), road.bg());
        assertEquals(0, c.cell(0, 0).layer());
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {"BRAILLE, ⣿⣿⣿⣿|⠒⠒⠒⠒", "SEXTANT, ⣿⣿⣿⣿|⠒⠒⠒⠒", "BOX, ████|▀▀▀▀", "LATIN1, ####|''''", "ASCII, ####|''''"})
    void dotLayersDegradeWithCharset(Charset charset, String expected) throws RenderException {
        Canvas c = Renderer.render(VIEW, FILL_AND_LINE, charset(charset), SQUARE_AND_LINE);
        assertEquals(expected.replace('|', '\n') + "\n", c.toPlain());
    }

    @Test
    void shadeStrategyUsesCoverage() throws RenderException {
        Style style = new Style(List.of(layer("area", paint(PaintKind.FILL, "#00ff00", null, Weight.LIGHT, GlyphStrategy.SHADE))));
        assertEquals("████\n████\n", Renderer.render(VIEW, style, charset(Charset.BOX), SQUARE_AND_LINE).toPlain());
        assertEquals("####\n####\n", Renderer.render(VIEW, style, ASCII, SQUARE_AND_LINE).toPlain());
    }

    @Test
    void boxLinesJoinAtIntersections() throws RenderException {
        Canvas c = Renderer.render(VIEW, ROADS, charset(Charset.BOX), CROSSING);
        assertEquals("  │ \n━━╋━\n", c.toPlain());
        assertEquals(1, c.cell(2, 1).layer());
        assertEquals(new Attrs(true, false), c.cell(0, 1).attrs());
        assertEquals(Attrs.NONE, c.cell(2, 0).attrs());
    }

    @Test
    void boxLinesDegradeToAscii() throws RenderException {
        assertEquals("  | \n==+=\n", Renderer.render(VIEW, ROADS, ASCII, CROSSING).toPlain());
    }

    @Test
    void boxLinesKeepTheBackgroundBeneath() throws RenderException {
        Style style = new Style(List.of(
                layer("area", paint(PaintKind.FILL, "#00ff00", "#003300", Weight.LIGHT, GlyphStrategy.BRAILLE)),
                layer("road", paint(PaintKind.LINE, "#ffaa00", null, Weight.LIGHT, GlyphStrategy.BOX_LINE))));
        Canvas c = Renderer.render(VIEW, style, Capabilities.DEFAULT, SQUARE_AND_LINE);
        assertEquals("⣿⣿⣿⣿\n────\n", c.toPlain());
        assertEquals(Rgb.fromHex("#003300"), c.cell(1, 1).bg());
    }

    @Test
    void markersDrawOneGlyphPerCell() throws RenderException {
        // Local (40, 4080) in tile 2/2/1 is dot (6.5, 3): column 3, row 0.
        TileSource points = id -> id.equals(new TileId(2, 2, 1))
                ? Optional.of(new Tile(id, List.of(new Layer("poi", List.of(feature(new Geometry.Point(new double[] {40, 4080})))))))
                : Optional.empty();
        Style style = new Style(List.of(layer("poi", new Paint(PaintKind.POINT, Rgb.fromHex("#aa0000"), null, Weight.LIGHT, GlyphStrategy.MARKER, '⌂'))));
        assertEquals("   ⌂\n    \n", Renderer.render(VIEW, style, Capabilities.DEFAULT, points).toPlain());
        assertEquals("   H\n    \n", Renderer.render(VIEW, style, ASCII, points).toPlain());
    }

    @Test
    void fillPatternsCoverOnlyTheirDots() throws RenderException {
        Paint dotted = new Paint(PaintKind.FILL, Rgb.fromHex("#00ff00"), null, Weight.LIGHT, GlyphStrategy.BRAILLE, '●',
                Styles.pattern("⠪", "test"));
        Style style = new Style(List.of(layer("area", dotted)));
        assertEquals("⠪⠪⠪⠪\n⠪⠪⠪⠪\n", Renderer.render(VIEW, style, Capabilities.DEFAULT, SQUARE_AND_LINE).toPlain());
    }

    @Test
    void styleCanCapCharsetAndDropColor() throws RenderException {
        Style asciiMono = new Style(ROADS.layers(), List.of(), Charset.ASCII, true);
        Canvas c = Renderer.render(VIEW, asciiMono, charset(Charset.BRAILLE), CROSSING);
        assertEquals("  | \n==+=\n", c.toPlain());
        for (Cell cell : c.cells()) {
            assertEquals(null, cell.fg());
            assertEquals(null, cell.bg());
        }
        assertEquals(new Attrs(true, false), c.cell(0, 1).attrs(), "weight still shows as bold");
    }

    @Test
    void layersOutsideTheirZoomRangeAreSkipped() throws RenderException {
        Style style = new Style(List.of(new StyleLayer("area", "area", Map.of(), 5, 25,
                paint(PaintKind.FILL, "#00ff00", null, Weight.LIGHT, GlyphStrategy.BRAILLE))));
        assertEquals("    \n    \n", Renderer.render(VIEW, style, Capabilities.DEFAULT, SQUARE_AND_LINE).toPlain());
    }

    @Test
    void overzoomsBeyondTheSourceMaxZoom() throws RenderException {
        List<TileId> requested = new java.util.ArrayList<>();
        TileSource shallow = new TileSource() {
            @Override
            public Optional<Tile> fetch(TileId id) {
                requested.add(id);
                return SQUARE_AND_LINE_FETCH.apply(id);
            }

            @Override
            public int maxZoom() {
                return 1;
            }
        };
        Canvas c = Renderer.render(VIEW, FILL_AND_LINE, Capabilities.DEFAULT, shallow);
        assertEquals(List.of(new TileId(1, 0, 0), new TileId(1, 1, 0), new TileId(1, 0, 1), new TileId(1, 1, 1)), requested);
        assertEquals(requested, Renderer.tilesFor(VIEW, 1));
        // A z1 tile is 512 dots, so the line 24 units below tile 1/x/1's top lands 3 dots down, on the bottom dot row.
        assertEquals("⣿⣿⣿⣿" + "\n" + "⣀⣀⣀⣀" + "\n", c.toPlain());
    }

    @Test
    void tileErrorsBecomeRenderExceptions() {
        TileSource broken = id -> {
            throw new TileException("boom");
        };
        assertThrows(RenderException.class, () -> Renderer.render(VIEW, Styles.defaultStyle(), Capabilities.DEFAULT, broken));
    }
}
