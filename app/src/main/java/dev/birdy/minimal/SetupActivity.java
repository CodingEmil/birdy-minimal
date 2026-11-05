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
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

// The visible "Birdy" app: explains how to make Birdy the home app and links to that setting.
public class SetupActivity extends Activity {
    private static final int CONTACTS = 1;
    private static final int WEATHER = 2;
    private static final int WEATHER_SETUP = 3;
    private TextView status, action;
    private Switch contacts, weather;
    private int weatherId;

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
        bindWeatherSwitch(prefs);
        bindFeedSwitch(R.id.block_shorts, R.id.allow_shorts, Feeds.Feed.SHORTS, prefs);
        bindFeedSwitch(R.id.block_reels, R.id.allow_reels, Feeds.Feed.REELS, prefs);
        findViewById(R.id.feeds_setup).setOnClickListener(v -> Grants.discloseFeeds(this));
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

    private void bindSwitch(int id, String key, SharedPreferences prefs) {
        bindSwitch(id, key, prefs, false);
    }

    private void bindSwitch(int id, String key, SharedPreferences prefs, boolean byDefault) {
        Switch view = findViewById(id);
        view.setChecked(prefs.getBoolean(key, byDefault));
        view.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
    }

    // Turning a feed on without its accessibility service asks for that service right away.
    // Under it, how long a day the feed may play first; shown while the switch is on.
    private void bindFeedSwitch(int id, int allowanceId, Feeds.Feed feed, SharedPreferences prefs) {
        Switch view = findViewById(id);
        TextView allowance = findViewById(allowanceId);
        view.setChecked(Feeds.blocked(this, feed));
        view.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(feed.pref, on).apply();
            renderFeedsSetup();
            renderAllowance(allowance, feed);
            if (on && !Feeds.serviceOn(this)) Grants.discloseFeeds(this);
        });
        allowance.setOnClickListener(v -> chooseAllowance(allowance, feed));
        renderAllowance(allowance, feed);
    }

    private void renderAllowance(TextView view, Feeds.Feed feed) {
        view.setVisibility(Feeds.blocked(this, feed) ? View.VISIBLE : View.GONE);
        int minutes = Feeds.allowedMinutes(this, feed);
        String text = "Pro Tag: " + Feeds.allowanceLabel(minutes);
        if (minutes > 0) text += " · heute " + Limits.duration((int) (Feeds.usedTodayMs(this, feed) / Feeds.MINUTE));
        view.setText(text + "  →");
    }

    // The usual times, and last "Eigene Zeit…" for any number of minutes.
    private void chooseAllowance(TextView view, Feeds.Feed feed) {
        int current = Feeds.allowedMinutes(this, feed);
        int presets = Feeds.ALLOWANCES.length;
        String[] labels = new String[presets + 1];
        int checked = presets; // a typed-in time
        for (int i = 0; i < presets; i++) {
            labels[i] = Feeds.ALLOWANCES[i] == 0 ? "Sofort sperren" : Limits.duration(Feeds.ALLOWANCES[i]) + " am Tag";
            if (Feeds.ALLOWANCES[i] == current) checked = i;
        }
        labels[presets] = checked == presets ? "Eigene Zeit: " + Limits.duration(current) + " am Tag" : "Eigene Zeit…";
        new AlertDialog.Builder(this)
                .setTitle(feed.label + " pro Tag")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == presets) {
                        askMinutes(view, feed, current);
                        return;
                    }
                    Feeds.setAllowedMinutes(this, feed, Feeds.ALLOWANCES[which]);
                    renderAllowance(view, feed);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void askMinutes(TextView view, Feeds.Feed feed, int current) {
        float dp = getResources().getDisplayMetrics().density;
        EditText input = new EditText(this);
        input.setId(R.id.allow_minutes);
        input.setHint("Minuten");
        if (current > 0) {
            input.setText(String.valueOf(current));
            input.setSelection(input.length());
        }
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF6B6B6B);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(24 * dp);
        box.setPaddingRelative(pad, Math.round(8 * dp), pad, 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Minuten pro Tag")
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, which) -> {
                    int minutes;
                    try {
                        minutes = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) { // empty or too long: keep what was set
                        return;
                    }
                    Feeds.setAllowedMinutes(this, feed, Math.max(0, Math.min(minutes, Feeds.MAX_MINUTES)));
                    renderAllowance(view, feed);
                })
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    // "Bedienungshilfe einschalten" shows while a feed is blocked but the service is off.
    private void renderFeedsSetup() {
        int visibility = Feeds.anyBlocked(this) && !Feeds.serviceOn(this) ? android.view.View.VISIBLE : android.view.View.GONE;
        findViewById(R.id.feeds_setup).setVisibility(visibility);
        findViewById(R.id.feeds_setup_hint).setVisibility(visibility);
    }

    // Re-checked on every return, e.g. after picking Birdy in the settings.
    @Override
    protected void onResume() {
        super.onResume();
        boolean active = isDefaultHome();
        status.setText(active ? "Aktiv als Launcher" : "Noch nicht aktiv");
        action.setText(active ? "Launcher wechseln" : "Als Startbildschirm festlegen");
        renderLimitsSummary();
        renderFeedsSetup();
        renderAllowance(findViewById(R.id.allow_shorts), Feeds.Feed.SHORTS); // today's minutes move on
        renderAllowance(findViewById(R.id.allow_reels), Feeds.Feed.REELS);
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
