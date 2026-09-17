package dev.tilemap.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyDecoderTest {
    /** Feeds chars; a null entry simulates a read timeout (a pause in typing). */
    private static List<Key> decode(Integer... input) throws IOException {
        Deque<Integer> queue = new ArrayDeque<>();
        for (Integer i : input) queue.add(i == null ? KeyDecoder.TIMEOUT : i);
        KeyDecoder decoder = new KeyDecoder(timeout -> queue.isEmpty() ? KeyDecoder.EOF : queue.poll());
        List<Key> keys = new ArrayList<>();
        Key k;
        while ((k = decoder.next()) != null) keys.add(k);
        return keys;
    }

    private static Integer[] chars(String s) {
        return s.chars().boxed().toArray(Integer[]::new);
    }

    @Test
    void printableAndControlKeys() throws IOException {
        assertEquals(List.of(Key.of('h'), Key.of('+'), Key.of(Key.Type.ENTER), Key.of(Key.Type.BACKSPACE), Key.of(Key.Type.INTERRUPT)),
                decode(chars("h+\r\u007f\u0003")));
    }

    @Test
    void arrowsInCsiAndSs3Forms() throws IOException {
        assertEquals(List.of(Key.of(Key.Type.UP), Key.of(Key.Type.LEFT), Key.of(Key.Type.DOWN), Key.of(Key.Type.RIGHT)),
                decode(chars("\u001b[A\u001b[D\u001bOB\u001bOC")));
    }

    @Test
    void shiftArrows() throws IOException {
        assertEquals(List.of(Key.of(Key.Type.SHIFT_UP), Key.of(Key.Type.SHIFT_RIGHT), Key.of(Key.Type.SHIFT_LEFT)),
                decode(chars("\u001b[1;2A\u001b[1;2C\u001b[d")));
    }

    @Test
    void loneEscapeAfterAPause() throws IOException {
        List<Integer> in = new ArrayList<>(List.of(27));
        in.add(null);
        in.add((int) 'q');
        assertEquals(List.of(Key.of(Key.Type.ESCAPE), Key.of('q')), decode(in.toArray(Integer[]::new)));
    }

    @Test
    void unknownSequencesAreSkipped() throws IOException {
        assertEquals(List.of(Key.of('x')), decode(chars("\u001b[15~\u001bax")));
    }

    @Test
    void sgrMouseReports() throws IOException {
        assertEquals(List.of(
                Key.mouse(Key.Type.MOUSE_DOWN, 9, 4),
                Key.mouse(Key.Type.MOUSE_DRAG, 11, 5),
                Key.mouse(Key.Type.MOUSE_UP, 11, 5),
                Key.mouse(Key.Type.WHEEL_UP, 0, 0),
                Key.mouse(Key.Type.WHEEL_DOWN, 2, 3)),
                decode(chars("\u001b[<0;10;5M\u001b[<32;12;6M\u001b[<0;12;6m\u001b[<64;1;1M\u001b[<65;3;4M\u001b[<2;3;4M")));
    }

    @Test
    void legacyMouseReports() throws IOException {
        // urxvt 1015: button + 32 in decimal. X10: three raw bytes, each + 32.
        assertEquals(List.of(Key.mouse(Key.Type.MOUSE_DOWN, 4, 1), Key.mouse(Key.Type.MOUSE_UP, 4, 1),
                        Key.mouse(Key.Type.MOUSE_DOWN, 0, 1), Key.mouse(Key.Type.WHEEL_UP, 2, 2)),
                decode(chars("\u001b[32;5;2M\u001b[35;5;2M\u001b[M !\"\u001b[M`##")));
    }

    @Test
    void supplementaryCharacters() throws IOException {
        assertEquals(List.of(Key.ofCodePoint(0x1F600)), decode(chars("😀")));
    }
}
