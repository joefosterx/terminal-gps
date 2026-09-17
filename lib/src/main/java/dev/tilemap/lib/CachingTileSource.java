package dev.tilemap.lib;

import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small LRU of decoded tiles in front of another source. Misses ("no tile") are cached too; failures are not.
 * Concurrent misses for the same tile may both reach the delegate.
 */
public final class CachingTileSource implements TileSource, AutoCloseable {
    private final TileSource delegate;
    private final Map<TileId, Optional<Tile>> cache;

    public CachingTileSource(TileSource delegate, int maxTiles) {
        if (maxTiles <= 0) throw new IllegalArgumentException("maxTiles must be positive");
        this.delegate = delegate;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TileId, Optional<Tile>> eldest) {
                return size() > maxTiles;
            }
        };
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        synchronized (cache) {
            Optional<Tile> hit = cache.get(id);
            if (hit != null) return hit;
        }
        Optional<Tile> tile = delegate.fetch(id);
        synchronized (cache) {
            cache.put(id, tile);
        }
        return tile;
    }

    @Override
    public int maxZoom() {
        return delegate.maxZoom();
    }

    int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable c) c.close();
    }
}
