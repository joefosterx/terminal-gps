package dev.tilemap.lib;

import java.util.Locale;

/** Output encodings for a rendered map. */
public enum Format {
    /** Glyphs only, newline per row. */
    PLAIN,
    /** Glyphs plus SGR color codes, reset per row. */
    ANSI,
    /** A {@code <pre>} with colored spans. */
    HTML,
    /** One {@code <text>} per row with colored {@code <tspan>}s and background rectangles. */
    SVG,
    /** The canvas cells as {@code {glyph, fg, bg, layer}} objects. */
    JSON;

    public static Format parse(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown format " + name + " (plain, ansi, html, svg, json)", e);
        }
    }
}
