package dev.tilemap.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Styles;
import dev.tilemap.viewer.AppState;
import org.junit.jupiter.api.Test;

/** Mirrors KeyHandlerTest in :view, gesture for key. */
class TouchHandlerTest {
    private final AppState s = new AppState(new LonLat(10, 50), 12, "default", Styles.defaultStyle(), Capabilities.DEFAULT);
    private final TouchHandler h = new TouchHandler(s);

    TouchHandlerTest() {
        h.setSize(40, 20);
    }

    @Test
    void dragMovesTheMapWithTheFinger() {
        double lon = s.center().lon(), lat = s.center().lat();
        assertTrue(h.drag(4, 0));
        assertTrue(s.center().lon() < lon, "dragging right shows what was to the west");
        assertTrue(h.drag(0, -2));
        assertTrue(s.center().lat() < lat, "dragging up shows what was to the south");
        assertTrue(h.drag(-4, 2));
        assertEquals(lon, s.center().lon(), 1e-9);
        assertEquals(lat, s.center().lat(), 1e-9);
    }

    @Test
    void pinchZoomsAroundTheFocalPointAndDoubleTapZoomsIn() {
        LonLat corner = s.at(0, 0, 40, 20);
        assertTrue(h.pinch(0.5, 0.5, 1));
        assertEquals(13, s.zoom, 1e-9);
        LonLat after = s.at(0, 0, 40, 20);
        assertEquals(corner.lon(), after.lon(), 1e-6);
        assertEquals(corner.lat(), after.lat(), 1e-6);
        assertFalse(h.pinch(20, 10, 0), "a pinch that did not scale needs no redraw");

        assertTrue(h.doubleTap(20.5, 10.5));
        assertEquals(14, s.zoom, 1e-9);
        assertTrue(h.twoFingerTap());
        assertEquals(13, s.zoom, 1e-9);
    }

    @Test
    void zoomIsClampedLikeTheTerminal() {
        assertTrue(h.pinch(20, 10, 100));
        assertEquals(AppState.MAX_ZOOM, s.zoom, 1e-9);
        assertTrue(h.pinch(20, 10, -100));
        assertEquals(0, s.zoom, 1e-9);
    }

    @Test
    void longPressInspectsTapMovesTheCursorAndBackCloses() {
        assertFalse(h.tap(3, 3), "taps outside inspect mode do nothing");
        assertFalse(h.back());
        assertTrue(h.longPress(5, 6));
        assertTrue(s.inspect);
        assertEquals(5, s.cursorCol);
        assertEquals(6, s.cursorRow);
        assertTrue(h.tap(100, -1));
        assertEquals(39, s.cursorCol);
        assertEquals(0, s.cursorRow);
        assertTrue(h.back());
        assertFalse(s.inspect);
    }
}
