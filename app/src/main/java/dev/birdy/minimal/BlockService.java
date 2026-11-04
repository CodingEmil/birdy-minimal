package dev.birdy.minimal;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

// Strict mode. As an accessibility service Birdy hears about every app that comes to the front, however it was
// opened (Birdy, a notification, Recents, a link). A blocked app is covered by the block screen at once; while a
// limited app stays in front, a timer re-checks when its time runs out or a quiet time begins.
// Reads only package names of windows, never their content (see res/xml/block_service.xml).
public class BlockService extends AccessibilityService {
    static final long MAX_WAIT = 60_000L; // re-check at least every minute: usage keeps counting, clocks drift

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::recheck;
    private final Map<String, Boolean> isApp = new HashMap<>(); // keyboards and system UI do not change the app in front
    private String current;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!isApp(pkg)) return;
        current = pkg;
        recheck();
    }

    // Packages with a launcher entry are "apps"; the keyboard or the notification shade have none.
    private boolean isApp(String pkg) {
        if (pkg.equals(getPackageName())) return true;
        Boolean known = isApp.get(pkg);
        if (known == null) {
            PackageManager pm = getPackageManager();
            isApp.put(pkg, known = pm.getLaunchIntentForPackage(pkg) != null);
        }
        return known;
    }

    void recheck() {
        handler.removeCallbacks(recheck);
        if (current == null || current.equals(getPackageName())) return;
        Limits.AppLimit limit = Usage.load(this).get(current);
        if (limit == null) return;
        long used = limit.dailyMinutes > 0 ? Usage.todayMs(this, current) : 0;
        Limits.Block block = limit.check(used, Usage.msOfDay());
        if (block != null) {
            BlockActivity.show(this, current, block);
            return;
        }
        long wait = limit.msUntilBlock(used, Usage.msOfDay());
        handler.postDelayed(recheck, Math.max(1_000L, Math.min(MAX_WAIT, wait + 500)));
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(recheck);
        return super.onUnbind(intent);
    }
}
