package dev.tilemap.view;

import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;

/** A full-screen cell grid the viewer draws into. */
interface Display extends AutoCloseable {
    int cols();

    int rows();

    /** Shows {@code frame} ({@code cols × rows}, row-major), redrawing only what changed since the last call. */
    void draw(Cell[] frame, int cols, int rows, ColorDepth depth);

    /** Puts text on the system clipboard, if the terminal supports it. */
    default void copy(String text) {}

    /** Forgets what is on screen so the next draw repaints everything (after a resize). */
    void invalidate();

    @Override
    void close();
}
