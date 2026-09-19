package dev.tilemap.view;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.LonLat;
import dev.tilemap.lib.MapRequestJson;
import dev.tilemap.viewer.AppState;
import dev.tilemap.viewer.TileCache;
import dev.tilemap.viewer.ViewerConfig;
import java.nio.file.Path;
import java.util.Map;

/**
 * Effective viewer settings: built-in defaults, then {@code config.json}, then environment for the source, then
 * command-line flags. Parse errors throw {@link IllegalArgumentException}.
 */
record ViewerOptions(LonLat center, double zoom, String source, String key, String style, Charset charset, ColorDepth color,
                     boolean labels, int memoryTiles, boolean diskCache, Path cacheDir, Path configFile, boolean help) {
    static final String USAGE = """
            Usage: tilemap-view [--center LON,LAT] [--zoom Z] [--source URL|FILE] [--key KEY]
                                [--style NAME|FILE] [--charset ascii|box|braille] [--color none|16|256|true]
                                [--no-labels] [--no-disk-cache] [--config FILE] [--help]

            Interactive map viewer. Press ? inside for keys.
            Default source: $TILEMAP_SOURCE, else OpenFreeMap (https://tiles.openfreemap.org/planet).
            Settings file: $XDG_CONFIG_HOME/tilemap/config.json or ~/.config/tilemap/config.json.
            Style presets: default, dark, mono, vt220, high-contrast.
            """;

    /** The {@code --config} value if given, so the file can be read before the other flags are applied. */
    static Path configFlag(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--config")) return Path.of(args[i + 1]);
        }
        return null;
    }

    static ViewerOptions parse(String[] args, Map<String, String> env, ViewerConfig config, Path configFile) {
        LonLat center = config.centerOpt().map(c -> new LonLat(c[0], c[1])).orElse(new LonLat(0, 20));
        double zoom = config.zoom() != null ? config.zoom() : 2;
        String source = firstNonNull(env.get("TILEMAP_SOURCE"), config.source());
        String key = firstNonNull(env.get("TILEMAP_KEY"), config.key());
        String style = firstNonNull(config.style(), "default");
        Charset charset = config.charset() != null ? MapRequestJson.charset(config.charset()) : null;
        ColorDepth color = config.color() != null ? MapRequestJson.colorDepth(config.color()) : null;
        boolean labels = config.labels() == null || config.labels();
        int memoryTiles = config.memoryTiles() != null ? config.memoryTiles() : TileCache.DEFAULT_CAPACITY;
        boolean diskCache = config.diskCache() == null || config.diskCache();
        Path cacheDir = config.cacheDir() != null ? Path.of(config.cacheDir()) : null;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> help = true;
                case "--no-labels" -> labels = false;
                case "--no-disk-cache" -> diskCache = false;
                case "--config" -> value(args, ++i, arg);
                case "--center" -> {
                    String[] p = value(args, ++i, arg).split(",");
                    if (p.length != 2) throw new IllegalArgumentException("--center expects LON,LAT");
                    center = new LonLat(number(p[0], arg), number(p[1], arg));
                }
                case "--zoom" -> zoom = number(value(args, ++i, arg), arg);
                case "--source" -> source = value(args, ++i, arg);
                case "--key" -> key = value(args, ++i, arg);
                case "--style" -> style = value(args, ++i, arg);
                case "--charset" -> charset = MapRequestJson.charset(value(args, ++i, arg));
                case "--color" -> color = MapRequestJson.colorDepth(value(args, ++i, arg));
                default -> throw new IllegalArgumentException("unknown option " + arg);
            }
        }
        if (Math.abs(center.lon()) > 180 || Math.abs(center.lat()) > 90) throw new IllegalArgumentException("center out of range");
        if (zoom < 0 || zoom > AppState.MAX_ZOOM) throw new IllegalArgumentException("zoom must be between 0 and " + AppState.MAX_ZOOM);
        if (memoryTiles < 16) throw new IllegalArgumentException("memoryTiles must be at least 16");
        return new ViewerOptions(center, zoom, source, key, style, charset, color, labels, memoryTiles, diskCache, cacheDir, configFile, help);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " needs a value");
        return args[i];
    }

    private static double number(String text, String option) {
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + ": not a number: " + text, e);
        }
    }
}
