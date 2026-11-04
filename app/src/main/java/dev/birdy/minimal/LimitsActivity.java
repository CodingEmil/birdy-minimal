package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The time limits settings page: all apps with limits (tap to edit), adding one, and strict mode's grants.
public class LimitsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.limits);
        findViewById(R.id.limits_add).setOnClickListener(v -> pickApp());
    }

    @Override
    protected void onResume() { // back from an app's limits or a settings page
        super.onResume();
        render();
        LimitActivity.bindStrictStatus(this, findViewById(android.R.id.content));
    }

    // One row per app with limits: name, then what is set.
    private void render() {
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
            row.setLayoutParams(lp);
            android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(label).append('\n');
            int start = text.length();
            text.append(a.summary());
            text.setSpan(new android.text.style.ForegroundColorSpan(0xFF8C8C8C), start, text.length(), 0);
            text.setSpan(new android.text.style.RelativeSizeSpan(0.82f), start, text.length(), 0);
            row.setText(text);
            row.setTextColor(getColorStateList(R.color.item_text));
            row.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            row.setOnClickListener(v -> LimitActivity.open(this, a.pkg, label));
            list.addView(row);
        }
        findViewById(R.id.limits_empty).setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // All apps with a launcher entry, alphabetical, except Birdy itself.
    private void pickApp() {
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (LauncherActivityInfo info : getSystemService(LauncherApps.class).getActivityList(null, Process.myUserHandle())) {
            String pkg = info.getComponentName().getPackageName();
            if (!pkg.equals(getPackageName())) byPkg.putIfAbsent(pkg, info.getLabel().toString());
        }
        List<Map.Entry<String, String>> apps = new ArrayList<>(byPkg.entrySet());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.getValue(), b.getValue()));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).getValue();
        new AlertDialog.Builder(this)
                .setTitle("App auswählen")
                .setItems(labels, (d, which) -> LimitActivity.open(this, apps.get(which).getKey(), apps.get(which).getValue()))
                .setNegativeButton("Abbrechen", null)
                .show();
    }
}
