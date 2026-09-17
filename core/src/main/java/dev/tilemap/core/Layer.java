package dev.tilemap.core;

import java.util.List;

/** A named source layer within a tile ("water", "transportation", ...). */
public record Layer(String name, List<Feature> features) {
    public Layer {
        features = List.copyOf(features);
    }
}
