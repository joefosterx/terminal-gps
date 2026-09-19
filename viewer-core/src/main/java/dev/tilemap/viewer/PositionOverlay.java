package dev.tilemap.viewer;

import dev.tilemap.core.Feature;
import dev.tilemap.core.Geometry;
import dev.tilemap.core.GlyphStrategy;
import dev.tilemap.core.Layer;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Paint;
import dev.tilemap.core.PaintKind;
import dev.tilemap.core.Projection;
import dev.tilemap.core.Rgb;
import dev.tilemap.core.Style;
import dev.tilemap.core.StyleLayer;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import dev.tilemap.core.Weight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds one point, the device's position, to whatever tiles a source returns, as a {@value #LAYER} layer the
 * {@link #style} rule draws as a marker. Wrap the source the renderer reads, not the one the cache fills, so a
 * moving position never bakes a stale marker into cached tiles. No core change: a tile's layers are just data.
 */
public final class PositionOverlay implements TileSource {
    public static final String LAYER = "gps";
    public static final int MARKER = '◎';

    private final TileSource upstream;
    private volatile LonLat position;

    public PositionOverlay(TileSource upstream) {
        this.upstream = upstream;
    }

    /** The point to mark, or null for none. Safe to call from any thread. */
    public void setPosition(LonLat position) {
        this.position = position;
    }

    public LonLat position() {
        return position;
    }

    /** {@code base} plus a rule for the marker, drawn above everything and never covered by labels. */
    public static Style style(Style base) {
        Paint paint = new Paint(PaintKind.POINT, new Rgb(0xff, 0x20, 0x20), null, Weight.HEAVY, GlyphStrategy.MARKER, MARKER);
        StyleLayer rule = new StyleLayer("my-location", LAYER, Map.of(), 0, Projection.MAX_ZOOM + 1, paint, true);
        List<StyleLayer> layers = new ArrayList<>(base.layers());
        layers.add(rule);
        return new Style(layers, base.labels(), base.maxCharset(), base.monochrome());
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        Optional<Tile> tile = upstream.fetch(id);
        LonLat at = position;
        if (at == null) return tile;
        double n = 1 << id.z();
        double x = (Projection.mercX(at.lon()) * n - id.x()) * Tile.EXTENT;
        double y = (Projection.mercY(at.lat()) * n - id.y()) * Tile.EXTENT;
        if (x < 0 || y < 0 || x >= Tile.EXTENT || y >= Tile.EXTENT) return tile;
        Feature me = new Feature(new Geometry.Point(new double[] {x, y}), Map.of("class", "me"));
        List<Layer> layers = new ArrayList<>(tile.map(Tile::layers).orElse(List.of()));
        layers.add(new Layer(LAYER, List.of(me)));
        return Optional.of(new Tile(id, layers));
    }

    @Override
    public int maxZoom() {
        return upstream.maxZoom();
    }
}
