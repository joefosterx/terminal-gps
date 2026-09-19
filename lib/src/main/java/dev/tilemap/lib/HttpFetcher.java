package dev.tilemap.lib;

import java.io.IOException;
import java.net.URI;

/**
 * One blocking HTTP GET, so {@link HttpTileSource} does not depend on {@code java.net.http}, which Android lacks.
 * Implementations send {@link HttpTileSource#USER_AGENT} and {@code Accept-Encoding: gzip}; the body is returned
 * exactly as sent, and the tile source removes any gzip encoding itself. Must be safe to call from several threads.
 */
public interface HttpFetcher {
    /** A response. Header values are null when absent. */
    record Response(int status, byte[] body, String contentEncoding, String cacheControl) {}

    Response get(URI uri) throws IOException;
}
