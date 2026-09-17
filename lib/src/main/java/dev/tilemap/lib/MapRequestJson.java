package dev.tilemap.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Style;
import dev.tilemap.core.Styles;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The JSON form of a {@link MapRequest}, used by {@code tilemap --request FILE}:
 *
 * <pre>{@code
 * {
 *   "area": {"center": [-0.1276, 51.5072], "zoom": 14}      // or {"bbox": [west, south, east, north]}
 *   "size": [120, 40],
 *   "style": "default",                                     // preset name, style file path, or inline style
 *   "charset": "braille",                                   // ascii | latin1 | box | braille | sextant
 *   "color": "256",                                         // none | 16 | 256 | true
 *   "source": "https://tiles.openfreemap.org/planet",       // URL, .pmtiles or .geojson path
 *   "key": null,
 *   "labels": true
 * }
 * }</pre>
 *
 * Only {@code area} and {@code size} are required. Relative paths resolve against the request file's directory.
 * Malformed requests throw {@link IllegalArgumentException}.
 */
public final class MapRequestJson {
    private MapRequestJson() {}

    public static MapRequest read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Path parent = file.toAbsolutePath().getParent();
            return read(r, parent);
        }
    }

    public static MapRequest read(Reader json, Path baseDir) throws IOException {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid request JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("request must be a JSON object");

        JsonNode size = root.path("size");
        if (!size.isArray() || size.size() != 2 || !size.get(0).canConvertToInt() || !size.get(1).canConvertToInt()) {
            throw new IllegalArgumentException("\"size\" must be [cols, rows]");
        }
        String key = root.hasNonNull("key") ? root.get("key").asText() : null;
        SourceConfig source = root.hasNonNull("source")
                ? SourceConfig.fromString(resolve(root.get("source").asText(), baseDir), key)
                : SourceConfig.OPENFREEMAP;
        Capabilities caps = new Capabilities(
                root.hasNonNull("charset") ? charset(root.get("charset").asText()) : Capabilities.DEFAULT.charset(),
                root.hasNonNull("color") ? colorDepth(root.get("color").asText()) : Capabilities.DEFAULT.color());
        return new MapRequest(area(root.path("area")), size.get(0).asInt(), size.get(1).asInt(),
                style(root.get("style"), baseDir), caps, source, root.path("labels").asBoolean(true));
    }

    private static Area area(JsonNode area) {
        if (area.has("bbox")) {
            double[] b = numbers(area.get("bbox"), 4, "\"bbox\" must be [west, south, east, north]");
            return new Area.BBox(b[0], b[1], b[2], b[3]);
        }
        if (area.has("center")) {
            double[] c = numbers(area.get("center"), 2, "\"center\" must be [lon, lat]");
            if (!area.path("zoom").isNumber()) throw new IllegalArgumentException("\"area.zoom\" is required with a center");
            return new Area.Center(c[0], c[1], area.get("zoom").asDouble());
        }
        throw new IllegalArgumentException("\"area\" needs \"bbox\" or \"center\" and \"zoom\"");
    }

    private static double[] numbers(JsonNode array, int n, String message) {
        if (!array.isArray() || array.size() != n) throw new IllegalArgumentException(message);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            if (!array.get(i).isNumber()) throw new IllegalArgumentException(message);
            out[i] = array.get(i).asDouble();
        }
        return out;
    }

    private static Style style(JsonNode node, Path baseDir) throws IOException {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return Styles.fromJson(new StringReader(node.toString()));
        return style(resolve(node.asText(), baseDir));
    }

    /** A preset name or a style file path. */
    public static Style style(String nameOrFile) throws IOException {
        var preset = Styles.preset(nameOrFile);
        if (preset.isPresent()) return preset.get();
        Path path = Path.of(nameOrFile);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("unknown style " + nameOrFile + " (presets: " + String.join(", ", Styles.PRESETS) + ")");
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return Styles.fromJson(r);
        }
    }

    public static Charset charset(String name) {
        try {
            return Charset.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown charset " + name + " (ascii, latin1, box, braille, sextant)", e);
        }
    }

    public static ColorDepth colorDepth(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "none", "0" -> ColorDepth.NONE;
            case "16" -> ColorDepth.C16;
            case "256" -> ColorDepth.C256;
            case "true", "truecolor", "24bit" -> ColorDepth.TRUE;
            default -> throw new IllegalArgumentException("unknown color depth " + name + " (none, 16, 256, true)");
        };
    }

    /** Relative file paths resolve against {@code baseDir}; URLs and preset names pass through. */
    private static String resolve(String value, Path baseDir) {
        if (baseDir == null || value.contains("://") || Styles.preset(value).isPresent()) return value;
        Path p = Path.of(value);
        return p.isAbsolute() ? value : baseDir.resolve(p).toString();
    }
}
