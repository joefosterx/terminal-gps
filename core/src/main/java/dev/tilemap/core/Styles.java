package dev.tilemap.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Style loading. The JSON shape is:
 *
 * <pre>{@code
 * {"layers": [
 *   {"id": "water", "source": "water", "filter": {"natural": "water", "highway": ["primary", "secondary"]},
 *    "minZoom": 0, "maxZoom": 25,
 *    "paint": {"kind": "fill", "fg": "#5d9bd5", "bg": "#aad3df", "weight": "light", "strategy": "braille"}}
 * ]}
 * }</pre>
 *
 * Only {@code id}, {@code source}, {@code paint.kind} and {@code paint.fg} are required. Enum names are
 * case-insensitive and may use dashes ({@code box-line}). {@code paint.marker} is a single character for the
 * {@code marker} strategy (default {@code ●}).
 */
public final class Styles {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double DEFAULT_MAX_ZOOM = Projection.MAX_ZOOM + 1;
    private static final Style DEFAULT = loadResource("styles/default.json");

    private Styles() {}

    /** OSM-ish light style: land cover, water, buildings, roads. */
    public static Style defaultStyle() {
        return DEFAULT;
    }

    /** Names accepted by {@link #preset(String)}. */
    public static final List<String> PRESETS = List.of("default");

    /** A built-in style by name, if there is one. */
    public static java.util.Optional<Style> preset(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "default" -> java.util.Optional.of(DEFAULT);
            default -> java.util.Optional.empty();
        };
    }

    /** Parses a style; throws {@link IllegalArgumentException} for malformed content. */
    public static Style fromJson(Reader json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid style JSON: " + e.getMessage(), e);
        }
        JsonNode layers = root == null ? null : root.get("layers");
        if (layers == null || !layers.isArray()) throw new IllegalArgumentException("style needs a \"layers\" array");
        List<StyleLayer> out = new ArrayList<>();
        for (JsonNode l : layers) out.add(layer(l));
        return new Style(out);
    }

    private static StyleLayer layer(JsonNode l) {
        String id = text(l, "id", "layer");
        Map<String, List<String>> filter = new LinkedHashMap<>();
        JsonNode f = l.get("filter");
        if (f != null) {
            for (var e : f.properties()) {
                List<String> values = new ArrayList<>();
                if (e.getValue().isArray()) {
                    e.getValue().forEach(v -> values.add(v.asText()));
                } else {
                    values.add(e.getValue().asText());
                }
                filter.put(e.getKey(), values);
            }
        }
        JsonNode p = l.get("paint");
        if (p == null) throw new IllegalArgumentException("layer " + id + ": missing \"paint\"");
        Paint paint = new Paint(
                enumValue(PaintKind.class, text(p, "kind", id)),
                Rgb.fromHex(text(p, "fg", id)),
                p.hasNonNull("bg") ? Rgb.fromHex(p.get("bg").asText()) : null,
                p.hasNonNull("weight") ? enumValue(Weight.class, p.get("weight").asText()) : Weight.LIGHT,
                p.hasNonNull("strategy") ? enumValue(GlyphStrategy.class, p.get("strategy").asText()) : GlyphStrategy.BRAILLE,
                p.hasNonNull("marker") ? marker(p.get("marker").asText(), id) : Paint.DEFAULT_MARKER);
        return new StyleLayer(
                id,
                text(l, "source", id),
                filter,
                l.path("minZoom").asDouble(0),
                l.path("maxZoom").asDouble(DEFAULT_MAX_ZOOM),
                paint);
    }

    private static String text(JsonNode node, String field, String context) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) throw new IllegalArgumentException(context + ": missing string \"" + field + "\"");
        return v.asText();
    }

    private static int marker(String text, String context) {
        if (text.codePointCount(0, text.length()) != 1) {
            throw new IllegalArgumentException(context + ": \"marker\" must be exactly one character");
        }
        return text.codePointAt(0);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown " + type.getSimpleName() + ": " + name, e);
        }
    }

    private static Style loadResource(String name) {
        try (InputStream in = Styles.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("missing resource " + name);
            return fromJson(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
