package dev.tilemap.cli;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Style;
import dev.tilemap.lib.Area;
import dev.tilemap.lib.Format;
import dev.tilemap.lib.MapRequest;
import dev.tilemap.lib.MapRequestJson;
import dev.tilemap.lib.SourceConfig;
import dev.tilemap.lib.TileMap;
import dev.tilemap.lib.TileMapException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** {@code tilemap}: render a map to text. Exit codes: 0 ok, 2 bad arguments, 3 tile fetch failure. */
@Command(
        name = "tilemap",
        mixinStandardHelpOptions = true,
        version = "tilemap 0.1.0",
        sortOptions = false,
        usageHelpWidth = 100,
        description = "Renders vector map tiles as Unicode text.",
        footer = {
            "",
            "Sources: an http(s) tile URL template with {z}/{x}/{y} or a TileJSON URL, a .pmtiles file, or a",
            ".geojson file. Default: $TILEMAP_SOURCE, else OpenFreeMap (" + "https://tiles.openfreemap.org/planet).",
            "Maps from OpenFreeMap must credit: (c) OpenMapTiles (c) OpenStreetMap contributors.",
            "",
            "Exit codes: 0 ok, 2 bad arguments, 3 tile fetch failure (the partial map is still written",
            "unless --strict)."
        })
public final class Main implements Callable<Integer> {
    static final int EXIT_OK = 0, EXIT_USAGE = 2, EXIT_FETCH = 3;
    private static final int DEFAULT_COLS = 80, DEFAULT_ROWS = 24;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--bbox", paramLabel = "W,S,E,N", description = "Bounding box in degrees; picks the zoom that fits.")
    String bbox;

    @Option(names = "--center", paramLabel = "LON,LAT", description = "Map center in degrees (use with --zoom).")
    String center;

    @Option(names = "--zoom", paramLabel = "Z", description = "Zoom level, fractional allowed.")
    Double zoom;

    @Option(names = "--size", paramLabel = "COLSxROWS", description = "Output size in cells (default 80x24).")
    String size;

    @Option(names = "--fit-terminal", description = "Size the map to the current terminal, leaving one row for the prompt.")
    boolean fitTerminal;

    @Option(names = "--request", paramLabel = "FILE", description = "Read area, size, style, charset, color and source from JSON.")
    Path request;

    @Option(names = "--style", paramLabel = "NAME|FILE", description = "Style preset or JSON style file (default: default).")
    String style;

    @Option(names = "--charset", paramLabel = "SET", description = "ascii, latin1, box, braille or sextant (default: braille).")
    String charset;

    @Option(names = "--color", paramLabel = "DEPTH", description = "none, 16, 256 or true (default: 256).")
    String color;

    @Option(names = "--format", paramLabel = "FMT", defaultValue = "plain", description = "plain, ansi, html, svg or json (default: plain).")
    String format;

    @Option(names = "--source", paramLabel = "URL|FILE", description = "Tile source; see below.")
    String source;

    @Option(names = "--key", paramLabel = "KEY", description = "API key for the tile server (default: $TILEMAP_KEY).")
    String key;

    @Option(names = "--no-labels", description = "Skip the label pass (fastest).")
    boolean noLabels;

    @Option(names = "--strict", description = "On tile failures, write nothing instead of a partial map.")
    boolean strict;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE", description = "Write to FILE instead of stdout.")
    Path output;

    private final Map<String, String> env;
    private final Function<Map<String, String>, Optional<TerminalSize.Size>> terminal;

