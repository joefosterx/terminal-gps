package dev.tilemap.viewer;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Style;
import dev.tilemap.core.Viewport;

/**
 * A viewer's only mutable model: where the map is, how it is drawn, and which overlay is open. The center is kept
 * in normalized Mercator units so panning by cells is exact at every latitude. Not thread-safe: the terminal viewer
 * touches it from the render thread only; the Android app guards it with a lock.
 */
public final class AppState {
    /** Deeper than any public vector tileset; beyond this, overzoom just magnifies. */
    public static final double MAX_ZOOM = 22;
    private static final double MAX_MERC_Y = Projection.mercY(-Projection.MAX_LAT);

    public enum Copy { NONE, PLAIN, ANSI }

    public double centerX;
    public double centerY;
    public double zoom;
    public String styleName;
    public Style style;
    public Capabilities caps;
    public boolean labels = true;
    /** Labels switched off automatically because frames were over budget; cleared by zooming or pressing n. */
    public boolean labelsPausedForSpeed;
    public boolean help;
    /** Text typed at the go-to prompt, or null when the prompt is closed. */
    public StringBuilder prompt;
    /** Inspect mode shows the features under a movable cursor. */
    public boolean inspect;
    public int cursorCol;
    public int cursorRow;
    /** Last mouse position of a drag in progress, or -1. */
    public int dragCol = -1;
    public int dragRow = -1;
    /** A copy of the view requested by a key press, done by the next frame. */
    public Copy copy = Copy.NONE;
    public String message = "";
    public boolean quit;

    public AppState(LonLat center, double zoom, String styleName, Style style, Capabilities caps) {
        this.centerX = Projection.mercX(center.lon());
        this.centerY = Projection.mercY(center.lat());
        this.zoom = clampZoom(zoom);
        this.styleName = styleName;
        this.style = style;
        this.caps = caps;
    }

    public LonLat center() {
        return new LonLat(Projection.lon(centerX), Projection.lat(centerY));
    }

    public Viewport viewport(int cols, int rows) {
        return new Viewport(center(), zoom, Math.max(1, cols), Math.max(1, rows));
    }

    public boolean labelsOn() {
        return labels && !labelsPausedForSpeed;
    }

    /** Moves the center by a number of cells, matching the renderer's dot scaling. */
    public void panCells(double cols, double rows) {
        double world = Projection.worldDots(zoom);
        centerX = clampX(centerX + cols * 2 / world);
        centerY = clampY(centerY + rows * 4 / (world * 2 * Viewport.DEFAULT_CELL_ASPECT));
    }

    public void zoomBy(double delta) {
        zoom = clampZoom(zoom + delta);
        labelsPausedForSpeed = false;
    }

    /** Zooms while keeping the point under map cell ({@code col}, {@code row}) where it is on screen. */
    public void zoomAt(double col, double row, double delta, int mapCols, int mapRows) {
        double scaleY = 2 * Viewport.DEFAULT_CELL_ASPECT;
        double dx = (col + 0.5) * 2 - mapCols, dy = (row + 0.5) * 4 - mapRows * 2;
        double world = Projection.worldDots(zoom);
        double mx = centerX + dx / world, my = centerY + dy / (world * scaleY);
        zoomBy(delta);
        double after = Projection.worldDots(zoom);
        centerX = clampX(mx - dx / after);
        centerY = clampY(my - dy / (after * scaleY));
    }

    public void goTo(LonLat center, Double newZoom) {
        centerX = Projection.mercX(center.lon());
        centerY = Projection.mercY(center.lat());
        if (newZoom != null) zoom = clampZoom(newZoom);
        labelsPausedForSpeed = false;
    }

    /** The geographic position at the center of map cell ({@code col}, {@code row}). */
    public LonLat at(int col, int row, int mapCols, int mapRows) {
        double world = Projection.worldDots(zoom);
        double x = centerX + ((col + 0.5) * 2 - mapCols) / world;
        double y = centerY + ((row + 0.5) * 4 - mapRows * 2) / (world * 2 * Viewport.DEFAULT_CELL_ASPECT);
        return new LonLat(Projection.lon(clampX(x)), Projection.lat(clampY(y)));
    }

    private static double clampX(double x) {
        return Math.max(0, Math.min(1, x));
    }

    private static double clampY(double y) {
        return Math.max(0, Math.min(MAX_MERC_Y, y));
    }

    private static double clampZoom(double z) {
        return Math.max(0, Math.min(MAX_ZOOM, z));
    }
}
