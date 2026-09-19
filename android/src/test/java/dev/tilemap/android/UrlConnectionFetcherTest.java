package dev.tilemap.android;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.tilemap.lib.HttpFetcher;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Parity with the JDK fetcher: status, headers and an untouched gzip body. The server is a few lines over a
 * socket because {@code com.sun.net.httpserver} is not on the Android unit-test classpath.
 */
class UrlConnectionFetcherTest {
    private ServerSocket server;
    private Thread serverThread;
    private String base;
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private final AtomicReference<String> acceptEncoding = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort();
        serverThread = new Thread(() -> {
            try {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        serve(s);
                    }
                }
            } catch (IOException ignored) {
                // Closed by stop().
            }
        }, "test-http");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        serverThread.join(2000);
    }

    private void serve(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        String request = in.readLine();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            String name = line.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (name.equals("user-agent")) userAgent.set(value);
            if (name.equals("accept-encoding")) acceptEncoding.set(value);
        }
        String path = request.split(" ")[1];
        int status;
        byte[] body;
        String headers = "";
        switch (path) {
            case "/plain" -> {
                status = 200;
                body = "hello".getBytes(StandardCharsets.UTF_8);
                headers = "Cache-Control: max-age=60\r\n";
            }
            case "/gz" -> {
                status = 200;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                    gz.write("zipped".getBytes(StandardCharsets.UTF_8));
                }
                body = bytes.toByteArray();
                headers = "Content-Encoding: gzip\r\n";
            }
            case "/missing" -> {
                status = 404;
                body = new byte[0];
            }
            default -> {
                status = 503;
                body = "busy".getBytes(StandardCharsets.UTF_8);
            }
        }
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + status + " X\r\n" + headers + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    @Test
    void statusHeadersAndBody() throws Exception {
        HttpFetcher fetcher = new UrlConnectionFetcher();
        HttpFetcher.Response ok = fetcher.get(URI.create(base + "/plain"));
        assertEquals(200, ok.status());
        assertEquals("hello", new String(ok.body(), StandardCharsets.UTF_8));
        assertEquals("max-age=60", ok.cacheControl());
        assertNull(ok.contentEncoding());
        assertEquals("tilemap/0.1.0", userAgent.get());
        assertEquals("gzip", acceptEncoding.get());

        HttpFetcher.Response gz = fetcher.get(URI.create(base + "/gz"));
        assertEquals("gzip", gz.contentEncoding());
        assertArrayEquals(new byte[] {0x1f, (byte) 0x8b}, new byte[] {gz.body()[0], gz.body()[1]}, "body left encoded");

        assertEquals(404, fetcher.get(URI.create(base + "/missing")).status());
        HttpFetcher.Response broken = fetcher.get(URI.create(base + "/broken"));
        assertEquals(503, broken.status());
        assertEquals("busy", new String(broken.body(), StandardCharsets.UTF_8));
    }
}
