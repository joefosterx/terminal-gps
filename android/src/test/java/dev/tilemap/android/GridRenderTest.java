package dev.tilemap.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Renderer;
import dev.tilemap.core.Styles;
import dev.tilemap.core.Viewport;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The whole path on the JVM: preferences → view model → fetch pool → renderer → frame → grid view. The map the
 * view model produces must be the terminal viewer's map, cell for cell.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GridRenderTest {
    private static Path fixture() {
        String fixtures = System.getProperty("tilemap.fixtures");
        return (fixtures != null ? Path.of(fixtures) : Path.of("..", "fixtures")).resolve("town.geojson");
    }

    @Test
    public void rendersTheFixtureTownLikeTheTerminalViewer() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        prefs.edit().clear()
                .putString("source", fixture().toString())
                .putString(AppPrefs.KEY_LAST_VIEW, "10.0,50.0,15")
                .putString("charset", "braille")
                .putString("color", "true")
                .apply();

        MapViewModel model = new MapViewModel(app);
        AtomicReference<MapViewModel.Frame> last = new AtomicReference<>();
        model.frames().observeForever(last::set);
        model.setSize(80, 30);
        model.resume();

        Canvas expected;
        try (Reader r = Files.newBufferedReader(fixture(), StandardCharsets.UTF_8)) {
            expected = Renderer.render(new Viewport(new LonLat(10.0, 50.0), 15, 80, 30), Styles.defaultStyle(),
                    new Capabilities(Charset.BRAILLE, ColorDepth.TRUE), GeoJsonTileSource.parse(r));
        }
        long deadline = System.nanoTime() + 20_000_000_000L;
        Canvas got = null;
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            got = model.lastCanvas();
            if (got != null && got.toPlain().equals(expected.toPlain())) break;
            Thread.sleep(50);
        }
        assertNotNull("no frame within 20 s", got);
        assertEquals(expected.toPlain(), got.toPlain());

        shadowOf(Looper.getMainLooper()).idle();
        MapViewModel.Frame frame = last.get();
        assertNotNull(frame);
        assertEquals(80, frame.cols());
        assertEquals(30, frame.rows());
        assertTrue(frame.status(), frame.status().contains("tiles 1/1") || frame.status().contains("tiles 2/2")
                || frame.status().contains("tiles 4/4"));
        assertTrue(frame.status(), frame.status().contains("z15.00"));
        assertTrue("no placeholders once tiles are in", java.util.Arrays.stream(frame.cells()).noneMatch(c -> c.codePoint() == '░'));

        TextGridView view = new TextGridView(app, null);
        view.setCellWidthDp(8);
        view.measure(0, 0);
        view.layout(0, 0, 800, 600);
        view.setFrame(frame);
        Bitmap bitmap = view.toBitmap(1f, "© test");
        assertNotNull(bitmap);
        assertEquals(Math.round(80 * 8 * app.getResources().getDisplayMetrics().density), bitmap.getWidth());

        model.pause();
        assertEquals("10.000000,50.000000,15.000", prefs.getString(AppPrefs.KEY_LAST_VIEW, null));
    }

    @Test
    public void gesturesReachTheStateAndBackClosesInspect() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putString(AppPrefs.KEY_LAST_VIEW, "0,0,3").apply();
        MapViewModel model = new MapViewModel(app);
        model.setSize(40, 20);
        GridGestures g = model.gestures();
        assertTrue(g.doubleTap(20, 10));
        assertTrue(g.longPress(3, 4));
        assertTrue(model.back());
        assertTrue(!model.back());
        model.goTo("2.35, 48.86, 12");
        model.goTo("nonsense");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(app.getString(R.string.go_to_error), model.messages().getValue());
    }
}
