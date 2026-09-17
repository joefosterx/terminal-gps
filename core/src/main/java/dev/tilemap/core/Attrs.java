package dev.tilemap.core;

/** Text attributes that survive when color does not. */
public record Attrs(boolean bold, boolean dim) {
    public static final Attrs NONE = new Attrs(false, false);
}
