package dev.tilemap.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the styled features under one cell, for the viewer's inspect mode. A feature is under a cell when a point
 * falls in the cell, a line crosses it, or a polygon contains the cell's center or has an edge crossing it.
 */
public final class Inspector {
    private Inspector() {}

    /**
     * What matched: the style layer (or {@code "label:" + rule id} for label-only features such as place names),
     * the source layer, and the feature's tags.
     */
    public record Hit(String layer, String source, Map<String, String> tags) {}

    /** Hits under ({@code col}, {@code row}), topmost style layer first, then label rules; duplicates across tiles are merged. */
    public static List<Hit> at(Viewport vp, Style style, TileSource src, int col, int row) throws RenderException {
        ViewTransform view = ViewTransform.of(vp);
        double x0 = col * 2, y0 = row * 4, x1 = x0 + 2, y1 = y0 + 4;
        List<Set<Hit>> byLayer = new ArrayList<>();
        for (int i = 0; i < style.layers().size(); i++) byLayer.add(new LinkedHashSet<>());
        Set<Hit> labelHits = new LinkedHashSet<>();

        for (TileId id : Renderer.tilesFor(vp, src.maxZoom())) {
            Optional<Tile> tile;
            try {
                tile = src.fetch(id);
            } catch (TileException e) {
                throw new RenderException("tile " + id + ": " + e.getMessage(), e);
            }
            if (tile.isEmpty()) continue;
            Renderer.TileProjector proj = new Renderer.TileProjector(view, id);
            for (Layer layer : tile.get().layers()) {
                for (int i = 0; i < style.layers().size(); i++) {
                    StyleLayer sl = style.layers().get(i);
                    if (!sl.visibleAt(vp.zoom()) || !sl.source().equals(layer.name())) continue;
                    for (Feature f : layer.features()) {
                        if (sl.draws(f.geom()) && sl.matches(f) && covers(proj.toDots(f.geom()), x0, y0, x1, y1)) {
                            byLayer.get(i).add(new Hit(sl.id(), layer.name(), f.tags()));
                        }
                    }
                }
                for (LabelRule rule : style.labels()) {
                    if (!rule.visibleAt(vp.zoom()) || !rule.source().equals(layer.name())) continue;
                    for (Feature f : layer.features()) {
                        if (f.tags().containsKey(rule.field()) && rule.matches(f) && covers(proj.toDots(f.geom()), x0, y0, x1, y1)) {
                            labelHits.add(new Hit("label:" + rule.id(), layer.name(), f.tags()));
                        }
                    }
                }
            }
        }
        List<Hit> out = new ArrayList<>();
        for (int i = byLayer.size() - 1; i >= 0; i--) out.addAll(byLayer.get(i));
        for (Hit h : labelHits) {
            if (out.stream().noneMatch(o -> o.tags().equals(h.tags()) && o.source().equals(h.source()))) out.add(h);
        }
        return out;
    }

    static boolean covers(Geometry dots, double x0, double y0, double x1, double y1) {
        return switch (dots) {
            case Geometry.Point p -> {
                for (int i = 0; i + 1 < p.coords().length; i += 2) {
                    double x = p.coords()[i], y = p.coords()[i + 1];
                    if (x >= x0 && x < x1 && y >= y0 && y < y1) yield true;
                }
                yield false;
            }
            case Geometry.Line l -> l.parts().stream().anyMatch(part -> crosses(part, false, x0, y0, x1, y1));
            case Geometry.Polygon p -> containsPoint(p.rings(), (x0 + x1) / 2, (y0 + y1) / 2)
                    || p.rings().stream().anyMatch(ring -> crosses(ring, true, x0, y0, x1, y1));
        };
    }

    private static boolean crosses(double[] pts, boolean closed, double x0, double y0, double x1, double y1) {
        int points = pts.length / 2;
        int segments = closed ? points : points - 1;
        for (int s = 0; s < segments; s++) {
            int e = (s + 1) % points;
            if (Raster.clip(pts[2 * s], pts[2 * s + 1], pts[2 * e], pts[2 * e + 1], x0, y0, x1, y1) != null) return true;
        }
        return false;
    }

    /** Even-odd point in polygon, matching the fill rule. */
    static boolean containsPoint(List<double[]> rings, double x, double y) {
        boolean inside = false;
        for (double[] ring : rings) {
            int points = ring.length / 2;
            for (int p = 0; p < points; p++) {
                int q = (p + 1) % points;
                double ax = ring[2 * p], ay = ring[2 * p + 1], bx = ring[2 * q], by = ring[2 * q + 1];
                if ((ay <= y) != (by <= y) && x < ax + (y - ay) * (bx - ax) / (by - ay)) inside = !inside;
            }
        }
        return inside;
    }
}
