package dev.tilemap.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import dev.tilemap.core.Canvas;
import dev.tilemap.lib.Format;
import dev.tilemap.lib.TileMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The Share action: the current map as text, ANSI, HTML or an image, with the attribution the licence requires. */
final class ShareSheet {
    private ShareSheet() {}

    static void show(Activity activity, MapViewModel model, TextGridView view) {
        Canvas canvas = model.lastCanvas();
        if (canvas == null) return;
        String attribution = model.attribution();
        String[] items = {
            activity.getString(R.string.copy_text), activity.getString(R.string.copy_ansi),
            activity.getString(R.string.share_text), activity.getString(R.string.share_html), activity.getString(R.string.share_image),
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.share)
                .setItems(items, (d, which) -> {
                    try {
                        switch (which) {
                            case 0 -> copy(activity, withFooter(canvas.toPlain(), attribution));
                            case 1 -> copy(activity, withFooter(canvas.toAnsi(model.caps()), attribution));
                            case 2 -> sendText(activity, withFooter(canvas.toPlain(), attribution));
                            case 3 -> sendFile(activity, "map.html", "text/html",
                                    TileMap.format(canvas, Format.HTML, model.caps()).getBytes(StandardCharsets.UTF_8));
                            case 4 -> {
                                Bitmap bitmap = view.toBitmap(2f, attribution);
                                if (bitmap != null) sendFile(activity, "map.png", "image/png", png(bitmap));
                            }
                            default -> { }
                        }
                    } catch (IOException e) {
                        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static String withFooter(String text, String attribution) {
        return attribution.isEmpty() ? text : text + attribution + "\n";
    }

    private static void copy(Activity activity, String text) {
        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("map", text));
        Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private static void sendText(Activity activity, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
    }

    private static void sendFile(Activity activity, String name, String mime, byte[] bytes) throws IOException {
        File dir = new File(activity.getCacheDir(), "share");
        Files.createDirectories(dir.toPath());
        File file = new File(dir, name);
        Files.write(file.toPath(), bytes);
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
    }

    private static byte[] png(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
