package dev.tilemap.core;

import java.util.List;

/** An ordered list of drawing rules, where later layers have higher priority, plus label rules. */
public record Style(List<StyleLayer> layers, List<LabelRule> labels) {
    /** Layer indexes are stored as a {@code short} per dot. */
    public static final int MAX_LAYERS = Short.MAX_VALUE;

    public Style {
        layers = List.copyOf(layers);
        labels = List.copyOf(labels);
        if (layers.size() > MAX_LAYERS) throw new IllegalArgumentException("too many style layers");
    }

    /** A style without labels. */
    public Style(List<StyleLayer> layers) {
        this(layers, List.of());
    }
}
