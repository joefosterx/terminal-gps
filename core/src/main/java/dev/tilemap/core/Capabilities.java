package dev.tilemap.core;

import java.util.Objects;

/** What the output terminal can show. Chosen by the caller; the core never detects it. */
public record Capabilities(Charset charset, ColorDepth color) {
    public enum Charset { ASCII, LATIN1, BOX, BRAILLE, SEXTANT }

    public enum ColorDepth { NONE, C16, C256, TRUE }

    /** The CLI default: braille and 256 colors. */
    public static final Capabilities DEFAULT = new Capabilities(Charset.BRAILLE, ColorDepth.C256);

    public Capabilities {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(color, "color");
    }
}
