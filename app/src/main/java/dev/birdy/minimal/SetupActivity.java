package dev.birdy.minimal;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

// The visible "Birdy" app: whether Birdy is the home app (and how to make it one), then a row per settings page.
public class SetupActivity extends Activity {
    private TextView status, action;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.setup);
        status = findViewById(R.id.status);
        action = findViewById(R.id.action);
        action.setOnClickListener(v -> openHomeSettings());
        opens(R.id.setup_home, HomeSettingsActivity.class);
        opens(R.id.setup_list, ListSettingsActivity.class);
        opens(R.id.setup_limits, LimitsActivity.class);
        opens(R.id.setup_feeds, FeedsActivity.class);
        opens(R.id.setup_help, HelpActivity.class);
        findViewById(R.id.privacy).setOnClickListener(v ->
                Grants.open(this, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Grants.PRIVACY_URL))));
        bindWhatsNew();
    }

    // "Versionshinweise" opens the popup from after an update again; dev builds have no notes, so only the version.
    private void bindWhatsNew() {
        boolean notes = WhatsNew.hasNotes(this);
        View row = findViewById(R.id.whatsnew);
        row.setVisibility(notes ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> WhatsNew.show(this));
        ((TextView) findViewById(R.id.version)).setText(
                "Version " + WhatsNew.versionName(this) + (notes ? " · was sich mit diesem Update geändert hat." : ""));
    }

    private void opens(int id, Class<? extends Activity> page) {
        findViewById(id).setOnClickListener(v -> startActivity(new Intent(this, page)));
    }

    // Re-checked on every return, e.g. after picking Birdy in the settings or changing a page.
    @Override
    protected void onResume() {
        super.onResume();
        boolean active = isDefaultHome();
        status.setText(active ? "Aktiv als Launcher" : "Noch nicht aktiv");
        action.setText(active ? "Launcher wechseln" : "Als Startbildschirm festlegen");
        findViewById(R.id.onboarding).setVisibility(active ? View.GONE : View.VISIBLE);
        renderLimitsSummary();
        renderFeedsSummary();
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

    // One line under "Shorts & Reels": what each feed gets, and a missing accessibility service.
    private void renderFeedsSummary() {
        String text;
        if (!Feeds.anyBlocked(this)) {
            text = "Shorts und Reels sperren, sofort oder nach einer Zeit am Tag.";
        } else {
            text = "Shorts " + feedState(Feeds.Feed.SHORTS) + " · Reels " + feedState(Feeds.Feed.REELS);
            if (!Feeds.serviceOn(this)) text += " · Bedienungshilfe fehlt";
        }
        ((TextView) findViewById(R.id.setup_feeds_summary)).setText(text);
    }

    private String feedState(Feeds.Feed feed) {
        return Feeds.blocked(this, feed) ? Feeds.allowanceLabel(Feeds.allowedMinutes(this, feed)) : "aus";
    }

    // For the settings pages: a switch that stands for one boolean setting. MainActivity listens for these keys.
    static void bindSwitch(Activity page, int id, String key, boolean byDefault) {
        SharedPreferences prefs = page.getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        Switch view = page.findViewById(id);
        view.setChecked(prefs.getBoolean(key, byDefault));
        view.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
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
