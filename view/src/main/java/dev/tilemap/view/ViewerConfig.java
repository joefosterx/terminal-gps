package dev.tilemap.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Viewer settings from {@code config.json}. Every field is optional; command-line flags override it.
 *
 * <pre>{@code
 * {
 *   "source": "https://tiles.openfreemap.org/planet",   // URL, .pmtiles or .geojson
 *   "key": null,
 *   "style": "dark",                                     // preset name or style file
 *   "center": [-0.1276, 51.5072],
 *   "zoom": 14,
 *   "charset": "braille",                                // skips the braille probe when set
 *   "color": "true",
 *   "labels": true,
 *   "memoryTiles": 512,
 *   "diskCache": true,
 *   "cacheDir": "/path/to/cache"
 * }
 * }</pre>
 */
record ViewerConfig(String source, String key, String style, double[] center, Double zoom, String charset, String color,
                    Boolean labels, Integer memoryTiles, Boolean diskCache, String cacheDir) {
    static final ViewerConfig EMPTY = new ViewerConfig(null, null, null, null, null, null, null, null, null, null, null);

    /** {@code $XDG_CONFIG_HOME/tilemap/config.json}, falling back to {@code ~/.config/tilemap/config.json}. */
    static Path defaultPath(Map<String, String> env, Path home) {
        String xdg = env.get("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".config");
        return base.resolve("tilemap").resolve("config.json");
    }

    /** Reads a config file; a missing file is an empty config, a malformed one an {@link IllegalArgumentException}. */
    static ViewerConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return EMPTY;
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(file.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException(file + ": invalid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException(file + ": expected a JSON object");
        Path dir = file.toAbsolutePath().getParent();
        double[] center = null;
        if (root.has("center")) {
            JsonNode c = root.get("center");
            if (!c.isArray() || c.size() != 2 || !c.get(0).isNumber() || !c.get(1).isNumber()) {
                throw new IllegalArgumentException(file + ": \"center\" must be [lon, lat]");
            }
            center = new double[] {c.get(0).asDouble(), c.get(1).asDouble()};
        }
        return new ViewerConfig(
                relative(text(root, "source"), dir),
                text(root, "key"),
                relative(text(root, "style"), dir),
                center,
                root.hasNonNull("zoom") ? root.get("zoom").asDouble() : null,
                text(root, "charset"),
                text(root, "color"),
                root.hasNonNull("labels") ? root.get("labels").asBoolean() : null,
                root.hasNonNull("memoryTiles") ? root.get("memoryTiles").asInt() : null,
                root.hasNonNull("diskCache") ? root.get("diskCache").asBoolean() : null,
                relative(text(root, "cacheDir"), dir));
    }

    private static String text(JsonNode root, String field) {
        return root.hasNonNull(field) ? root.get(field).asText() : null;
    }

    /** Relative file paths in the config resolve against its directory; URLs and preset names are left alone. */
    private static String relative(String value, Path dir) {
        if (value == null || value.contains("://") || dev.tilemap.core.Styles.preset(value).isPresent()) return value;
        Path p = Path.of(value);
        return p.isAbsolute() || !Files.exists(dir.resolve(p)) ? value : dir.resolve(p).toString();
    }

    Optional<double[]> centerOpt() {
        return Optional.ofNullable(center);
    }
}
