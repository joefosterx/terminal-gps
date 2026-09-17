package dev.tilemap.core;

import java.util.List;

/** A decoded vector tile. Geometry coordinates are tile-local, 0 to {@link #EXTENT}. */
public record Tile(TileId id, List<Layer> layers) {
    public static final int EXTENT = 4096;

    public Tile {
        layers = List.copyOf(layers);
    }
}
