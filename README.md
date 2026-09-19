# terminal-gps

Real street maps, drawn in your terminal with Unicode text.

terminal-gps renders vector map tiles (OpenStreetMap data via OpenFreeMap, your own tile server, a PMTiles file, or
GeoJSON) as braille, box-drawing, or plain-ASCII characters, with optional ANSI color. It comes in two forms:

- **`tilemap`**: a command-line tool that prints a map of any place to stdout or a file, as plain text, ANSI, HTML,
  SVG or JSON.
- **`tilemap-view`**: a full-screen interactive viewer. Pan and zoom with the keyboard or mouse, inspect features,
  switch styles, and copy the view to the clipboard.

Both are built on the same rendering library, which you can also use from any JVM program.

```
⣿⣿⣿     │     ⢸⣿⣿⣿⣿⣿⣿⣿Old Town ┃   ⢸⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿
⣿⣿⣿     │     ⢸⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿     ┃   ⢸⣿⣿⣿⣿⣿⣿⣿Central Park⣿⣿⣿⣿⣿⣿⣿⣿⣿
⠛⠛⠛     │     ⠘⠛⠛⠛⠛⠛⠛⠛⠛⠛⠛⠛     ┃   ⢸⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿
        │                      ┃   ⠈⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉
━━━━━━━━╋━━━━━━━━━━━━━━━━Bridge Street━━━━━━━━━━━━━━━━━┳━━━━━━━━
        │ Mill Lane            ┃ Main Street           │
        │                      ┃             Park Lane │
        │           ● Library  ┃                       │
        │     ⢰⣶⣶⣶⣶⣶⣶⣶⣶⣶⣶⣶     ┃      ⣶⣶⣶⣶⣶⣶⣶⣶⣶⣶⣶⡆     │     ⣶⣶⣶
        │     ⢸⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿     ┃      ⣿⣿⣿⣿⣿⣿⣿Millbrook │     ⣿⣿⣿
```

The same map with `--charset ascii`, which works on any terminal down to a real VT220:

```
|  :#####:  |  #####Old Town#Central Park####: |
|  :::::::  |  ::::::  | :###################: |
|           |     Main Street                  |
+=Ring Road=+====Bridge Street=====+===========+
|           |          |           |           |
|           |  ######  |   ####Millbrook####:  |
```

## Features

- **Several glyph sets**: braille (2×4 dots per cell) for fills, box-drawing lines whose joints follow real road
  intersections, plus sextant, Latin-1 and pure-ASCII fallbacks.
