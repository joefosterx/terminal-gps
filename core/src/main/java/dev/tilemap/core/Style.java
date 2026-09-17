package dev.tilemap.core;

import dev.tilemap.core.Capabilities.Charset;
import java.util.List;

/**
 * An ordered list of drawing rules, where later layers have higher priority, plus label rules. {@code maxCharset}
 * (nullable) caps the charset the style renders with whatever the terminal supports, and {@code monochrome} drops
 * all colors; together they make honest ASCII or colorless presets.
 */
public record Style(List<StyleLayer> layers, List<LabelRule> labels, Charset maxCharset, boolean monochrome) {
    /** Layer indexes are stored as a {@code short} per dot. */
    public static final int MAX_LAYERS = Short.MAX_VALUE;

    public Style {
        layers = List.copyOf(layers);
        labels = List.copyOf(labels);
        if (layers.size() > MAX_LAYERS) throw new IllegalArgumentException("too many style layers");
    }

    public Style(List<StyleLayer> layers, List<LabelRule> labels) {
        this(layers, labels, null, false);
    }

    /** A style without labels. */
    public Style(List<StyleLayer> layers) {
        this(layers, List.of());
    }

    /** The charset to render with on a terminal that supports {@code terminal}. */
    public Charset effectiveCharset(Charset terminal) {
        return maxCharset != null && maxCharset.compareTo(terminal) < 0 ? maxCharset : terminal;
    }
}
