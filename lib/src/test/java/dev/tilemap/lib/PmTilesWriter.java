package dev.tilemap.lib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.zip.GZIPOutputStream;

/** Test-only PMTiles v3 writer: clustered, with optional gzip, leaf directories and run-length deduplication. */
final class PmTilesWriter {
    record Options(boolean gzipTiles, boolean gzipDirectories, int leafSize, int minZoom, int maxZoom) {}

    private record Entry(long tileId, int runLength, long offset, int length) {}

    private PmTilesWriter() {}

    /** {@code tiles} maps PMTiles tile ids to raw MVT bytes. */
    static void write(Path path, SortedMap<Long, byte[]> tiles, Options o) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        List<Entry> entries = new ArrayList<>();
        byte[] previous = null;
        for (var t : tiles.entrySet()) {
            byte[] payload = o.gzipTiles ? gzip(t.getValue()) : t.getValue();
            Entry last = entries.isEmpty() ? null : entries.getLast();
            if (last != null && t.getKey() == last.tileId + last.runLength && Arrays.equals(payload, previous)) {
                entries.set(entries.size() - 1, new Entry(last.tileId, last.runLength + 1, last.offset, last.length));
                continue;
            }
            entries.add(new Entry(t.getKey(), 1, data.size(), payload.length));
            data.write(payload);
            previous = payload;
        }

        byte[] root;
        ByteArrayOutputStream leaves = new ByteArrayOutputStream();
        if (o.leafSize <= 0 || entries.size() <= o.leafSize) {
            root = directory(entries, o.gzipDirectories);
        } else {
            List<Entry> rootEntries = new ArrayList<>();
            for (int i = 0; i < entries.size(); i += o.leafSize) {
                List<Entry> chunk = entries.subList(i, Math.min(entries.size(), i + o.leafSize));
                byte[] leaf = directory(chunk, o.gzipDirectories);
                rootEntries.add(new Entry(chunk.getFirst().tileId, 0, leaves.size(), leaf.length));
                leaves.write(leaf);
            }
            root = directory(rootEntries, o.gzipDirectories);
        }
        byte[] metadata = "{}".getBytes(StandardCharsets.UTF_8);

        long rootOffset = PmTilesSource.HEADER_BYTES;
        long metadataOffset = rootOffset + root.length;
        long leafOffset = metadataOffset + metadata.length;
        long dataOffset = leafOffset + leaves.size();
        ByteBuffer h = ByteBuffer.allocate(PmTilesSource.HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        h.put("PMTiles".getBytes(StandardCharsets.US_ASCII)).put((byte) 3);
        h.putLong(rootOffset).putLong(root.length);
        h.putLong(metadataOffset).putLong(metadata.length);
        h.putLong(leafOffset).putLong(leaves.size());
        h.putLong(dataOffset).putLong(data.size());
        h.putLong(entries.stream().mapToLong(Entry::runLength).sum());
        h.putLong(entries.size());
        h.putLong(entries.size());
        h.put((byte) 1);
        h.put((byte) (o.gzipDirectories ? PmTilesSource.COMPRESSION_GZIP : PmTilesSource.COMPRESSION_NONE));
        h.put((byte) (o.gzipTiles ? PmTilesSource.COMPRESSION_GZIP : PmTilesSource.COMPRESSION_NONE));
        h.put((byte) PmTilesSource.TILE_TYPE_MVT);
        h.put((byte) o.minZoom).put((byte) o.maxZoom);
        // Bounds, center zoom and center are left zero.

        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(h.array());
            out.write(root);
            out.write(metadata);
            leaves.writeTo(out);
            data.writeTo(out);
        }
    }

    private static byte[] directory(List<Entry> entries, boolean gzip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varint(out, entries.size());
        long last = 0;
        for (Entry e : entries) {
            varint(out, e.tileId - last);
            last = e.tileId;
        }
        for (Entry e : entries) varint(out, e.runLength);
        for (Entry e : entries) varint(out, e.length);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean contiguous = i > 0 && e.offset == entries.get(i - 1).offset + entries.get(i - 1).length;
            varint(out, contiguous ? 0 : e.offset + 1);
        }
        return gzip ? gzip(out.toByteArray()) : out.toByteArray();
    }

    private static void varint(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7fL) != 0) {
            out.write((int) (v & 0x7f) | 0x80);
            v >>>= 7;
        }
        out.write((int) v);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(data);
        }
        return bytes.toByteArray();
    }
}
