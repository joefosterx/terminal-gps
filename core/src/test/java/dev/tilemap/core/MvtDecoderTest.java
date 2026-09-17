package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.CodedOutputStream;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MvtDecoderTest {
    private static final TileId ID = new TileId(14, 8530, 5585);

    // Geometry examples from the Mapbox Vector Tile 2.1 specification, section 4.3.5.
    @Test
    void specPoint() throws TileException {
        var p = assertInstanceOf(Geometry.Point.class, MvtDecoder.geometry(1, new int[] {9, 50, 34}, 1));
        assertArrayEquals(new double[] {25, 17}, p.coords());
    }

    @Test
    void specMultiPoint() throws TileException {
        var p = assertInstanceOf(Geometry.Point.class, MvtDecoder.geometry(1, new int[] {17, 10, 14, 3, 9}, 1));
        assertArrayEquals(new double[] {5, 7, 3, 2}, p.coords());
    }

    @Test
    void specMultiLineString() throws TileException {
        int[] g = {9, 4, 4, 18, 0, 16, 16, 0, 9, 17, 17, 10, 4, 8};
        var l = assertInstanceOf(Geometry.Line.class, MvtDecoder.geometry(2, g, 1));
        assertEquals(2, l.parts().size());
        assertArrayEquals(new double[] {2, 2, 2, 10, 10, 10}, l.parts().get(0));
        assertArrayEquals(new double[] {1, 1, 3, 5}, l.parts().get(1));
    }

    @Test
    void specPolygonWithHole() throws TileException {
        int[] g = {9, 0, 0, 26, 20, 0, 0, 20, 19, 0, 15, 9, 22, 2, 26, 18, 0, 0, 18, 17, 0, 15,
                   9, 4, 13, 26, 0, 8, 8, 0, 0, 7, 15};
        var p = assertInstanceOf(Geometry.Polygon.class, MvtDecoder.geometry(3, g, 1));
        assertEquals(3, p.rings().size());
        assertArrayEquals(new double[] {0, 0, 10, 0, 10, 10, 0, 10}, p.rings().get(0));
        assertArrayEquals(new double[] {11, 11, 20, 11, 20, 20, 11, 20}, p.rings().get(1));
        assertArrayEquals(new double[] {13, 13, 13, 17, 17, 17, 17, 13}, p.rings().get(2));
    }

    @Test
    void scalesToTileExtent() throws TileException {
        var p = assertInstanceOf(Geometry.Point.class, MvtDecoder.geometry(1, new int[] {9, 50, 34}, 4096.0 / 512));
        assertArrayEquals(new double[] {200, 136}, p.coords());
    }

    @Test
    void dropsUnknownAndDegenerateGeometry() throws TileException {
        assertNull(MvtDecoder.geometry(0, new int[] {9, 50, 34}, 1));
        assertNull(MvtDecoder.geometry(2, new int[] {9, 50, 34}, 1));
        assertThrows(TileException.class, () -> MvtDecoder.geometry(2, new int[] {9, 50}, 1));
        assertThrows(TileException.class, () -> MvtDecoder.geometry(2, new int[] {5, 1, 1}, 1));
    }

    @Test
    void decodesLayersTagsAndExtent() throws Exception {
        byte[] tile = handWrittenTile();
        Tile t = MvtDecoder.decode(ID, tile);
        assertEquals(1, t.layers().size());
        Layer layer = t.layers().get(0);
        assertEquals("poi", layer.name());
        Feature f = layer.features().get(0);
        assertEquals(Map.of("name", "Café", "rank", "3", "height", "12.5", "open", "true", "ele", "-7"), f.tags());
        assertArrayEquals(new double[] {200, 136}, assertInstanceOf(Geometry.Point.class, f.geom()).coords());
    }

    @Test
    void inflatesGzip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(handWrittenTile());
        }
        assertEquals(MvtDecoder.decode(ID, handWrittenTile()).layers().get(0).name(),
                MvtDecoder.decode(ID, bytes.toByteArray()).layers().get(0).name());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(TileException.class, () -> MvtDecoder.decode(ID, new byte[] {0x1a, 0x7f, 0x01}));
    }

    /** The fixture encoded to MVT and decoded again renders exactly like the GeoJSON source. */
    @ParameterizedTest
    @ValueSource(ints = {13, 14, 15})
    void roundTripRendersIdentically(int zoom) throws Exception {
        TileSource geojson = Fixtures.town();
        TileSource mvt = id -> {
            Optional<Tile> tile = geojson.fetch(id);
            return tile.isEmpty() ? tile : Optional.of(MvtDecoder.decode(id, MvtEncoder.encode(tile.get())));
        };
        Viewport vp = new Viewport(Fixtures.TOWN_CENTER, zoom, 80, 30);
        Capabilities caps = new Capabilities(Charset.BRAILLE, ColorDepth.TRUE);
        assertEquals(Renderer.render(vp, Styles.defaultStyle(), caps, geojson).toPlain(),
                Renderer.render(vp, Styles.defaultStyle(), caps, mvt).toPlain());
    }

    /** One "poi" layer with extent 512, one point, and one value of each scalar type. Written by hand with protobuf. */
    private static byte[] handWrittenTile() throws IOException {
        ByteArrayOutputStream layer = new ByteArrayOutputStream();
        CodedOutputStream l = CodedOutputStream.newInstance(layer);
        l.writeUInt32(15, 2);
        l.writeString(1, "poi");
        l.writeByteArray(2, message(f -> {
            f.writeTag(2, 2);
            f.writeUInt32NoTag(10);
            for (int v : new int[] {0, 0, 1, 1, 2, 2, 3, 3, 4, 4}) f.writeUInt32NoTag(v);
            f.writeEnum(3, 1);
            f.writeTag(4, 2);
            f.writeUInt32NoTag(3);
            for (int v : new int[] {9, 50, 34}) f.writeUInt32NoTag(v);
        }));
        for (String k : List.of("name", "rank", "height", "open", "ele")) l.writeString(3, k);
        l.writeByteArray(4, message(v -> v.writeString(1, "Café")));
        l.writeByteArray(4, message(v -> v.writeUInt64(5, 3)));
        l.writeByteArray(4, message(v -> v.writeDouble(3, 12.5)));
        l.writeByteArray(4, message(v -> v.writeBool(7, true)));
        l.writeByteArray(4, message(v -> v.writeSInt64(6, -7)));
        l.writeUInt32(5, 512);
        l.flush();

        return message(t -> t.writeByteArray(3, layer.toByteArray()));
    }

    private interface Body {
        void write(CodedOutputStream out) throws IOException;
    }

    private static byte[] message(Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        body.write(out);
        out.flush();
        return bytes.toByteArray();
    }
}
