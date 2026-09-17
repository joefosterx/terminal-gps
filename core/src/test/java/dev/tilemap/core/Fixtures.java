package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared test fixtures and golden-file comparison. Regenerate goldens with {@code gradlew :core:test -PupdateGoldens}. */
final class Fixtures {
    static final LonLat TOWN_CENTER = new LonLat(10.0, 50.0);
    private static final Path GOLDEN_DIR = Path.of("src", "test", "golden");
    private static GeoJsonTileSource town;

    private Fixtures() {}

    static Path dir() {
        String dir = System.getProperty("tilemap.fixtures");
        return dir != null ? Path.of(dir) : Path.of("..", "fixtures");
    }

    static synchronized GeoJsonTileSource town() {
        if (town == null) {
            try (Reader r = Files.newBufferedReader(dir().resolve("town.geojson"), StandardCharsets.UTF_8)) {
                town = GeoJsonTileSource.parse(r);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return town;
    }

    static void assertGolden(String name, String actual) throws IOException {
        Path file = GOLDEN_DIR.resolve(name);
        if (Boolean.getBoolean("tilemap.updateGoldens")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(file)) {
            throw new AssertionError("missing golden " + file + "; run gradlew :core:test -PupdateGoldens");
        }
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), actual, "golden " + name);
    }
}
