package dev.tilemap.core;

import java.util.Optional;

/** Where tiles come from. An empty result means "no data here", not an error. */
public interface TileSource {
    Optional<Tile> fetch(TileId id) throws TileException;

    /** The deepest zoom this source has tiles for; the renderer overzooms beyond it. */
    default int maxZoom() {
        return Projection.MAX_ZOOM;
    }
}
