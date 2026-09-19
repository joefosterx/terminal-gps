package dev.tilemap.view;

import dev.tilemap.core.Canvas;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.RenderException;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import dev.tilemap.viewer.AppState;
import dev.tilemap.viewer.CellRect;
import dev.tilemap.viewer.InspectPanel;
import dev.tilemap.viewer.TileCache;
import dev.tilemap.viewer.TilePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The render loop. Drains all pending events, applies them to {@link AppState}, and renders at most one frame per
 * batch, so key repeat and bursts of arriving tiles coalesce. Rendering reads only the {@link TileCache}; missing
 * tiles are fetched in the background and shown as placeholders until they arrive.
 */
final class Viewer {
    static final long FRAME_BUDGET_NANOS = 16_000_000L;
    static final int SLOW_FRAMES_BEFORE_DEGRADING = 3;

    private final AppState state;
    private final TileCache cache;
    private final Display display;
    private final BlockingQueue<Event> events;
    private final String attribution;
    private long lastRenderNanos;
    private int slowFrames;
    private int mapCols = 1;
    private int mapRows = 1;

    Viewer(AppState state, TileCache cache, Display display, BlockingQueue<Event> events, String attribution) {
        this.state = state;
        this.cache = cache;
        this.display = display;
        this.events = events;
        this.attribution = attribution;
    }

    void run() throws InterruptedException {
        boolean dirty = true;
        while (!state.quit) {
            Event event = dirty ? events.poll() : events.poll(1, TimeUnit.SECONDS);
            if (event == null && !dirty) {
                // Idle: refresh the status bar while fetches are outstanding or failed tiles may be due a retry.
                dirty = cache.pending() > 0 || cache.failed() > 0;
            }
            while (event != null) {
                dirty |= apply(event);
                if (state.quit) return;
                event = events.poll();
            }
            if (dirty) {
                frame();
                dirty = false;
            }
        }
    }

    boolean apply(Event event) {
        return switch (event) {
            case Event.KeyPressed k -> KeyHandler.handle(state, k.key(), mapCols, mapRows);
            case Event.Resized r -> {
                display.invalidate();
                yield true;
            }
            case Event.TileArrived t -> true;
        };
    }

    /** Renders and draws one frame at the display's current size. */
    void frame() {
        int cols = display.cols(), rows = display.rows();
        if (cols < 2 || rows < 2) return;
        mapCols = cols;
        mapRows = rows - 1;
        Viewport vp = state.viewport(mapCols, mapRows);
        List<TileId> visible = Renderer.tilesFor(vp, cache.maxZoom());
        cache.want(visible, TilePlan.prefetch(vp, visible));

        long start = System.nanoTime();
        Canvas map;
        try {
            map = Renderer.render(vp, state.style, state.caps, cache, state.labelsOn());
        } catch (RenderException e) {
            throw new IllegalStateException("the tile cache never fails", e);
        }
        lastRenderNanos = System.nanoTime() - start;
        budget();

        List<CellRect> missing = new ArrayList<>();
        int loaded = 0;
        for (TileId id : visible) {
            if (cache.has(id)) {
                loaded++;
            } else {
                missing.add(TilePlan.rect(vp, id));
            }
        }
        if (state.copy != AppState.Copy.NONE) {
            display.copy(state.copy == AppState.Copy.PLAIN ? map.toPlain() : map.toAnsi(state.caps));
            state.message = "copied " + mapCols + "x" + mapRows + (state.copy == AppState.Copy.PLAIN ? " text" : " ANSI");
            state.copy = AppState.Copy.NONE;
        }

        FrameComposer.Overlay overlay = FrameComposer.Overlay.NONE;
        int[] cursor = null;
        if (state.help) {
            overlay = new FrameComposer.Overlay(KeyHandler.HELP, FrameComposer.Placement.CENTER);
        } else if (state.inspect) {
            state.cursorCol = Math.min(state.cursorCol, mapCols - 1);
            state.cursorRow = Math.min(state.cursorRow, mapRows - 1);
            cursor = new int[] {state.cursorCol, state.cursorRow};
            FrameComposer.Placement side = state.cursorCol < mapCols / 2 ? FrameComposer.Placement.RIGHT : FrameComposer.Placement.LEFT;
            overlay = new FrameComposer.Overlay(InspectPanel.lines(state, cache, vp, mapCols, mapRows, "Esc closes",
                    Math.max(3, mapRows - 2), Math.max(20, mapCols / 2 - 4)), side);
        }
        display.draw(FrameComposer.compose(map, cols, rows, missing, statusLeft(visible.size(), loaded), statusRight(), overlay,
                cursor, state.style.effectiveCharset(state.caps.charset())), cols, rows, state.caps.color());
    }

    /** Drops labels after several frames over budget; zooming, go-to or pressing n brings them back. */
    private void budget() {
        if (lastRenderNanos > FRAME_BUDGET_NANOS && state.labelsOn()) {
            if (++slowFrames >= SLOW_FRAMES_BEFORE_DEGRADING) {
                state.labelsPausedForSpeed = true;
                slowFrames = 0;
            }
        } else {
            slowFrames = 0;
        }
    }

    private String statusLeft(int visible, int loaded) {
        if (state.prompt != null) return " Go to lon,lat[,zoom]: " + state.prompt + "_";
        // Most important first: the bar is truncated from the right on narrow terminals.
        LonLat c = state.center();
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, " %.5f, %.5f  z%.2f", c.lat(), c.lon(), state.zoom));
        if (!state.message.isEmpty()) sb.append("  ").append(state.message);
        if (state.labelsPausedForSpeed) sb.append("  labels paused (slow)");
        int failed = cache.failed();
        if (failed > 0) sb.append("  ").append(failed).append(" failed: ").append(cache.lastError());
        sb.append(String.format(Locale.ROOT, "  tiles %d/%d  queue %d  %d ms", loaded, visible, cache.pending(), lastRenderNanos / 1_000_000));
        return sb.toString();
    }

    private String statusRight() {
        String right = state.caps.charset().name().toLowerCase(Locale.ROOT) + "/" + KeyHandler.name(state.caps.color()) + "  ? help ";
        return attribution.isEmpty() ? right : attribution + "  " + right;
    }

}
