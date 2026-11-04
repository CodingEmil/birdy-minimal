package dev.birdy.minimal;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

// Foreground time per app today, from Android's usage events (needs "Nutzungsdatenzugriff"),
// and the checks for the two grants strict mode needs.
final class Usage {
    static final int RESUMED = 1, PAUSED = 2, SCREEN_OFF = 16, STOPPED = 23, SHUTDOWN = 26; // UsageEvents.Event types

    static Clock clock = Clock.systemDefaultZone(); // replaced in tests

    private Usage() {}

    static long now() { return clock.millis(); }

    static long startOfDay() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
    }

    static long msOfDay() { return now() - startOfDay(); }

    // One usage event, reduced to what the sum needs.
    interface Event {
        int type();
        String pkg();
        String cls();
        long time();
    }

    // Time pkg spent in front between from and now. An app counts as in front while any of its activities is
    // resumed; a pause without a resume today means it was already open at midnight.
    static long foregroundMs(Iterable<Event> events, String pkg, long from, long now) {
        Set<String> resumed = new HashSet<>(), seen = new HashSet<>();
        long total = 0, since = -1;
        for (Event e : events) {
            if (e.type() == SCREEN_OFF || e.type() == SHUTDOWN) {
                if (since >= 0) total += e.time() - since;
                since = -1;
                resumed.clear();
                continue;
            }
            if (!pkg.equals(e.pkg())) continue;
            if (e.type() == RESUMED) {
                if (resumed.isEmpty()) since = e.time();
                resumed.add(e.cls());
                seen.add(e.cls());
            } else if (e.type() == PAUSED || e.type() == STOPPED) {
                if (resumed.remove(e.cls())) {
                    if (resumed.isEmpty() && since >= 0) { total += e.time() - since; since = -1; }
                } else if (e.type() == PAUSED && !seen.contains(e.cls()) && resumed.isEmpty()) {
                    total += e.time() - from; // open since before the window
                }
                seen.add(e.cls());
            }
        }
        if (since >= 0) total += now - since;
        return Math.max(0, total);
    }

    // Today's foreground time of pkg; 0 without usage access.
    static long todayMs(Context context, String pkg) {
        if (!hasUsageAccess(context)) return 0;
        long from = startOfDay(), now = now();
        UsageEvents raw = context.getSystemService(UsageStatsManager.class).queryEvents(from, now);
        java.util.List<Event> events = new java.util.ArrayList<>();
        while (raw != null && raw.hasNextEvent()) {
            UsageEvents.Event e = new UsageEvents.Event();
            raw.getNextEvent(e);
            int type = e.getEventType();
            long time = e.getTimeStamp();
            String p = e.getPackageName(), c = e.getClassName();
            events.add(new Event() {
                @Override public int type() { return type; }
                @Override public String pkg() { return p; }
                @Override public String cls() { return c; }
                @Override public long time() { return time; }
            });
        }
        return foregroundMs(events, pkg, from, now);
    }

    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static boolean strictModeOn(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(context, BlockService.class);
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    // The block decision shared by the launcher and the accessibility service; null when allowed.
    static Limits.Block check(Context context, Limits.AppLimit limit) {
        if (limit == null) return null;
        long used = limit.dailyMinutes > 0 ? todayMs(context, limit.pkg) : 0;
        return limit.check(used, msOfDay());
    }

    static Limits load(Context context) {
        return Limits.parse(context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getString("limits", ""));
    }

    static void save(Context context, Limits limits) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit().putString("limits", limits.serialize()).apply();
    }
}
