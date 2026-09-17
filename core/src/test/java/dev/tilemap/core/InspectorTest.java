package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectorTest {
    private static final Viewport VIEW = new Viewport(Fixtures.TOWN_CENTER, 15, 100, 40);

    /** The cell containing a lon/lat, via the same projection the renderer uses. */
    private static int[] cellOf(double lon, double lat) {
        ViewTransform t = ViewTransform.of(VIEW);
        return new int[] {(int) Math.floor(t.dotX(Projection.mercX(lon)) / 2), (int) Math.floor(t.dotY(Projection.mercY(lat)) / 4)};
    }

    private static List<Inspector.Hit> at(double lon, double lat) throws RenderException {
        int[] cell = cellOf(lon, lat);
        return Inspector.at(VIEW, Styles.defaultStyle(), Fixtures.town(), cell[0], cell[1]);
    }

    @Test
    void roadsOverParksTopmostFirst() throws RenderException {
        // Main Street (secondary) at lon 10.000 runs past Central Park's west edge; inside the park, a cell away, is grass.
        // The street lies exactly on a cell boundary, so tile-unit rounding may put it in the neighboring cell.
        int[] cell = cellOf(10.0000, 49.9990);
        List<Inspector.Hit> hits = List.of();
        for (int dc = -1; dc <= 1 && hits.isEmpty(); dc++) {
            hits = Inspector.at(VIEW, Styles.defaultStyle(), Fixtures.town(), cell[0] + dc, cell[1]);
        }
        assertEquals("road-major", hits.getFirst().layer());
        assertEquals("Main Street", hits.getFirst().tags().get("name"));

        List<Inspector.Hit> park = at(10.0008, 50.0008);
        assertTrue(park.stream().anyMatch(h -> h.layer().equals("park") && "Central Park".equals(h.tags().get("name"))), park.toString());
        assertTrue(park.stream().noneMatch(h -> h.layer().equals("water")));
    }

    @Test
    void holesAreNotInsideAndThePondIs() throws RenderException {
        List<Inspector.Hit> pond = at(10.0020, 50.0017);
        assertEquals("water", pond.getFirst().layer());
        assertEquals("Mill Pond", pond.getFirst().tags().get("name"));
        assertTrue(pond.stream().noneMatch(h -> h.layer().equals("park")), "the pond sits in the park's hole");
    }

    @Test
    void labelOnlyFeaturesAndDedupAcrossTiles() throws RenderException {
        List<Inspector.Hit> place = at(10.0010, 49.9990);
        assertTrue(place.stream().anyMatch(h -> h.layer().equals("label:place-major") && "Millbrook".equals(h.tags().get("name"))), place.toString());
        assertEquals(place.size(), place.stream().distinct().count());
    }

    @Test
    void emptyLandHasNothing() throws RenderException {
        assertFalse(at(10.0085, 50.0060).stream().anyMatch(h -> !h.layer().startsWith("label:")));
    }

    @Test
    void geometryTests() {
        double[] square = {0, 0, 10, 0, 10, 10, 0, 10};
        double[] hole = {3, 3, 7, 3, 7, 7, 3, 7};
        assertTrue(Inspector.containsPoint(List.of(square), 5, 5));
        assertFalse(Inspector.containsPoint(List.of(square, hole), 5, 5));
        assertTrue(Inspector.covers(new Geometry.Line(List.of(new double[] {-5, 1, 5, 1})), 0, 0, 2, 4));
        assertFalse(Inspector.covers(new Geometry.Point(new double[] {2, 1}), 0, 0, 2, 4));
        assertEquals(Map.of(), new Inspector.Hit("x", "y", Map.of()).tags());
    }
}
