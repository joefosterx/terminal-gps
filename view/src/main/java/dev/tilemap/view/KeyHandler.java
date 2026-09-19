package dev.tilemap.view;

import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.LonLat;
import dev.tilemap.core.Styles;
import dev.tilemap.viewer.AppState;
import java.util.List;

/** Applies key presses and mouse events to {@link AppState}. Pure: no terminal, no rendering. */
final class KeyHandler {
    static final double WHEEL_ZOOM = 0.5;

    static final List<String> HELP = List.of(
            "h j k l / arrows    pan one cell",
            "H J K L / shift     pan half a screen",
            "mouse drag / wheel  pan / zoom at pointer",
            "+ -  (= _)          zoom in / out by 1",
            "] [                 zoom in / out by 0.25",
            "g                   go to lon,lat[,zoom]",
            "i                   inspect features (Esc closes)",
            "y / Y               copy view as text / ANSI",
            "s                   next style preset",
            "c                   cycle color depth",
            "n                   toggle labels",
            "?                   this help",
            "q / Ctrl-C          quit");

    private KeyHandler() {}

    /** Returns true if the screen needs redrawing. {@code mapCols}/{@code mapRows} are the map area in cells. */
    static boolean handle(AppState s, Key key, int mapCols, int mapRows) {
        if (key.type() == Key.Type.INTERRUPT) {
            s.quit = true;
            return false;
        }
        if (s.prompt != null) return key.type().isMouse() ? false : prompt(s, key);
        if (key.type().isMouse()) return mouse(s, key, mapCols, mapRows);
        if (s.help) {
            s.help = false;
            if (key.type() == Key.Type.CHAR && key.ch() == 'q') s.quit = true;
            return true;
        }
        s.message = "";
        if (s.inspect && inspectKey(s, key, mapCols, mapRows)) return true;
        switch (key.type()) {
            case LEFT -> s.panCells(-1, 0);
            case RIGHT -> s.panCells(1, 0);
            case UP -> s.panCells(0, -1);
            case DOWN -> s.panCells(0, 1);
            case SHIFT_LEFT -> s.panCells(-halve(mapCols), 0);
            case SHIFT_RIGHT -> s.panCells(halve(mapCols), 0);
            case SHIFT_UP -> s.panCells(0, -halve(mapRows));
            case SHIFT_DOWN -> s.panCells(0, halve(mapRows));
            case CHAR -> {
                return character(s, key.ch(), mapCols, mapRows);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** In inspect mode, movement keys move the cursor instead of the map. Returns false for keys it does not use. */
    private static boolean inspectKey(AppState s, Key key, int mapCols, int mapRows) {
        int dc = 0, dr = 0;
        switch (key.type()) {
            case LEFT -> dc = -1;
            case RIGHT -> dc = 1;
            case UP -> dr = -1;
            case DOWN -> dr = 1;
            case SHIFT_LEFT -> dc = -8;
            case SHIFT_RIGHT -> dc = 8;
            case SHIFT_UP -> dr = -4;
            case SHIFT_DOWN -> dr = 4;
            case ESCAPE -> {
                s.inspect = false;
                return true;
            }
            case CHAR -> {
                switch (key.ch()) {
                    case 'h' -> dc = -1;
                    case 'l' -> dc = 1;
                    case 'k' -> dr = -1;
                    case 'j' -> dr = 1;
                    case 'H' -> dc = -8;
                    case 'L' -> dc = 8;
                    case 'K' -> dr = -4;
                    case 'J' -> dr = 4;
                    default -> {
                        return false;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        s.cursorCol = Math.max(0, Math.min(mapCols - 1, s.cursorCol + dc));
        s.cursorRow = Math.max(0, Math.min(mapRows - 1, s.cursorRow + dr));
        return true;
    }

    private static boolean mouse(AppState s, Key key, int mapCols, int mapRows) {
        boolean onMap = key.row() < mapRows && key.col() < mapCols;
        switch (key.type()) {
            case WHEEL_UP, WHEEL_DOWN -> {
                if (!onMap) return false;
                s.zoomAt(key.col(), key.row(), key.type() == Key.Type.WHEEL_UP ? WHEEL_ZOOM : -WHEEL_ZOOM, mapCols, mapRows);
                return true;
            }
            case MOUSE_DOWN -> {
                if (!onMap) return false;
                if (s.help) {
                    s.help = false;
                    return true;
                }
                if (s.inspect) {
                    s.cursorCol = key.col();
                    s.cursorRow = key.row();
                    return true;
                }
                s.dragCol = key.col();
                s.dragRow = key.row();
                return false;
            }
            case MOUSE_DRAG -> {
                if (s.dragCol < 0) return false;
                // Dragging moves the map with the pointer, so the center moves the other way.
                s.panCells(s.dragCol - key.col(), s.dragRow - key.row());
                s.dragCol = key.col();
                s.dragRow = key.row();
                return true;
            }
            case MOUSE_UP -> {
                s.dragCol = -1;
                s.dragRow = -1;
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean character(AppState s, int ch, int mapCols, int mapRows) {
        switch (ch) {
            case 'h' -> s.panCells(-1, 0);
            case 'l' -> s.panCells(1, 0);
            case 'k' -> s.panCells(0, -1);
            case 'j' -> s.panCells(0, 1);
            case 'H' -> s.panCells(-halve(mapCols), 0);
            case 'L' -> s.panCells(halve(mapCols), 0);
            case 'K' -> s.panCells(0, -halve(mapRows));
            case 'J' -> s.panCells(0, halve(mapRows));
            case '+', '=' -> s.zoomBy(1);
            case '-', '_' -> s.zoomBy(-1);
            case ']' -> s.zoomBy(0.25);
            case '[' -> s.zoomBy(-0.25);
            case 'g' -> s.prompt = new StringBuilder();
            case '?' -> s.help = true;
            case 'i' -> {
                s.inspect = !s.inspect;
                if (s.inspect) {
                    s.cursorCol = mapCols / 2;
                    s.cursorRow = mapRows / 2;
                }
            }
            case 'y' -> s.copy = AppState.Copy.PLAIN;
            case 'Y' -> s.copy = AppState.Copy.ANSI;
            case 'q' -> {
                s.quit = true;
                return false;
            }
            case 'c' -> {
                ColorDepth[] order = {ColorDepth.TRUE, ColorDepth.C256, ColorDepth.C16, ColorDepth.NONE};
                int i = List.of(order).indexOf(s.caps.color());
                s.caps = new Capabilities(s.caps.charset(), order[(i + 1) % order.length]);
                s.message = "color: " + name(s.caps.color());
            }
            case 's' -> {
                int i = Styles.PRESETS.indexOf(s.styleName);
                String next = Styles.PRESETS.get((i + 1) % Styles.PRESETS.size());
                s.styleName = next;
                s.style = Styles.preset(next).orElseThrow();
                s.message = "style: " + next;
            }
            case 'n' -> {
                s.labels = !(s.labels && !s.labelsPausedForSpeed);
                s.labelsPausedForSpeed = false;
                s.message = s.labels ? "labels on" : "labels off";
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean prompt(AppState s, Key key) {
        switch (key.type()) {
            case ESCAPE -> s.prompt = null;
            case BACKSPACE -> {
                if (!s.prompt.isEmpty()) s.prompt.setLength(s.prompt.length() - 1);
            }
            case ENTER -> {
                String text = s.prompt.toString();
                s.prompt = null;
                try {
                    String[] parts = text.split(",");
                    if (parts.length < 2 || parts.length > 3) throw new NumberFormatException();
                    double lon = Double.parseDouble(parts[0].strip());
                    double lat = Double.parseDouble(parts[1].strip());
                    if (lon < -180 || lon > 180 || lat < -90 || lat > 90) throw new NumberFormatException();
                    Double zoom = parts.length == 3 ? Double.parseDouble(parts[2].strip()) : null;
                    s.goTo(new LonLat(lon, lat), zoom);
                    s.message = "";
                } catch (NumberFormatException e) {
                    s.message = "go to: expected lon,lat[,zoom]";
                }
            }
            case CHAR -> {
                if (key.ch() >= 0x20 && s.prompt.length() < 64) s.prompt.appendCodePoint(key.ch());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static double halve(int cells) {
        return Math.max(1, cells / 2);
    }

    static String name(ColorDepth depth) {
        return switch (depth) {
            case NONE -> "none";
            case C16 -> "16";
            case C256 -> "256";
            case TRUE -> "true";
        };
    }
}
