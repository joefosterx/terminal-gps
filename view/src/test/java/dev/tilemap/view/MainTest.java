package dev.tilemap.view;

import dev.tilemap.viewer.ViewerConfig;
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
                "--color", "16", "--no-labels", "--source", "x.pmtiles"}, Map.of("TILEMAP_KEY", "k"), ViewerConfig.EMPTY, null);
        assertEquals(new LonLat(-0.12, 51.5), o.center());
        assertEquals(14.5, o.zoom());
        assertEquals(Charset.BOX, o.charset());
        assertEquals(ColorDepth.C16, o.color());
        assertFalse(o.labels());
        assertEquals("x.pmtiles", o.source());
        assertEquals("k", o.key());

        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--zoom"}, Map.of(), ViewerConfig.EMPTY, null));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--zoom", "40"}, Map.of(), ViewerConfig.EMPTY, null));
        assertThrows(IllegalArgumentException.class, () -> ViewerOptions.parse(new String[] {"--bogus"}, Map.of(), ViewerConfig.EMPTY, null));
        assertTrue(ViewerOptions.parse(new String[] {"--help"}, Map.of(), ViewerConfig.EMPTY, null).help());
    }

    @Test
    void configFileThenEnvironmentThenFlags(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("mine.json"), "{\"extends\": \"dark\"}");
        java.nio.file.Path file = dir.resolve("config.json");
        java.nio.file.Files.writeString(file, """
                {"source": "https://tiles.example/{z}/{x}/{y}.pbf", "style": "mine.json", "center": [2.35, 48.86], "zoom": 13,
                 "charset": "box", "labels": false, "memoryTiles": 128, "diskCache": false}
                """);
        ViewerConfig config = ViewerConfig.load(file);
        ViewerOptions fromConfig = ViewerOptions.parse(new String[0], Map.of(), config, file);
        assertEquals(new LonLat(2.35, 48.86), fromConfig.center());
        assertEquals(13, fromConfig.zoom());
        assertEquals(dir.resolve("mine.json").toString(), fromConfig.style());
        assertEquals(Charset.BOX, fromConfig.charset());
        assertFalse(fromConfig.labels());
        assertEquals(128, fromConfig.memoryTiles());
        assertFalse(fromConfig.diskCache());

        ViewerOptions env = ViewerOptions.parse(new String[0], Map.of("TILEMAP_SOURCE", "env.pmtiles"), config, file);
        assertEquals("env.pmtiles", env.source());
        ViewerOptions flags = ViewerOptions.parse(new String[] {"--config", file.toString(), "--style", "vt220", "--zoom", "3"},
                Map.of("TILEMAP_SOURCE", "env.pmtiles"), config, file);
        assertEquals("vt220", flags.style());
        assertEquals(3, flags.zoom());
        assertEquals(file, ViewerOptions.configFlag(new String[] {"--zoom", "3", "--config", file.toString()}));

        assertEquals(ViewerConfig.EMPTY, ViewerConfig.load(dir.resolve("absent.json")));
        java.nio.file.Files.writeString(dir.resolve("bad.json"), "{\"center\": [1]}");
        assertThrows(IllegalArgumentException.class, () -> ViewerConfig.load(dir.resolve("bad.json")));
        assertEquals(java.nio.file.Path.of("/x", "tilemap", "config.json"), ViewerConfig.defaultPath(Map.of("XDG_CONFIG_HOME", "/x"), java.nio.file.Path.of("/h")));
        assertEquals(java.nio.file.Path.of("/h", ".config", "tilemap", "config.json"), ViewerConfig.defaultPath(Map.of(), java.nio.file.Path.of("/h")));
    }

    @Test
    void brailleProbe() throws Exception {
        assertEquals(java.util.OptionalInt.of(3), TerminalProbe.column("junk\u001b[12;3R"));
        assertTrue(TerminalProbe.column("nothing").isEmpty());

        dev.tilemap.core.Capabilities guess = new dev.tilemap.core.Capabilities(Charset.BRAILLE, ColorDepth.TRUE);
        assertEquals(guess, Main.probed(guess, java.util.OptionalInt.of(1)));
        assertEquals(guess, Main.probed(guess, java.util.OptionalInt.empty()));
        assertEquals(Charset.BOX, Main.probed(guess, java.util.OptionalInt.of(2)).charset());

        for (String reply : new String[] {"\u001b[7;2R", "\u001b[7;3R"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm-256color")
                    .streams(new ByteArrayInputStream(reply.getBytes(StandardCharsets.UTF_8)), out).size(new Size(20, 5))
                    .encoding(StandardCharsets.UTF_8).stdinEncoding(StandardCharsets.UTF_8).stdoutEncoding(StandardCharsets.UTF_8).build()) {
                terminal.enterRawMode();
                java.util.OptionalInt width = TerminalProbe.brailleWidth(terminal);
                assertEquals(reply.endsWith("2R") ? 1 : 2, width.getAsInt());
            }
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("⣿\u001b[6n"));
        }
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
            display.copy("hi");
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
        assertTrue(written.contains("\u001b]52;c;aGk=\u0007"), "OSC 52 copy");
        assertTrue(written.contains("\u001b[?1049l"), "leaves the alternate screen");
        assertTrue(written.contains("\u001b[?1006h") && written.contains("\u001b[?1006l"), "mouse reporting on and off");
    }
}
