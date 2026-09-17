package dev.tilemap.view;

import java.io.IOException;

/**
 * Turns raw terminal input into {@link Key}s: printable characters, control keys, and the CSI/SS3 escape sequences
 * terminals send for arrows ({@code ESC [ A}, {@code ESC O A}) and shift-arrows ({@code ESC [ 1 ; 2 A}). A lone ESC
 * is recognized when nothing follows within {@link #ESCAPE_TIMEOUT_MS}.
 */
final class KeyDecoder {
    static final long ESCAPE_TIMEOUT_MS = 50;
    static final int EOF = -1, TIMEOUT = -2;

    /** A character stream with timed reads, matching JLine's {@code NonBlockingReader}. */
    interface CharSource {
        /** The next char, {@link #EOF}, or {@link #TIMEOUT}; {@code timeoutMs} 0 blocks. */
        int read(long timeoutMs) throws IOException;
    }

    private final CharSource in;

    KeyDecoder(CharSource in) {
        this.in = in;
    }

    /** Blocks for the next recognized key; returns null at end of input. Unrecognized input is skipped. */
    Key next() throws IOException {
        while (true) {
            int c = in.read(0);
            if (c == EOF) return null;
            if (c < 0) continue;
            Key key = decode(c);
            if (key != null) return key;
        }
    }

    private Key decode(int c) throws IOException {
        switch (c) {
            case 27 -> {
                return escape();
            }
            case '\r', '\n' -> {
                return Key.of(Key.Type.ENTER);
            }
            case 127, '\b' -> {
                return Key.of(Key.Type.BACKSPACE);
            }
            case 3 -> {
                return Key.of(Key.Type.INTERRUPT);
            }
            default -> {
                if (c < 0x20) return null;
                if (Character.isHighSurrogate((char) c)) {
                    int low = in.read(ESCAPE_TIMEOUT_MS);
                    return low >= 0 && Character.isLowSurrogate((char) low)
                            ? new Key(Key.Type.CHAR, Character.toCodePoint((char) c, (char) low))
                            : null;
                }
                return new Key(Key.Type.CHAR, c);
            }
        }
    }

    private Key escape() throws IOException {
        int next = in.read(ESCAPE_TIMEOUT_MS);
        if (next == TIMEOUT || next == EOF) return Key.of(Key.Type.ESCAPE);
        if (next != '[' && next != 'O') return null; // Alt+key: ignored
        StringBuilder params = new StringBuilder();
        while (true) {
            int ch = in.read(ESCAPE_TIMEOUT_MS);
            if (ch < 0) return null;
            if (ch >= 0x40 && ch <= 0x7e) return sequence(params.toString(), (char) ch);
            params.append((char) ch);
            if (params.length() > 16) return null;
        }
    }

    private static Key sequence(String params, char last) {
        boolean shift = params.endsWith(";2");
        return switch (last) {
            case 'A' -> Key.of(shift ? Key.Type.SHIFT_UP : Key.Type.UP);
            case 'B' -> Key.of(shift ? Key.Type.SHIFT_DOWN : Key.Type.DOWN);
            case 'C' -> Key.of(shift ? Key.Type.SHIFT_RIGHT : Key.Type.RIGHT);
            case 'D' -> Key.of(shift ? Key.Type.SHIFT_LEFT : Key.Type.LEFT);
            // rxvt reports shift-arrows as ESC [ a..d.
            case 'a' -> Key.of(Key.Type.SHIFT_UP);
            case 'b' -> Key.of(Key.Type.SHIFT_DOWN);
            case 'c' -> Key.of(Key.Type.SHIFT_RIGHT);
            case 'd' -> Key.of(Key.Type.SHIFT_LEFT);
            default -> null;
        };
    }
}
