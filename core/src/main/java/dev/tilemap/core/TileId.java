package dev.tilemap.core;

/** A slippy-map tile address. Orders by zoom, then row, then column. */
public record TileId(int z, int x, int y) implements Comparable<TileId> {
    public TileId {
        if (z < 0 || z > Projection.MAX_ZOOM) throw new IllegalArgumentException("bad zoom " + z);
        int n = 1 << z;
        if (x < 0 || y < 0 || x >= n || y >= n) throw new IllegalArgumentException("bad tile " + z + "/" + x + "/" + y);
    }

    @Override
    public int compareTo(TileId o) {
        if (z != o.z) return Integer.compare(z, o.z);
        if (y != o.y) return Integer.compare(y, o.y);
        return Integer.compare(x, o.x);
    }

    @Override
    public String toString() {
        return z + "/" + x + "/" + y;
    }
}
