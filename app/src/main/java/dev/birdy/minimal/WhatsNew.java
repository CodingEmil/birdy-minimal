package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// "Neu in Birdy 1.4" once after an update. The text is this version's CHANGELOG section, built into the app as
// assets/whatsnew.txt (see app/build.gradle.kts). A fresh install shows nothing, it only remembers the version.
final class WhatsNew {
    static final String SEEN = "seenVersion";

    interface Source {
        String text(Context context);
    }

    static final Source ASSET = WhatsNew::asset;
    static Source source = ASSET; // replaced in tests

    private WhatsNew() {}

    // Returns true when the popup is shown, so the caller can hold back other dialogs.
    static boolean showAfterUpdate(Activity activity) {
        PackageInfo info;
        try {
            info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        @SuppressWarnings("deprecation")
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        long seen = prefs.getLong(SEEN, -1);
        if (seen >= code) return false;
        prefs.edit().putLong(SEEN, code).apply();
        boolean updated = seen >= 0 || info.lastUpdateTime > info.firstInstallTime; // also updates from before this popup existed
        String text = source.text(activity).trim();
        if (!updated || text.isEmpty() || "dev".equals(info.versionName)) return false;
        new AlertDialog.Builder(activity)
                .setTitle("Neu in Birdy " + info.versionName)
                .setMessage(text)
                .setPositiveButton("Schön", null)
                .show();
        return true;
    }

    private static String asset(Context context) {
        try (InputStream in = context.getAssets().open("whatsnew.txt")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }
}
