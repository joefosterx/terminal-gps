package dev.tilemap.core;

import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test-only MVT writer: turns a {@link Tile} back into protobuf bytes, rounding coordinates to integers at
 * {@link Tile#EXTENT}. All tag values are written as strings. Output is deterministic.
 */
public final class MvtEncoder {
    private MvtEncoder() {}

    public static byte[] encode(Tile tile) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CodedOutputStream out = CodedOutputStream.newInstance(bytes);
            for (Layer layer : tile.layers()) out.writeByteArray(3, layer(layer));
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] layer(Layer layer) throws IOException {
        Map<String, Integer> keys = new LinkedHashMap<>();
        Map<String, Integer> values = new LinkedHashMap<>();
        List<byte[]> features = new ArrayList<>();
        for (Feature f : layer.features()) {
            List<Integer> tags = new ArrayList<>();
            for (var e : new TreeMap<>(f.tags()).entrySet()) {
                tags.add(keys.computeIfAbsent(e.getKey(), k -> keys.size()));
                tags.add(values.computeIfAbsent(e.getValue(), v -> values.size()));
            }
            features.add(feature(f.geom(), tags));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(15, 2);
        out.writeString(1, layer.name());
        for (byte[] f : features) out.writeByteArray(2, f);
        for (String k : keys.keySet()) out.writeString(3, k);
        for (String v : values.keySet()) {
            ByteArrayOutputStream vb = new ByteArrayOutputStream();
            CodedOutputStream vo = CodedOutputStream.newInstance(vb);
            vo.writeString(1, v);
            vo.flush();
            out.writeByteArray(4, vb.toByteArray());
        }
        out.writeUInt32(5, Tile.EXTENT);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] feature(Geometry geom, List<Integer> tags) throws IOException {
        List<Integer> cmds = new ArrayList<>();
        int type;
        int[] cursor = {0, 0};
        switch (geom) {
            case Geometry.Point p -> {
                type = 1;
                int n = p.coords().length / 2;
                cmds.add(command(1, n));
                for (int i = 0; i < n; i++) delta(cmds, cursor, p.coords()[2 * i], p.coords()[2 * i + 1]);
            }
            case Geometry.Line l -> {
                type = 2;
                for (double[] part : l.parts()) path(cmds, cursor, part, false);
            }
            case Geometry.Polygon p -> {
                type = 3;
                for (double[] ring : p.rings()) path(cmds, cursor, ring, true);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        packed(out, 2, tags);
        out.writeEnum(3, type);
        packed(out, 4, cmds);
        out.flush();
        return bytes.toByteArray();
    }

    /** Writes a path; polygon rings drop a repeated closing point and end with ClosePath. */
    private static void path(List<Integer> cmds, int[] cursor, double[] pts, boolean ring) {
        int n = pts.length / 2;
        if (ring && n > 1 && pts[0] == pts[2 * n - 2] && pts[1] == pts[2 * n - 1]) n--;
        cmds.add(command(1, 1));
        delta(cmds, cursor, pts[0], pts[1]);
        cmds.add(command(2, n - 1));
        for (int i = 1; i < n; i++) delta(cmds, cursor, pts[2 * i], pts[2 * i + 1]);
        if (ring) cmds.add(command(7, 1));
    }

    private static void delta(List<Integer> cmds, int[] cursor, double x, double y) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        cmds.add(zigzag(ix - cursor[0]));
        cmds.add(zigzag(iy - cursor[1]));
        cursor[0] = ix;
        cursor[1] = iy;
    }

    private static int command(int id, int count) {
        return id | count << 3;
    }

    private static int zigzag(int n) {
        return (n << 1) ^ (n >> 31);
    }

    private static void packed(CodedOutputStream out, int field, List<Integer> values) throws IOException {
        int size = 0;
        for (int v : values) size += CodedOutputStream.computeUInt32SizeNoTag(v);
        out.writeTag(field, 2);
        out.writeUInt32NoTag(size);
        for (int v : values) out.writeUInt32NoTag(v);
    }
}