- **Color at any depth**: 24-bit truecolor, 256, 16, or none. Colors are quantized for the depth you choose.
- **Labels** for places, streets, parks and points of interest, placed so they don't overlap.
- **Style presets**: `default`, `dark`, `mono`, `vt220` and `high-contrast`, or your own JSON style.
- **Tile sources**: any `{z}/{x}/{y}` Mapbox Vector Tile URL or TileJSON endpoint, local PMTiles v3 archives (fully
  offline), and GeoJSON files. Works out of the box with [OpenFreeMap](https://openfreemap.org/), no API key needed.
- **Output formats** (CLI): plain text, ANSI, HTML, SVG and JSON.
- **Viewer**: pan/zoom with keyboard or mouse, asynchronous tile loading, memory and disk tile caches, feature
  inspector, clipboard copy over OSC 52, and a startup probe that detects terminals where braille renders badly.
- **Deterministic rendering**: the same input always produces the same bytes, verified by golden-file tests.

## Requirements

- **Java 21 or newer** to run. To build, the Gradle wrapper is included, and Gradle downloads a Java 21 toolchain
  if you don't have one.
- A terminal with a font that includes braille and box-drawing characters for the best result. Most modern terminals
  (Windows Terminal, kitty, WezTerm, Alacritty, iTerm2, GNOME Terminal, Konsole) work well. For anything else, use
  `--charset ascii`.

## Build

```sh
git clone https://github.com/joefosterx/terminal-gps.git
cd terminal-gps
./gradlew assemble        # on Windows: gradlew.bat assemble
```

This produces two self-contained JARs:

| JAR | What it is |
| --- | --- |
| `cli/build/libs/tilemap.jar` | the `tilemap` command-line renderer |
| `view/build/libs/tilemap-view.jar` | the `tilemap-view` interactive viewer |

For convenience you can wrap them in shell aliases:

```sh
alias tilemap='java -jar /path/to/terminal-gps/cli/build/libs/tilemap.jar'
alias tilemap-view='java -jar /path/to/terminal-gps/view/build/libs/tilemap-view.jar'
```

To try it without a network connection, render the small test town that ships with the repository:

```sh
java -jar cli/build/libs/tilemap.jar --source fixtures/town.geojson --center 10.0,50.0 --zoom 15 --size 64x20
```

## Using the CLI: `tilemap`

Pick an area, either a center and zoom or a bounding box, and `tilemap` prints the map.

```sh
# Trafalgar Square, sized to fill the current terminal, in color
tilemap --center -0.1276,51.5072 --zoom 16 --fit-terminal --format ansi

# The Eiffel Tower area as a 120x40 HTML page
tilemap --bbox 2.29,48.85,2.31,48.86 --size 120x40 --format html -o eiffel.html

# What a VT220 would show: ASCII only, no color
tilemap --center -73.9855,40.7580 --zoom 15 --charset ascii --color none

# Fully offline from a PMTiles extract, with the dark style
tilemap --source city.pmtiles --center 13.405,52.52 --zoom 14 --style dark --format ansi
```

### Options

| Option | Description |
| --- | --- |
| `--center LON,LAT` and `--zoom Z` | Map center in degrees, and zoom level (fractional allowed). |
| `--bbox W,S,E,N` | Bounding box in degrees. Picks the largest zoom at which the box fits. |
| `--size COLSxROWS` | Output size in character cells. Default `80x24`. |
| `--fit-terminal` | Size the map to the current terminal, leaving one row for the prompt. |
| `--style NAME\|FILE` | Style preset or a JSON style file. Default `default`. |
| `--charset SET` | `ascii`, `latin1`, `box`, `braille` or `sextant`. Default `braille`. |
| `--color DEPTH` | `none`, `16`, `256` or `true`. Default `256`. Colors appear only with `--format ansi`, `html` or `svg`. |
| `--format FMT` | `plain`, `ansi`, `html`, `svg` or `json`. Default `plain`. |
| `--source URL\|FILE` | Tile source (see [Tile sources](#tile-sources)). |
| `--key KEY` | API key for the tile server. Default `$TILEMAP_KEY`. |
| `--no-labels` | Skip labels. This is the fastest mode. |
| `--strict` | If any tile fails to load, write nothing instead of a partial map. |
| `-o, --output FILE` | Write to a file instead of stdout. |
| `--request FILE` | Read the whole request from a JSON file (see below). |
| `-h, --help` / `-V, --version` | Show help or version. |

### Output formats

| Format | What you get | Good for |
| --- | --- | --- |
| `plain` | Characters only, one line per row | logs, READMEs, email |
| `ansi` | Characters with ANSI color codes | `cat` in a terminal, `less -R`, tmux panes |
| `html` | A `<pre>` block with colored spans | web pages, wikis, status pages |
| `svg` | One `<text>` element per row | images that stay crisp and selectable |
| `json` | Every cell as `{glyph, fg, bg, layer}` | post-processing in other programs and languages |

### Request files

Instead of flags, you can describe a map in JSON and pass it with `--request map.json`. Flags such as `--style`,
`--charset`, `--color` and `--source` still override the file.

```json
{
  "area": {"center": [-0.1276, 51.5072], "zoom": 14},
  "size": [120, 40],
  "style": "dark",
  "charset": "braille",
  "color": "256",
  "source": "https://tiles.openfreemap.org/planet",
  "labels": true
}
```

Only `area` and `size` are required. `area` can also be `{"bbox": [west, south, east, north]}`.

### Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success |
| `2` | Invalid arguments |
| `3` | One or more tiles failed to load. The partial map is still written unless `--strict` is set. |

## Using the viewer: `tilemap-view`

```sh
tilemap-view --center -0.1276,51.5072 --zoom 14
```

With no arguments, the viewer opens on a world view. It needs an interactive terminal; for scripts and pipes use
`tilemap` instead.

### Keys

| Key | Action |
| --- | --- |
| `h j k l` / arrow keys | Pan one cell |
| `H J K L` / Shift+arrows | Pan half a screen |
| Mouse drag / wheel | Pan / zoom at the pointer |
| `+` `-` (or `=` `_`) | Zoom in / out by 1 |
| `]` `[` | Zoom in / out by 0.25 |
| `g` | Go to `lon,lat` or `lon,lat,zoom` |
| `i` | Inspect: move a cursor and see the tags of the features under it (Esc closes) |
| `y` / `Y` | Copy the view to the clipboard as plain text / ANSI (uses OSC 52) |
| `s` | Cycle style presets |
| `c` | Cycle color depth (true, 256, 16, none) |
| `n` | Toggle labels |
| `?` | Help |
| `q` / Ctrl-C | Quit |

The status bar shows the center coordinates, zoom level and tile loading progress. Tiles that are still loading
appear as a light placeholder pattern and fill in as they arrive.

### Options

```
tilemap-view [--center LON,LAT] [--zoom Z] [--source URL|FILE] [--key KEY]
             [--style NAME|FILE] [--charset ascii|box|braille] [--color none|16|256|true]
             [--no-labels] [--no-disk-cache] [--config FILE] [--help]
```

If you don't pass `--charset` or `--color`, the viewer detects them from `$COLORTERM`, `$TERM` and the platform. It
also checks whether braille characters render one column wide. If they don't, it switches to box drawing.

### Configuration file

The viewer reads optional defaults from `$XDG_CONFIG_HOME/tilemap/config.json`, falling back to
`~/.config/tilemap/config.json`, or from the file given with `--config`. Every field is optional, and command-line
flags take precedence.

```json
{
  "source": "https://tiles.openfreemap.org/planet",
  "style": "dark",
  "center": [-0.1276, 51.5072],
  "zoom": 14,
  "charset": "braille",
  "color": "true",
  "labels": true,
  "memoryTiles": 512,
  "diskCache": true,
  "cacheDir": "/path/to/cache"
}
```

Setting `charset` skips the braille probe. Relative paths in the file resolve against the file's own directory.

### Tile cache

Downloaded tiles are kept in memory and on disk, and the disk cache respects the server's `Cache-Control` headers.
The default disk location is `$XDG_CACHE_HOME/tilemap`, `%LOCALAPPDATA%\tilemap\cache` on Windows, or
`~/.cache/tilemap` otherwise. Turn it off with `--no-disk-cache` or `"diskCache": false`.

## Android app

The same viewer runs on Android as the `:android` module: the same renderer, the same tile sources, drawn into a
grid view that paints braille, box-drawing and block glyphs as shapes so every phone shows the same map.

| Gesture | Action |
| --- | --- |
| Drag / fling | Pan |
| Pinch | Zoom, fractional, around the fingers |
| Double tap / two-finger tap | Zoom in / out by 1 |
| Long press | Inspect the features under that cell (tap the panel or press Back to close) |
| Bottom bar | Go to `lon,lat[,zoom]`, cycle style, toggle labels, share (text, ANSI, HTML, PNG), settings |

Settings use the same field names as the viewer's `config.json`, and a config file can be imported. PMTiles and
GeoJSON files are picked with the system file picker and read in place, so an offline city extract works without
being copied.

Building it needs an Android SDK. The module is included automatically when `ANDROID_HOME` is set or a
`local.properties` with `sdk.dir` exists (or with `-Pandroid`); without one, `./gradlew build` skips it.

```sh
./gradlew :android:assembleDebug        # android/build/outputs/apk/debug/android-debug.apk
./gradlew :android:testDebugUnitTest    # JVM tests, including a Robolectric render of the fixture town
```

## Tile sources

Both tools accept the same sources through `--source`, the `source` config field, or the `TILEMAP_SOURCE`
environment variable:

| Source | Example |
| --- | --- |
| Vector tile URL template (MVT) | `https://example.com/tiles/{z}/{x}/{y}.pbf` |
| TileJSON URL | `https://tiles.openfreemap.org/planet` |
| PMTiles v3 archive (offline) | `city.pmtiles` |
| GeoJSON FeatureCollection | `my-data.geojson` |

The default is [OpenFreeMap](https://openfreemap.org/)'s free planet tiles, which use the OpenMapTiles schema.
Styles expect OpenMapTiles layer names (`water`, `park`, `transportation`, `building`, `place`, `poi` and so on).

If a server needs an API key, pass it with `--key` or `TILEMAP_KEY`. It replaces a `{key}` placeholder in the URL,
or is added as a `key=` query parameter if there is no placeholder.

> **Attribution:** maps made from OpenFreeMap or other OpenStreetMap-based tiles must credit
> "© OpenMapTiles © OpenStreetMap contributors". The viewer shows this in its status bar. If you publish CLI output,
> add the credit yourself.

## Styles

Five presets are built in:

| Preset | Look |
| --- | --- |
| `default` | Light, OpenStreetMap-like colors |
| `dark` | Dark background, muted colors |
| `mono` | No color. Relies on glyph weight and fill patterns. |
| `vt220` | ASCII only, no color, bold for emphasis |
| `high-contrast` | Strong colors for readability |

A custom style is a JSON file. The easiest way to make one is to extend a preset and change a few colors:

```json
{
  "extends": "default",
  "paint": {
    "water": {"fg": "#3f76a8", "bg": "#132436"},
    "road-major": {"fg": "#e0a040"}
  }
}
```

A style can also define its own `layers` (which source layer to draw, a tag filter, a zoom range, and paint:
colors, line weight and glyph strategy) and `labels` (which features to name, and their priority). See the
[built-in presets](core/src/main/resources/dev/tilemap/core/styles/) and the documentation in
[`Styles.java`](core/src/main/java/dev/tilemap/core/Styles.java) for the full format.

## Using it as a Java library

The `:lib` module gives other JVM programs the same rendering the CLI uses:

```java
import dev.tilemap.core.Capabilities;
import dev.tilemap.lib.*;

MapRequest req = new MapRequest(
        new Area.Center(-0.1276, 51.5072, 15),   // or new Area.BBox(w, s, e, n)
        100, 30,                                  // columns, rows
        null,                                     // style: null means the default preset
        Capabilities.DEFAULT,                     // braille, 256 colors
        SourceConfig.OPENFREEMAP);

String ansi = TileMap.renderString(req, Format.ANSI);
```

`TileMap.renderCanvas(req)` returns the grid of cells (glyph, colors, layer) if you want to draw it yourself, for
example in a game engine. For non-JVM programs, use `tilemap --format json`.

## Project layout

| Module | Contents |
| --- | --- |
| [`core/`](core/) | The renderer: projection, rasterizing, glyph selection, labels, styles, MVT decoding. No I/O and no terminal dependencies. |
| [`lib/`](lib/) | Map requests, HTTP/PMTiles/GeoJSON tile sources, caching, output formats. |
| [`viewer-core/`](viewer-core/) | The interactive viewers' shared model: view state, the async tile cache, prefetch. No terminal or Android code. |
| [`cli/`](cli/) | The `tilemap` command. |
| [`view/`](view/) | The `tilemap-view` interactive viewer, built on JLine. |
| [`fixtures/`](fixtures/) | A small synthetic town in GeoJSON, used by the tests. |
| [`docs/`](docs/) | The design document. |

## Development

```sh
./gradlew test                     # all tests (offline)
./gradlew :core:test               # just the renderer
./gradlew test -Plive              # also run tests that fetch real tiles from OpenFreeMap
./gradlew test -PupdateGoldens     # regenerate golden files after an intentional rendering change
```

Rendering is checked with golden-file tests: the fixture town is rendered at several zoom levels, charsets, color
depths and style presets, and the output is compared byte for byte to the files in `core/src/test/golden/` and
`lib/src/test/golden/`. If you change how maps look, regenerate the goldens, review the diff, and commit them with
your change.

The design and roadmap are in [docs/Terminal Map Renderer — Design.md](docs/Terminal%20Map%20Renderer%20—%20Design.md).
