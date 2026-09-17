package dev.tilemap.core;

/** A 24-bit color. */
public record Rgb(int r, int g, int b) {
    public Rgb {
        if (((r | g | b) & ~0xff) != 0) throw new IllegalArgumentException("channel out of range");
    }

    /** Parses {@code #rrggbb}. */
    public static Rgb fromHex(String hex) {
        if (hex.length() != 7 || hex.charAt(0) != '#') throw new IllegalArgumentException("expected #rrggbb: " + hex);
        int v = Integer.parseInt(hex.substring(1), 16);
        return new Rgb(v >> 16 & 0xff, v >> 8 & 0xff, v & 0xff);
    }

    public String toHex() {
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
