package dev.tilemap.lib;

import dev.tilemap.core.MvtDecoder;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads vector tiles from a local PMTiles v3 archive. Supports uncompressed and gzip directories and tiles.
 * Reads are positional, so one instance can serve several threads.
 *
 * @see <a href="https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md">PMTiles v3 spec</a>
 */
public final class PmTilesSource implements TileSource, AutoCloseable {
    static final int HEADER_BYTES = 127;
    static final int COMPRESSION_UNKNOWN = 0, COMPRESSION_NONE = 1, COMPRESSION_GZIP = 2;
    static final int TILE_TYPE_UNKNOWN = 0, TILE_TYPE_MVT = 1;
    private static final int MAX_DIRECTORY_DEPTH = 4;
    private static final int LEAF_CACHE = 64;

    /** The fields of the fixed-size header this reader uses. */
    record Header(long rootOffset, long rootLength, long leafOffset, long tileDataOffset,
                  int internalCompression, int tileCompression, int tileType, int minZoom, int maxZoom) {}

    /** Parallel arrays, sorted by tile id. A run length of 0 marks a leaf directory. */
    record Directory(long[] tileIds, int[] runLengths, long[] offsets, int[] lengths) {
        int size() {
            return tileIds.length;
        }
    }

    private final FileChannel file;
    private final Header header;
    private final Directory root;
    private final Map<Long, Directory> leaves = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Directory> eldest) {
            return size() > LEAF_CACHE;
        }
    };

    private PmTilesSource(FileChannel file, Header header, Directory root) {
        this.file = file;
        this.header = header;
        this.root = root;
    }

    public static PmTilesSource open(Path path) throws IOException {
        FileChannel file = FileChannel.open(path, StandardOpenOption.READ);
        try {
            Header header = header(read(file, 0, HEADER_BYTES));
            if (header.tileType != TILE_TYPE_MVT && header.tileType != TILE_TYPE_UNKNOWN) {
                throw new IOException(path + ": not a vector tile archive (tile type " + header.tileType + ")");
            }
            checkCompression(header.internalCompression, path + " directories");
            checkCompression(header.tileCompression, path + " tiles");
            Directory root = directory(decompress(read(file, header.rootOffset, header.rootLength), header.internalCompression));
            return new PmTilesSource(file, header, root);
        } catch (IOException | RuntimeException e) {
            file.close();
            throw e;
        }
    }

    @Override
    public Optional<Tile> fetch(TileId id) throws TileException {
        if (id.z() < header.minZoom || id.z() > header.maxZoom) return Optional.empty();
        long tileId = tileId(id.z(), id.x(), id.y());
        try {
            Directory dir = root;
            for (int depth = 0; depth <= MAX_DIRECTORY_DEPTH; depth++) {
                int i = find(dir, tileId);
                if (i < 0) return Optional.empty();
                if (dir.runLengths[i] > 0) {
                    byte[] data = read(file, header.tileDataOffset + dir.offsets[i], dir.lengths[i]);
                    return Optional.of(MvtDecoder.decode(id, decompress(data, header.tileCompression)));
                }
                dir = leaf(header.leafOffset + dir.offsets[i], dir.lengths[i]);
            }
            throw new TileException("PMTiles directories nested too deeply at " + id);
        } catch (IOException e) {
            throw new TileException("reading " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int maxZoom() {
        return header.maxZoom;
    }

    Header header() {
        return header;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private Directory leaf(long offset, int length) throws IOException {
        synchronized (leaves) {
            Directory cached = leaves.get(offset);
            if (cached != null) return cached;
        }
        Directory dir = directory(decompress(read(file, offset, length), header.internalCompression));
        synchronized (leaves) {
            leaves.put(offset, dir);
        }
        return dir;
    }

    /**
     * The entry covering {@code tileId}: the last entry whose id is not greater, provided it is a leaf directory
     * or its run includes the id. Returns -1 if none does.
     */
    static int find(Directory dir, long tileId) {
        int lo = 0, hi = dir.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long cmp = dir.tileIds[mid];
            if (cmp < tileId) {
                lo = mid + 1;
            } else if (cmp > tileId) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        if (hi < 0) return -1;
        if (dir.runLengths[hi] == 0) return hi;
        return tileId - dir.tileIds[hi] < dir.runLengths[hi] ? hi : -1;
    }

    /** Position of z/x/y on the PMTiles Hilbert ordering, counting all tiles at lower zooms first. */
    static long tileId(int z, int x, int y) {
        long acc = ((1L << (2 * z)) - 1) / 3;
        long n = 1L << z;
        long tx = x, ty = y, d = 0;
        for (long s = n / 2; s > 0; s /= 2) {
            long rx = (tx & s) > 0 ? 1 : 0;
            long ry = (ty & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            if (ry == 0) {
                if (rx == 1) {
                    tx = n - 1 - tx;
                    ty = n - 1 - ty;
                }
                long t = tx;
                tx = ty;
                ty = t;
            }
        }
        return acc + d;
    }

    static Header header(byte[] bytes) throws IOException {
        if (bytes.length < HEADER_BYTES || !"PMTiles".equals(new String(bytes, 0, 7, StandardCharsets.US_ASCII))) {
            throw new IOException("not a PMTiles archive");
        }
        if (bytes[7] != 3) throw new IOException("unsupported PMTiles version " + bytes[7]);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new Header(b.getLong(8), b.getLong(16), b.getLong(40), b.getLong(56),
                bytes[97] & 0xff, bytes[98] & 0xff, bytes[99] & 0xff, bytes[100] & 0xff, bytes[101] & 0xff);
    }

    static Directory directory(byte[] bytes) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        int n = Math.toIntExact(varint(in));
        long[] ids = new long[n];
        int[] runs = new int[n];
        long[] offsets = new long[n];
        int[] lengths = new int[n];
        long last = 0;
        for (int i = 0; i < n; i++) {
            last += varint(in);
            ids[i] = last;
        }
        for (int i = 0; i < n; i++) runs[i] = Math.toIntExact(varint(in));
        for (int i = 0; i < n; i++) lengths[i] = Math.toIntExact(varint(in));
        for (int i = 0; i < n; i++) {
            long v = varint(in);
            offsets[i] = v == 0 && i > 0 ? offsets[i - 1] + lengths[i - 1] : v - 1;
        }
        return new Directory(ids, runs, offsets, lengths);
    }

    private static long varint(ByteBuffer in) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (!in.hasRemaining()) throw new EOFException("truncated PMTiles directory");
            byte b = in.get();
            value |= (long) (b & 0x7f) << shift;
            if (b >= 0) return value;
        }
        throw new IOException("varint too long");
    }

    private static void checkCompression(int compression, String what) throws IOException {
        if (compression != COMPRESSION_NONE && compression != COMPRESSION_GZIP && compression != COMPRESSION_UNKNOWN) {
            throw new IOException(what + ": unsupported compression " + compression + " (only none and gzip)");
        }
    }

    /** "Unknown" compression is sniffed: gzip if it has the gzip magic. */
    private static byte[] decompress(byte[] data, int compression) throws IOException {
        boolean gzip = compression == COMPRESSION_GZIP || (compression == COMPRESSION_UNKNOWN && MvtDecoder.isGzip(data));
        return gzip ? MvtDecoder.gunzip(data) : data;
    }

    private static byte[] read(FileChannel file, long offset, long length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Math.toIntExact(length));
        long pos = offset;
        while (buf.hasRemaining()) {
            int n = file.read(buf, pos);
            if (n < 0) throw new EOFException("PMTiles archive truncated at " + pos);
            pos += n;
        }
        return buf.array();
    }
}
