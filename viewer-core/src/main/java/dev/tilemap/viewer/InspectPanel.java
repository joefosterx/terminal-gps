package dev.tilemap.viewer;

import dev.tilemap.core.Inspector;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.RenderException;
import dev.tilemap.core.TileSource;
import dev.tilemap.core.Viewport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The text of the inspect panel: the cursor position, then each hit's layer and tags (translated names left out). */
public final class InspectPanel {
    private InspectPanel() {}

    /**
     * @param closeHint appended to the first line, for example {@code "Esc closes"}; empty for none
     * @param maxLines the last line becomes {@code …} when the text is longer
     * @param width lines longer than this are cut with {@code …}
     */
    public static List<String> lines(AppState state, TileSource tiles, Viewport vp, int mapCols, int mapRows,
                                     String closeHint, int maxLines, int width) {
        LonLat at = state.at(state.cursorCol, state.cursorRow, mapCols, mapRows);
        List<String> lines = new ArrayList<>();
        String position = String.format(Locale.ROOT, "%.5f, %.5f", at.lat(), at.lon());
        lines.add(closeHint.isEmpty() ? position : position + "   " + closeHint);
        List<Inspector.Hit> hits;
        try {
            hits = Inspector.at(vp, state.style, tiles, state.cursorCol, state.cursorRow);
        } catch (RenderException e) {
            throw new IllegalStateException("a cache-only source never fails", e);
        }
        if (hits.isEmpty()) lines.add("(no features here)");
        for (Inspector.Hit hit : hits) {
            lines.add("");
            lines.add(hit.layer() + "  (" + hit.source() + ")");
            hit.tags().entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("name:") && !e.getKey().startsWith("name_"))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> lines.add("  " + e.getKey() + " = " + e.getValue()));
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (out.size() == maxLines) {
                out.set(maxLines - 1, "…");
                break;
            }
            out.add(line.codePointCount(0, line.length()) > width
                    ? new String(line.codePoints().limit(width - 1).toArray(), 0, width - 1) + "…"
                    : line);
        }
        return out;
    }
}
