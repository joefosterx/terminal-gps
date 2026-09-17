package dev.tilemap.core;

/** Rendering failed, usually because the {@link TileSource} did. */
public class RenderException extends Exception {
    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
