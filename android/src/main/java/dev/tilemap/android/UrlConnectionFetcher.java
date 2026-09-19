package dev.tilemap.android;

import dev.tilemap.lib.HttpFetcher;
import dev.tilemap.lib.HttpTileSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** The Android {@link HttpFetcher}, on the platform's {@link HttpURLConnection}; no third-party HTTP stack. */
final class UrlConnectionFetcher implements HttpFetcher {
    private static final int TIMEOUT_MILLIS = 30_000;

    @Override
    public Response get(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MILLIS);
            conn.setReadTimeout(TIMEOUT_MILLIS);
            conn.setRequestProperty("User-Agent", HttpTileSource.USER_AGENT);
            // Asking explicitly stops the platform from transparently gunzipping, so the encoding header stays true.
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] body = in == null ? new byte[0] : readAll(in);
            return new Response(status, body, conn.getHeaderField("Content-Encoding"), conn.getHeaderField("Cache-Control"));
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
