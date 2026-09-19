package dev.tilemap.android;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

/** The map screen: the grid, a status strip, and the five actions a keyboard used to provide. */
public final class MapActivity extends AppCompatActivity {
    private MapViewModel model;
    private TextGridView map;
    private TextView status;
    private TextView inspect;
    private LocationTracker tracker;
    private final ActivityResultLauncher<String> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    model.toggleLocation();
                    tracker.start();
                } else {
                    Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        model = new ViewModelProvider(this).get(MapViewModel.class);
        map = findViewById(R.id.map);
        status = findViewById(R.id.status);
        inspect = findViewById(R.id.inspect);

        map.setGestures(model.gestures());
        map.setSizeListener(model::setSize);
        model.cellWidthDp().observe(this, map::setCellWidthDp);
        model.frames().observe(this, frame -> {
            map.setFrame(frame);
            status.setText(frame.status());
            if (frame.inspect() != null) {
                inspect.setText(String.join("\n", frame.inspect()) + "\n\n" + getString(R.string.inspect_close));
                inspect.setVisibility(TextView.VISIBLE);
            } else {
                inspect.setVisibility(TextView.GONE);
            }
        });
        model.messages().observe(this, m -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
        inspect.setOnClickListener(v -> model.stopInspect());

        findViewById(R.id.goTo).setOnClickListener(v -> goToDialog());
        findViewById(R.id.styleButton).setOnClickListener(v -> model.cycleStyle());
        findViewById(R.id.labelsButton).setOnClickListener(v -> model.toggleLabels());
        findViewById(R.id.shareButton).setOnClickListener(v -> ShareSheet.show(this, model, map));
        findViewById(R.id.settingsButton).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        tracker = new LocationTracker(this, model::setLocation);
        findViewById(R.id.locate).setOnClickListener(v -> {
            if (LocationTracker.hasPermission(this)) {
                model.toggleLocation();
                tracker.start();
            } else {
                askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
        // A long press turns the marker off again.
        findViewById(R.id.locate).setOnLongClickListener(v -> {
            model.stopLocating();
            tracker.stop();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!model.back()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(AppPrefs.KEY_TIP_SHOWN, false)) {
            Toast.makeText(this, R.string.first_run_tip, Toast.LENGTH_LONG).show();
            prefs.edit().putBoolean(AppPrefs.KEY_TIP_SHOWN, true).apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        model.applyPrefs();
        model.resume();
        if (model.locating() && LocationTracker.hasPermission(this)) tracker.start();
    }

    @Override
    protected void onStop() {
        tracker.stop();
        model.pause();
        super.onStop();
    }

    private void goToDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.go_to_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.go_to)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> model.goTo(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
