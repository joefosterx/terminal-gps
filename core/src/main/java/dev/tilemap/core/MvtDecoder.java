package dev.tilemap.core;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Decodes Mapbox Vector Tile 2.x protobuf bytes into a {@link Tile}. Gzipped input is detected and inflated.
 * Coordinates are rescaled from each layer's extent to {@link Tile#EXTENT}. Polygon rings keep their MVT order
 * but winding is ignored, because even-odd fill needs neither.
 */
public final class MvtDecoder {
    // Field numbers from vector_tile.proto.
    private static final int TILE_LAYERS = 3;
    private static final int LAYER_NAME = 1, LAYER_FEATURES = 2, LAYER_KEYS = 3, LAYER_VALUES = 4, LAYER_EXTENT = 5;
    private static final int FEATURE_TAGS = 2, FEATURE_TYPE = 3, FEATURE_GEOMETRY = 4;
    private static final int TYPE_POINT = 1, TYPE_LINESTRING = 2, TYPE_POLYGON = 3;
    private static final int CMD_MOVE_TO = 1, CMD_LINE_TO = 2, CMD_CLOSE_PATH = 7;

    private MvtDecoder() {}

    public static Tile decode(TileId id, byte[] data) throws TileException {
        try {
            byte[] raw = isGzip(data) ? gunzip(data) : data;
            CodedInputStream in = CodedInputStream.newInstance(raw);
            List<Layer> layers = new ArrayList<>();
            int tag;
            while ((tag = in.readTag()) != 0) {
                if (WireFormat.getTagFieldNumber(tag) == TILE_LAYERS && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                    int limit = in.pushLimit(in.readRawVarint32());
                    layers.add(layer(in));
                    in.popLimit(limit);
                } else {
                    in.skipField(tag);
                }
            }
            return new Tile(id, layers);
        } catch (IOException | RuntimeException e) {
            throw new TileException("invalid vector tile " + id + ": " + e.getMessage(), e);
        }
    }

    public static boolean isGzip(byte[] data) {
        return data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
    }

    public static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    /** A feature as read; geometry is decoded once the layer's extent is known, since it may come last. */
    private record RawFeature(int[] tags, int type, int[] geometry) {}

