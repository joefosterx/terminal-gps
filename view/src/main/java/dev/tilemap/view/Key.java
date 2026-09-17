package dev.tilemap.view;

/** A decoded key press, independent of the terminal library. {@code ch} is set only for {@link Type#CHAR}. */
record Key(Type type, int ch) {
    enum Type {
        CHAR, UP, DOWN, LEFT, RIGHT, SHIFT_UP, SHIFT_DOWN, SHIFT_LEFT, SHIFT_RIGHT, ENTER, ESCAPE, BACKSPACE, INTERRUPT
    }

    static Key of(char ch) {
        return new Key(Type.CHAR, ch);
    }

    static Key of(Type type) {
        return new Key(type, 0);
    }
}
