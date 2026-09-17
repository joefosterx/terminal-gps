package dev.tilemap.core;

import java.util.Objects;

/**
 * How a style layer looks. {@code fg} is required; {@code bg} may be null to leave the background alone.
 * {@code marker} is the code point drawn by the {@link GlyphStrategy#MARKER} strategy. {@code pattern} limits which
 * dots of each cell a fill covers, in grid order (bit {@code row * 2 + col} of the 2×4 cell); {@link #SOLID} covers
 * all of them. Patterns let fills be told apart without color.
 */
public record Paint(PaintKind kind, Rgb fg, Rgb bg, Weight weight, GlyphStrategy strategy, int marker, int pattern) {
    public static final int DEFAULT_MARKER = '●';
    public static final int SOLID = 0xff;

    public Paint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fg, "fg");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(strategy, "strategy");
        if (!Character.isValidCodePoint(marker) || Character.isISOControl(marker)) {
            throw new IllegalArgumentException("bad marker code point " + marker);
        }
        if (pattern <= 0 || pattern > SOLID) throw new IllegalArgumentException("pattern must set 1 to 8 dots");
    }

    public Paint(PaintKind kind, Rgb fg, Rgb bg, Weight weight, GlyphStrategy strategy, int marker) {
        this(kind, fg, bg, weight, strategy, marker, SOLID);
    }

    public Paint(PaintKind kind, Rgb fg, Rgb bg, Weight weight, GlyphStrategy strategy) {
        this(kind, fg, bg, weight, strategy, DEFAULT_MARKER, SOLID);
    }
}
