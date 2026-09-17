package dev.tilemap.view;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.LonLat;
import dev.tilemap.lib.MapRequestJson;
import java.util.Map;

/** Command-line options for {@code tilemap-view}. Parse errors throw {@link IllegalArgumentException}. */
record ViewerOptions(LonLat center, double zoom, String source, String key, String style, Charset charset, ColorDepth color,
                     boolean labels, boolean help) {
    static final String USAGE = """
            Usage: tilemap-view [--center LON,LAT] [--zoom Z] [--source URL|FILE] [--key KEY]
                                [--style NAME|FILE] [--charset ascii|box|braille] [--color none|16|256|true]
                                [--no-labels] [--help]

            Interactive map viewer. Press ? inside for keys.
            Default source: $TILEMAP_SOURCE, else OpenFreeMap (https://tiles.openfreemap.org/planet).
            """;

    static ViewerOptions parse(String[] args, Map<String, String> env) {
        LonLat center = new LonLat(0, 20);
        double zoom = 2;
        String source = env.get("TILEMAP_SOURCE"), key = env.get("TILEMAP_KEY"), style = "default";
        Charset charset = null;
        ColorDepth color = null;
        boolean labels = true, help = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> help = true;
                case "--no-labels" -> labels = false;
                case "--center" -> {
                    String[] p = value(args, ++i, arg).split(",");
                    if (p.length != 2) throw new IllegalArgumentException("--center expects LON,LAT");
                    center = new LonLat(number(p[0], arg), number(p[1], arg));
                    if (Math.abs(center.lon()) > 180 || Math.abs(center.lat()) > 90) throw new IllegalArgumentException("--center out of range");
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
        if (zoom < 0 || zoom > AppState.MAX_ZOOM) throw new IllegalArgumentException("--zoom must be between 0 and " + AppState.MAX_ZOOM);
        return new ViewerOptions(center, zoom, source, key, style, charset, color, labels, help);
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
