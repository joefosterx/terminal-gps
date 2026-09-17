package dev.tilemap.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tilemap.core.Capabilities.Charset;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Style loading. The JSON shape is:
 *
 * <pre>{@code
 * {"layers": [
 *   {"id": "water", "source": "water", "filter": {"class": ["lake", "river"]}, "minZoom": 0, "maxZoom": 25,
 *    "protect": true,
 *    "paint": {"kind": "fill", "fg": "#5d9bd5", "bg": "#aad3df", "weight": "light", "strategy": "braille",
 *              "pattern": "⠪"}}
 * ],
 *  "labels": [
 *   {"id": "town", "source": "place", "filter": {"class": "town"}, "minZoom": 10,
 *    "field": "name", "priority": 90, "maxWidth": 24, "bold": true}
 * ]}
 * }</pre>
 *
 * <ul>
 *   <li>Layers need {@code id}, {@code source}, {@code paint.kind} and {@code paint.fg}; labels need {@code id} and
 *       {@code source}. Enum names are case-insensitive and may use dashes ({@code box-line}).
 *   <li>{@code paint.marker} is one character for the {@code marker} strategy (default {@code ●}).
 *       {@code paint.pattern} is a braille character whose raised dots are the dots a fill covers in each cell.
 *   <li>{@code "protect": true} keeps labels off a layer.
 *   <li>{@code "maxCharset"} caps the charset (for example {@code "ascii"}); {@code "monochrome": true} drops colors.
 *   <li>{@code "extends": "default"} starts from a preset: {@code layers} and {@code labels}, when given, replace
 *       the inherited lists, and {@code "paint": {"water": {"fg": "#..."}}} changes fields of inherited layers.
 * </ul>
 */
public final class Styles {
    /** Built-in presets, in the order the viewer cycles through them. */
    public static final List<String> PRESETS = List.of("default", "dark", "mono", "vt220", "high-contrast");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double DEFAULT_MAX_ZOOM = Projection.MAX_ZOOM + 1;
    private static final Map<String, Style> LOADED = new ConcurrentHashMap<>();

    private Styles() {}

    /** OSM-ish light style: land cover, water, buildings, roads, markers and labels. */
    public static Style defaultStyle() {
        return preset("default").orElseThrow();
    }

