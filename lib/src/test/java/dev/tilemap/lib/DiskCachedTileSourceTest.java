package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskCachedTileSourceTest {
    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> cacheControl = new AtomicReference<>("public, max-age=60");
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private HttpTileSource http;
    private TileId present;

    @BeforeEach
    void start() throws Exception {
        SortedMap<Long, byte[]> tiles = TownTiles.mvt();
        present = dev.tilemap.core.Renderer.tilesFor(TownTiles.view(14)).getFirst();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            String[] p = exchange.getRequestURI().getPath().replace(".pbf", "").split("/");
            byte[] body = tiles.get(PmTilesSource.tileId(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            int code = status.get() != 200 ? status.get() : body == null ? 404 : 200;
            if (cacheControl.get() != null) exchange.getResponseHeaders().add("Cache-Control", cacheControl.get());
            byte[] out = code == 200 ? body : new byte[0];
            exchange.sendResponseHeaders(code, out.length == 0 ? -1 : out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        http = new HttpTileSource("http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.pbf", null, HttpTileSource.defaultFetcher());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TileSource cached() {
        return new DiskCachedTileSource(http, dir, now::get);
    }

    private Path fileFor(TileId id) {
        return dir.resolve(id.z() + "/" + id.x() + "/" + id.y() + ".mvt");
    }

    @Test
    void servesFromDiskUntilExpiry() throws TileException {
        assertTrue(cached().fetch(present).isPresent());
        assertEquals(1, hits.get());
        assertTrue(Files.exists(fileFor(present)));

        assertTrue(cached().fetch(present).isPresent(), "a new instance reads the file");
        assertEquals(1, hits.get());

        now.addAndGet(61_000);
        assertTrue(cached().fetch(present).isPresent());
        assertEquals(2, hits.get(), "expired entries are fetched again");
    }

    @Test
    void missingTilesAreRememberedAsEmptyFiles() throws Exception {
        TileId absent = new TileId(14, 0, 0);
        assertTrue(cached().fetch(absent).isEmpty());
        assertEquals(0, Files.size(fileFor(absent)));
        assertTrue(cached().fetch(absent).isEmpty());
        assertEquals(1, hits.get());
    }

    @Test
    void staleCopiesCoverServerFailures() throws TileException {
        cached().fetch(present);
        now.addAndGet(3_600_000);
        status.set(503);
        assertTrue(cached().fetch(present).isPresent());
        assertEquals(2, hits.get());

        TileId never = new TileId(14, 1, 1);
        assertThrows(TileException.class, () -> cached().fetch(never));
    }

    @Test
    void noStoreIsNotWrittenAndMissingMaxAgeUsesTheDefault() throws Exception {
        cacheControl.set("no-store");
        cached().fetch(present);
        assertFalse(Files.exists(fileFor(present)));

        cacheControl.set(null);
        cached().fetch(present);
        assertEquals(now.get() + DiskCachedTileSource.DEFAULT_MAX_AGE_SECONDS * 1000, Files.getLastModifiedTime(fileFor(present)).toMillis());
    }

    @Test
    void corruptFilesAreReplaced() throws Exception {
        cached().fetch(present);
        Files.write(fileFor(present), new byte[] {0x1a, 0x7f, 0x01});
        assertTrue(cached().fetch(present).isPresent());
        assertEquals(2, hits.get());
    }

    @Test
    void cacheControlParsingAndDirectories() {
        assertEquals(315360000, HttpTileSource.maxAge("public, max-age=315360000"));
        assertEquals(0, HttpTileSource.maxAge("max-age=60, no-store"));
        assertEquals(HttpTileSource.RawTile.UNKNOWN_AGE, HttpTileSource.maxAge(""));
        assertEquals(HttpTileSource.RawTile.UNKNOWN_AGE, HttpTileSource.maxAge("max-age=soon"));

        HttpTileSource other = new HttpTileSource("https://other.example/{z}/{x}/{y}", null, HttpTileSource.defaultFetcher());
        assertNotEquals(DiskCachedTileSource.directoryFor(dir, http), DiskCachedTileSource.directoryFor(dir, other));
        assertEquals(Path.of("/x", "tilemap"), DiskCachedTileSource.defaultRoot(Map.of("XDG_CACHE_HOME", "/x"), "Linux", Path.of("/home/u")));
        assertEquals(Path.of("/home/u", ".cache", "tilemap"), DiskCachedTileSource.defaultRoot(Map.of(), "Linux", Path.of("/home/u")));
        assertEquals(Path.of("C:/L", "tilemap", "cache"), DiskCachedTileSource.defaultRoot(Map.of("LOCALAPPDATA", "C:/L"), "Windows 11", Path.of("C:/u")));
    }
}
