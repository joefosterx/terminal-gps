package dev.tilemap.lib;

import dev.tilemap.core.MvtDecoder;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Keeps raw MVT bytes from an {@link HttpTileSource} on disk at {@code dir/{z}/{x}/{y}.mvt}. A file's modification
 * time holds its expiry, taken from the server's {@code Cache-Control: max-age} (or {@link #DEFAULT_MAX_AGE_SECONDS}
 * when the server gives none). An empty file records "no tile here". {@code no-store} responses are not written. When
 * the server fails, an expired copy is served rather than nothing. Corrupt files are deleted and fetched again.
 */
public final class DiskCachedTileSource implements TileSource {
    public static final long DEFAULT_MAX_AGE_SECONDS = 7 * 24 * 3600;

    private final HttpTileSource http;
    private final Path dir;
    private final LongSupplier epochMillis;

    public DiskCachedTileSource(HttpTileSource http, Path dir, LongSupplier epochMillis) {
        this.http = http;
        this.dir = dir;
        this.epochMillis = epochMillis;
    }

    /** A per-source directory under {@code root}, so different servers and tileset versions never share files. */
    public static Path directoryFor(Path root, HttpTileSource http) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(http.template().getBytes(StandardCharsets.UTF_8));
            return root.resolve(HexFormat.of().formatHex(hash, 0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The default cache root: {@code $XDG_CACHE_HOME/tilemap}, else {@code %LOCALAPPDATA%\tilemap\cache} on Windows,
     * else {@code ~/.cache/tilemap}.
     */
    public static Path defaultRoot(Map<String, String> env, String os, Path home) {
        String xdg = env.get("XDG_CACHE_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg, "tilemap");
        String local = env.get("LOCALAPPDATA");
        if (os.toLowerCase(Locale.ROOT).startsWith("windows") && local != null && !local.isBlank()) return Path.of(local, "tilemap", "cache");
        return home.resolve(".cache").resolve("tilemap");
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        Path file = dir.resolve(Integer.toString(id.z())).resolve(Integer.toString(id.x())).resolve(id.y() + ".mvt");
        long now = epochMillis.getAsLong();
        byte[] cached = read(file);
        if (cached != null && expiry(file) > now) {
            Optional<Tile> tile = decode(id, cached, file);
            if (tile != null) return tile;
        }

        HttpTileSource.RawTile raw;
        try {
            raw = http.fetchRaw(id);
        } catch (TileException e) {
            if (cached != null) {
                Optional<Tile> stale = decode(id, cached, file);
                if (stale != null) return stale;
            }
            throw e;
        }
        if (raw.maxAgeSeconds() != 0) {
            long maxAge = raw.maxAgeSeconds() == HttpTileSource.RawTile.UNKNOWN_AGE ? DEFAULT_MAX_AGE_SECONDS : raw.maxAgeSeconds();
            write(file, raw.bytes() == null ? new byte[0] : raw.bytes(), now + Math.min(maxAge, 3650L * 24 * 3600) * 1000);
        }
        return raw.bytes() == null ? Optional.empty() : Optional.of(MvtDecoder.decode(id, raw.bytes()));
    }

    @Override
    public int maxZoom() {
        return http.maxZoom();
    }

    /** Null if the file is corrupt (it is then deleted). */
    private static Optional<Tile> decode(TileId id, byte[] bytes, Path file) {
        if (bytes.length == 0) return Optional.empty();
        try {
            return Optional.of(MvtDecoder.decode(id, bytes));
        } catch (TileException e) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // It will be overwritten on the next successful fetch.
            }
            return null;
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
    }

    private static long expiry(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Best effort: a cache that cannot be written just means fetching again later. */
    private static void write(Path file, byte[] bytes, long expiresAt) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), ".tile", ".tmp");
            Files.write(tmp, bytes);
            Files.setLastModifiedTime(tmp, FileTime.fromMillis(expiresAt));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Not cached this time.
        }
    }
}
