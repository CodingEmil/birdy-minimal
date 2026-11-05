package dev.birdy.minimal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// "Shorts & Reels": an accessibility service of its own, limited to YouTube and Instagram
// (res/xml/feed_service.xml). It looks only for the view ids of their short-video players, nothing else, and
// when a blocked one fills the screen it counts the time against the day's allowance. Once that is spent it presses
// Back and then Home; a blocked feed in a picture-in-picture window is dragged onto the close target.
// Strict mode's service stays as it was: it never reads window content.
public class FeedService extends AccessibilityService {
    static final long THROTTLE = 250, BACK_MS = 200, NOTICE_MS = 3_000;
    static final long TICK = 1_000, MAX_GAP = 2 * TICK; // while a feed plays: look once a second, count at most a gap

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Feeds.Guard guard = new Feeds.Guard(), pipGuard = new Feeds.Guard();
    private final Runnable check = this::check;
    private boolean checkPending;
    private long lastNotice;
    private TextView notice;
    private Feeds.Feed watched; // the feed that played at the last look, while its allowance lasts
    private long watchedAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        Feeds.Feed feed = Feeds.forPackage(event.getPackageName().toString());
        if (feed == null || !Feeds.blocked(this, feed)) return;
        // Never push a pending check back: a playing Reel sends content changes every 100 ms without a pause.
        if (!checkPending) scheduleCheck(THROTTLE);
    }

    private void scheduleCheck(long delay) {
        handler.removeCallbacks(check);
        checkPending = true;
        handler.postDelayed(check, delay);
    }

    void check() {
        checkPending = false;
        if (checkPictureInPicture()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Feeds.Feed feed = root == null ? null : blockedFeedIn(root);
        if (feed == null) {
            watched = null;
            return;
        }
        if (!Feeds.usedUp(this, feed)) {
            watching(feed);
            return;
        }
        if (!guard.settled(SystemClock.elapsedRealtime())) {
            scheduleCheck(Feeds.Guard.SETTLE); // look again once the last step settled
            return;
        }
        // Back first, or the app would stay on the feed and throw the user out again the next time it opens.
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), BACK_MS);
        acted(feed);
    }

    // A picture-in-picture window is never the active one (focus stays with the app behind it), and Back does not
    // reach it. So look at every window, and drag a PiP window that shows a blocked feed onto the system's close
    // target, as a finger would. Returns true when it acted or is waiting for the last drag to settle.
    private boolean checkPictureInPicture() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (!window.isInPictureInPictureMode()) continue;
            AccessibilityNodeInfo root = window.getRoot();
            Feeds.Feed feed = root == null ? null : blockedFeedIn(root);
            if (feed == null) continue;
            if (!Feeds.usedUp(this, feed)) {
                watching(feed);
                return true;
            }
            if (!pipGuard.settled(SystemClock.elapsedRealtime())) {
                scheduleCheck(Feeds.Guard.SETTLE);
                return true;
            }
            Rect pip = new Rect();
            window.getBoundsInScreen(pip);
            DisplayMetrics m = getResources().getDisplayMetrics();
            int[] drag = Feeds.dismissDrag(pip, m.widthPixels, m.heightPixels, m.density);
            Path path = new Path();
            path.moveTo(drag[0], drag[1]);
            path.lineTo(drag[2], drag[3]);
            dispatchGesture(new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, Feeds.DRAG_MS)).build(), null, null);
            acted(feed);
            return true;
        }
        return false;
    }

    // Allowance left: count the time since the last look and look again in a second, so the block comes on time
    // even when the app sends no events. A dark screen counts nothing.
    private void watching(Feeds.Feed feed) {
        long now = SystemClock.elapsedRealtime();
        if (!getSystemService(PowerManager.class).isInteractive()) {
            watched = null;
            return;
        }
        if (watched == feed) Feeds.addUsed(this, feed, Math.min(now - watchedAt, MAX_GAP));
        watched = feed;
        watchedAt = now;
        scheduleCheck(TICK);
    }

    private Feeds.Feed blockedFeedIn(AccessibilityNodeInfo root) {
        if (root.getPackageName() == null) return null;
        Feeds.Feed feed = Feeds.forPackage(root.getPackageName().toString());
        return feed != null && Feeds.blocked(this, feed) && Feeds.showing(screen(root), feed) ? feed : null;
    }

    private void acted(Feeds.Feed feed) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotice > 4_000) {
            lastNotice = now;
            int minutes = Feeds.allowedMinutes(this, feed);
            showNotice(minutes == 0 ? "Du hast " + feed.label + " gesperrt, du Hund!"
                    : Limits.duration(minutes) + " " + feed.label + " für heute sind um.");
        }
        watched = null;
        scheduleCheck(Feeds.Guard.SETTLE); // did it leave the feed?
    }

    // Not a Toast: Android drops those from a background app without the notification permission, which Birdy does
    // not ask for. An accessibility overlay needs no permission and lets touches through.
    private void showNotice(String text) {
        removeNotice();
        float dp = getResources().getDisplayMetrics().density;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setTypeface(getResources().getFont(R.font.outfit));
        view.setPadding(Math.round(20 * dp), Math.round(12 * dp), Math.round(20 * dp), Math.round(12 * dp));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setStroke(Math.round(dp), Color.WHITE);
        background.setCornerRadius(12 * dp);
        view.setBackground(background);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = Math.round(96 * dp);
        getSystemService(WindowManager.class).addView(view, params);
        notice = view;
        handler.postDelayed(this::removeNotice, NOTICE_MS);
    }

    private void removeNotice() {
        if (notice == null) return;
        getSystemService(WindowManager.class).removeView(notice);
        notice = null;
    }

    private static Feeds.Screen screen(AccessibilityNodeInfo root) {
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        int height = bounds.height();
        return new Feeds.Screen() {
            @Override public int height() { return height; }

            @Override public List<Rect> visible(String fullViewId) {
                List<Rect> out = new ArrayList<>();
                for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(fullViewId)) {
                    if (!n.isVisibleToUser()) continue;
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    out.add(r);
                }
                return out;
            }
        };
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacksAndMessages(null);
        removeNotice();
        return super.onUnbind(intent);
    }
}
