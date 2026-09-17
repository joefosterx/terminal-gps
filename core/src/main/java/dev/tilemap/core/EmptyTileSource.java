package dev.tilemap.core;

import java.util.Optional;

/** A source with no tiles at all. */
public final class EmptyTileSource implements TileSource {
    public static final EmptyTileSource INSTANCE = new EmptyTileSource();

    private EmptyTileSource() {}

    @Override
    public Optional<Tile> fetch(TileId id) {
        return Optional.empty();
    }
}
