package dev.tilemap.android;

import android.content.SharedPreferences;
import dev.tilemap.core.LonLat;
import dev.tilemap.viewer.ViewerConfig;
import java.util.Locale;

/**
 * The app's settings, read from the default {@link SharedPreferences}. Keys are the {@code config.json} field
 * names so an imported config maps one to one; {@code cellSize} and {@code prefetchOnMeteredNetworks} are new.
 */
record AppPrefs(String source, String key, String style, String charset, String color, boolean labels, String cellSize,
                int memoryTiles, boolean diskCache, boolean prefetchOnMetered, long cacheCleared) {
    static final String KEY_LAST_VIEW = "lastView";
    static final String KEY_TIP_SHOWN = "tipShown";
    static final String KEY_CACHE_CLEARED = "cacheCleared";
    private static final int DEFAULT_MEMORY_TILES = 256;

    static AppPrefs load(SharedPreferences p) {
        return new AppPrefs(
                blankToNull(p.getString("source", null)),
                blankToNull(p.getString("key", null)),
                p.getString("style", "default"),
                p.getString("charset", "braille"),
                p.getString("color", "true"),
                p.getBoolean("labels", true),
                p.getString("cellSize", "medium"),
                parseInt(p.getString("memoryTiles", null), DEFAULT_MEMORY_TILES),
                p.getBoolean("diskCache", true),
                p.getBoolean("prefetchOnMeteredNetworks", true),
                p.getLong(KEY_CACHE_CLEARED, 0));
    }

    float cellWidthDp() {
        return switch (cellSize) {
            case "small" -> 6;
            case "large" -> 10;
            default -> 8;
        };
    }

    /** What decides whether the tile source must be reopened. */
    String sourceIdentity() {
        return source + "|" + key + "|" + diskCache + "|" + memoryTiles + "|" + cacheCleared;
    }

    /** The last map view, as {@code lon,lat,zoom}, or null. */
    static double[] lastView(SharedPreferences p) {
        String s = p.getString(KEY_LAST_VIEW, null);
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length != 3) return null;
        try {
            return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void saveLastView(SharedPreferences p, LonLat center, double zoom) {
        p.edit().putString(KEY_LAST_VIEW, String.format(Locale.ROOT, "%.6f,%.6f,%.3f", center.lon(), center.lat(), zoom)).apply();
    }

    /** Copies a terminal viewer config into the preferences; absent fields are left alone. */
    static void importConfig(SharedPreferences p, ViewerConfig c) {
        SharedPreferences.Editor e = p.edit();
        if (c.source() != null) e.putString("source", c.source());
        if (c.key() != null) e.putString("key", c.key());
        if (c.style() != null) e.putString("style", c.style());
        if (c.charset() != null) e.putString("charset", c.charset());
        if (c.color() != null) e.putString("color", c.color());
        if (c.labels() != null) e.putBoolean("labels", c.labels());
        if (c.memoryTiles() != null) e.putString("memoryTiles", Integer.toString(c.memoryTiles()));
        if (c.diskCache() != null) e.putBoolean("diskCache", c.diskCache());
        if (c.centerOpt().isPresent()) {
            double[] center = c.centerOpt().get();
            double zoom = c.zoom() != null ? c.zoom() : 12;
            e.putString(KEY_LAST_VIEW, String.format(Locale.ROOT, "%.6f,%.6f,%.3f", center[0], center[1], zoom));
        }
        e.apply();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Math.max(16, Integer.parseInt(s.strip()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
