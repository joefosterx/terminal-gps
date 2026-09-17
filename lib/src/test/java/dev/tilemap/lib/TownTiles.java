package dev.tilemap.lib;

import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.MvtEncoder;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/** The shared town fixture as a GeoJSON source and as MVT tiles keyed by PMTiles tile id. */
final class TownTiles {
    static final LonLat CENTER = new LonLat(10.0, 50.0);
    static final int[] ZOOMS = {13, 14, 15};

    private TownTiles() {}

    static Path fixture() {
        String dir = System.getProperty("tilemap.fixtures");
        return (dir != null ? Path.of(dir) : Path.of("..", "fixtures")).resolve("town.geojson");
    }

    static GeoJsonTileSource geojson() throws IOException {
        try (Reader r = Files.newBufferedReader(fixture(), StandardCharsets.UTF_8)) {
            return GeoJsonTileSource.parse(r);
        }
    }

    static Viewport view(int zoom) {
        return new Viewport(CENTER, zoom, 80, 30);
    }

    /** Every tile the three fixture viewports need, MVT-encoded. */
    static SortedMap<Long, byte[]> mvt() throws Exception {
        GeoJsonTileSource src = geojson();
        SortedMap<Long, byte[]> tiles = new TreeMap<>();
        for (int zoom : ZOOMS) {
            for (TileId id : Renderer.tilesFor(view(zoom))) {
                Optional<Tile> tile = src.fetch(id);
                if (tile.isPresent()) tiles.put(PmTilesSource.tileId(id.z(), id.x(), id.y()), MvtEncoder.encode(tile.get()));
            }
        }
        return tiles;
    }
}
