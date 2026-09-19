package dev.tilemap.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;
import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.RenderException;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Style;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import dev.tilemap.lib.MapRequestJson;
import dev.tilemap.viewer.AppState;
import dev.tilemap.viewer.CellRect;
import dev.tilemap.viewer.InspectPanel;
import dev.tilemap.viewer.Placeholders;
import dev.tilemap.viewer.PositionOverlay;
import dev.tilemap.viewer.TileCache;
import dev.tilemap.viewer.TilePlan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns what must outlive the Activity: the {@link AppState}, the {@link TileCache} with its source, the fetch pool
 * and the render thread. Gestures mutate the state on the main thread under its lock; rendering snapshots it on the
 * render thread and posts a {@link Frame}. Dirty marks coalesce so a fast drag renders at most once per render.
 */
public final class MapViewModel extends AndroidViewModel {
    /** One rendered screen. {@code cursorCol} is -1 when not inspecting; {@code inspect} is then null. */
    public record Frame(Cell[] cells, int cols, int rows, ColorDepth depth, boolean darkBackground, String status,
                        List<String> inspect, int cursorCol, int cursorRow) {}

    /** Looser than the terminal's 16 ms: a phone JIT starts cold, and drags coalesce to one render per render anyway. */
    static final long FRAME_BUDGET_NANOS = 40_000_000L;
    static final int SLOW_FRAMES_BEFORE_DEGRADING = 3;
    private static final int FETCH_THREADS = 8;

