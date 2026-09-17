package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against an in-process HTTP server that serves the town fixture as MVT. */
class HttpTileSourceTest {
    private HttpServer server;
    private String base;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> userAgents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        SortedMap<Long, byte[]> tiles = TownTiles.mvt();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/tiles/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            String[] p = exchange.getRequestURI().getPath().replace(".pbf", "").split("/");
            int z = Integer.parseInt(p[2]), x = Integer.parseInt(p[3]), y = Integer.parseInt(p[4]);
            byte[] body = tiles.get(PmTilesSource.tileId(z, x, y));
            respond(exchange, body == null ? 404 : 200, body == null ? new byte[0] : body);
        });
        server.createContext("/broken/", exchange -> respond(exchange, 503, "busy".getBytes(StandardCharsets.UTF_8)));
        // Like real servers, TileJSON is sent gzip-encoded because the client accepts it.
        server.createContext("/tiles.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            respond(exchange, 200, gzip("""
                    {"tilejson": "3.0.0", "maxzoom": 13, "tiles": ["%s/tiles/{z}/{x}/{y}.pbf?token={key}"]}
                    """.formatted(base).getBytes(StandardCharsets.UTF_8)));
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bytes)) {
            gz.write(data);
        }
        return bytes.toByteArray();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void rendersTheFixtureOverHttp() throws Exception {
        TileSource http = new HttpTileSource(base + "/tiles/{z}/{x}/{y}.pbf", null, HttpTileSource.defaultClient());
        TileSource geojson = TownTiles.geojson();
        String expected = Renderer.render(TownTiles.view(14), Styles.defaultStyle(), Capabilities.DEFAULT, geojson).toPlain();
        assertEquals(expected, Renderer.render(TownTiles.view(14), Styles.defaultStyle(), Capabilities.DEFAULT, http).toPlain());
        assertEquals(HttpTileSource.USER_AGENT, userAgents.get(0));
    }

    @Test
    void notFoundIsEmptyAndServerErrorsThrow() throws Exception {
        TileSource http = new HttpTileSource(base + "/tiles/{z}/{x}/{y}.pbf", null, HttpTileSource.defaultClient());
        assertTrue(http.fetch(new TileId(14, 0, 0)).isEmpty());

        TileSource broken = new HttpTileSource(base + "/broken/{z}/{x}/{y}", null, HttpTileSource.defaultClient());
        TileException e = assertThrows(TileException.class, () -> broken.fetch(new TileId(1, 0, 0)));
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
    }

    @Test
    void keysAreSubstitutedOrAppended() {
        var client = HttpTileSource.defaultClient();
        TileId id = new TileId(3, 4, 5);
        assertEquals(URI.create("https://t.example/3/4/5.pbf?api_key=a%2Bb"),
                new HttpTileSource("https://t.example/{z}/{x}/{y}.pbf?api_key={key}", "a+b", client).uri(id));
        assertEquals(URI.create("https://t.example/3/4/5.pbf?key=k"),
                new HttpTileSource("https://t.example/{z}/{x}/{y}.pbf", "k", client).uri(id));
        assertEquals(URI.create("https://t.example/3/4/5.pbf?v=2&key=k"),
                new HttpTileSource("https://t.example/{z}/{x}/{y}.pbf?v=2", "k", client).uri(id));
        assertThrows(IllegalArgumentException.class, () -> new HttpTileSource("https://t.example/tiles", null, client));
    }

    @Test
    void tileJsonProvidesTemplateAndMaxZoom() throws Exception {
        TileSource src = TileSources.open(new SourceConfig.Url(base + "/tiles.json", "secret"));
        assertEquals(13, src.maxZoom());

        // A zoom-15 view overzooms: it fetches zoom-13 tiles.
        String expected = Renderer.render(TownTiles.view(13), Styles.defaultStyle(), Capabilities.DEFAULT, TownTiles.geojson()).toPlain();
        Renderer.render(TownTiles.view(15), Styles.defaultStyle(), Capabilities.DEFAULT, src);
        assertTrue(requests.stream().allMatch(r -> r.startsWith("/tiles/13/") && r.endsWith("?token=secret")), requests.toString());

        assertEquals(expected, Renderer.render(TownTiles.view(13), Styles.defaultStyle(), Capabilities.DEFAULT, src).toPlain());
        requests.clear();
        Renderer.render(TownTiles.view(13), Styles.defaultStyle(), Capabilities.DEFAULT, src);
        assertEquals(List.of(), requests, "a repeated render is served from the cache");
    }
}
