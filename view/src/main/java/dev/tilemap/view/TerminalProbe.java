package dev.tilemap.view;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.terminal.Terminal;

/**
 * Asks the terminal how wide a braille glyph really is: move to column 1, print one, and request the cursor position
 * ({@code CSI 6n}). One column means braille is usable; two means the font renders it double-width (or as tofu),
 * so box drawing is the better choice. Terminals that never answer are left to the environment-based guess.
 */
final class TerminalProbe {
    static final long TIMEOUT_MS = 400;
    private static final Pattern REPORT = Pattern.compile("\u001b\\[(\\d+);(\\d+)R");

    private TerminalProbe() {}

    /** Width in columns of {@code ⣿}, or empty if the terminal did not report. Must run in raw mode before input is read. */
    static OptionalInt brailleWidth(Terminal terminal) {
        terminal.writer().write("\r⣿\u001b[6n");
        terminal.flush();
        StringBuilder reply = new StringBuilder();
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000;
        try {
            while (true) {
                long left = (deadline - System.nanoTime()) / 1_000_000;
                if (left <= 0) break;
                int c = terminal.reader().read(left);
                if (c < 0) break;
                reply.append((char) c);
                if (c == 'R') break;
            }
        } catch (IOException e) {
            return OptionalInt.empty();
        } finally {
            terminal.writer().write("\r\u001b[K");
            terminal.flush();
        }
        return column(reply).stream().map(col -> col - 1).findFirst();
    }

    /** The 1-based column of a cursor position report anywhere in {@code text}. */
    static OptionalInt column(CharSequence text) {
        Matcher m = REPORT.matcher(text);
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group(2))) : OptionalInt.empty();
    }
}