    /** A built-in style by name, if there is one. */
    public static Optional<Style> preset(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!PRESETS.contains(key)) return Optional.empty();
        if (!LOADED.containsKey(key)) {
            // Not computeIfAbsent: presets extend other presets, so loading one may load another.
            LOADED.putIfAbsent(key, loadResource("styles/" + key + ".json"));
        }
        return Optional.of(LOADED.get(key));
    }

    /** Parses a style; throws {@link IllegalArgumentException} for malformed content. */
    public static Style fromJson(Reader json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid style JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("style must be a JSON object");

        Style base = null;
        if (root.hasNonNull("extends")) {
            String name = root.get("extends").asText();
            base = preset(name).orElseThrow(() -> new IllegalArgumentException("unknown preset to extend: " + name));
        }

        List<StyleLayer> layers;
        JsonNode layerNodes = root.get("layers");
        if (layerNodes != null) {
            if (!layerNodes.isArray()) throw new IllegalArgumentException("\"layers\" must be an array");
            layers = new ArrayList<>();
            for (JsonNode l : layerNodes) layers.add(layer(l));
        } else if (base != null) {
            layers = new ArrayList<>(base.layers());
        } else {
            throw new IllegalArgumentException("style needs a \"layers\" array or \"extends\"");
        }

        JsonNode overrides = root.get("paint");
        if (overrides != null) {
            if (!overrides.isObject()) throw new IllegalArgumentException("\"paint\" overrides must be an object");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < layers.size(); i++) {
                StyleLayer l = layers.get(i);
                JsonNode o = overrides.get(l.id());
                if (o == null) continue;
                seen.add(l.id());
                layers.set(i, new StyleLayer(l.id(), l.source(), l.filter(), l.minZoom(), l.maxZoom(), paint(o, l.paint(), l.id()), l.protect()));
            }
            for (var e : overrides.properties()) {
                if (!seen.contains(e.getKey())) throw new IllegalArgumentException("\"paint\" override for unknown layer " + e.getKey());
            }
        }

        List<LabelRule> labels = new ArrayList<>();
        JsonNode labelNodes = root.get("labels");
        if (labelNodes != null && !labelNodes.isNull()) {
            if (!labelNodes.isArray()) throw new IllegalArgumentException("\"labels\" must be an array");
            for (JsonNode l : labelNodes) labels.add(label(l));
        } else if (base != null) {
            labels.addAll(base.labels());
        }

        Charset maxCharset = root.hasNonNull("maxCharset")
                ? enumValue(Charset.class, root.get("maxCharset").asText())
                : base != null ? base.maxCharset() : null;
        boolean monochrome = root.has("monochrome") ? root.get("monochrome").asBoolean() : base != null && base.monochrome();
        return new Style(layers, labels, maxCharset, monochrome);
    }

    private static StyleLayer layer(JsonNode l) {
        String id = text(l, "id", "layer");
        JsonNode p = l.get("paint");
        if (p == null) throw new IllegalArgumentException("layer " + id + ": missing \"paint\"");
        return new StyleLayer(
                id,
                text(l, "source", id),
                filter(l),
                l.path("minZoom").asDouble(0),
                l.path("maxZoom").asDouble(DEFAULT_MAX_ZOOM),
                paint(p, null, id),
                l.path("protect").asBoolean(false));
    }

    /** Reads a paint; fields missing from {@code p} come from {@code base}, or defaults when there is no base. */
    private static Paint paint(JsonNode p, Paint base, String id) {
        PaintKind kind = p.hasNonNull("kind") ? enumValue(PaintKind.class, p.get("kind").asText())
                : base != null ? base.kind() : null;
        if (kind == null) throw new IllegalArgumentException(id + ": missing string \"kind\"");
        Rgb fg = p.hasNonNull("fg") ? Rgb.fromHex(p.get("fg").asText()) : base != null ? base.fg() : null;
        if (fg == null) throw new IllegalArgumentException(id + ": missing string \"fg\"");
        Rgb bg = p.has("bg") ? (p.get("bg").isNull() ? null : Rgb.fromHex(p.get("bg").asText())) : base != null ? base.bg() : null;
        return new Paint(
                kind,
                fg,
                bg,
                p.hasNonNull("weight") ? enumValue(Weight.class, p.get("weight").asText()) : base != null ? base.weight() : Weight.LIGHT,
                p.hasNonNull("strategy") ? enumValue(GlyphStrategy.class, p.get("strategy").asText())
                        : base != null ? base.strategy() : GlyphStrategy.BRAILLE,
                p.hasNonNull("marker") ? marker(p.get("marker").asText(), id) : base != null ? base.marker() : Paint.DEFAULT_MARKER,
                p.hasNonNull("pattern") ? pattern(p.get("pattern").asText(), id) : base != null ? base.pattern() : Paint.SOLID);
    }

    private static LabelRule label(JsonNode l) {
        String id = text(l, "id", "label");
        return new LabelRule(
                id,
                text(l, "source", id),
                filter(l),
                l.path("minZoom").asDouble(0),
                l.path("maxZoom").asDouble(DEFAULT_MAX_ZOOM),
                l.hasNonNull("field") ? l.get("field").asText() : LabelRule.DEFAULT_FIELD,
                l.path("priority").asInt(0),
                l.path("maxWidth").asInt(LabelRule.DEFAULT_MAX_WIDTH),
                l.path("bold").asBoolean(false));
    }

    private static Map<String, List<String>> filter(JsonNode l) {
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
        return filter;
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

    /** A braille character's raised dots, converted to grid order (bit {@code row * 2 + col}). */
    static int pattern(String text, String context) {
        int cp = text.codePointCount(0, text.length()) == 1 ? text.codePointAt(0) : -1;
        if (cp <= Braille.BASE || cp > Braille.BASE + 0xff) {
            throw new IllegalArgumentException(context + ": \"pattern\" must be one braille character with at least one dot");
        }
        int mask = cp - Braille.BASE, grid = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                if ((mask & Braille.bit(col, row)) != 0) grid |= 1 << (row * 2 + col);
            }
        }
        return grid;
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
