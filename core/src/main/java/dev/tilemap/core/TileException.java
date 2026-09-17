package dev.tilemap.core;

/** A tile could not be fetched or decoded. */
public class TileException extends Exception {
    public TileException(String message) {
        super(message);
    }

    public TileException(String message, Throwable cause) {
        super(message, cause);
    }
}