    private static Layer layer(CodedInputStream in) throws IOException, TileException {
        String name = "";
        int extent = 4096;
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<RawFeature> raw = new ArrayList<>();
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case LAYER_NAME -> name = in.readString();
                case LAYER_FEATURES -> {
                    int limit = in.pushLimit(in.readRawVarint32());
                    raw.add(feature(in));
                    in.popLimit(limit);
                }
                case LAYER_KEYS -> keys.add(in.readString());
                case LAYER_VALUES -> {
                    int limit = in.pushLimit(in.readRawVarint32());
                    values.add(value(in));
                    in.popLimit(limit);
                }
                case LAYER_EXTENT -> extent = in.readUInt32();
                default -> in.skipField(tag);
            }
        }
        if (extent <= 0) throw new TileException("layer " + name + ": bad extent " + extent);

        double scale = (double) Tile.EXTENT / extent;
        List<Feature> features = new ArrayList<>(raw.size());
        for (RawFeature f : raw) {
            Geometry geom = geometry(f.type, f.geometry, scale);
            if (geom == null) continue;
            if (f.tags.length % 2 != 0) throw new TileException("layer " + name + ": odd tag count");
            Map<String, String> tags = new LinkedHashMap<>();
            for (int i = 0; i < f.tags.length; i += 2) {
                int k = f.tags[i], v = f.tags[i + 1];
                if (k < 0 || k >= keys.size() || v < 0 || v >= values.size()) {
                    throw new TileException("layer " + name + ": tag index out of range");
                }
                tags.put(keys.get(k), values.get(v));
            }
            features.add(new Feature(geom, tags));
        }
        return new Layer(name, features);
    }

    private static RawFeature feature(CodedInputStream in) throws IOException {
        int[] tags = new int[0];
        int[] geometry = new int[0];
        int type = 0;
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case FEATURE_TAGS -> tags = uints(in, tag, tags);
                case FEATURE_TYPE -> type = in.readEnum();
                case FEATURE_GEOMETRY -> geometry = uints(in, tag, geometry);
                default -> in.skipField(tag);
            }
        }
        return new RawFeature(tags, type, geometry);
    }

    /** Reads a repeated uint32 field in packed or unpacked form, appending to {@code prev}. */
    private static int[] uints(CodedInputStream in, int tag, int[] prev) throws IOException {
        if (WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            int[] out = Arrays.copyOf(prev, prev.length + 1);
            out[prev.length] = in.readUInt32();
            return out;
        }
        int limit = in.pushLimit(in.readRawVarint32());
        int[] out = Arrays.copyOf(prev, prev.length + 16);
        int n = prev.length;
        while (!in.isAtEnd()) {
            if (n == out.length) out = Arrays.copyOf(out, out.length * 2);
            out[n++] = in.readUInt32();
        }
        in.popLimit(limit);
        return Arrays.copyOf(out, n);
    }

    private static String value(CodedInputStream in) throws IOException {
        String out = "";
        int tag;
        while ((tag = in.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1 -> out = in.readString();
                case 2 -> out = number(in.readFloat());
                case 3 -> out = number(in.readDouble());
                case 4 -> out = Long.toString(in.readInt64());
                case 5 -> out = Long.toUnsignedString(in.readUInt64());
                case 6 -> out = Long.toString(in.readSInt64());
                case 7 -> out = Boolean.toString(in.readBool());
                default -> in.skipField(tag);
            }
        }
        return out;
    }

    /** Whole numbers print without a fraction so tag filters can match "3" against 3.0. */
    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
    }

    /** Returns null for unknown types and geometry too short to draw. */
    static Geometry geometry(int type, int[] g, double scale) throws TileException {
        if (type != TYPE_POINT && type != TYPE_LINESTRING && type != TYPE_POLYGON) return null;
        List<double[]> parts = new ArrayList<>();
        double[] current = new double[16];
        int size = 0;
        int x = 0, y = 0;
        int i = 0;
        while (i < g.length) {
            int cmd = g[i] & 0x7;
            int count = g[i] >>> 3;
            i++;
            switch (cmd) {
                case CMD_MOVE_TO, CMD_LINE_TO -> {
                    if (i + 2L * count > g.length) throw new TileException("truncated geometry");
                    for (int c = 0; c < count; c++) {
                        x += zigzag(g[i++]);
                        y += zigzag(g[i++]);
                        if (cmd == CMD_MOVE_TO && type != TYPE_POINT) {
                            flush(parts, current, size, type);
                            size = 0;
                        }
                        if (size + 2 > current.length) current = Arrays.copyOf(current, current.length * 2);
                        current[size++] = x * scale;
                        current[size++] = y * scale;
                    }
                }
                case CMD_CLOSE_PATH -> {
                    // Rings are implicitly closed.
                }
                default -> throw new TileException("unknown geometry command " + cmd);
            }
        }
        flush(parts, current, size, type);
        if (parts.isEmpty()) return null;
        return switch (type) {
            case TYPE_POINT -> new Geometry.Point(parts.get(0));
            case TYPE_LINESTRING -> new Geometry.Line(parts);
            default -> new Geometry.Polygon(parts);
        };
    }

    private static void flush(List<double[]> parts, double[] coords, int size, int type) {
        int minPoints = switch (type) {
            case TYPE_POINT -> 1;
            case TYPE_LINESTRING -> 2;
            default -> 3;
        };
        if (size >= minPoints * 2) parts.add(Arrays.copyOf(coords, size));
    }

    private static int zigzag(int n) {
        return (n >>> 1) ^ -(n & 1);
    }
}
