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
    void reportsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader("{}")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader("not json")));
        assertThrows(IllegalArgumentException.class, () -> Styles.fromJson(new StringReader(
                "{\"layers\": [{\"id\": \"x\", \"source\": \"s\", \"paint\": {\"kind\": \"blob\", \"fg\": \"#000000\"}}]}")));
    }
}
