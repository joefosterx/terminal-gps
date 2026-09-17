package dev.tilemap.core;

import java.util.Map;

/** A geometry plus its tags, as decoded; no styling. */
public record Feature(Geometry geom, Map<String, String> tags) {
    public Feature {
        tags = Map.copyOf(tags);
    }
}
