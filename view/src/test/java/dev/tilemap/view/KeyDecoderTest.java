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
    void supplementaryCharacters() throws IOException {
        assertEquals(List.of(new Key(Key.Type.CHAR, 0x1F600)), decode(chars("😀")));
    }
}
