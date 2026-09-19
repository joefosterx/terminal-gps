package dev.tilemap.lib;

import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.RenderException;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Tile;
import dev.tilemap.core.TileException;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Headless entry point: request in, string or canvas out. */
public final class TileMap {
    private TileMap() {}

    /** A tile that could not be loaded; the map was drawn without it. */
    public record TileFailure(TileId id, String message) {}

    /** A possibly partial render. */
    public record Result(Canvas canvas, List<TileFailure> failures) {
        public Result {
            failures = List.copyOf(failures);
        }

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    /** Renders and formats; fails if any tile could not be loaded. */
    public static String renderString(MapRequest req, Format fmt) throws TileMapException {
        return OutputFormats.format(renderCanvas(req), fmt, req.caps());
    }

    /** Renders to cells; fails if any tile could not be loaded. */
    public static Canvas renderCanvas(MapRequest req) throws TileMapException {
        Result result = render(req);
        if (!result.complete()) {
            TileFailure first = result.failures().get(0);
            throw new TileMapException(result.failures().size() + " tile(s) failed, first " + first.id() + ": " + first.message());
        }
        return result.canvas();
    }

    /**
     * Renders what can be loaded. Tiles that fail are left blank and listed in the result; only a source that
     * cannot be opened at all throws.
     */
    public static Result render(MapRequest req) throws TileMapException {
        TileSource src;
        try {
            src = TileSources.open(req.source());
        } catch (IOException e) {
            throw new TileMapException("cannot open source: " + e.getMessage(), e);
        }
        try {
            return render(req, src);
        } finally {
            if (src instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // Nothing useful to do; the render is already complete.
                }
            }
        }
    }

    /**
     * Renders from a caller-owned source, ignoring {@code req.source()}. Use this to share one source (and its
     * cache) across many renders.
     */
    public static Result render(MapRequest req, TileSource src) throws TileMapException {
        List<TileFailure> failures = new ArrayList<>();
        TileSource lenient = new TileSource() {
            @Override
            public Optional<Tile> fetch(TileId id) {
                try {
                    return src.fetch(id);
                } catch (TileException e) {
                    failures.add(new TileFailure(id, e.getMessage()));
                    return Optional.empty();
                }
            }

            @Override
            public int maxZoom() {
                return src.maxZoom();
            }
        };
        try {
            Canvas canvas = Renderer.render(req.viewport(), req.style(), req.caps(), lenient, req.labels());
            return new Result(canvas, failures);
        } catch (RenderException e) {
            throw new TileMapException(e.getMessage(), e);
        }
    }

    /** Encodes an already rendered canvas. */
    public static String format(Canvas canvas, Format fmt, Capabilities caps) {
        return OutputFormats.format(canvas, fmt, caps);
    }
}
