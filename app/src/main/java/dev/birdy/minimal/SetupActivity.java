package dev.birdy.minimal;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

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
    }

    // Re-checked on every return, e.g. after picking Birdy in the settings.
    @Override
    protected void onResume() {
        super.onResume();
        boolean active = isDefaultHome();
        status.setText(active ? "Aktiv als Launcher" : "Noch nicht aktiv");
        action.setText(active ? "Launcher wechseln" : "Als Startbildschirm festlegen");
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
