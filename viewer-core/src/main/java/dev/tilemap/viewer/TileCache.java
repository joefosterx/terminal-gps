package dev.tilemap.viewer;

import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * An LRU of decoded tiles that never blocks the render thread. As a {@link TileSource} it answers from memory only;
 * a miss returns empty and starts a background fetch, and {@code onChange} is called when the fetch finishes so the
 * viewer can redraw. Fetches for tiles that are no longer wanted by the time a fetch slot frees up are skipped.
 * Failed tiles are retried after {@link #RETRY_NANOS}.
 */
public final class TileCache implements TileSource {
    public static final long RETRY_NANOS = 30_000_000_000L;
    public static final int DEFAULT_CAPACITY = 512;
    public static final int MAX_CONCURRENT_FETCHES = 8;

    private final TileSource upstream;
    private final Executor executor;
    private final Consumer<TileId> onChange;
    private final LongSupplier clock;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_FETCHES);
    private final Map<TileId, Optional<Tile>> tiles;
    private final Set<TileId> pending = new HashSet<>();
    private final Map<TileId, Long> retryAt = new HashMap<>();
    private volatile Set<TileId> wanted = Set.of();
    private volatile String lastError;

    public TileCache(TileSource upstream, int capacity, Executor executor, Consumer<TileId> onChange, LongSupplier clock) {
        this.upstream = upstream;
        this.executor = executor;
        this.onChange = onChange;
        this.clock = clock;
        this.tiles = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TileId, Optional<Tile>> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public Optional<Tile> fetch(TileId id) {
        synchronized (this) {
            Optional<Tile> hit = tiles.get(id);
            if (hit != null) return hit;
        }
        request(id);
        return Optional.empty();
    }

    @Override
    public int maxZoom() {
        return upstream.maxZoom();
    }

    /** Declares what the next frames need: {@code visible} is requested first, then {@code prefetch}. */
    public void want(Collection<TileId> visible, Collection<TileId> prefetch) {
        Set<TileId> all = new HashSet<>(visible);
        all.addAll(prefetch);
        wanted = all;
        for (TileId id : visible) request(id);
        for (TileId id : prefetch) request(id);
    }

    public synchronized boolean has(TileId id) {
        return tiles.containsKey(id);
    }

    public synchronized int pending() {
        return pending.size();
    }

    public synchronized int failed() {
        long now = clock.getAsLong();
        return (int) retryAt.values().stream().filter(t -> t > now).count();
    }

    public String lastError() {
        return lastError;
    }

    private void request(TileId id) {
        synchronized (this) {
            if (tiles.containsKey(id) || pending.contains(id)) return;
            Long retry = retryAt.get(id);
            if (retry != null && retry > clock.getAsLong()) return;
            pending.add(id);
        }
        executor.execute(() -> load(id));
    }

    private void load(TileId id) {
        boolean loaded = false;
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (this) {
                pending.remove(id);
            }
            return;
        }
        try {
            if (!wanted.contains(id)) return;
            Optional<Tile> tile = upstream.fetch(id);
            synchronized (this) {
                tiles.put(id, tile);
                retryAt.remove(id);
            }
            loaded = true;
        } catch (TileException | RuntimeException e) {
            lastError = "tile " + id + ": " + e.getMessage();
            synchronized (this) {
                retryAt.put(id, clock.getAsLong() + RETRY_NANOS);
            }
            loaded = true;
        } finally {
            permits.release();
            synchronized (this) {
                pending.remove(id);
            }
            if (loaded) onChange.accept(id);
        }
    }
}
