package dev.tilemap.view;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Style;
import dev.tilemap.core.Viewport;

/**
 * The viewer's only mutable model: where the map is, how it is drawn, and which overlay is open. The center is kept
 * in normalized Mercator units so panning by cells is exact at every latitude. Touched only by the render thread.
 */
final class AppState {
    /** Deeper than any public vector tileset; beyond this, overzoom just magnifies. */
    static final double MAX_ZOOM = 22;
    private static final double MAX_MERC_Y = Projection.mercY(-Projection.MAX_LAT);

    double centerX;
    double centerY;
    double zoom;
    String styleName;
    Style style;
    Capabilities caps;
    boolean labels = true;
    /** Labels switched off automatically because frames were over budget; cleared by zooming or pressing n. */
    boolean labelsPausedForSpeed;
    boolean help;
    /** Text typed at the go-to prompt, or null when the prompt is closed. */
    StringBuilder prompt;
    String message = "";
    boolean quit;

    AppState(LonLat center, double zoom, String styleName, Style style, Capabilities caps) {
        this.centerX = Projection.mercX(center.lon());
        this.centerY = Projection.mercY(center.lat());
        this.zoom = clampZoom(zoom);
        this.styleName = styleName;
        this.style = style;
        this.caps = caps;
    }

    LonLat center() {
        return new LonLat(Projection.lon(centerX), Projection.lat(centerY));
    }

    Viewport viewport(int cols, int rows) {
        return new Viewport(center(), zoom, Math.max(1, cols), Math.max(1, rows));
    }

    boolean labelsOn() {
        return labels && !labelsPausedForSpeed;
    }

    /** Moves the center by a number of cells, matching the renderer's dot scaling. */
    void panCells(double cols, double rows) {
        double world = Projection.worldDots(zoom);
        centerX = Math.max(0, Math.min(1, centerX + cols * 2 / world));
        centerY = Math.max(0, Math.min(MAX_MERC_Y, centerY + rows * 4 / (world * 2 * Viewport.DEFAULT_CELL_ASPECT)));
    }

    void zoomBy(double delta) {
        zoom = clampZoom(zoom + delta);
        labelsPausedForSpeed = false;
    }

    void goTo(LonLat center, Double newZoom) {
        centerX = Projection.mercX(center.lon());
        centerY = Projection.mercY(center.lat());
        if (newZoom != null) zoom = clampZoom(newZoom);
        labelsPausedForSpeed = false;
    }

    private static double clampZoom(double z) {
        return Math.max(0, Math.min(MAX_ZOOM, z));
    }
}
