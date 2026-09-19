package dev.tilemap.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import dev.tilemap.viewer.ViewerConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** The settings screen; keys match {@code config.json}, see {@link AppPrefs}. */
public final class SettingsFragment extends PreferenceFragmentCompat {
    private final ActivityResultLauncher<String[]> pickSource = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::sourcePicked);
    private final ActivityResultLauncher<String[]> pickConfig = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::configPicked);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case "pickFile" -> pickSource.launch(new String[] {"*/*"});
            case "useDefaultSource" -> {
                getPreferenceManager().getSharedPreferences().edit().remove("source").apply();
                Toast.makeText(requireContext(), R.string.pref_use_default_source, Toast.LENGTH_SHORT).show();
            }
            case "clearCache" -> clearCache();
            case "importConfig" -> pickConfig.launch(new String[] {"application/json", "text/*", "*/*"});
            default -> {
                return super.onPreferenceTreeClick(preference);
            }
        }
        return true;
    }

    private void sourcePicked(Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        ctx.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getPreferenceManager().getSharedPreferences().edit().putString("source", uri.toString()).apply();
        Toast.makeText(ctx, SourceOpener.displayName(ctx.getContentResolver(), uri), Toast.LENGTH_SHORT).show();
    }

    private void configPicked(Uri uri) {
        if (uri == null) return;
        Context ctx = requireContext();
        try {
            Path tmp = Files.createTempFile(ctx.getCacheDir().toPath(), "config", ".json");
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("cannot read " + uri);
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                AppPrefs.importConfig(getPreferenceManager().getSharedPreferences(), ViewerConfig.load(tmp));
            } finally {
                Files.deleteIfExists(tmp);
            }
            Toast.makeText(ctx, R.string.config_imported, Toast.LENGTH_SHORT).show();
            // Redraw the screen so the imported values show.
            setPreferenceScreen(null);
            setPreferencesFromResource(R.xml.preferences, null);
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(ctx, getString(R.string.config_import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void clearCache() {
        Context ctx = requireContext();
        Path root = SourceOpener.diskCacheRoot(ctx);
        try {
            if (Files.exists(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (IOException e) {
            Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        // Bumping this makes the view model rebuild its memory cache too.
        getPreferenceManager().getSharedPreferences().edit().putLong(AppPrefs.KEY_CACHE_CLEARED, System.currentTimeMillis()).apply();
        Toast.makeText(ctx, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
    }
}
