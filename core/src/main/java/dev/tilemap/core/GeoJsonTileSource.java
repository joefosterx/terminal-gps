package dev.tilemap.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory tile source over a GeoJSON FeatureCollection, for tests and small overlays.
 *
 * <p>Each feature's {@code "layer"} property names its source layer (default {@code "default"}); all other
 * non-null properties become string tags. Multi-geometries collapse into one {@link Geometry} (multipolygon
 * rings are simply all rings, which even-odd fill handles). Features are not clipped: a tile contains every
 * feature whose bounding box touches it, in tile-local coordinates that may fall outside 0–4096. Drawing the
 * same feature from several tiles is harmless because dot ownership is idempotent. Coordinates are rounded to
 * whole tile units, exactly as MVT stores them, so a GeoJSON source renders identically to its MVT encoding.
 */
public final class GeoJsonTileSource implements TileSource {
    public static final String LAYER_PROPERTY = "layer";
    private static final String DEFAULT_LAYER = "default";

    /** A feature in normalized Mercator coordinates, with its bounding box. */
    private record WorldFeature(String layer, Kind kind, List<double[]> parts, Map<String, String> tags,
                                double minX, double minY, double maxX, double maxY) {}

    private enum Kind { POINT, LINE, POLYGON }

    private final List<WorldFeature> features;

    private GeoJsonTileSource(List<WorldFeature> features) {
        this.features = List.copyOf(features);
    }

    /** Parses a FeatureCollection; throws {@link IllegalArgumentException} for content it cannot use. */
    public static GeoJsonTileSource parse(Reader json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(json);
        if (root == null || !"FeatureCollection".equals(root.path("type").asText())) {
            throw new IllegalArgumentException("expected a GeoJSON FeatureCollection");
        }
        List<WorldFeature> out = new ArrayList<>();
        int index = 0;
        for (JsonNode f : root.path("features")) {
            try {
                out.add(feature(f));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("feature " + index + ": " + e.getMessage(), e);
            }
            index++;
        }
        return new GeoJsonTileSource(out);
    }

    @Override
    public Optional<Tile> fetch(TileId id) {
        double n = 1 << id.z();
        double minX = id.x() / n, minY = id.y() / n, maxX = (id.x() + 1) / n, maxY = (id.y() + 1) / n;
        Map<String, List<Feature>> layers = new LinkedHashMap<>();
        for (WorldFeature wf : features) {
            if (wf.maxX < minX || wf.minX > maxX || wf.maxY < minY || wf.minY > maxY) continue;
            List<double[]> local = new ArrayList<>(wf.parts.size());
            for (double[] part : wf.parts) {
                double[] l = new double[part.length];
                for (int i = 0; i + 1 < part.length; i += 2) {
                    l[i] = Math.rint((part[i] * n - id.x()) * Tile.EXTENT);
                    l[i + 1] = Math.rint((part[i + 1] * n - id.y()) * Tile.EXTENT);
                }
                local.add(l);
            }
            Geometry geom = switch (wf.kind) {
                case POINT -> new Geometry.Point(concat(local));
                case LINE -> new Geometry.Line(local);
                case POLYGON -> new Geometry.Polygon(local);
            };
            layers.computeIfAbsent(wf.layer, k -> new ArrayList<>()).add(new Feature(geom, wf.tags));
        }
        if (layers.isEmpty()) return Optional.empty();
        List<Layer> out = new ArrayList<>(layers.size());
        layers.forEach((name, fs) -> out.add(new Layer(name, fs)));
        return Optional.of(new Tile(id, out));
    }

    private static WorldFeature feature(JsonNode f) {
        JsonNode geom = f.get("geometry");
        if (geom == null || geom.isNull()) throw new IllegalArgumentException("missing geometry");
        JsonNode c = geom.path("coordinates");
        Kind kind;
        List<double[]> parts = new ArrayList<>();
        switch (geom.path("type").asText()) {
            case "Point" -> {
                kind = Kind.POINT;
                parts.add(positions(List.of(c)));
            }
            case "MultiPoint" -> {
                kind = Kind.POINT;
                parts.add(positions(elements(c)));
            }
            case "LineString" -> {
                kind = Kind.LINE;
                parts.add(positions(elements(c)));
            }
            case "MultiLineString" -> {
                kind = Kind.LINE;
                for (JsonNode line : c) parts.add(positions(elements(line)));
            }
            case "Polygon" -> {
                kind = Kind.POLYGON;
                for (JsonNode ring : c) parts.add(positions(elements(ring)));
            }
            case "MultiPolygon" -> {
                kind = Kind.POLYGON;
                for (JsonNode poly : c) for (JsonNode ring : poly) parts.add(positions(elements(ring)));
            }
            default -> throw new IllegalArgumentException("unsupported geometry type " + geom.path("type").asText());
        }

        String layer = DEFAULT_LAYER;
        Map<String, String> tags = new LinkedHashMap<>();
        for (var e : f.path("properties").properties()) {
            if (e.getValue().isNull()) continue;
            String value = e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString();
            if (e.getKey().equals(LAYER_PROPERTY)) {
                layer = value;
            } else {
                tags.put(e.getKey(), value);
            }
        }

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double[] part : parts) {
            for (int i = 0; i + 1 < part.length; i += 2) {
                minX = Math.min(minX, part[i]);
                maxX = Math.max(maxX, part[i]);
                minY = Math.min(minY, part[i + 1]);
                maxY = Math.max(maxY, part[i + 1]);
            }
        }
        return new WorldFeature(layer, kind, parts, tags, minX, minY, maxX, maxY);
    }

    private static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> out = new ArrayList<>();
        array.forEach(out::add);
        return out;
    }

    /** {@code [[lon, lat], ...]} to a flat Mercator array. */
    private static double[] positions(List<JsonNode> positions) {
        double[] out = new double[positions.size() * 2];
        for (int i = 0; i < positions.size(); i++) {
            JsonNode p = positions.get(i);
            if (!p.isArray() || p.size() < 2) throw new IllegalArgumentException("bad position " + p);
            out[2 * i] = Projection.mercX(p.get(0).asDouble());
            out[2 * i + 1] = Projection.mercY(p.get(1).asDouble());
        }
        return out;
    }

    private static double[] concat(List<double[]> parts) {
        int len = 0;
        for (double[] p : parts) len += p.length;
        double[] out = new double[len];
        int at = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
