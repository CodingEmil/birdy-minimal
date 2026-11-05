package dev.birdy.minimal;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

// "Apps & Suche": the order of the app list and what the search finds. MainActivity re-sorts right away.
public class ListSettingsActivity extends Activity {
    private static final int CONTACTS = 1;
    private Switch contacts;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings_list);
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        SetupActivity.bindSwitch(this, R.id.top_apps, "top", false);
        SetupActivity.bindSwitch(this, R.id.newest, "newest", false);
        SetupActivity.bindSwitch(this, R.id.type, "type", false);
        SetupActivity.bindSwitch(this, R.id.bottom, "bottom", false);
        SetupActivity.bindSwitch(this, R.id.web, "web", true);
        contacts = findViewById(R.id.contacts);
        contacts.setChecked(prefs.getBoolean("contacts", false) && ContactSearch.allowed(this));
        contacts.setOnCheckedChangeListener((v, on) -> {
            if (on && !ContactSearch.allowed(this)) { // the setting turns on once Android grants access
                requestPermissions(new String[] {android.Manifest.permission.READ_CONTACTS}, CONTACTS);
                return;
            }
            prefs.edit().putBoolean("contacts", on).apply();
        });
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
}
