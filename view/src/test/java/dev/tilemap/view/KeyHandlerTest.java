package dev.tilemap.view;

import dev.tilemap.viewer.AppState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Styles;
import org.junit.jupiter.api.Test;

class KeyHandlerTest {
    private final AppState s = new AppState(new LonLat(10, 50), 14, "default", Styles.defaultStyle(),
            new Capabilities(Charset.BRAILLE, ColorDepth.TRUE));

    private boolean press(char ch) {
        return KeyHandler.handle(s, Key.of(ch), 80, 30);
    }

    private boolean press(Key.Type type) {
        return KeyHandler.handle(s, Key.of(type), 80, 30);
    }

    @Test
    void panOneCellMovesTwoDotsOrFourDots() {
        double x = s.centerX, y = s.centerY, world = Projection.worldDots(14);
        assertTrue(press('l'));
        assertEquals(x + 2 / world, s.centerX, 1e-15);
        press(Key.Type.DOWN);
        assertEquals(y + 4 / world, s.centerY, 1e-15);
        press('h');
        press('k');
        assertEquals(x, s.centerX, 1e-15);
        assertEquals(y, s.centerY, 1e-15);
    }

    @Test
    void shiftPansHalfAScreen() {
        double x = s.centerX, world = Projection.worldDots(14);
        press('L');
        assertEquals(x + 40 * 2 / world, s.centerX, 1e-15);
        press(Key.Type.SHIFT_LEFT);
        assertEquals(x, s.centerX, 1e-15);
    }

    @Test
    void zoomSteps() {
        press('+');
        press(']');
        assertEquals(15.25, s.zoom);
        press('_');
        press('[');
        press('-');
        assertEquals(13, s.zoom);
        for (int i = 0; i < 30; i++) press('=');
        assertEquals(AppState.MAX_ZOOM, s.zoom);
    }

    @Test
    void goToPrompt() {
        press('g');
        for (char c : "2.35,48.86,16".toCharArray()) press(c);
        press(Key.Type.BACKSPACE);
        press('5');
        press(Key.Type.ENTER);
        assertNull(s.prompt);
        assertEquals(2.35, s.center().lon(), 1e-9);
        assertEquals(48.86, s.center().lat(), 1e-9);
        assertEquals(15, s.zoom);

        press('g');
        press('x');
        press(Key.Type.ENTER);
        assertTrue(s.message.startsWith("go to:"));

        press('g');
        press('q');       // typed into the prompt, not a quit
        press(Key.Type.ESCAPE);
        assertFalse(s.quit);
        assertNull(s.prompt);
    }

    @Test
    void colorCyclesAndLabelsToggle() {
        press('c');
        assertEquals(ColorDepth.C256, s.caps.color());
        press('c');
        press('c');
        press('c');
        assertEquals(ColorDepth.TRUE, s.caps.color());

        press('n');
        assertFalse(s.labelsOn());
        press('n');
        assertTrue(s.labelsOn());
        s.labelsPausedForSpeed = true;
        press('n');
        assertTrue(s.labelsOn(), "n resumes labels paused for speed");
    }

    @Test
    void helpAndQuit() {
        press('?');
        assertTrue(s.help);
        press('j');                     // any key closes help without acting
        assertFalse(s.help);
        press('q');
        assertTrue(s.quit);

        AppState other = new AppState(new LonLat(0, 0), 3, "default", Styles.defaultStyle(), Capabilities.DEFAULT);
        KeyHandler.handle(other, Key.of(Key.Type.INTERRUPT), 80, 30);
        assertTrue(other.quit);
    }

    @Test
    void dragMovesTheMapWithThePointer() {
        double x = s.centerX, y = s.centerY, world = Projection.worldDots(14);
        assertFalse(KeyHandler.handle(s, Key.mouse(Key.Type.MOUSE_DOWN, 40, 10), 80, 30));
        assertTrue(KeyHandler.handle(s, Key.mouse(Key.Type.MOUSE_DRAG, 43, 12), 80, 30));
        assertEquals(x - 3 * 2 / world, s.centerX, 1e-15);
        assertEquals(y - 2 * 4 / world, s.centerY, 1e-15);
        KeyHandler.handle(s, Key.mouse(Key.Type.MOUSE_UP, 43, 12), 80, 30);
        assertFalse(KeyHandler.handle(s, Key.mouse(Key.Type.MOUSE_DRAG, 50, 12), 80, 30), "no drag after release");
    }

    @Test
    void wheelZoomKeepsThePointUnderThePointer() {
        LonLat before = s.at(70, 5, 80, 30);
        assertTrue(KeyHandler.handle(s, Key.mouse(Key.Type.WHEEL_UP, 70, 5), 80, 30));
        assertEquals(14.5, s.zoom);
        LonLat after = s.at(70, 5, 80, 30);
        assertEquals(before.lon(), after.lon(), 1e-9);
        assertEquals(before.lat(), after.lat(), 1e-9);
        KeyHandler.handle(s, Key.mouse(Key.Type.WHEEL_DOWN, 70, 5), 80, 30);
        assertEquals(14, s.zoom);
        assertFalse(KeyHandler.handle(s, Key.mouse(Key.Type.WHEEL_UP, 70, 30), 80, 30), "the status bar is not map");
    }

    @Test
    void inspectModeMovesACursorInsteadOfTheMap() {
        double x = s.centerX;
        press('i');
        assertTrue(s.inspect);
        assertEquals(40, s.cursorCol);
        assertEquals(15, s.cursorRow);
        press('l');
        press('L');
        press(Key.Type.UP);
        assertEquals(49, s.cursorCol);
        assertEquals(14, s.cursorRow);
        assertEquals(x, s.centerX, "the map did not move");
        KeyHandler.handle(s, Key.mouse(Key.Type.MOUSE_DOWN, 3, 4), 80, 30);
        assertEquals(3, s.cursorCol);
        for (int i = 0; i < 10; i++) press('h');
        assertEquals(0, s.cursorCol, "clamped to the map");
        press('+');
        assertEquals(15, s.zoom, "zoom still works while inspecting");
        press(Key.Type.ESCAPE);
        assertFalse(s.inspect);
    }

    @Test
    void copyRequests() {
        press('y');
        assertEquals(AppState.Copy.PLAIN, s.copy);
        press('Y');
        assertEquals(AppState.Copy.ANSI, s.copy);
    }

    @Test
    void presetsCycle() {
        press('s');
        assertEquals("dark", s.styleName);
        for (int i = 0; i < 4; i++) press('s');
        assertEquals("default", s.styleName);
    }

    @Test
    void panningClampsAtTheWorldEdge() {
        AppState edge = new AppState(new LonLat(-180, 85), 0, "default", Styles.defaultStyle(), Capabilities.DEFAULT);
        for (int i = 0; i < 50; i++) KeyHandler.handle(edge, Key.of(Key.Type.SHIFT_UP), 80, 30);
        for (int i = 0; i < 50; i++) KeyHandler.handle(edge, Key.of(Key.Type.SHIFT_LEFT), 80, 30);
        assertEquals(0, edge.centerX);
        assertEquals(0, edge.centerY);
    }
}
