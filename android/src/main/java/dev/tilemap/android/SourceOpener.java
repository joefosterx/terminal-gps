package dev.tilemap.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import dev.tilemap.core.GeoJsonTileSource;
import dev.tilemap.core.TileSource;
import dev.tilemap.lib.HttpFetcher;
import dev.tilemap.lib.PmTilesSource;
import dev.tilemap.lib.SourceConfig;
import dev.tilemap.lib.TileSources;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens the configured tile source. URLs go through {@link TileSources} as in the terminal viewer; files come as
 * Storage Access Framework {@code content://} URIs and are opened through the content resolver, PMTiles by channel
 * so a multi-gigabyte extract is never copied.
 */
final class SourceOpener {
    private SourceOpener() {}

    static boolean isDefault(AppPrefs prefs) {
        return prefs.source() == null;
    }

    static String attribution(Context ctx, AppPrefs prefs) {
        return isDefault(prefs) ? ctx.getString(R.string.attribution_openfreemap) : "";
    }

    static Path diskCacheRoot(Context ctx) {
        return ctx.getCacheDir().toPath().resolve("tilemap");
    }

    static TileSource open(Context ctx, AppPrefs prefs, HttpFetcher fetcher) throws IOException {
        Path cacheRoot = prefs.diskCache() ? diskCacheRoot(ctx) : null;
        String source = prefs.source();
        if (source == null) {
            SourceConfig config = prefs.key() == null ? SourceConfig.OPENFREEMAP : new SourceConfig.Url(SourceConfig.OPENFREEMAP.template(), prefs.key());
            return TileSources.open(config, 0, cacheRoot, fetcher);
        }
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return TileSources.open(new SourceConfig.Url(source, prefs.key()), 0, cacheRoot, fetcher);
        }
        if (lower.startsWith("content://")) return openDocument(ctx, Uri.parse(source));
        return TileSources.open(SourceConfig.fromString(source, prefs.key()), 0, cacheRoot, fetcher);
    }

    private static TileSource openDocument(Context ctx, Uri uri) throws IOException {
        ContentResolver resolver = ctx.getContentResolver();
        String name = displayName(resolver, uri);
        if (name.toLowerCase(Locale.ROOT).endsWith(".pmtiles")) {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
            if (pfd == null) throw new FileNotFoundException(name);
            // Closing the channel closes the stream, which closes the descriptor.
            FileChannel channel = new ParcelFileDescriptor.AutoCloseInputStream(pfd).getChannel();
            return PmTilesSource.open(channel, name);
        }
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new FileNotFoundException(name);
            return GeoJsonTileSource.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) {
            // Some providers refuse metadata queries; fall through to the path.
        }
        String path = uri.getLastPathSegment();
        return path == null ? uri.toString() : path;
    }
}
