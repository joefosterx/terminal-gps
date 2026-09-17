package dev.tilemap.core;

import java.util.List;

/** An ordered list of drawing rules; later layers have higher priority. */
public record Style(List<StyleLayer> layers) {
    /** Layer indexes are stored as a {@code short} per dot. */
    public static final int MAX_LAYERS = Short.MAX_VALUE;

    public Style {
        layers = List.copyOf(layers);
        if (layers.size() > MAX_LAYERS) throw new IllegalArgumentException("too many style layers");
    }
}
