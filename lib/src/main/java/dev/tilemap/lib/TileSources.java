package dev.tilemap.lib;

import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.TileSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/** Opens a {@link TileSource} for a {@link SourceConfig}. */
public final class TileSources {
    /** Decoded tiles kept per source; enough for a few screens of a large terminal. */
    public static final int DEFAULT_CACHE_TILES = 64;

    private TileSources() {}

    /**
     * Opens the configured source. Network and file sources are wrapped in a {@link CachingTileSource}. Close the
     * result if it is {@link AutoCloseable} (PMTiles holds an open file).
     */
    public static TileSource open(SourceConfig config) throws IOException {
        return open(config, DEFAULT_CACHE_TILES);
    }

    /** Like {@link #open(SourceConfig)} with a cache of {@code cacheTiles} decoded tiles, or none if zero. */
    public static TileSource open(SourceConfig config, int cacheTiles) throws IOException {
        return open(config, cacheTiles, null);
    }

    /**
     * Like {@link #open(SourceConfig, int)}; when {@code diskCacheRoot} is not null, tiles from a URL source are also
     * kept on disk under it (see {@link DiskCachedTileSource}).
     */
    public static TileSource open(SourceConfig config, int cacheTiles, java.nio.file.Path diskCacheRoot) throws IOException {
        TileSource raw = switch (config) {
            case SourceConfig.Url url -> {
                HttpTileSource http = http(url);
                yield diskCacheRoot == null ? http
                        : new DiskCachedTileSource(http, DiskCachedTileSource.directoryFor(diskCacheRoot, http), System::currentTimeMillis);
            }
            case SourceConfig.PmTiles pm -> PmTilesSource.open(pm.path());
            case SourceConfig.GeoJson gj -> GeoJsonTileSource.parse(
                    new InputStreamReader(new ByteArrayInputStream(gj.bytes()), StandardCharsets.UTF_8));
        };
        return cacheTiles > 0 && !(raw instanceof GeoJsonTileSource) ? new CachingTileSource(raw, cacheTiles) : raw;
    }

    private static HttpTileSource http(SourceConfig.Url url) throws IOException {
        HttpClient client = HttpTileSource.defaultClient();
        if (HttpTileSource.isTemplate(url.template())) return new HttpTileSource(url.template(), url.key(), client);
        return HttpTileSource.fromTileJson(URI.create(url.template()), url.key(), client);
    }
}
