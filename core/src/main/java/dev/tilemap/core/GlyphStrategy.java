package dev.tilemap.core;

/**
 * How a layer becomes glyphs.
 *
 * <ul>
 *   <li>{@link #BOX_LINE} applies to {@link PaintKind#LINE} layers and bypasses the dot buffer: lines are traced
 *       into a per-cell connectivity grid and joints come from a table.
 *   <li>{@link #MARKER} applies to {@link PaintKind#POINT} layers: one {@link Paint#marker()} glyph per cell.
 *   <li>Everything else is rasterized into dots and mapped per cell with {@link #BRAILLE}, {@link #BLOCK},
 *       {@link #SHADE} or {@link #ASCII}; box-line fills and marker lines/polygons fall back to braille.
 * </ul>
 *
 * The capabilities' charset then degrades glyphs the terminal cannot show: braille becomes blocks under
 * {@code BOX} and ASCII below that. {@link #SEXTANT} renders as braille until sextants are implemented.
 */
public enum GlyphStrategy { BRAILLE, SEXTANT, BLOCK, SHADE, BOX_LINE, MARKER, ASCII }
