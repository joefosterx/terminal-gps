package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LabelsTest {
    private static final Capabilities BOX = new Capabilities(Charset.BOX, ColorDepth.NONE);

    /** Milestone 5 acceptance: every named fixture feature is labelled in full, once, at zoom 15. */
    @Test
    void allTwentyFixtureLabelsPlaceWithoutOverlap() throws Exception {
        List<String> names = new ArrayList<>();
        for (var f : new ObjectMapper().readTree(Files.readString(Fixtures.dir().resolve("town.geojson"))).path("features")) {
            if (f.path("properties").has("name")) names.add(f.path("properties").path("name").asText());
        }
        assertEquals(20, names.size());

        // 250 x 150 cells at zoom 15 shows the whole town.
        Viewport vp = new Viewport(Fixtures.TOWN_CENTER, 15, 250, 150);
        Canvas canvas = Renderer.render(vp, Styles.defaultStyle(), BOX, Fixtures.town());
        String plain = canvas.toPlain();

        List<String> missing = new ArrayList<>();
        int expectedCells = 0;
        for (String name : names) {
            // Don't count occurrences inside longer names ("Station" in "Station Road").
            int count = occurrences(plain, name);
            for (String other : names) {
                if (!other.equals(name) && other.contains(name)) count -= occurrences(plain, other);
            }
            if (count != 1) missing.add(name + " x" + count);
            expectedCells += name.length();
        }
        long labelCells = java.util.Arrays.stream(canvas.cells()).filter(c -> c.layer() == Cell.LABEL_LAYER).count();
        assertTrue(missing.isEmpty(), "labels not placed exactly once: " + missing + "\n" + plain);
        // No label overwrote another: every label cell is accounted for by exactly one name.
        assertEquals(expectedCells, labelCells);
    }

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void noLabelsSkipsThePass() throws Exception {
        Viewport vp = new Viewport(Fixtures.TOWN_CENTER, 15, 120, 60);
        Canvas canvas = Renderer.render(vp, Styles.defaultStyle(), BOX, Fixtures.town(), false);
        assertTrue(java.util.Arrays.stream(canvas.cells()).noneMatch(c -> c.layer() == Cell.LABEL_LAYER));
    }

    // A 20 x 5 view at zoom 2 centered on 0,0: one tile unit is 1/16 dot; tile 2/2/2 starts at dot (20, 10).

    private static final Viewport VIEW = new Viewport(new LonLat(0, 0), 2, 20, 5);

    private static Style style(List<StyleLayer> layers, LabelRule... rules) {
        return new Style(layers, List.of(rules));
    }

    private static LabelRule rule(String source, int priority, int maxWidth) {
        return new LabelRule(source, source, Map.of(), 0, 25, "name", priority, maxWidth, false);
    }

    /** Local tile units in tile 2/2/2 for a cell position (col >= 10, row >= 2.5 are in that tile). */
    private static double[] local(double col, double row) {
        return new double[] {(col * 2 - 20) * 16, (row * 4 - 10) * 16};
    }

    private static TileSource source(Map<String, List<Feature>> layers) {
        return id -> {
            if (!id.equals(new TileId(2, 2, 2))) return Optional.empty();
            List<Layer> out = new ArrayList<>();
            layers.forEach((name, fs) -> out.add(new Layer(name, fs)));
            return Optional.of(new Tile(id, out));
        };
    }

    private static Feature point(double col, double row, String name) {
        return new Feature(new Geometry.Point(local(col + 0.5, row + 0.5)), Map.of("name", name));
    }

    @Test
    void pointsGoRightThenLeft() throws RenderException {
        // Alpha fits right of its anchor; Beta is at the right edge so it goes left, one cell clear of Alpha.
        TileSource src = source(Map.of("place", List.of(point(5, 2, "Alpha"), point(18, 2, "Beta"))));
        Canvas c = Renderer.render(VIEW, style(List.of(), rule("place", 1, 24)), Capabilities.DEFAULT, src);
        assertEquals("       Alpha Beta", c.toPlain().lines().toList().get(2).stripTrailing());
    }

    @Test
    void protectedLayersAreNotCovered() throws RenderException {
        Paint water = new Paint(PaintKind.FILL, Rgb.fromHex("#0000ff"), null, Weight.LIGHT, GlyphStrategy.BRAILLE);
        StyleLayer lake = new StyleLayer("lake", "lake", Map.of(), 0, 25, water, true);
        // A lake over columns 14-19 of row 3 blocks the right placement, so the label goes left.
        Feature lakeShape = new Feature(new Geometry.Polygon(List.of(concat(local(14, 3), local(20, 3), local(20, 4), local(14, 4)))), Map.of());
        Map<String, List<Feature>> layers = new java.util.LinkedHashMap<>();
        layers.put("lake", List.of(lakeShape));
        layers.put("place", List.of(point(11, 3, "Home")));
        Canvas c = Renderer.render(VIEW, style(List.of(lake), rule("place", 1, 24)), Capabilities.DEFAULT, source(layers));
        assertEquals("      Home    ⣿⣿⣿⣿⣿⣿", c.toPlain().lines().toList().get(3));
    }

    @Test
    void higherPriorityWinsAndDuplicatesAreDropped() throws RenderException {
        Map<String, List<Feature>> layers = new java.util.LinkedHashMap<>();
        layers.put("minor", List.of(point(10, 3, "Low"), point(15, 1, "Twin")));
        layers.put("major", List.of(point(10, 3, "High"), point(15, 1, "Twin")));
        Canvas c = Renderer.render(VIEW, style(List.of(), rule("minor", 1, 24), rule("major", 9, 24)), Capabilities.DEFAULT, source(layers));
        List<String> rows = c.toPlain().lines().map(String::stripTrailing).toList();
        assertEquals("          Twin", rows.get(1));
        assertEquals("      Low   High", rows.get(3));
    }

    @Test
    void horizontalLinesAreLabelledAlongTheirRun() throws RenderException {
        Feature road = new Feature(new Geometry.Line(List.of(concat(local(10.5, 3.5), local(20.5, 3.5)))), Map.of("name", "Main"));
        Canvas c = Renderer.render(VIEW, style(List.of(), rule("road", 1, 24)), Capabilities.DEFAULT, source(Map.of("road", List.of(road))));
        assertEquals(" ".repeat(13) + "Main", c.toPlain().lines().toList().get(3).stripTrailing());
    }

    @Test
    void verticalLinesAreLabelledBeside() throws RenderException {
        Feature road = new Feature(new Geometry.Line(List.of(concat(local(12.5, 2.5), local(12.5, 5.5)))), Map.of("name", "Side"));
        Canvas c = Renderer.render(VIEW, style(List.of(), rule("road", 1, 24)), Capabilities.DEFAULT, source(Map.of("road", List.of(road))));
        assertEquals(" ".repeat(14) + "Side", c.toPlain().lines().toList().get(3).stripTrailing());
    }

    @Test
    void cleansAndTruncatesText() {
        assertArrayEquals("Long …".codePoints().toArray(), Labels.clean("Long   name here", 6));
        assertArrayEquals("Café".codePoints().toArray(), Labels.clean("Cafe" + (char) 0x301, 24));
        assertNull(Labels.clean("  ", 24));
        assertNull(Labels.clean("東京", 24));
    }

    @Test
    void truncatesAtTheEdgeAndDropsWhenUnderFourCharactersFit() {
        LabelRule rule = rule("place", 1, 24);

        Labels edge = new Labels(10, 1);
        edge.add(rule, new Geometry.Point(new double[] {3, 1}), "Riverside");   // cell (1, 0)
        Cell[] cells = blank(10);
        edge.place(cells, List.of());
        assertEquals("   Rivers…", text(cells));

        Labels cramped = new Labels(6, 1);
        cramped.add(rule, new Geometry.Point(new double[] {7, 1}), "Nowhere");  // cell (3, 0)
        Cell[] none = blank(6);
        cramped.place(none, List.of());
        assertEquals("      ", text(none));
    }

    private static Cell[] blank(int n) {
        Cell[] cells = new Cell[n];
        java.util.Arrays.fill(cells, Cell.EMPTY);
        return cells;
    }

    private static String text(Cell[] cells) {
        StringBuilder sb = new StringBuilder();
        for (Cell c : cells) sb.appendCodePoint(c.codePoint());
        return sb.toString();
    }

    private static double[] concat(double[]... parts) {
        int n = 0;
        for (double[] p : parts) n += p.length;
        double[] out = new double[n];
        int at = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
