package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

// The two grants time limits rely on, and the warning that leads to whichever is missing.
// Usage access only matters for daily limits; strict mode matters for every limit.
final class Grants {
    static final String WARNED = "grantsWarned"; // set when the warning was shown, cleared once all is set again
    static final String PRIVACY_URL = "https://github.com/CodingEmil/birdy-minimal/blob/main/PRIVACY.md";

    private Grants() {}

    // Home screen: warn once when limits exist but a grant is missing, e.g. revoked after an update.
    // It comes back only after everything was fine in between.
    static void warnOnceFromHome(Activity activity) {
        Limits limits = Usage.load(activity);
        if (limits.all().isEmpty()) return;
        boolean needsUsage = anyDailyLimit(limits);
        android.content.SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (allSet(activity, needsUsage)) {
            prefs.edit().remove(WARNED).apply();
        } else if (!prefs.getBoolean(WARNED, false)) {
            warn(activity, needsUsage, null);
        }
    }

    static boolean anyDailyLimit(Limits limits) {
        for (Limits.AppLimit a : limits.all()) if (a.dailyMinutes > 0) return true;
        return false;
    }

    static boolean usageMissing(Context context, boolean needsUsage) {
        return needsUsage && !Usage.hasUsageAccess(context);
    }

    static boolean strictMissing(Context context) {
        return !Usage.strictModeOn(context);
    }

    static boolean allSet(Context context, boolean needsUsage) {
        return !usageMissing(context, needsUsage) && !strictMissing(context);
    }

    // "Achtung" popup listing what is missing and why; "Jetzt einrichten" opens the first missing setting.
    // Returns false when nothing is missing (and shows nothing).
    static boolean warn(Activity activity, boolean needsUsage, Runnable onSetup) {
        boolean usage = usageMissing(activity, needsUsage), strict = strictMissing(activity);
        if (!usage && !strict) return false;
        List<String> lines = new ArrayList<>();
        if (usage) lines.add("• Nutzungsdatenzugriff: Ohne ihn kennt Birdy die Nutzungszeit nicht, das Tageslimit greift nicht.");
        if (strict) lines.add("• Strenger Modus: Ohne ihn sperrt Birdy nur, wenn du die App über Birdy öffnest, nicht über Benachrichtigungen oder „Zuletzt verwendet“.");
        String message = String.join("\n\n", lines);
        if (!usage) message += "\n\nIst der Schalter dort ausgegraut: App-Info von Birdy → ⋮ → „Eingeschränkte Einstellungen zulassen“.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle("Achtung: Zeitlimit noch nicht wirksam")
                .setMessage(message)
                .setNegativeButton("Später", null)
                .setPositiveButton("Jetzt einrichten", (d, w) -> {
                    if (usage) {
                        if (onSetup != null) onSetup.run();
                        open(activity, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } else {
                        discloseStrictMode(activity, onSetup);
                    }
                });
        if (!usage) dialog.setNeutralButton("App-Info", (d, w) -> {
            if (onSetup != null) onSetup.run();
            open(activity, appInfo(activity));
        });
        dialog.show();
        activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit().putBoolean(WARNED, true).apply();
        return true;
    }

    // Shown every time before Birdy sends someone to switch its accessibility service on: what it does with it,
    // what it never does, and a choice. Google Play requires this "prominent disclosure" before the grant.
    static void discloseStrictMode(Activity activity, Runnable onAgree) {
        new AlertDialog.Builder(activity)
                .setTitle("Strenger Modus nutzt eine Bedienungshilfe")
                .setMessage("Birdy nutzt die Android-Bedienungshilfe (AccessibilityService) nur, um zu erkennen, welche App "
                        + "gerade im Vordergrund ist. So kann Birdy Apps sperren, deren Tageslimit aufgebraucht ist, deren "
                        + "Schonzeit läuft oder deren Session vorbei ist, auch wenn sie über Benachrichtigungen oder "
                        + "„Zuletzt verwendet“ geöffnet werden.\n\n"
                        + "Birdy liest dabei keine Bildschirminhalte, keine Eingaben und keine Texte, führt keine Aktionen "
                        + "für dich aus und überträgt nichts. Alles bleibt auf diesem Gerät.\n\n"
                        + "Du kannst den strengen Modus jederzeit in den Bedienungshilfen wieder ausschalten.")
                .setNegativeButton("Ablehnen", null)
                .setNeutralButton("Datenschutz", (d, w) -> open(activity, new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))))
                .setPositiveButton("Zustimmen", (d, w) -> {
                    if (onAgree != null) onAgree.run();
                    open(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .show();
    }

    // Same kind of disclosure for "Birdy Shorts & Reels", which (unlike strict mode) looks into two apps' windows.
    static void discloseFeeds(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Shorts & Reels nutzt eine Bedienungshilfe")
                .setMessage("Birdy nutzt dafür eine eigene Android-Bedienungshilfe (AccessibilityService) „Birdy Shorts & Reels“. "
                        + "Sie ist nur in YouTube und Instagram aktiv und prüft dort nur, ob der Shorts- bzw. Reels-Player "
                        + "offen ist, auch im kleinen Bild-im-Bild-Fenster. Ist er offen und gesperrt, drückt Birdy für dich "
                        + "„Zurück“ bzw. zieht das Bild-im-Bild-Fenster auf „Schließen“.\n\n"
                        + "Birdy liest dabei keine Texte, Kommentare, Nachrichten oder Eingaben, schaut in keine anderen Apps, "
                        + "speichert nichts darüber und überträgt nichts. Alles bleibt auf diesem Gerät.\n\n"
                        + "Du kannst sie jederzeit in den Bedienungshilfen wieder ausschalten.")
                .setNegativeButton("Ablehnen", null)
                .setNeutralButton("Datenschutz", (d, w) -> open(activity, new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))))
                .setPositiveButton("Zustimmen", (d, w) -> open(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show();
    }

    static Intent appInfo(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null));
    }

    static void open(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
