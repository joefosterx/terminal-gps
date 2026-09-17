package dev.tilemap.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.LonLat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void parsesOptions() {
        ViewerOptions o = ViewerOptions.parse(new String[] {"--center", "-0.12,51.5", "--zoom", "14.5", "--charset", "box",
                "--color", "16", "--no-labels", "--source", "x.pmtiles"}, Map.of("TILEMAP_KEY", "k"));
        assertEquals(new LonLat(-0.12, 51.5), o.center());
        assertEquals(14.5, o.zoom());
        assertEquals(Charset.BOX, o.charset());
        assertEquals(ColorDepth.C16, o.color());
        assertFalse(o.labels());
        assertEquals("x.pmtiles", o.source());
        assertEquals("k", o.key());

        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--zoom"}, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--zoom", "40"}, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--bogus"}, Map.of()));
        assertTrue(ViewerOptions.parse(new String[] {"--help"}, Map.of()).help());
    }

    @Test
    void detectsCapabilitiesFromEnvironment() {
        assertEquals(ColorDepth.TRUE, TerminalCaps.color(Map.of("COLORTERM", "truecolor", "TERM", "xterm"), "Linux"));
        assertEquals(ColorDepth.C256, TerminalCaps.color(Map.of("TERM", "screen-256color"), "Linux"));
        assertEquals(ColorDepth.C16, TerminalCaps.color(Map.of("TERM", "xterm"), "Linux"));
        assertEquals(ColorDepth.TRUE, TerminalCaps.color(Map.of("WT_SESSION", "x"), "Windows 11"));
        assertEquals(Charset.BOX, TerminalCaps.charset(Map.of("TERM", "linux")));
        assertEquals(Charset.ASCII, TerminalCaps.charset(Map.of("TERM", "vt220")));
        assertEquals(Charset.BRAILLE, TerminalCaps.charset(Map.of("TERM", "xterm-kitty")));
        assertEquals(Charset.ASCII, TerminalCaps.detect(Map.of(), "Linux", Charset.ASCII, null).charset());
    }

    /** Drives the JLine adapters over in-memory streams with a fake xterm. */
    @Test
    void jlineDisplayAndInputRoundTrip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] typed = "j\u001b[1;2Cq".getBytes(StandardCharsets.UTF_8);
        try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(typed), out).size(new Size(20, 5)).encoding(StandardCharsets.UTF_8)
                .stdinEncoding(StandardCharsets.UTF_8).stdoutEncoding(StandardCharsets.UTF_8).build()) {
            JLineDisplay display = new JLineDisplay(terminal);
            assertEquals(20, display.cols());
            assertEquals(5, display.rows());
            dev.tilemap.core.Cell[] frame = new dev.tilemap.core.Cell[100];
            java.util.Arrays.fill(frame, dev.tilemap.core.Cell.EMPTY);
            frame[0] = new dev.tilemap.core.Cell('⣿', null, null, dev.tilemap.core.Attrs.NONE, 0);
            display.draw(frame, 20, 5, ColorDepth.C256);
            display.close();

            LinkedBlockingQueue<Event> events = new LinkedBlockingQueue<>();
            Main.readKeys(terminal, events);
            assertEquals(new Event.KeyPressed(Key.of('j')), events.take());
            assertEquals(new Event.KeyPressed(Key.of(Key.Type.SHIFT_RIGHT)), events.take());
            assertEquals(new Event.KeyPressed(Key.of('q')), events.take());
            assertEquals(new Event.KeyPressed(Key.of(Key.Type.INTERRUPT)), events.take(), "end of input quits");
        }
        String written = out.toString(StandardCharsets.UTF_8);
        assertTrue(written.contains("\u001b[?1049h"), "enters the alternate screen");
        assertTrue(written.contains("\u001b[1;1H⣿"), written);
        assertTrue(written.contains("\u001b[?1049l"), "leaves the alternate screen");
    }
}
