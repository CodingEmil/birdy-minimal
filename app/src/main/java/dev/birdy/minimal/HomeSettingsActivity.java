package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;

// "Startbildschirm": the temperature next to the date, names in small letters, a black wallpaper, and swiping down
// for notifications.
public class HomeSettingsActivity extends Activity {
    private static final int WEATHER = 2;
    private static final int WEATHER_SETUP = 3;
    private Switch weather;
    private int weatherId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings_home);
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        SetupActivity.bindSwitch(this, R.id.shade, "shade", true);
        SetupActivity.bindSwitch(this, R.id.lowercase, MainActivity.LOWERCASE, false);
        bindWeatherSwitch(prefs);
        findViewById(R.id.black_wallpaper).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Hintergrund schwarz machen?")
                .setMessage("Dein aktuelles Hintergrundbild wird durch Schwarz ersetzt. Der Sperrbildschirm bleibt, wie er ist.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ersetzen", (dialog, which) -> setBlackWallpaper())
                .show());
    }

    // Home wallpaper only: with FLAG_SYSTEM alone, Android keeps the current image on the lock screen.
    private void setBlackWallpaper() {
        Bitmap black = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        black.eraseColor(Color.BLACK);
        try {
            WallpaperManager.getInstance(this).setBitmap(black, null, true, WallpaperManager.FLAG_SYSTEM);
            Toast.makeText(this, "Hintergrund ist jetzt schwarz", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) { // e.g. blocked by a device policy
            Toast.makeText(this, "Hintergrund ließ sich nicht ändern", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindWeatherSwitch(SharedPreferences prefs) {
        weather = findViewById(R.id.weather);
        if (Weather.provider(this) == null) { // no known weather widget on this phone
            weather.setVisibility(View.GONE);
            findViewById(R.id.weather_hint).setVisibility(View.GONE);
            return;
        }
        weather.setChecked(prefs.getInt(Weather.KEY, Weather.NONE) != Weather.NONE);
        weather.setOnCheckedChangeListener((v, on) -> {
            int saved = prefs.getInt(Weather.KEY, Weather.NONE);
            if (on == (saved != Weather.NONE)) return;
            if (!on) {
                Weather.delete(this, saved);
                prefs.edit().remove(Weather.KEY).apply();
                return;
            }
            weatherId = Weather.allocate(this);
            if (Weather.bind(this, weatherId)) weatherBound();
            else startActivityForResult(Weather.bindDialog(this, weatherId), WEATHER);
        });
    }

    // Some widgets, likely Samsung's, want their own setup before they show anything.
    private void weatherBound() {
        if (!Weather.configure(this, weatherId, WEATHER_SETUP)) {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putInt(Weather.KEY, weatherId).apply();
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != WEATHER && code != WEATHER_SETUP) return;
        if (result != RESULT_OK) {
            Weather.delete(this, weatherId);
            weather.setChecked(false);
        } else if (code == WEATHER) {
            weatherBound();
        } else {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putInt(Weather.KEY, weatherId).apply();
        }
    }
}
