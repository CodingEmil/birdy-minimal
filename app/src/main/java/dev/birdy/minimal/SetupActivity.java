package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

// The visible "Birdy" app: explains how to make Birdy the home app and links to that setting.
public class SetupActivity extends Activity {
    private static final int CONTACTS = 1;
    private TextView status, action;
    private Switch contacts;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.setup);
        status = findViewById(R.id.status);
        action = findViewById(R.id.action);
        action.setOnClickListener(v -> openHomeSettings());
        // MainActivity listens for these keys and re-sorts its list right away.
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        bindSwitch(R.id.top_apps, "top", prefs);
        bindSwitch(R.id.newest, "newest", prefs);
        bindSwitch(R.id.type, "type", prefs);
        bindSwitch(R.id.bottom, "bottom", prefs);
        bindSwitch(R.id.shade, "shade", prefs, true);
        bindSwitch(R.id.web, "web", prefs, true);
        contacts = findViewById(R.id.contacts);
        contacts.setChecked(prefs.getBoolean("contacts", false) && ContactSearch.allowed(this));
        contacts.setOnCheckedChangeListener((v, on) -> {
            if (on && !ContactSearch.allowed(this)) { // the setting turns on once Android grants access
                requestPermissions(new String[] {android.Manifest.permission.READ_CONTACTS}, CONTACTS);
                return;
            }
            prefs.edit().putBoolean("contacts", on).apply();
        });
        findViewById(R.id.setup_limits).setOnClickListener(v -> startActivity(new Intent(this, LimitsActivity.class)));
        findViewById(R.id.privacy).setOnClickListener(v ->
                Grants.open(this, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Grants.PRIVACY_URL))));
        findViewById(R.id.black_wallpaper).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Hintergrund schwarz machen?")
                .setMessage("Dein aktuelles Hintergrundbild wird durch Schwarz ersetzt. Der Sperrbildschirm bleibt, wie er ist.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ersetzen", (dialog, which) -> setBlackWallpaper())
                .show());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != CONTACTS) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putBoolean("contacts", granted).apply();
        contacts.setChecked(granted);
        if (!granted) {
            Toast.makeText(this, "Ohne Zugriff auf Kontakte sucht Birdy nur Apps. Erlauben lässt er sich in der App-Info von Birdy.",
                    Toast.LENGTH_LONG).show();
        }
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

    private void bindSwitch(int id, String key, SharedPreferences prefs) {
        bindSwitch(id, key, prefs, false);
    }

    private void bindSwitch(int id, String key, SharedPreferences prefs, boolean byDefault) {
        Switch view = findViewById(id);
        view.setChecked(prefs.getBoolean(key, byDefault));
        view.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
    }

    // Re-checked on every return, e.g. after picking Birdy in the settings.
    @Override
    protected void onResume() {
        super.onResume();
        boolean active = isDefaultHome();
        status.setText(active ? "Aktiv als Launcher" : "Noch nicht aktiv");
        action.setText(active ? "Launcher wechseln" : "Als Startbildschirm festlegen");
        renderLimitsSummary();
    }

    // One line under "Zeitlimits": how many apps, and whether strict mode is on.
    private void renderLimitsSummary() {
        int apps = 0; // installed ones; limits of uninstalled apps stay saved but do not count
        for (Limits.AppLimit limit : Usage.load(this).all()) {
            try {
                getPackageManager().getApplicationInfo(limit.pkg, 0);
                apps++;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        String text = apps == 0
                ? "Tageslimit, Schonzeiten und „wie lange?“ pro App."
                : (apps == 1 ? "1 App" : apps + " Apps") + " mit Limit · strenger Modus " + (Usage.strictModeOn(this) ? "an" : "aus");
        ((TextView) findViewById(R.id.setup_limits_summary)).setText(text);
    }

    private boolean isDefaultHome() {
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && getPackageName().equals(home.activityInfo.packageName);
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException e) { // some OEM builds lack the direct page
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
