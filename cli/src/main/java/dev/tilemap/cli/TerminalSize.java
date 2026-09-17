package dev.tilemap.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort terminal size for {@code --fit-terminal}. */
final class TerminalSize {
    record Size(int cols, int rows) {}

    private TerminalSize() {}

    /**
     * {@code $COLUMNS}/{@code $LINES} if both are set (shells set them but do not always export them), otherwise
     * {@code stty size} on the controlling terminal, or the console window size on Windows.
     */
    static Optional<Size> detect(Map<String, String> env) {
        Optional<Size> fromEnv = fromEnv(env);
        if (fromEnv.isPresent()) return fromEnv;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return windows
                ? run(parseTwoNumbers("width", "height"),
                        "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "$s = $Host.UI.RawUI.WindowSize; \"width=$($s.Width) height=$($s.Height)\"")
                : run(TerminalSize::parseStty, "sh", "-c", "stty size < /dev/tty");
    }

    static Optional<Size> fromEnv(Map<String, String> env) {
        try {
            int cols = Integer.parseInt(env.getOrDefault("COLUMNS", "").strip());
            int rows = Integer.parseInt(env.getOrDefault("LINES", "").strip());
            return cols > 0 && rows > 0 ? Optional.of(new Size(cols, rows)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** {@code stty size} prints "rows cols". */
    static Optional<Size> parseStty(String output) {
        Matcher m = Pattern.compile("^\\s*(\\d+)\\s+(\\d+)\\s*$").matcher(output);
        if (!m.find()) return Optional.empty();
        int rows = Integer.parseInt(m.group(1)), cols = Integer.parseInt(m.group(2));
        return cols > 0 && rows > 0 ? Optional.of(new Size(cols, rows)) : Optional.empty();
    }

    private static java.util.function.Function<String, Optional<Size>> parseTwoNumbers(String colsKey, String rowsKey) {
        return output -> {
            Matcher c = Pattern.compile(colsKey + "=(\\d+)").matcher(output);
            Matcher r = Pattern.compile(rowsKey + "=(\\d+)").matcher(output);
            if (!c.find() || !r.find()) return Optional.empty();
            int cols = Integer.parseInt(c.group(1)), rows = Integer.parseInt(r.group(1));
            return cols > 0 && rows > 0 ? Optional.of(new Size(cols, rows)) : Optional.empty();
        };
    }

    private static Optional<Size> run(java.util.function.Function<String, Optional<Size>> parse, String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) return Optional.empty();
            return parse.apply(new String(out, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
