package dev.tilemap.core;

import dev.tilemap.core.Capabilities.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pipeline: plan tiles, fetch, filter by style, rasterize into dots or cells, map to glyphs, place labels.
 */
public final class Renderer {
    private Renderer() {}

    /** How a dot layer's cells are drawn once the charset has been taken into account. */
    enum DotGlyph { BRAILLE, BLOCK, SHADE, ASCII }

    public static Canvas render(Viewport vp, Style style, Capabilities caps, TileSource src) throws RenderException {
        return render(vp, style, caps, src, true);
    }

    /** Renders with or without the label pass; skipping labels is the fastest render. */
    public static Canvas render(Viewport vp, Style style, Capabilities caps, TileSource src, boolean labels) throws RenderException {
        ViewTransform view = ViewTransform.of(vp);
        DotBuffer dots = new DotBuffer(vp.cols(), vp.rows());
        CellBuffer cells = new CellBuffer(vp.cols(), vp.rows());
        Labels labelPass = new Labels(vp.cols(), vp.rows());

        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < style.layers().size(); i++) {
            if (style.layers().get(i).visibleAt(vp.zoom())) visible.add(i);
        }
        List<LabelRule> labelRules = new ArrayList<>();
        if (labels) {
            for (LabelRule rule : style.labels()) {
                if (rule.visibleAt(vp.zoom())) labelRules.add(rule);
            }
        }

