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
// opened (Birdy, a notification, Recents, a link). A blocked app is covered by the block screen at once, an app
// that asks first by the session screen; while a limited app stays in front, a timer re-checks when its time or
// session runs out or a quiet time begins.
// Reads only package names of windows, never their content (see res/xml/block_service.xml).
public class BlockService extends AccessibilityService {
    static final long MAX_WAIT = 60_000L; // re-check at least every minute: usage keeps counting, clocks drift
    static final long SETTLE = 600L; // after Recents or the shade closed: time for the app's activity to resume
    static final long COVER_HOLD = 3_000L; // longest an app's burst of window changes may follow its cover

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::recheck;
    private final Runnable settle = this::settle;
    private final Map<String, Boolean> isApp = new HashMap<>(); // keyboards and system UI do not change the app in front
    private String current;
    // The cover (block or session screen) opened last, as "block:<pkg>" / "ask:<pkg>", and when. An app coming to
    // the front reports several window changes in a row (splash, main window, ...); the first opens the cover, the
    // rest must not open it again, or it flashes up twice. Any other window in between (Birdy's home, another app,
    // Recents) means the app may really be back, so the next change counts again.
    private String cover;
    private long coverAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (cover != null && !cover.endsWith(":" + pkg) && !isCover(pkg, event.getClassName())) cover = null;
        if (!isApp(pkg)) { // Recents, the notification shade, a keyboard: the app behind may have just changed
            handler.removeCallbacks(settle);
            handler.postDelayed(settle, SETTLE);
            return;
        }
        handler.removeCallbacks(settle);
        current = pkg;
        recheck();
    }

    // Safety net for an app that came to the front without a window change of its own reaching Birdy (some
    // launchers' Recents animate the app in): with usage access, Android's usage events say which app resumed last.
    void settle() {
        String pkg = Usage.lastResumed(this, SETTLE + 10_000L);
        if (pkg == null || pkg.equals(current) || !isApp(pkg)) return;
        current = pkg;
        recheck();
    }

    private boolean isCover(String pkg, CharSequence cls) {
        if (!pkg.equals(getPackageName()) || cls == null) return false;
        String c = cls.toString();
        return c.equals(BlockActivity.class.getName()) || c.equals(SessionActivity.class.getName());
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
        Limits.Moment moment = Usage.moment(this, limit);
        Limits.Block block = limit.check(moment);
        if (block != null) {
            String key = (block.ask ? "ask:" : "block:") + current;
            long now = android.os.SystemClock.elapsedRealtime();
            if (key.equals(cover) && now - coverAt < COVER_HOLD) return; // already on its way or showing
            cover = key;
            coverAt = now;
            if (block.ask) SessionActivity.show(this, current, block); else BlockActivity.show(this, current, block);
            return;
        }
        cover = null;
        long wait = limit.msUntilBlock(moment);
        handler.postDelayed(recheck, Math.max(1_000L, Math.min(MAX_WAIT, wait + 500)));
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(recheck);
        handler.removeCallbacks(settle);
        return super.onUnbind(intent);
    }
}
