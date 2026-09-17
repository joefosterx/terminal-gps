package dev.tilemap.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tilemap.core.MvtDecoder;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Blocking MVT fetches from a {@code {z}/{x}/{y}} URL template. 404 and 204 mean "no tile"; other non-2xx
 * statuses and network failures are {@link TileException}s. Safe to call from several threads.
 */
public final class HttpTileSource implements TileSource {
    static final String USER_AGENT = "tilemap/0.1.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String template;
    private final String key;
    private final int maxZoom;
    private final HttpClient client;

    public HttpTileSource(String template, String key, HttpClient client) {
        this(template, key, Projection.MAX_ZOOM, client);
    }

    public HttpTileSource(String template, String key, int maxZoom, HttpClient client) {
        if (!isTemplate(template)) throw new IllegalArgumentException("URL template needs {z}, {x} and {y}: " + template);
        this.template = template;
        this.key = key;
        this.maxZoom = maxZoom;
        this.client = client;
    }

    public static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    static boolean isTemplate(String url) {
        return url.contains("{z}") && url.contains("{x}") && url.contains("{y}");
    }

    /** Reads a TileJSON document and uses its first tile URL and its {@code maxzoom}. */
    public static HttpTileSource fromTileJson(URI tileJson, String key, HttpClient client) throws IOException {
        HttpResponse<byte[]> response = send(client, withKey(tileJson.toString(), key));
        if (response.statusCode() / 100 != 2) throw new IOException("HTTP " + response.statusCode() + " for " + tileJson);
        JsonNode doc = new ObjectMapper().readTree(body(response));
        JsonNode tiles = doc.path("tiles");
        if (!tiles.isArray() || tiles.isEmpty() || !tiles.get(0).isTextual()) {
            throw new IOException("TileJSON at " + tileJson + " has no tiles URL");
        }
        int maxZoom = doc.path("maxzoom").asInt(Projection.MAX_ZOOM);
        return new HttpTileSource(tiles.get(0).asText(), key, maxZoom, client);
    }

    /** Raw MVT bytes (null when the server has no tile) and the response's max age. */
    public record RawTile(byte[] bytes, long maxAgeSeconds) {
        /** No {@code Cache-Control} max-age was given. */
        public static final long UNKNOWN_AGE = -1;
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        RawTile raw = fetchRaw(id);
        return raw.bytes() == null ? Optional.empty() : Optional.of(MvtDecoder.decode(id, raw.bytes()));
    }

    /** Fetches without decoding, for callers that cache bytes. {@code maxAgeSeconds} is 0 for {@code no-store}. */
    public RawTile fetchRaw(TileId id) throws TileException {
        if (id.z() > maxZoom) return new RawTile(null, RawTile.UNKNOWN_AGE);
        URI uri = uri(id);
        HttpResponse<byte[]> response;
        try {
            response = send(client, uri);
        } catch (IOException e) {
            throw new TileException("fetching " + uri + ": " + e.getMessage(), e);
        }
        int status = response.statusCode();
        long maxAge = maxAge(response.headers().firstValue("Cache-Control").orElse(""));
        if (status == 404 || status == 204) return new RawTile(null, maxAge);
        if (status / 100 != 2) throw new TileException("HTTP " + status + " for " + uri);
        byte[] body;
        try {
            body = body(response);
        } catch (IOException e) {
            throw new TileException("reading " + uri + ": " + e.getMessage(), e);
        }
        return new RawTile(body.length == 0 ? null : body, maxAge);
    }

    /** Seconds from {@code max-age}, 0 for {@code no-store} or {@code no-cache}, otherwise {@link RawTile#UNKNOWN_AGE}. */
    static long maxAge(String cacheControl) {
        long maxAge = RawTile.UNKNOWN_AGE;
        for (String directive : cacheControl.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String d = directive.strip();
            if (d.equals("no-store") || d.equals("no-cache")) return 0;
            if (d.startsWith("max-age=")) {
                try {
                    maxAge = Math.max(0, Long.parseLong(d.substring(8).strip()));
                } catch (NumberFormatException ignored) {
                    // Malformed: treat as absent.
                }
            }
        }
        return maxAge;
    }

    /** The URL template in use (after TileJSON resolution). */
    public String template() {
        return template;
    }

    @Override
    public int maxZoom() {
        return maxZoom;
    }

    URI uri(TileId id) {
        String url = template
                .replace("{z}", Integer.toString(id.z()))
                .replace("{x}", Integer.toString(id.x()))
                .replace("{y}", Integer.toString(id.y()));
        return withKey(url, key);
    }

    private static URI withKey(String url, String key) {
        if (key == null) return URI.create(url.replace("{key}", ""));
        String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8);
        if (url.contains("{key}")) return URI.create(url.replace("{key}", encoded));
        return URI.create(url + (url.contains("?") ? "&" : "?") + "key=" + encoded);
    }

    /** The response body with any gzip {@code Content-Encoding} removed; HttpClient does not do this itself. */
    static byte[] body(HttpResponse<byte[]> response) throws IOException {
        boolean gzip = response.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
        return gzip && MvtDecoder.isGzip(response.body()) ? MvtDecoder.gunzip(response.body()) : response.body();
    }

    private static HttpResponse<byte[]> send(HttpClient client, URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
