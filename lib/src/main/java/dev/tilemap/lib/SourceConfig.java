package dev.tilemap.lib;

import java.nio.file.Path;
import java.util.Objects;

/** Where a map's tiles come from. */
public sealed interface SourceConfig {
    /**
     * OpenFreeMap's public planet tiles (OpenMapTiles schema, no key). Maps made from it must credit
     * "© OpenMapTiles © OpenStreetMap contributors".
     */
    Url OPENFREEMAP = new Url("https://tiles.openfreemap.org/planet", null);

    /**
     * Interprets a command-line source: an {@code http(s)} URL (tile template or TileJSON), a {@code .pmtiles}
     * file, or a {@code .geojson}/{@code .json} file. Throws {@link IllegalArgumentException} for anything else,
     * including files that do not exist.
     */
    static SourceConfig fromString(String spec, String key) throws java.io.IOException {
        String lower = spec.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return new Url(spec, key);
        Path path = Path.of(spec);
        if (!java.nio.file.Files.isRegularFile(path)) throw new IllegalArgumentException("no such source file: " + spec);
        if (lower.endsWith(".pmtiles")) return new PmTiles(path);
        if (lower.endsWith(".geojson") || lower.endsWith(".json")) return new GeoJson(java.nio.file.Files.readAllBytes(path));
        throw new IllegalArgumentException("unrecognized source " + spec + " (expected a URL, .pmtiles or .geojson)");
    }

    /**
     * A tile server. {@code template} is either a tile URL containing {@code {z}}, {@code {x}} and {@code {y}}, or a
     * TileJSON URL whose first {@code tiles} entry is used. {@code key} (nullable) replaces {@code {key}} in the
     * template, or is appended as a {@code key} query parameter when the template has no placeholder.
     */
    record Url(String template, String key) implements SourceConfig {
        public Url {
            Objects.requireNonNull(template, "template");
        }
    }

    /** A local PMTiles v3 archive of vector tiles. */
    record PmTiles(Path path) implements SourceConfig {
        public PmTiles {
            Objects.requireNonNull(path, "path");
        }
    }

    /** An in-memory GeoJSON FeatureCollection; see {@link dev.tilemap.core.GeoJsonTileSource}. */
    record GeoJson(byte[] bytes) implements SourceConfig {
        public GeoJson {
            Objects.requireNonNull(bytes, "bytes");
        }
    }
}
