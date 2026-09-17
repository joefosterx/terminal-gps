package dev.tilemap.view;

import dev.tilemap.core.TileId;

/** Everything the render loop reacts to, posted from the input thread, signal handlers and fetch threads. */
sealed interface Event {
    record KeyPressed(Key key) implements Event {}

    record Resized() implements Event {}

    record TileArrived(TileId id) implements Event {}
}
