package dev.tilemap.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileId;
import dev.tilemap.core.TileSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PmTilesSourceTest {
    @TempDir
    Path dir;

    @Test
    void hilbertTileIdsMatchTheSpec() {
        assertEquals(0, PmTilesSource.tileId(0, 0, 0));
        assertEquals(1, PmTilesSource.tileId(1, 0, 0));
        assertEquals(2, PmTilesSource.tileId(1, 0, 1));
        assertEquals(3, PmTilesSource.tileId(1, 1, 1));
        assertEquals(4, PmTilesSource.tileId(1, 1, 0));
        assertEquals(5, PmTilesSource.tileId(2, 0, 0));
        assertEquals(19078479, PmTilesSource.tileId(12, 3423, 1763));
    }

    /** The fixture rendered from a PMTiles archive matches the GeoJSON render in every packing variant. */
    @ParameterizedTest(name = "gzipTiles={0} gzipDirs={1} leafSize={2}")
    @CsvSource({"false, false, 0", "true, true, 0", "false, true, 3", "true, false, 1"})
    void rendersTheFixture(boolean gzipTiles, boolean gzipDirs, int leafSize) throws Exception {
        Path file = dir.resolve("town.pmtiles");
        PmTilesWriter.write(file, TownTiles.mvt(), new PmTilesWriter.Options(gzipTiles, gzipDirs, leafSize, 13, 15));
        TileSource geojson = TownTiles.geojson();
        try (PmTilesSource pm = PmTilesSource.open(file)) {
            assertEquals(15, pm.maxZoom());
            for (int zoom : TownTiles.ZOOMS) {
                String expected = Renderer.render(TownTiles.view(zoom), Styles.defaultStyle(), Capabilities.DEFAULT, geojson).toPlain();
                String actual = Renderer.render(TownTiles.view(zoom), Styles.defaultStyle(), Capabilities.DEFAULT, pm).toPlain();
                assertEquals(expected, actual, "zoom " + zoom);
            }
        }
    }

    @Test
    void missingTilesAndZoomsAreEmpty() throws Exception {
        Path file = dir.resolve("town.pmtiles");
        PmTilesWriter.write(file, TownTiles.mvt(), new PmTilesWriter.Options(false, false, 0, 13, 15));
        try (PmTilesSource pm = PmTilesSource.open(file)) {
            assertTrue(pm.fetch(new TileId(14, 0, 0)).isEmpty());
            assertTrue(pm.fetch(new TileId(16, 34648, 22594)).isEmpty());
            assertTrue(pm.fetch(new TileId(2, 2, 1)).isEmpty());
        }
    }

    @Test
    void identicalNeighborsShareARun() throws Exception {
        byte[] tile = TownTiles.mvt().values().iterator().next();
        SortedMap<Long, byte[]> tiles = new TreeMap<>();
        for (long id = PmTilesSource.tileId(3, 0, 0); id < PmTilesSource.tileId(4, 0, 0); id++) tiles.put(id, tile);
        Path file = dir.resolve("runs.pmtiles");
        PmTilesWriter.write(file, tiles, new PmTilesWriter.Options(false, false, 0, 3, 3));

        PmTilesSource.Directory root;
        try (PmTilesSource pm = PmTilesSource.open(file)) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertTrue(pm.fetch(new TileId(3, x, y)).isPresent());
            root = PmTilesSource.directory(java.util.Arrays.copyOfRange(Files.readAllBytes(file),
                    (int) pm.header().rootOffset(), (int) (pm.header().rootOffset() + pm.header().rootLength())));
        }
        assertEquals(1, root.size());
        assertEquals(64, root.runLengths()[0]);
    }

    @Test
    void findRespectsRunsAndLeaves() {
        var dir = new PmTilesSource.Directory(new long[] {10, 20, 40}, new int[] {5, 0, 1}, new long[] {0, 0, 9}, new int[] {9, 9, 9});
        assertEquals(-1, PmTilesSource.find(dir, 9));
        assertEquals(0, PmTilesSource.find(dir, 14));
        assertEquals(-1, PmTilesSource.find(dir, 15));
        assertEquals(1, PmTilesSource.find(dir, 33));   // inside a leaf's range
        assertEquals(2, PmTilesSource.find(dir, 40));
        assertEquals(-1, PmTilesSource.find(dir, 41));
    }

    @Test
    void rejectsOtherFiles() throws IOException {
        Path file = dir.resolve("nope.pmtiles");
        Files.write(file, new byte[200]);
        assertThrows(IOException.class, () -> PmTilesSource.open(file));

        Path raster = dir.resolve("raster.pmtiles");
        PmTilesWriter.write(raster, new TreeMap<>(java.util.Map.of(0L, new byte[] {1})), new PmTilesWriter.Options(false, false, 0, 0, 0));
        byte[] bytes = Files.readAllBytes(raster);
        bytes[99] = 2; // PNG
        Files.write(raster, bytes);
        IOException e = assertThrows(IOException.class, () -> PmTilesSource.open(raster));
        assertTrue(e.getMessage().contains("not a vector tile archive"));

        bytes[99] = 1;
        bytes[98] = 3; // brotli
        Files.write(raster, bytes);
        assertThrows(IOException.class, () -> PmTilesSource.open(raster));
    }

    @Test
    void openedThroughSourceConfigIsCached() throws Exception {
        Path file = dir.resolve("town.pmtiles");
        PmTilesWriter.write(file, TownTiles.mvt(), new PmTilesWriter.Options(false, false, 0, 13, 15));
        TileSource src = TileSources.open(new SourceConfig.PmTiles(file));
        try {
            List<TileId> ids = Renderer.tilesFor(TownTiles.view(14));
            assertTrue(src.fetch(ids.get(0)).isPresent());
            assertEquals(15, src.maxZoom());
        } finally {
            ((AutoCloseable) src).close();
        }
    }
}
