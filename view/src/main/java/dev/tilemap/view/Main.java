package dev.tilemap.view;

import dev.tilemap.core.Style;
import dev.tilemap.core.TileSource;
import dev.tilemap.lib.MapRequestJson;
import dev.tilemap.lib.SourceConfig;
import dev.tilemap.lib.TileSources;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** {@code tilemap-view}: a full-screen, pannable map. Exit codes: 0 ok, 2 bad arguments or no terminal, 3 source failure. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        ViewerOptions opts;
        Style style;
        SourceConfig config;
        try {
            opts = ViewerOptions.parse(args, System.getenv());
            if (opts.help()) {
                System.out.print(ViewerOptions.USAGE);
                return 0;
            }
            style = MapRequestJson.style(opts.style());
            config = opts.source() == null
                    ? (opts.key() == null ? SourceConfig.OPENFREEMAP : new SourceConfig.Url(SourceConfig.OPENFREEMAP.template(), opts.key()))
                    : SourceConfig.fromString(opts.source(), opts.key());
        } catch (IllegalArgumentException | IOException e) {
            err.println("tilemap-view: " + e.getMessage());
            err.print(ViewerOptions.USAGE);
            return 2;
        }

        TileSource upstream;
        try {
            upstream = TileSources.open(config, 0);
        } catch (IOException e) {
            err.println("tilemap-view: cannot open source: " + e.getMessage());
            return 3;
        }
        String attribution = config.equals(SourceConfig.OPENFREEMAP) || (config instanceof SourceConfig.Url u
                && u.template().equals(SourceConfig.OPENFREEMAP.template())) ? "© OpenMapTiles © OpenStreetMap" : "";

        try (Terminal terminal = TerminalBuilder.builder().system(true)
                     // stdout otherwise follows stdout.encoding, which is the legacy code page on Windows.
                     .encoding(StandardCharsets.UTF_8).stdinEncoding(StandardCharsets.UTF_8).stdoutEncoding(StandardCharsets.UTF_8)
                     .build();
             ExecutorService fetchers = Executors.newVirtualThreadPerTaskExecutor()) {
            if (terminal.getType().startsWith(Terminal.TYPE_DUMB) || terminal.getWidth() <= 0) {
                err.println("tilemap-view: needs an interactive terminal (use tilemap for scripts and pipes)");
                return 2;
            }
            BlockingQueue<Event> events = new LinkedBlockingQueue<>();
            AppState state = new AppState(opts.center(), opts.zoom(), opts.style(), style,
                    TerminalCaps.detect(System.getenv(), System.getProperty("os.name", ""), opts.charset(), opts.color()));
            state.labels = opts.labels();
            TileCache cache = new TileCache(upstream, TileCache.DEFAULT_CAPACITY, fetchers,
                    id -> events.offer(new Event.TileArrived(id)), System::nanoTime);

            terminal.handle(Terminal.Signal.WINCH, s -> events.offer(new Event.Resized()));
            terminal.handle(Terminal.Signal.INT, s -> events.offer(new Event.KeyPressed(Key.of(Key.Type.INTERRUPT))));

            try (JLineDisplay display = new JLineDisplay(terminal)) {
                Thread input = Thread.ofPlatform().daemon().name("tilemap-input").start(() -> readKeys(terminal, events));
                new Viewer(state, cache, display, events, attribution).run();
                input.interrupt();
            }
            return 0;
        } catch (IOException e) {
            err.println("tilemap-view: terminal error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        } finally {
            if (upstream instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // Exiting anyway.
                }
            }
        }
    }

    /** Runs on a dedicated platform thread until end of input. */
    static void readKeys(Terminal terminal, BlockingQueue<Event> events) {
        KeyDecoder decoder = new KeyDecoder(timeout -> terminal.reader().read(timeout));
        try {
            Key key;
            while ((key = decoder.next()) != null) events.put(new Event.KeyPressed(key));
            events.put(new Event.KeyPressed(Key.of(Key.Type.INTERRUPT)));
        } catch (IOException e) {
            events.offer(new Event.KeyPressed(Key.of(Key.Type.INTERRUPT)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
