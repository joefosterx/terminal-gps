package dev.tilemap.core;

import java.util.Objects;

/**
 * How a style layer looks. {@code fg} is required; {@code bg} may be null to leave the background alone.
 * {@code marker} is the code point drawn by the {@link GlyphStrategy#MARKER} strategy.
 */
public record Paint(PaintKind kind, Rgb fg, Rgb bg, Weight weight, GlyphStrategy strategy, int marker) {
    public static final int DEFAULT_MARKER = '●';

    public Paint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fg, "fg");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(strategy, "strategy");
        if (!Character.isValidCodePoint(marker) || Character.isISOControl(marker)) {
            throw new IllegalArgumentException("bad marker code point " + marker);
        }
    }

    public Paint(PaintKind kind, Rgb fg, Rgb bg, Weight weight, GlyphStrategy strategy) {
        this(kind, fg, bg, weight, strategy, DEFAULT_MARKER);
    }
}
