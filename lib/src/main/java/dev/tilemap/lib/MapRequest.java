package dev.tilemap.lib;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Style;
import dev.tilemap.core.Styles;
import dev.tilemap.core.Viewport;
import java.util.Objects;

/**
 * Everything needed to render one map. A null {@code style} means {@link Styles#defaultStyle()} and null
 * {@code caps} means {@link Capabilities#DEFAULT}.
 */
public record MapRequest(Area area, int cols, int rows, Style style, Capabilities caps, SourceConfig source) {
    public MapRequest {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(source, "source");
        if (cols <= 0 || rows <= 0) throw new IllegalArgumentException("size must be at least 1x1");
        if (style == null) style = Styles.defaultStyle();
        if (caps == null) caps = Capabilities.DEFAULT;
    }

    public Viewport viewport() {
        return area.viewport(cols, rows);
    }
}
