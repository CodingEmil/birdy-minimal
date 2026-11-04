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
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

// The visible "Birdy" app: explains how to make Birdy the home app and links to that setting.
public class SetupActivity extends Activity {
    private TextView status, action;

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
        renderLimits();
        LimitActivity.bindStrictStatus(this, findViewById(android.R.id.content));
    }

    // One row per app with limits: name, then what is set; tap edits.
    private void renderLimits() {
        LinearLayout list = findViewById(R.id.limits_list);
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        for (Limits.AppLimit a : Usage.load(this).all()) {
            String label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(a.pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                continue; // not installed right now; the limit stays saved
            }
            TextView row = new TextView(this, null, 0, R.style.Birdy_Line);
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setText(label + "\n" + a.summary());
            row.setTextColor(getColorStateList(R.color.item_text));
            row.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            row.setOnClickListener(v -> LimitActivity.open(this, a.pkg, label));
            list.addView(row);
        }
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
