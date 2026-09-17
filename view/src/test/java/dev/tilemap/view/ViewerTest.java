package dev.tilemap.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Styles;
import dev.tilemap.core.TileId;
import dev.tilemap.core.Viewport;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ViewerTest {
    /** Captures frames as text, one line per row. */
    static final class FakeDisplay implements Display {
        volatile int cols;
        volatile int rows;
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<String> copies = new LinkedBlockingQueue<>();
        int invalidations;

        FakeDisplay(int cols, int rows) {
            this.cols = cols;
            this.rows = rows;
        }

        @Override
        public int cols() {
            return cols;
        }

        @Override
        public int rows() {
            return rows;
        }

        @Override
        public void draw(Cell[] frame, int cols, int rows, ColorDepth depth) {
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) sb.appendCodePoint(frame[r * cols + c].codePoint());
                sb.append('\n');
            }
            frames.add(sb.toString());
        }

        @Override
        public void invalidate() {
            invalidations++;
        }

        @Override
        public void copy(String text) {
            copies.add(text);
        }

        @Override
        public void close() {}

        String awaitFrame(Predicate<String> condition) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                String f = frames.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (f != null && condition.test(f)) return f;
            }
            throw new AssertionError("no matching frame within 10 s");
        }
    }

    private static GeoJsonTileSource town() throws Exception {
        String fixtures = System.getProperty("tilemap.fixtures");
        Path file = (fixtures != null ? Path.of(fixtures) : Path.of("..", "fixtures")).resolve("town.geojson");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GeoJsonTileSource.parse(r);
        }
    }

    @Test
    @Timeout(30)
    void placeholdersThenTilesThenPanZoomResizeAndQuit() throws Exception {
        BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        FakeDisplay display = new FakeDisplay(100, 31);
        AppState state = new AppState(new LonLat(10.0005, 49.9995), 15, "default", Styles.defaultStyle(),
                new Capabilities(Charset.BOX, ColorDepth.TRUE));
        // Hold fetches until the first frame is checked, so the placeholder frame is deterministic.
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        GeoJsonTileSource source = town();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            TileCache cache = new TileCache(id -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return source.fetch(id);
            }, 64, pool, id -> events.offer(new Event.TileArrived(id)), System::nanoTime);
            Viewer viewer = new Viewer(state, cache, display, events, "© test");
            Thread loop = Thread.ofVirtual().start(() -> {
                try {
                    viewer.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            String first = display.awaitFrame(f -> true);
            assertTrue(first.lines().limit(30).allMatch(l -> l.chars().allMatch(ch -> ch == '░')), first);
            String status = first.lines().toList().get(30);
            assertTrue(status.contains("z15.00") && status.contains("tiles 0/") && status.contains("© test"), status);

            release.countDown();
            String loaded = display.awaitFrame(f -> f.contains("Bridge Street"));
            assertTrue(!loaded.contains("░"), loaded);

            events.put(new Event.KeyPressed(Key.of('-')));
            display.awaitFrame(f -> f.contains("z14.00"));
            events.put(new Event.KeyPressed(Key.of('?')));
            display.awaitFrame(f -> f.contains("pan one cell"));
            events.put(new Event.KeyPressed(Key.of(Key.Type.ESCAPE)));
            display.awaitFrame(f -> !f.contains("pan one cell"));

            events.put(new Event.KeyPressed(Key.of('i')));
            String inspecting = display.awaitFrame(f -> f.contains("Esc closes"));
            assertTrue(inspecting.contains("(no features here)") || inspecting.contains("(transportation)")
                    || inspecting.contains("(landcover)") || inspecting.contains("(building)"), inspecting);
            events.put(new Event.KeyPressed(Key.of(Key.Type.ESCAPE)));
            display.awaitFrame(f -> !f.contains("Esc closes"));

            events.put(new Event.KeyPressed(Key.of('y')));
            display.awaitFrame(f -> f.contains("copied 100x30 text"));
            String copied = display.copies.take();
            assertEquals(30, copied.lines().count());
            assertTrue(copied.contains("Bridge Street") || copied.contains("Main Street"), copied);

            display.cols = 60;
            display.rows = 20;
            events.put(new Event.Resized());
            String resized = display.awaitFrame(f -> f.lines().count() == 20);
            assertEquals(60, resized.lines().toList().getFirst().length());
            assertEquals(1, display.invalidations);

            events.put(new Event.KeyPressed(Key.of('q')));
            loop.join(5000);
            assertTrue(!loop.isAlive());
        }
    }

    @Test
    void tileRectMatchesRendererProjection() {
        // At zoom 2 centered on 0,0 in a 20 x 5 view, tile 2/2/2 starts at dot (20, 10): cell (10, 2.5).
        Viewport vp = new Viewport(new LonLat(0, 0), 2, 20, 5);
        FrameComposer.Rect r = Viewer.tileRect(vp, new TileId(2, 2, 2));
        assertEquals(new FrameComposer.Rect(10, 2, 138, 67), r);
    }

    @Test
    void prefetchIsTheRingAndParents() {
        List<TileId> visible = List.of(new TileId(3, 4, 4), new TileId(3, 5, 4));
        List<TileId> ring = Viewer.prefetch(new Viewport(new LonLat(0, 0), 3, 10, 10), visible);
        assertEquals(10 + 1, ring.size()); // the 4 x 3 block around them minus the 2 visible, plus parent 2/2/2
        assertTrue(ring.contains(new TileId(2, 2, 2)));
        assertTrue(ring.stream().noneMatch(visible::contains));
    }
}
