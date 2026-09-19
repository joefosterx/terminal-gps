package dev.tilemap.android;

import dev.tilemap.viewer.AppState;

/**
 * Applies gestures to {@link AppState}: the app's counterpart of the terminal viewer's KeyHandler. Pure, so it is
 * unit-tested on the JVM; the caller holds the state's lock.
 */
final class TouchHandler implements GridGestures {
    private final AppState state;
    private int mapCols = 1;
    private int mapRows = 1;

    TouchHandler(AppState state) {
        this.state = state;
    }

    void setSize(int cols, int rows) {
        mapCols = Math.max(1, cols);
        mapRows = Math.max(1, rows);
    }

    @Override
    public boolean drag(double dCols, double dRows) {
        // The map follows the finger, so the center moves the other way.
        state.panCells(-dCols, -dRows);
        return true;
    }

    @Override
    public boolean pinch(double col, double row, double zoomDelta) {
        if (zoomDelta == 0) return false;
        state.zoomAt(col - 0.5, row - 0.5, zoomDelta, mapCols, mapRows);
        return true;
    }

    @Override
    public boolean doubleTap(double col, double row) {
        state.zoomAt(col - 0.5, row - 0.5, 1, mapCols, mapRows);
        return true;
    }

    @Override
    public boolean twoFingerTap() {
        state.zoomBy(-1);
        return true;
    }

    @Override
    public boolean longPress(int col, int row) {
        state.inspect = true;
        moveCursor(col, row);
        return true;
    }

    @Override
    public boolean tap(int col, int row) {
        if (!state.inspect) return false;
        moveCursor(col, row);
        return true;
    }

    /** The back gesture: closes inspect mode. Returns false when there was nothing to close. */
    boolean back() {
        if (!state.inspect) return false;
        state.inspect = false;
        return true;
    }

    private void moveCursor(int col, int row) {
        state.cursorCol = Math.max(0, Math.min(mapCols - 1, col));
        state.cursorRow = Math.max(0, Math.min(mapRows - 1, row));
    }
}
