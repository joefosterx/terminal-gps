package dev.tilemap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tilemap.core.Capabilities.Charset;
import dev.tilemap.core.Capabilities.ColorDepth;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

/** Byte-for-byte snapshots of {@code fixtures/town.geojson}. */
class GoldenTest {
    private static final Capabilities BRAILLE = new Capabilities(Charset.BRAILLE, ColorDepth.TRUE);

    private static Canvas town(double zoom) throws RenderException {
        return town(zoom, BRAILLE);
    }

    private static Canvas town(double zoom, Capabilities caps) throws RenderException {
        return Renderer.render(new Viewport(Fixtures.TOWN_CENTER, zoom, 80, 30), Styles.defaultStyle(), caps, Fixtures.town());
    }

    @ParameterizedTest(name = "z{0} {1}")
    @CsvSource({"13, BRAILLE", "14, BRAILLE", "15, BRAILLE", "13, BOX", "14, BOX", "15, BOX", "13, ASCII", "14, ASCII", "15, ASCII"})
    void plain(int zoom, Charset charset) throws Exception {
        Canvas canvas = town(zoom, new Capabilities(charset, ColorDepth.NONE));
        Fixtures.assertGolden("town-z" + zoom + "-" + charset.name().toLowerCase() + ".txt", canvas.toPlain());
    }

    @ParameterizedTest
    @EnumSource(ColorDepth.class)
    void ansiColorDepths(ColorDepth depth) throws Exception {
        String ansi = town(14).toAnsi(new Capabilities(Charset.BRAILLE, depth));
        Fixtures.assertGolden("town-z14-ansi-" + depth.name().toLowerCase() + ".txt", ansi);
    }

    @Test
    void renderingIsDeterministic() throws RenderException, IOException {
        assertEquals(town(14.5), town(14.5));
        assertEquals(town(14.5).toAnsi(BRAILLE), town(14.5).toAnsi(BRAILLE));
    }

    @Test
    void waterAndParksRenderAsBraille() throws RenderException {
        Canvas canvas = town(14);
        Style style = Styles.defaultStyle();
        int water = 0, park = 0;
        for (Cell cell : canvas.cells()) {
            if (cell.layer() < 0) continue;
            String id = style.layers().get(cell.layer()).id();
            boolean braille = cell.codePoint() >= 0x2800 && cell.codePoint() <= 0x28ff;
            if (id.equals("water") && braille) water++;
            if (id.equals("park") && braille) park++;
        }
        assertTrue(water > 20, "water cells: " + water);
        assertTrue(park > 20, "park cells: " + park);
    }
}
