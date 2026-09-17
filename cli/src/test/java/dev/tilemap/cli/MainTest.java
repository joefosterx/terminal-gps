package dev.tilemap.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.tilemap.lib.Area;
import dev.tilemap.lib.Format;
import dev.tilemap.lib.MapRequest;
import dev.tilemap.lib.SourceConfig;
import dev.tilemap.lib.TileMap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private static Path town() {
        String fixtures = System.getProperty("tilemap.fixtures");
        return (fixtures != null ? Path.of(fixtures) : Path.of("..", "fixtures")).resolve("town.geojson");
    }

    private int run(Map<String, String> env, TerminalSize.Size terminal, String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
                env, e -> Optional.ofNullable(terminal));
    }

    private int run(String... args) {
        return run(Map.of(), null, args);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private static String expected(int cols, int rows, Format fmt) throws Exception {
        MapRequest req = new MapRequest(new Area.Center(10, 50, 14), cols, rows, null, null,
                new SourceConfig.GeoJson(Files.readAllBytes(town())));
        return TileMap.renderString(req, fmt);
    }

    @Test
    void rendersCenterAndZoom() throws Exception {
        int code = run("--center", "10,50", "--zoom", "14", "--size", "60x20", "--source", town().toString());
        assertEquals(0, code, stderr());
        assertEquals(expected(60, 20, Format.PLAIN), stdout());
    }

    @Test
    void negativeCoordinatesParse() {
        int code = run("--center", "-0.1276,51.5072", "--zoom", "14", "--size", "4x2", "--source", town().toString());
        assertEquals(0, code, stderr());
        assertEquals("    \n    \n", stdout());
    }

    @Test
    void fitTerminalLeavesARowForThePrompt() throws Exception {
        int code = run(Map.of(), new TerminalSize.Size(50, 16),
                "--center", "10,50", "--zoom", "14", "--fit-terminal", "--source", town().toString(), "--format", "ansi");
        assertEquals(0, code, stderr());
        assertEquals(expected(50, 15, Format.ANSI), stdout());
    }

    @Test
    void fitTerminalFallsBackWithAWarning() {
        int code = run("--center", "10,50", "--zoom", "14", "--fit-terminal", "--source", town().toString());
        assertEquals(0, code);
        assertEquals(24, stdout().lines().count());
        assertTrue(stderr().contains("could not detect the terminal size"), stderr());
    }

    @Test
    void writesFormatsToAFile() throws Exception {
        Path html = dir.resolve("map.html");
        int code = run("--bbox", "9.995,49.996,10.006,50.004", "--size", "40x12", "--format", "html",
                "--source", town().toString(), "-o", html.toString());
        assertEquals(0, code, stderr());
        assertEquals("", stdout());
        assertTrue(Files.readString(html).startsWith("<meta charset=\"utf-8\">\n<pre"));
    }

    @Test
    void readsARequestFile() throws Exception {
        Path req = dir.resolve("req.json");
        Files.writeString(req, """
                {"area": {"center": [10, 50], "zoom": 14}, "size": [60, 20], "source": "%s"}
                """.formatted(town().toString().replace("\\", "\\\\")));
        int code = run("--request", req.toString());
        assertEquals(0, code, stderr());
        assertEquals(expected(60, 20, Format.PLAIN), stdout());
    }

    @Test
    void badArgumentsExitWithTwo() {
        assertEquals(2, run("--zoom", "14"));
        assertEquals(2, run("--center", "10,50", "--zoom", "14", "--size", "wide"));
        assertEquals(2, run("--center", "10", "--zoom", "14"));
        assertEquals(2, run("--bbox", "1,2,3,4", "--center", "1,2", "--zoom", "3"));
        assertEquals(2, run("--center", "10,50", "--zoom", "14", "--charset", "emoji"));
        assertEquals(2, run("--center", "10,50", "--zoom", "14", "--source", "missing.pmtiles"));
        assertEquals(2, run("--center", "10,50", "--zoom", "14", "--format", "pdf"));
        assertEquals(2, run("--bogus"));
    }

    @Test
    void tileFailuresExitWithThreeAndStillWriteUnlessStrict() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.pbf";
            assertEquals(3, run("--center", "10,50", "--zoom", "14", "--size", "4x2", "--source", url));
            assertEquals("    \n    \n", stdout());
            assertTrue(stderr().contains("HTTP 500"), stderr());

            out.reset();
            assertEquals(3, run("--center", "10,50", "--zoom", "14", "--size", "4x2", "--source", url, "--strict"));
            assertEquals("", stdout());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void terminalSizeParsing() {
        assertEquals(Optional.of(new TerminalSize.Size(120, 40)), TerminalSize.fromEnv(Map.of("COLUMNS", "120", "LINES", "40")));
        assertEquals(Optional.empty(), TerminalSize.fromEnv(Map.of("COLUMNS", "120")));
        assertEquals(Optional.of(new TerminalSize.Size(132, 43)), TerminalSize.parseStty("43 132\n"));
        assertEquals(Optional.empty(), TerminalSize.parseStty("stty: not a tty"));
    }
}