    private final AppState state;
    private final TouchHandler touch;
    private final ExecutorService fetchers = Executors.newFixedThreadPool(FETCH_THREADS, r -> {
        Thread t = new Thread(r, "tilemap-fetch");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(r -> new Thread(r, "tilemap-render"));
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final MutableLiveData<Frame> frames = new MutableLiveData<>();
    private final MutableLiveData<String> messages = new MutableLiveData<>();
    private final MutableLiveData<Float> cellWidthDp = new MutableLiveData<>();
    private final SharedPreferences prefs;
    private AppPrefs settings;
    private TileCache cache;
    private PositionOverlay overlay;
    private DeferredSource upstream;
    /** The user asked for their position; the marker shows once a fix arrives. */
    private volatile boolean locating;
    /** Keep the view centered on the position until the user pans. */
    private volatile boolean following;
    private Style markedBase;
    private Style marked;
    private volatile String attribution = "";
    private volatile int cols;
    private volatile int rows;
    private volatile boolean paused = true;
    /** A finger is down or a fling is running: frames skip the label pass and the settle frame brings it back. */
    private volatile boolean interacting;
    private volatile Canvas lastCanvas;
    private long lastRenderNanos;
    private int slowFrames;

    public MapViewModel(Application app) {
        super(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        settings = AppPrefs.load(prefs);
        double[] last = AppPrefs.lastView(prefs);
        LonLat center = last != null ? new LonLat(last[0], last[1]) : new LonLat(0, 20);
        double zoom = last != null ? last[2] : 2;
        state = new AppState(center, zoom, settings.style(), style(settings.style()), caps(settings));
        state.labels = settings.labels();
        touch = new TouchHandler(state);
        openSource();
        cellWidthDp.setValue(settings.cellWidthDp());
    }

    LiveData<Frame> frames() {
        return frames;
    }

    /** One-shot notices for a toast: copy done, go-to errors. */
    LiveData<String> messages() {
        return messages;
    }

    LiveData<Float> cellWidthDp() {
        return cellWidthDp;
    }

    String attribution() {
        return attribution;
    }

    Canvas lastCanvas() {
        return lastCanvas;
    }

    Capabilities caps() {
        synchronized (state) {
            return state.caps;
        }
    }

    /** Gestures from the view: applied under the state lock, then a redraw is scheduled. */
    GridGestures gestures() {
        return new GridGestures() {
            @Override
            public boolean drag(double dCols, double dRows) {
                following = false;
                synchronized (state) {
                    return apply(touch.drag(dCols, dRows));
                }
            }

            @Override
            public boolean pinch(double col, double row, double zoomDelta) {
                synchronized (state) {
                    return apply(touch.pinch(col, row, zoomDelta));
                }
            }

            @Override
            public boolean doubleTap(double col, double row) {
                synchronized (state) {
                    return apply(touch.doubleTap(col, row));
                }
            }

            @Override
            public boolean twoFingerTap() {
                synchronized (state) {
                    return apply(touch.twoFingerTap());
                }
            }

            @Override
            public boolean longPress(int col, int row) {
                synchronized (state) {
                    return apply(touch.longPress(col, row));
                }
            }

            @Override
            public boolean tap(int col, int row) {
                synchronized (state) {
                    return apply(touch.tap(col, row));
                }
            }
        };
    }

    private boolean apply(boolean changed) {
        if (changed) markDirty();
        return changed;
    }

    /** The back gesture: closes inspect mode. Returns false when the Activity should handle it. */
    boolean back() {
        synchronized (state) {
            return apply(touch.back());
        }
    }

    void setSize(int cols, int rows) {
        if (cols == this.cols && rows == this.rows) return;
        this.cols = cols;
        this.rows = rows;
        synchronized (state) {
            touch.setSize(cols, rows);
        }
        markDirty();
    }

    void goTo(String text) {
        try {
            String[] parts = text.split(",");
            if (parts.length < 2 || parts.length > 3) throw new NumberFormatException();
            double lon = Double.parseDouble(parts[0].strip());
            double lat = Double.parseDouble(parts[1].strip());
            if (lon < -180 || lon > 180 || lat < -90 || lat > 90) throw new NumberFormatException();
            Double zoom = parts.length == 3 ? Double.parseDouble(parts[2].strip()) : null;
            synchronized (state) {
                state.goTo(new LonLat(lon, lat), zoom);
                state.inspect = false;
            }
            markDirty();
        } catch (NumberFormatException e) {
            messages.setValue(getApplication().getString(R.string.go_to_error));
        }
    }

    boolean locating() {
        return locating;
    }

    /**
     * The my-location button: the first press turns the marker on and follows the position; while following,
     * a press stops following; when not following, a press recenters and follows again.
     */
    void toggleLocation() {
        if (!locating) {
            locating = true;
            following = true;
            if (overlay.position() == null) messages.setValue(getApplication().getString(R.string.location_waiting));
        } else {
            following = !following;
        }
        LonLat at = overlay.position();
        if (following && at != null) center(at);
        markDirty();
    }

    void stopLocating() {
        locating = false;
        following = false;
        overlay.setPosition(null);
        markDirty();
    }

    /** A fix from {@link LocationTracker}; any thread. */
    void setLocation(LonLat at) {
        if (!locating) return;
        overlay.setPosition(at);
        if (following) center(at);
        markDirty();
    }

    private void center(LonLat at) {
        synchronized (state) {
            state.goTo(at, state.zoom < 14 ? 15.0 : null);
        }
    }

    void cycleStyle() {
        String next;
        synchronized (state) {
            int i = Styles.PRESETS.indexOf(state.styleName);
            next = Styles.PRESETS.get((i + 1) % Styles.PRESETS.size());
            state.styleName = next;
            state.style = Styles.preset(next).orElseThrow();
            state.message = "style: " + next;
        }
        prefs.edit().putString("style", next).apply();
        markDirty();
    }

    void toggleLabels() {
        boolean on;
        synchronized (state) {
            state.labels = !(state.labels && !state.labelsPausedForSpeed);
            state.labelsPausedForSpeed = false;
            on = state.labels;
            state.message = on ? "labels on" : "labels off";
        }
        prefs.edit().putBoolean("labels", on).apply();
        markDirty();
    }

    void stopInspect() {
        synchronized (state) {
            state.inspect = false;
        }
        markDirty();
    }

    /** Re-reads preferences after the settings screen; reopens the source only when it changed. */
    void applyPrefs() {
        AppPrefs fresh = AppPrefs.load(prefs);
        boolean reopen = !fresh.sourceIdentity().equals(settings.sourceIdentity());
        settings = fresh;
        synchronized (state) {
            if (!fresh.style().equals(state.styleName)) {
                state.styleName = fresh.style();
                state.style = style(fresh.style());
            }
            state.caps = caps(fresh);
            state.labels = fresh.labels();
            state.labelsPausedForSpeed = false;
        }
        if (reopen) openSource();
        Float width = fresh.cellWidthDp();
        if (!width.equals(cellWidthDp.getValue())) cellWidthDp.setValue(width);
        markDirty();
    }

    void setInteracting(boolean now) {
        interacting = now;
        if (!now) {
            synchronized (state) {
                state.labelsPausedForSpeed = false;
            }
            slowFrames = 0;
        }
        markDirty();
    }

    void resume() {
        paused = false;
        markDirty();
    }

    /** Stops fetching and rendering; the caches stay. */
    void pause() {
        paused = true;
        synchronized (state) {
            AppPrefs.saveLastView(prefs, state.center(), state.zoom);
        }
        TileCache c = cache;
        if (c != null) c.want(List.of(), List.of());
    }

    private void openSource() {
        DeferredSource old = upstream;
        if (old != null) fetchers.execute(() -> close(old));
        AppPrefs s = settings;
        Context app = getApplication();
        attribution = SourceOpener.attribution(app, s);
        upstream = new DeferredSource(() -> SourceOpener.open(app, s, new UrlConnectionFetcher()));
        cache = new TileCache(upstream, s.memoryTiles(), fetchers, id -> markDirty(), System::nanoTime);
        PositionOverlay previous = overlay;
        overlay = new PositionOverlay(cache);
        if (previous != null) overlay.setPosition(previous.position());
    }

    /** The style with the marker rule appended, rebuilt only when the style changes. */
    private Style marked(Style base) {
        if (base != markedBase) {
            markedBase = base;
            marked = PositionOverlay.style(base);
        }
        return marked;
    }

    private static void close(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception ignored) {
            // Replaced anyway.
        }
    }

    void markDirty() {
        dirty.set(true);
        if (paused) return;
        if (scheduled.compareAndSet(false, true)) renderer.execute(this::renderLoop);
    }

    private void renderLoop() {
        try {
            do {
                dirty.set(false);
                if (!paused) frame();
            } while (dirty.get() && !paused);
        } finally {
            scheduled.set(false);
        }
        // An event that landed between the loop's last check and the flag reset would otherwise be lost.
        if (dirty.get() && !paused) markDirty();
    }

    private void frame() {
        int cols = this.cols, rows = this.rows;
        TileCache cache = this.cache;
        PositionOverlay overlay = this.overlay;
        if (cols < 1 || rows < 1 || cache == null) return;
        Viewport vp;
        Style style;
        Capabilities caps;
        boolean labels, inspect;
        String styleName, message;
        int cursorCol, cursorRow;
        synchronized (state) {
            vp = state.viewport(cols, rows);
            style = state.style;
            caps = state.caps;
            labels = state.labelsOn() && !interacting;
            inspect = state.inspect;
            styleName = state.styleName;
            message = state.message;
            state.message = "";
            state.cursorCol = Math.min(state.cursorCol, cols - 1);
            state.cursorRow = Math.min(state.cursorRow, rows - 1);
            cursorCol = state.cursorCol;
            cursorRow = state.cursorRow;
        }
        List<TileId> visible = Renderer.tilesFor(vp, cache.maxZoom());
        cache.want(visible, prefetchAllowed() ? TilePlan.prefetch(vp, visible) : List.of());

        long start = System.nanoTime();
        Canvas map;
        try {
            map = Renderer.render(vp, locating ? marked(style) : style, caps, locating ? overlay : cache, labels);
        } catch (RenderException e) {
            throw new IllegalStateException("the tile cache never fails", e);
        }
        lastRenderNanos = System.nanoTime() - start;
        lastCanvas = map;
        Log.d("tilemap", "render " + cols + "x" + rows + " z" + String.format(Locale.ROOT, "%.2f", vp.zoom()) + " in " + lastRenderNanos / 1_000_000 + " ms, labels " + labels);
        budget();

        List<CellRect> missing = new ArrayList<>();
        int loaded = 0;
        for (TileId id : visible) {
            if (cache.has(id)) loaded++;
            else missing.add(TilePlan.rect(vp, id));
        }
        Cell[] cells = map.cells().clone();
        Placeholders.fill(cells, cols, rows, missing, style.effectiveCharset(caps.charset()));

        List<String> panel = null;
        if (inspect) {
            synchronized (state) {
                panel = InspectPanel.lines(state, locating ? overlay : cache, vp, cols, rows, "", 12, Math.max(20, cols - 2));
            }
        }
        String status = status(vp, cache, loaded, visible.size(), message, styleName);
        frames.postValue(new Frame(cells, cols, rows, caps.color(), !styleName.equals("default"), status, panel,
                inspect ? cursorCol : -1, inspect ? cursorRow : -1));
    }

    private boolean prefetchAllowed() {
        if (settings.prefetchOnMetered()) return true;
        ConnectivityManager cm = getApplication().getSystemService(ConnectivityManager.class);
        return cm == null || !cm.isActiveNetworkMetered();
    }

    /** Drops labels after several frames over budget; zooming, go-to or the labels button brings them back. */
    private void budget() {
        boolean on;
        synchronized (state) {
            on = state.labelsOn();
        }
        if (lastRenderNanos > FRAME_BUDGET_NANOS && on) {
            if (++slowFrames >= SLOW_FRAMES_BEFORE_DEGRADING) {
                synchronized (state) {
                    state.labelsPausedForSpeed = true;
                }
                slowFrames = 0;
            }
        } else {
            slowFrames = 0;
        }
    }

    private String status(Viewport vp, TileCache cache, int loaded, int visible, String message, String styleName) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%.5f, %.5f  z%.2f", vp.center().lat(), vp.center().lon(), vp.zoom()));
        if (!message.isEmpty()) sb.append("  ").append(message);
        boolean pausedLabels;
        synchronized (state) {
            pausedLabels = state.labelsPausedForSpeed;
        }
        if (pausedLabels) sb.append("  labels paused (slow)");
        if (following) sb.append("  \u25ce following");
        int failed = cache.failed();
        if (failed > 0) sb.append("  ").append(failed).append(" failed: ").append(cache.lastError());
        sb.append(String.format(Locale.ROOT, "  tiles %d/%d  queue %d  %d ms", loaded, visible, cache.pending(), lastRenderNanos / 1_000_000));
        sb.append('\n').append(styleName);
        if (!attribution.isEmpty()) sb.append("  ").append(attribution);
        return sb.toString();
    }

    private static Style style(String nameOrFile) {
        try {
            return MapRequestJson.style(nameOrFile);
        } catch (IOException | IllegalArgumentException e) {
            return Styles.defaultStyle();
        }
    }

    private static Capabilities caps(AppPrefs p) {
        try {
            return new Capabilities(MapRequestJson.charset(p.charset()), MapRequestJson.colorDepth(p.color()));
        } catch (IllegalArgumentException e) {
            return new Capabilities(Capabilities.Charset.BRAILLE, ColorDepth.TRUE);
        }
    }

    @Override
    protected void onCleared() {
        paused = true;
        renderer.shutdownNow();
        fetchers.shutdownNow();
        if (upstream != null) close(upstream);
    }
}
