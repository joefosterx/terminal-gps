package dev.tilemap.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TileCacheTest {
    private final Deque<Runnable> queued = new ArrayDeque<>();
    private final List<TileId> fetched = new ArrayList<>();
    private final List<TileId> changed = new ArrayList<>();
    private final AtomicLong now = new AtomicLong();

    private TileCache cache(int capacity, TileSource upstream) {
        return new TileCache(upstream, capacity, queued::add, changed::add, now::get);
    }

    private final TileSource upstream = id -> {
        fetched.add(id);
        if (id.x() == 7) throw new TileException("down");
        return Optional.of(new Tile(id, List.of()));
    };

    private void runQueued() {
        while (!queued.isEmpty()) queued.poll().run();
    }

    @Test
    void missesReturnEmptyAndFetchInTheBackground() {
        TileCache cache = cache(8, upstream);
        TileId id = new TileId(3, 1, 1);
        cache.want(List.of(id), List.of());
        assertTrue(cache.fetch(id).isEmpty());
        assertEquals(1, cache.pending());
        assertEquals(1, queued.size(), "a pending tile is not requested twice");

        runQueued();
        assertEquals(List.of(id), changed);
        assertTrue(cache.fetch(id).isPresent());
        assertEquals(0, cache.pending());
        assertEquals(List.of(id), fetched);
    }

    @Test
    void unwantedTilesAreSkippedWhenTheirTurnComes() {
        TileCache cache = cache(8, upstream);
        TileId stale = new TileId(3, 1, 1), fresh = new TileId(3, 2, 1);
        cache.want(List.of(stale), List.of());
        cache.want(List.of(fresh), List.of());
        runQueued();
        assertEquals(List.of(fresh), fetched);
        assertFalse(cache.has(stale));
        cache.want(List.of(stale), List.of());
        runQueued();
        assertTrue(cache.has(stale), "requested again once wanted");
    }

    @Test
    void failuresAreRetriedLater() {
        TileCache cache = cache(8, upstream);
        TileId bad = new TileId(4, 7, 1);
        cache.want(List.of(bad), List.of());
        runQueued();
        assertEquals(1, cache.failed());
        assertTrue(cache.lastError().contains("down"));
        cache.want(List.of(bad), List.of());
        assertTrue(queued.isEmpty(), "not retried before the backoff");
        now.addAndGet(TileCache.RETRY_NANOS + 1);
        assertEquals(0, cache.failed());
        cache.want(List.of(bad), List.of());
        runQueued();
        assertEquals(2, fetched.size());
    }

    @Test
    void evictsLeastRecentlyUsed() {
        TileCache cache = cache(2, upstream);
        TileId a = new TileId(2, 0, 0), b = new TileId(2, 1, 0), c = new TileId(2, 2, 0);
        cache.want(List.of(a, b), List.of(c));
        runQueued();
        assertFalse(cache.has(a));
        assertTrue(cache.has(b) && cache.has(c));
    }
}
