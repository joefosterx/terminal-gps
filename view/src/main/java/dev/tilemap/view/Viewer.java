package dev.tilemap.view;

import dev.tilemap.core.Canvas;
import dev.tilemap.core.Inspector;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Projection;
import dev.tilemap.core.RenderException;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        cache.want(visible, prefetch(vp, visible));

        long start = System.nanoTime();
        Canvas map;
        try {
            map = Renderer.render(vp, state.style, state.caps, cache, state.labelsOn());
        } catch (RenderException e) {
            throw new IllegalStateException("the tile cache never fails", e);
        }
        lastRenderNanos = System.nanoTime() - start;
        budget();

        List<FrameComposer.Rect> missing = new ArrayList<>();
        int loaded = 0;
        for (TileId id : visible) {
            if (cache.has(id)) {
                loaded++;
            } else {
                missing.add(tileRect(vp, id));
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
            overlay = new FrameComposer.Overlay(inspectLines(vp, Math.max(3, mapRows - 2), Math.max(20, mapCols / 2 - 4)), side);
        }
        display.draw(FrameComposer.compose(map, cols, rows, missing, statusLeft(visible.size(), loaded), statusRight(), overlay,
                cursor, state.style.effectiveCharset(state.caps.charset())), cols, rows, state.caps.color());
    }

    /** The inspect panel: the cursor position, then each hit's layer and tags (translated names left out). */
    private List<String> inspectLines(Viewport vp, int maxLines, int width) {
        LonLat at = state.at(state.cursorCol, state.cursorRow, mapCols, mapRows);
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%.5f, %.5f   Esc closes", at.lat(), at.lon()));
        List<Inspector.Hit> hits;
        try {
            hits = Inspector.at(vp, state.style, cache, state.cursorCol, state.cursorRow);
        } catch (RenderException e) {
            throw new IllegalStateException("the tile cache never fails", e);
        }
        if (hits.isEmpty()) lines.add("(no features here)");
        for (Inspector.Hit hit : hits) {
            lines.add("");
            lines.add(hit.layer() + "  (" + hit.source() + ")");
            hit.tags().entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("name:") && !e.getKey().startsWith("name_"))
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> lines.add("  " + e.getKey() + " = " + e.getValue()));
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (out.size() == maxLines) {
                out.set(maxLines - 1, "…");
                break;
            }
            out.add(line.codePointCount(0, line.length()) > width
                    ? new String(line.codePoints().limit(width - 1).toArray(), 0, width - 1) + "…"
                    : line);
        }
        return out;
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

    /** The ring of tiles around the view and the parents of the visible tiles, so small pans and zoom-outs hit cache. */
    static List<TileId> prefetch(Viewport vp, List<TileId> visible) {
        Set<TileId> out = new LinkedHashSet<>();
        if (!visible.isEmpty()) {
            TileId first = visible.getFirst(), last = visible.getLast();
            int z = first.z(), n = 1 << z;
            for (int y = first.y() - 1; y <= last.y() + 1; y++) {
                for (int x = first.x() - 1; x <= last.x() + 1; x++) {
                    if (x >= 0 && y >= 0 && x < n && y < n) out.add(new TileId(z, x, y));
                }
            }
            if (z > 0) {
                for (TileId id : visible) out.add(new TileId(z - 1, id.x() / 2, id.y() / 2));
            }
        }
        visible.forEach(out::remove);
        return new ArrayList<>(out);
    }

    /** The map cells a tile covers, using the renderer's projection (a cell is 2 × 4 dots). */
    static FrameComposer.Rect tileRect(Viewport vp, TileId id) {
        double world = Projection.worldDots(vp.zoom());
        double cx = Projection.mercX(vp.center().lon()), cy = Projection.mercY(vp.center().lat());
        double n = 1 << id.z();
        double x0 = ((id.x() / n - cx) * world + vp.cols()) / 2;
        double x1 = (((id.x() + 1) / n - cx) * world + vp.cols()) / 2;
        double scaleY = world * 2 * vp.cellAspect();
        double y0 = ((id.y() / n - cy) * scaleY + vp.rows() * 2) / 4;
        double y1 = (((id.y() + 1) / n - cy) * scaleY + vp.rows() * 2) / 4;
        return new FrameComposer.Rect((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
    }
}
