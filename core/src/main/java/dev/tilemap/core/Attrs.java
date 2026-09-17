package dev.tilemap.core;

/** Text attributes that survive when color does not. */
public record Attrs(boolean bold, boolean dim, boolean reverse) {
    public static final Attrs NONE = new Attrs(false, false, false);

    public Attrs(boolean bold, boolean dim) {
        this(bold, dim, false);
    }
}
