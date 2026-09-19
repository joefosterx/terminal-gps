package dev.tilemap.android;

/** Touch gestures in map-cell units, as {@link TextGridView} reports them. Each returns whether to redraw. */
interface GridGestures {
    /** One-finger drag or fling by a fraction of a cell; positive moves the finger right/down. */
    boolean drag(double dCols, double dRows);

    /** Pinch with the focal point at a cell; {@code zoomDelta} is log2 of the scale factor. */
    boolean pinch(double col, double row, double zoomDelta);

    boolean doubleTap(double col, double row);

    boolean twoFingerTap();

    boolean longPress(int col, int row);

    boolean tap(int col, int row);
}
