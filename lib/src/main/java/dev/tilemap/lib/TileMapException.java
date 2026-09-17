package dev.tilemap.lib;

/** A map could not be rendered completely: the source could not be opened or tiles failed to load. */
public class TileMapException extends Exception {
    public TileMapException(String message) {
        super(message);
    }

    public TileMapException(String message, Throwable cause) {
        super(message, cause);
    }
}