        for (TileId id : tilesFor(vp, src.maxZoom())) {
            Optional<Tile> tile;
            try {
                tile = src.fetch(id);
            } catch (TileException e) {
                throw new RenderException("tile " + id + ": " + e.getMessage(), e);
            }
            if (tile.isEmpty()) continue;
            TileProjector proj = new TileProjector(view, id);
            for (Layer layer : tile.get().layers()) {
                for (int i : visible) {
                    StyleLayer sl = style.layers().get(i);
                    if (!sl.source().equals(layer.name())) continue;
                    for (Feature f : layer.features()) {
                        if (sl.draws(f.geom()) && sl.matches(f)) draw(dots, cells, proj, f.geom(), sl.paint(), (short) i);
                    }
                }
                for (LabelRule rule : labelRules) {
                    if (!rule.source().equals(layer.name())) continue;
                    for (Feature f : layer.features()) {
                        String text = f.tags().get(rule.field());
                        if (text != null && rule.matches(f)) labelPass.add(rule, proj.toDots(f.geom()), text);
                    }
                }
            }
        }
        Cell[] out = compose(vp, style, style.effectiveCharset(caps.charset()), dots, cells);
        if (!labelPass.candidates().isEmpty()) labelPass.place(out, style.layers());
        return new Canvas(vp.cols(), vp.rows(), out);
    }

    /** Tiles backing a viewport, row-major from the north-west, so callers can prefetch. */
    public static List<TileId> tilesFor(Viewport vp) {
        return tilesFor(vp, Projection.MAX_ZOOM);
    }

    /** Tiles backing a viewport from a source whose deepest zoom is {@code maxZoom} (larger zooms overzoom). */
    public static List<TileId> tilesFor(Viewport vp, int maxZoom) {
        return ViewTransform.of(vp).tiles(Math.max(0, Math.min(vp.tileZoom(), maxZoom)));
    }

    static DotGlyph dotGlyph(GlyphStrategy strategy, Charset charset) {
        boolean box = charset.compareTo(Charset.BOX) >= 0;
        boolean braille = charset.compareTo(Charset.BRAILLE) >= 0;
        return switch (strategy) {
            case BLOCK -> box ? DotGlyph.BLOCK : DotGlyph.ASCII;
            case SHADE -> box ? DotGlyph.SHADE : DotGlyph.ASCII;
            case ASCII -> DotGlyph.ASCII;
            case BRAILLE, SEXTANT, BOX_LINE, MARKER -> braille ? DotGlyph.BRAILLE : box ? DotGlyph.BLOCK : DotGlyph.ASCII;
        };
    }

    private static void draw(DotBuffer dots, CellBuffer cells, TileProjector proj, Geometry geom, Paint paint, short layer) {
        int width = paint.weight().dots();
        boolean traced = paint.kind() == PaintKind.LINE && paint.strategy() == GlyphStrategy.BOX_LINE;
        switch (geom) {
            case Geometry.Polygon p -> {
                List<double[]> rings = new ArrayList<>(p.rings().size());
                for (double[] ring : p.rings()) rings.add(proj.toDots(ring));
                if (paint.kind() == PaintKind.FILL) {
                    Raster.fillPolygon(dots, rings, layer, paint.pattern());
                } else {
                    for (double[] ring : rings) {
                        if (traced) {
                            Raster.traceCells(cells, ring, true, layer);
                        } else {
                            Raster.strokeLine(dots, ring, true, width, layer);
                        }
                    }
                }
            }
            case Geometry.Line l -> {
                for (double[] part : l.parts()) {
                    if (traced) {
                        Raster.traceCells(cells, proj.toDots(part), false, layer);
                    } else {
                        Raster.strokeLine(dots, proj.toDots(part), false, width, layer);
                    }
                }
            }
            case Geometry.Point pt -> {
                double[] d = proj.toDots(pt.coords());
                for (int i = 0; i + 1 < d.length; i += 2) {
                    if (paint.strategy() == GlyphStrategy.MARKER) {
                        cells.marker((int) Math.floor(d[i] / 2), (int) Math.floor(d[i + 1] / 4), paint.marker(), layer);
                    } else {
                        Raster.point(dots, d[i], d[i + 1], width, layer);
                    }
                }
            }
        }
    }

    /**
     * Each cell shows its highest-priority layer: a marker, a road joint, or the dots of the top dot layer in that
     * layer's foreground. The background comes from the highest layer present in the cell that has a background
     * color, so a road over a park keeps the park.
     */
    private static Cell[] compose(Viewport vp, Style style, Charset charset, DotBuffer dots, CellBuffer cells) {
        List<StyleLayer> layers = style.layers();
        DotGlyph[] dotGlyphs = new DotGlyph[layers.size()];
        for (int i = 0; i < dotGlyphs.length; i++) dotGlyphs[i] = dotGlyph(layers.get(i).paint().strategy(), charset);
        boolean unicode = charset.compareTo(Charset.BOX) >= 0;

        Cell[] out = new Cell[vp.cols() * vp.rows()];
        for (int row = 0; row < vp.rows(); row++) {
            for (int col = 0; col < vp.cols(); col++) {
                int dotTop = -1;
                int bgLayer = -1;
                for (int i = 0; i < 8; i++) {
                    int o = dots.get(col * 2 + i % 2, row * 4 + i / 2);
                    if (o > dotTop) dotTop = o;
                    if (o > bgLayer && layers.get(o).paint().bg() != null) bgLayer = o;
                }
                int line = cells.lineLayer(col, row);
                int mark = cells.markerLayer(col, row);
                if (line > bgLayer && layers.get(line).paint().bg() != null) bgLayer = line;
                if (mark > bgLayer && layers.get(mark).paint().bg() != null) bgLayer = mark;

                int top = Math.max(dotTop, Math.max(line, mark));
                if (top < 0) {
                    out[row * vp.cols() + col] = Cell.EMPTY;
                    continue;
                }
                Paint paint = layers.get(top).paint();
                int glyph;
                if (top == mark) {
                    glyph = unicode ? cells.marker(col, row) : Glyphs.asciiMarker(cells.marker(col, row));
                } else if (top == line) {
                    int mask = cells.conn(col, row);
                    glyph = unicode ? Glyphs.box(mask, paint.weight()) : Glyphs.asciiBox(mask, paint.weight());
                } else {
                    int grid = 0;
                    for (int i = 0; i < 8; i++) {
                        if (dots.get(col * 2 + i % 2, row * 4 + i / 2) == top) grid |= 1 << i;
                    }
                    glyph = switch (dotGlyphs[top]) {
                        case BRAILLE -> Glyphs.braille(grid);
                        case BLOCK -> Glyphs.block(grid);
                        case SHADE -> Glyphs.shade(grid);
                        case ASCII -> Glyphs.asciiDots(grid);
                    };
                }
                Rgb bg = bgLayer >= 0 && !style.monochrome() ? layers.get(bgLayer).paint().bg() : null;
                Rgb fg = style.monochrome() ? null : paint.fg();
                out[row * vp.cols() + col] = new Cell(glyph, fg, bg, attrs(paint), top);
            }
        }
        return out;
    }

    /** Heavy lines are bold so major roads still stand out without color. */
    private static Attrs attrs(Paint paint) {
        return paint.kind() == PaintKind.LINE && paint.weight() != Weight.LIGHT ? new Attrs(true, false) : Attrs.NONE;
    }

    /** Tile-local coordinates to viewport dots for one tile. */
    record TileProjector(double ax, double bx, double ay, double by) {
        TileProjector(ViewTransform view, TileId id) {
            this(
                    view.scaleX() / ((double) (1 << id.z()) * Tile.EXTENT),
                    view.dotX((double) id.x() / (1 << id.z())),
                    view.scaleY() / ((double) (1 << id.z()) * Tile.EXTENT),
                    view.dotY((double) id.y() / (1 << id.z())));
        }

        Geometry toDots(Geometry g) {
            return switch (g) {
                case Geometry.Point p -> new Geometry.Point(toDots(p.coords()));
                case Geometry.Line l -> new Geometry.Line(l.parts().stream().map(this::toDots).toList());
                case Geometry.Polygon p -> new Geometry.Polygon(p.rings().stream().map(this::toDots).toList());
            };
        }

        double[] toDots(double[] local) {
            double[] out = new double[local.length];
            for (int i = 0; i + 1 < local.length; i += 2) {
                out[i] = local[i] * ax + bx;
                out[i + 1] = local[i + 1] * ay + by;
            }
            return out;
        }
    }
}
