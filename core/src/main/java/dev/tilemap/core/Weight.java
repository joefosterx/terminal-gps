package dev.tilemap.core;

/** Line weight: the box-drawing variant for roads, and the brush width in dots for dot-rastered lines. */
public enum Weight {
    LIGHT(1), HEAVY(2), DOUBLE(2);

    private final int dots;

    Weight(int dots) {
        this.dots = dots;
    }

    public int dots() {
        return dots;
    }
}
