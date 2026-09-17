package dev.tilemap.view;

import dev.tilemap.core.Ansi;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
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
        terminal.writer().write(Ansi.RESET);
        terminal.puts(Capability.cursor_visible);
        terminal.puts(Capability.keypad_local);
        terminal.puts(Capability.exit_ca_mode);
        terminal.flush();
        terminal.setAttributes(saved);
    }
}
