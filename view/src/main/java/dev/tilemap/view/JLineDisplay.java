package dev.tilemap.view;

import dev.tilemap.core.Ansi;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

/** A {@link Display} on a JLine terminal: raw mode, alternate screen, hidden cursor; restored on close. */
final class JLineDisplay implements Display {
    private final Terminal terminal;
    private final Attributes saved;
    private final ScreenDiff diff = new ScreenDiff();

    JLineDisplay(Terminal terminal) {
        this.terminal = terminal;
        this.saved = terminal.enterRawMode();
        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.keypad_xmit);
        terminal.puts(Capability.cursor_invisible);
        // Button-event tracking with SGR coordinates; JLine translates Windows console mouse input itself.
        terminal.trackMouse(Terminal.MouseTracking.Button);
        terminal.writer().write("\u001b[?1006h");
        terminal.flush();
    }

    /** OSC 52. Most modern terminals accept it; tmux needs {@code set -g set-clipboard on}. */
    @Override
    public void copy(String text) {
        terminal.writer().write("\u001b]52;c;" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\u0007");
        terminal.flush();
    }

    @Override
    public int cols() {
        return terminal.getWidth();
    }

    @Override
    public int rows() {
        return terminal.getHeight();
    }

    @Override
    public void draw(Cell[] frame, int cols, int rows, ColorDepth depth) {
        String out = diff.render(frame, cols, rows, depth);
        if (out.isEmpty()) return;
        terminal.writer().write(out);
        terminal.flush();
    }

    @Override
    public void invalidate() {
        diff.invalidate();
    }

    @Override
    public void close() {
        terminal.writer().write("\u001b[?1006l");
        terminal.trackMouse(Terminal.MouseTracking.Off);
        terminal.writer().write(Ansi.RESET);
        terminal.puts(Capability.cursor_visible);
        terminal.puts(Capability.keypad_local);
        terminal.puts(Capability.exit_ca_mode);
        terminal.flush();
        terminal.setAttributes(saved);
    }
}
