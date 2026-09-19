package dev.tilemap.viewer;

import dev.tilemap.core.Attrs;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Cell;
import java.util.List;

/** Marks the empty cells of tiles that have not loaded with a dim shade, so a blank area reads as "loading". */
public final class Placeholders {
    private static final Attrs DIM = new Attrs(false, true);

    private Placeholders() {}

    /** The placeholder cell for a charset: {@code ░} where the charset has it, {@code .} otherwise. */
    public static Cell cell(Charset charset) {
        return new Cell(charset.compareTo(Charset.BOX) >= 0 ? '░' : '.', null, null, DIM, -1);
    }

    /**
     * Fills the empty cells of {@code frame} (row-major, {@code cols} wide, {@code mapRows} map rows) inside each
     * rectangle in {@code missing}.
     */
    public static void fill(Cell[] frame, int cols, int mapRows, List<CellRect> missing, Charset charset) {
        Cell placeholder = cell(charset);
        for (CellRect rect : missing) {
            for (int r = Math.max(0, rect.row0()); r < Math.min(mapRows, rect.row1()); r++) {
                for (int c = Math.max(0, rect.col0()); c < Math.min(cols, rect.col1()); c++) {
                    if (frame[r * cols + c].equals(Cell.EMPTY)) frame[r * cols + c] = placeholder;
                }
            }
        }
    }
}
