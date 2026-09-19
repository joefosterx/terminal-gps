package dev.tilemap.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import dev.tilemap.core.LonLat;
import java.util.function.Consumer;

/** Device position from the platform {@link LocationManager}; no Play Services. Start only with permission. */
final class LocationTracker implements LocationListener {
    private static final long MIN_TIME_MILLIS = 2000;
    private static final float MIN_DISTANCE_METERS = 3;

    private final Context ctx;
    private final Consumer<LonLat> onFix;
    private boolean started;

    LocationTracker(Context ctx, Consumer<LonLat> onFix) {
        this.ctx = ctx.getApplicationContext();
        this.onFix = onFix;
    }

    static boolean hasPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // callers check hasPermission first
    void start() {
        if (started || !hasPermission(ctx)) return;
        LocationManager lm = ctx.getSystemService(LocationManager.class);
        if (lm == null) return;
        String provider = provider(lm);
        if (provider == null) return;
        Location last = lm.getLastKnownLocation(provider);
        if (last != null) onLocationChanged(last);
        lm.requestLocationUpdates(provider, MIN_TIME_MILLIS, MIN_DISTANCE_METERS, this);
        started = true;
    }

    void stop() {
        if (!started) return;
        LocationManager lm = ctx.getSystemService(LocationManager.class);
        if (lm != null) lm.removeUpdates(this);
        started = false;
    }

    private static String provider(LocationManager lm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        onFix.accept(new LonLat(location.getLongitude(), location.getLatitude()));
    }

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}
