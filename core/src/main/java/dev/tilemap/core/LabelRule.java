package dev.tilemap.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which features get text labels. Features from source layer {@code source} matching {@code filter} (same rules as
 * {@link StyleLayer}) are labelled with the value of tag {@code field} while {@code minZoom <= zoom < maxZoom}.
 * Higher {@code priority} labels are placed first; text longer than {@code maxWidth} cells is truncated with an
 * ellipsis.
 */
public record LabelRule(String id, String source, Map<String, List<String>> filter, double minZoom, double maxZoom,
                        String field, int priority, int maxWidth, boolean bold) {
    public static final String DEFAULT_FIELD = "name";
    public static final int DEFAULT_MAX_WIDTH = 24;
    /** Labels that cannot show at least this many characters are dropped. */
    public static final int MIN_CHARS = 4;

    public LabelRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(field, "field");
        if (maxWidth < MIN_CHARS) throw new IllegalArgumentException("label " + id + ": maxWidth must be at least " + MIN_CHARS);
        filter = StyleLayer.normalize(filter);
    }

    public boolean visibleAt(double zoom) {
        return zoom >= minZoom && zoom < maxZoom;
    }

    public boolean matches(Feature f) {
        return StyleLayer.matches(filter, f);
    }
}
