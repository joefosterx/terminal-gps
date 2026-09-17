package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CachingTileSourceTest {
    private final List<TileId> calls = new ArrayList<>();

    private final TileSource counting = new TileSource() {
        @Override
        public Optional<Tile> fetch(TileId id) throws TileException {
            calls.add(id);
            if (id.z() == 5) throw new TileException("down");
            return id.x() == 0 ? Optional.empty() : Optional.of(new Tile(id, List.of()));
        }

        @Override
        public int maxZoom() {
            return 14;
        }
    };

    @Test
    void servesRepeatsFromCacheIncludingMisses() throws TileException {
        CachingTileSource cache = new CachingTileSource(counting, 8);
        Optional<Tile> first = cache.fetch(new TileId(2, 1, 1));
        assertSame(first, cache.fetch(new TileId(2, 1, 1)));
        cache.fetch(new TileId(2, 0, 1));
        cache.fetch(new TileId(2, 0, 1));
        assertEquals(2, calls.size());
        assertEquals(14, cache.maxZoom());
    }

    @Test
    void evictsLeastRecentlyUsed() throws TileException {
        CachingTileSource cache = new CachingTileSource(counting, 2);
        TileId a = new TileId(3, 1, 0), b = new TileId(3, 2, 0), c = new TileId(3, 3, 0);
        cache.fetch(a);
        cache.fetch(b);
        cache.fetch(a);  // a is now most recent
        cache.fetch(c);  // evicts b
        assertEquals(2, cache.size());
        calls.clear();
        cache.fetch(a);
        cache.fetch(b);
        assertEquals(List.of(b), calls);
    }

    @Test
    void failuresAreNotCached() {
        CachingTileSource cache = new CachingTileSource(counting, 2);
        assertThrows(TileException.class, () -> cache.fetch(new TileId(5, 1, 1)));
        assertThrows(TileException.class, () -> cache.fetch(new TileId(5, 1, 1)));
        assertEquals(2, calls.size());
    }
}
