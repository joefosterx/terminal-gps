package dev.tilemap.view;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import java.util.Locale;
import java.util.Map;

/**
 * Guesses terminal capabilities from the environment; command-line flags override the guess. The cursor-position
 * probe for braille width is part of milestone 7.
 */
final class TerminalCaps {
    private TerminalCaps() {}

    static Capabilities detect(Map<String, String> env, String os, Charset charsetFlag, ColorDepth colorFlag) {
        return new Capabilities(charsetFlag != null ? charsetFlag : charset(env), colorFlag != null ? colorFlag : color(env, os));
    }

    static ColorDepth color(Map<String, String> env, String os) {
        String colorterm = env.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        String term = env.getOrDefault("TERM", "").toLowerCase(Locale.ROOT);
        if (colorterm.equals("truecolor") || colorterm.equals("24bit")) return ColorDepth.TRUE;
        if (env.containsKey("WT_SESSION")) return ColorDepth.TRUE;   // Windows Terminal
        if (term.contains("256color")) return ColorDepth.C256;
        if (term.equals("dumb")) return ColorDepth.NONE;
        if (term.isEmpty() && os.toLowerCase(Locale.ROOT).startsWith("windows")) return ColorDepth.TRUE;  // Windows console host
        return ColorDepth.C16;
    }

    static Charset charset(Map<String, String> env) {
        String term = env.getOrDefault("TERM", "").toLowerCase(Locale.ROOT);
        if (term.equals("linux")) return Charset.BOX;          // Linux VT fonts have box drawing but not braille
        if (term.startsWith("vt") || term.equals("dumb")) return Charset.ASCII;
        return Charset.BRAILLE;
    }
}
