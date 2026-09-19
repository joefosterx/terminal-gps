package dev.tilemap.lib;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The JVM {@link HttpFetcher} on {@code java.net.http}. Not loadable on Android; callers there supply their own,
 * and nothing in this package references this class except {@link HttpTileSource#defaultFetcher()}.
 */
public final class JdkHttpFetcher implements HttpFetcher {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;

    public JdkHttpFetcher() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public JdkHttpFetcher(HttpClient client) {
        this.client = client;
    }

    @Override
    public Response get(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", HttpTileSource.USER_AGENT)
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return new Response(response.statusCode(), response.body(),
                response.headers().firstValue("Content-Encoding").orElse(null),
                response.headers().firstValue("Cache-Control").orElse(null));
    }
}
