package dev.tilemap.view;

/**
 * A decoded key press or mouse event, independent of the terminal library. {@code ch} is set only for
 * {@link Type#CHAR}; {@code col} and {@code row} (0-based screen cells) only for mouse types.
 */
record Key(Type type, int ch, int col, int row) {
    enum Type {
        CHAR, UP, DOWN, LEFT, RIGHT, SHIFT_UP, SHIFT_DOWN, SHIFT_LEFT, SHIFT_RIGHT, ENTER, ESCAPE, BACKSPACE, INTERRUPT,
        MOUSE_DOWN, MOUSE_DRAG, MOUSE_UP, WHEEL_UP, WHEEL_DOWN;

        boolean isMouse() {
            return ordinal() >= MOUSE_DOWN.ordinal();
        }
    }

    static Key of(char ch) {
        return new Key(Type.CHAR, ch, -1, -1);
    }

    static Key ofCodePoint(int codePoint) {
        return new Key(Type.CHAR, codePoint, -1, -1);
    }

    static Key of(Type type) {
        return new Key(type, 0, -1, -1);
    }

    static Key mouse(Type type, int col, int row) {
        return new Key(type, 0, col, row);
    }
}
