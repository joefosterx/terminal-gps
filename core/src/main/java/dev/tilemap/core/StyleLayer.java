package dev.tilemap.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One drawing rule: features from source layer {@code source} whose tags match {@code filter}, drawn with
 * {@code paint} when {@code minZoom <= zoom < maxZoom}. A filter entry matches when the tag's value is one of
 * the listed values; the single value {@code "*"} matches any value as long as the tag is present. Cells owned by a
 * {@code protect}ed layer are never covered by labels.
 */
public record StyleLayer(String id, String source, Map<String, List<String>> filter, double minZoom, double maxZoom,
                         Paint paint, boolean protect) {
    public StyleLayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(paint, "paint");
        filter = normalize(filter);
    }

    public StyleLayer(String id, String source, Map<String, List<String>> filter, double minZoom, double maxZoom, Paint paint) {
        this(id, source, filter, minZoom, maxZoom, paint, false);
    }

    public boolean visibleAt(double zoom) {
        return zoom >= minZoom && zoom < maxZoom;
    }

    public boolean matches(Feature f) {
        return matches(filter, f);
    }

    static Map<String, List<String>> normalize(Map<String, List<String>> filter) {
        TreeMap<String, List<String>> sorted = new TreeMap<>();
        filter.forEach((k, v) -> sorted.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(sorted);
    }

    static boolean matches(Map<String, List<String>> filter, Feature f) {
        for (var e : filter.entrySet()) {
            String value = f.tags().get(e.getKey());
            if (value == null) return false;
            List<String> allowed = e.getValue();
            if (!(allowed.equals(List.of("*")) || allowed.contains(value))) return false;
        }
        return true;
    }

    /** Whether this layer's paint kind draws the given geometry; polygon outlines count as lines. */
    public boolean draws(Geometry g) {
        return switch (paint.kind()) {
            case FILL -> g instanceof Geometry.Polygon;
            case LINE -> g instanceof Geometry.Line || g instanceof Geometry.Polygon;
            case POINT -> g instanceof Geometry.Point;
        };
    }
}
