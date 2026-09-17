package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StylesTest {
    @Test
    void defaultStyleLoads() {
        List<String> ids = Styles.defaultStyle().layers().stream().map(StyleLayer::id).toList();
        assertEquals(List.of("forest", "park", "water", "waterway", "building", "rail", "road-minor", "road-major", "poi", "station"), ids);
    }

    @Test
    void appliesDefaults() {
        Style s = Styles.fromJson(new StringReader("""
                {"layers": [{"id": "w", "source": "water", "paint": {"kind": "fill", "fg": "#112233"}}]}
                """));
        StyleLayer l = s.layers().get(0);
        assertEquals(Map.of(), l.filter());
        assertEquals(0, l.minZoom());
        assertTrue(l.visibleAt(Projection.MAX_ZOOM));
        assertEquals(new Rgb(0x11, 0x22, 0x33), l.paint().fg());
        assertNull(l.paint().bg());
        assertEquals(Weight.LIGHT, l.paint().weight());
        assertEquals(GlyphStrategy.BRAILLE, l.paint().strategy());
    }

    @Test
    void parsesFiltersAndEnumSpellings() {
        Style s = Styles.fromJson(new StringReader("""
                {"layers": [{"id": "r", "source": "transportation", "minZoom": 12, "maxZoom": 16,
                  "filter": {"highway": ["primary", "secondary"], "name": "*"},
                  "paint": {"kind": "LINE", "fg": "#ffffff", "weight": "heavy", "strategy": "box-line"}}]}
                """));
        StyleLayer l = s.layers().get(0);
        assertEquals(GlyphStrategy.BOX_LINE, l.paint().strategy());
        assertTrue(l.visibleAt(12));
        assertFalse(l.visibleAt(16));

        Geometry line = new Geometry.Line(List.of(new double[] {0, 0, 1, 1}));
        assertTrue(l.matches(new Feature(line, Map.of("highway", "primary", "name", "A1"))));
        assertFalse(l.matches(new Feature(line, Map.of("highway", "primary"))));
        assertFalse(l.matches(new Feature(line, Map.of("highway", "residential", "name", "B"))));
    }

    @Test
    void parsesMarkers() {
        Style s = Styles.fromJson(new StringReader("""
                {"layers": [{"id": "p", "source": "poi", "paint": {"kind": "point", "fg": "#000000", "strategy": "marker", "marker": "⌂"}}]}
                """));
        assertEquals('⌂', s.layers().get(0).paint().marker());
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader(
                "{\"layers\": [{\"id\": \"p\", \"source\": \"s\", \"paint\": {\"kind\": \"point\", \"fg\": \"#000000\", \"marker\": \"ab\"}}]}")));
    }

    @Test
    void presetsLoadAndExtendDefault() {
        for (String name : Styles.PRESETS) {
            Style s = Styles.preset(name).orElseThrow();
            assertEquals(Styles.defaultStyle().layers().size(), s.layers().size(), name);
            assertEquals(Styles.defaultStyle().labels(), s.labels(), name);
        }
        Style vt220 = Styles.preset("VT220").orElseThrow();
        assertEquals(Capabilities.Charset.ASCII, vt220.maxCharset());
        assertTrue(vt220.monochrome());
        assertTrue(Styles.preset("mono").orElseThrow().monochrome());
        assertTrue(Styles.preset("nope").isEmpty());
    }

    @Test
    void extendsWithPaintOverrides() {
        Style s = Styles.fromJson(new StringReader("""
                {"extends": "default", "paint": {"water": {"fg": "#010203", "bg": null, "pattern": "⠪"}}}
                """));
        StyleLayer water = s.layers().stream().filter(l -> l.id().equals("water")).findFirst().orElseThrow();
        StyleLayer original = Styles.defaultStyle().layers().stream().filter(l -> l.id().equals("water")).findFirst().orElseThrow();
        assertEquals(new Rgb(1, 2, 3), water.paint().fg());
        assertNull(water.paint().bg());
        assertEquals(original.paint().kind(), water.paint().kind());
        assertTrue(water.protect());
        // ⠪ raises dots 2, 4 and 6: (row 1, col 0), (row 0, col 1), (row 2, col 1).
        assertEquals((1 << 2) | (1 << 1) | (1 << 5), water.paint().pattern());

        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader(
                "{\"extends\": \"default\", \"paint\": {\"lava\": {\"fg\": \"#ff0000\"}}}")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader("{\"extends\": \"gothic\"}")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader(
                "{\"extends\": \"default\", \"paint\": {\"water\": {\"pattern\": \"x\"}}}")));
    }

    @Test
    void reportsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader("{}")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader("not json")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader(
                "{\"layers\": [{\"id\": \"x\", \"source\": \"s\", \"paint\": {\"kind\": \"blob\", \"fg\": \"#000000\"}}]}")));
    }
}