    Main(Map<String, String> env, Function<Map<String, String>, Optional<TerminalSize.Size>> terminal) {
        this.env = env;
        this.terminal = terminal;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err, System.getenv(), TerminalSize::detect));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Map<String, String> env,
                   Function<Map<String, String>, Optional<TerminalSize.Size>> terminal) {
        CommandLine cli = new CommandLine(new Main(env, terminal));
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        cli.setExecutionExceptionHandler((e, cmd, parsed) -> {
            cmd.getErr().println("tilemap: " + e.getMessage());
            return e instanceof IllegalArgumentException ? EXIT_USAGE : 1;
        });
        return cli.execute(args);
    }

    @Override
    public Integer call() throws IOException {
        MapRequest req;
        Format fmt;
        try {
            req = buildRequest();
            fmt = Format.parse(format);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage(), e);
        }

        TileMap.Result result;
        try {
            result = TileMap.render(req);
        } catch (TileMapException e) {
            spec.commandLine().getErr().println("tilemap: " + e.getMessage());
            return EXIT_FETCH;
        }
        for (TileMap.TileFailure f : result.failures()) {
            spec.commandLine().getErr().println("tilemap: tile " + f.id() + ": " + f.message());
        }
        if (!result.complete() && strict) return EXIT_FETCH;

        String text = TileMap.format(result.canvas(), fmt, req.caps());
        if (output != null) {
            Files.writeString(output, text, StandardCharsets.UTF_8);
        } else {
            PrintWriter w = spec.commandLine().getOut();
            w.print(text);
            w.flush();
        }
        return result.complete() ? EXIT_OK : EXIT_FETCH;
    }

    private MapRequest buildRequest() throws IOException {
        if (request != null) {
            if (bbox != null || center != null || zoom != null || size != null || fitTerminal) {
                throw new IllegalArgumentException("--request cannot be combined with --bbox, --center, --zoom, --size or --fit-terminal");
            }
            MapRequest base = MapRequestJson.read(request);
            return new MapRequest(base.area(), base.cols(), base.rows(),
                    style != null ? MapRequestJson.style(style) : base.style(),
                    new Capabilities(
                            charset != null ? MapRequestJson.charset(charset) : base.caps().charset(),
                            color != null ? MapRequestJson.colorDepth(color) : base.caps().color()),
                    source != null ? sourceConfig() : base.source(),
                    base.labels() && !noLabels);
        }

        TerminalSize.Size cells = cells();
        Style resolvedStyle = style != null ? MapRequestJson.style(style) : null;
        Capabilities caps = new Capabilities(
                charset != null ? MapRequestJson.charset(charset) : Capabilities.DEFAULT.charset(),
                color != null ? MapRequestJson.colorDepth(color) : Capabilities.DEFAULT.color());
        return new MapRequest(area(), cells.cols(), cells.rows(), resolvedStyle, caps, sourceConfig(), !noLabels);
    }

    private Area area() {
        if (bbox != null) {
            if (center != null || zoom != null) throw new IllegalArgumentException("use either --bbox or --center/--zoom, not both");
            double[] b = numbers(bbox, 4, "--bbox expects W,S,E,N");
            return new Area.BBox(b[0], b[1], b[2], b[3]);
        }
        if (center == null || zoom == null) throw new IllegalArgumentException("give --bbox, or both --center and --zoom");
        double[] c = numbers(center, 2, "--center expects LON,LAT");
        return new Area.Center(c[0], c[1], zoom);
    }

    private TerminalSize.Size cells() {
        if (size != null && fitTerminal) throw new IllegalArgumentException("use either --size or --fit-terminal, not both");
        if (size != null) {
            Matcher m = Pattern.compile("(\\d+)[xX](\\d+)").matcher(size.strip());
            if (!m.matches()) throw new IllegalArgumentException("--size expects COLSxROWS, e.g. 120x40");
            int cols = Integer.parseInt(m.group(1)), rows = Integer.parseInt(m.group(2));
            if (cols <= 0 || rows <= 0) throw new IllegalArgumentException("--size must be at least 1x1");
            return new TerminalSize.Size(cols, rows);
        }
        if (fitTerminal) {
            Optional<TerminalSize.Size> detected = terminal.apply(env);
            if (detected.isPresent()) {
                TerminalSize.Size s = detected.get();
                return new TerminalSize.Size(s.cols(), Math.max(1, s.rows() - 1));
            }
            spec.commandLine().getErr().println("tilemap: could not detect the terminal size; using "
                    + DEFAULT_COLS + "x" + DEFAULT_ROWS + " (set COLUMNS and LINES to override)");
        }
        return new TerminalSize.Size(DEFAULT_COLS, DEFAULT_ROWS);
    }

    private SourceConfig sourceConfig() throws IOException {
        String k = key != null ? key : env.get("TILEMAP_KEY");
        String s = source != null ? source : env.get("TILEMAP_SOURCE");
        if (s == null || s.isBlank()) return k == null ? SourceConfig.OPENFREEMAP : new SourceConfig.Url(SourceConfig.OPENFREEMAP.template(), k);
        return SourceConfig.fromString(s, k);
    }

    private static double[] numbers(String text, int n, String message) {
        String[] parts = text.split(",");
        if (parts.length != n) throw new IllegalArgumentException(message);
        double[] out = new double[n];
        try {
            for (int i = 0; i < n; i++) out[i] = Double.parseDouble(parts[i].strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message, e);
        }
        return out;
    }
}
