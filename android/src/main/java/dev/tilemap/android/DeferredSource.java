package dev.tilemap.android;

import dev.tilemap.core.Projection;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.util.Optional;

/**
 * A tile source opened on first use, on whichever fetch thread asks first. Opening a TileJSON URL is a network
 * round trip, which Android forbids on the main thread; this keeps it off the render thread too.
 */
final class DeferredSource implements TileSource, AutoCloseable {
    interface Opener {
        TileSource open() throws IOException;
    }

    private final Opener opener;
    private TileSource source;
    private IOException failure;

    DeferredSource(Opener opener) {
        this.opener = opener;
    }

    private synchronized TileSource source() throws TileException {
        if (source != null) return source;
        if (failure != null) throw new TileException("source: " + failure.getMessage(), failure);
        try {
            source = opener.open();
            return source;
        } catch (IOException | RuntimeException e) {
            failure = e instanceof IOException io ? io : new IOException(e.getMessage(), e);
            throw new TileException("source: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        return source().fetch(id);
    }

    /** The real limit once opened; until then, no limit, so early frames may ask for tiles that turn out absent. */
    @Override
    public synchronized int maxZoom() {
        return source == null ? Projection.MAX_ZOOM : source.maxZoom();
    }

    @Override
    public synchronized void close() throws Exception {
        if (source instanceof AutoCloseable c) c.close();
        source = null;
    }
}
