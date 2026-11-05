package dev.birdy.minimal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// "Shorts & Reels": an accessibility service of its own, limited to YouTube and Instagram
// (res/xml/feed_service.xml). It looks only for the view ids of their short-video players, nothing else, and
// when a blocked one fills the screen it presses Back (Home if Back does not get out); a blocked feed in a
// picture-in-picture window is dragged onto the close target. Strict mode's service
// stays as it was: it never reads window content.
public class FeedService extends AccessibilityService {
    static final long DEBOUNCE = 250; // content changes come in bursts while scrolling

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Feeds.Guard guard = new Feeds.Guard(), pipGuard = new Feeds.Guard();
    private final Runnable check = this::check;
    private long lastToast;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        Feeds.Feed feed = Feeds.forPackage(event.getPackageName().toString());
        if (feed == null || !Feeds.blocked(this, feed)) return;
        handler.removeCallbacks(check);
        handler.postDelayed(check, DEBOUNCE);
    }

    void check() {
        if (checkPictureInPicture()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        Feeds.Feed feed = blockedFeedIn(root);
        if (feed == null) return;
        switch (guard.next(SystemClock.elapsedRealtime())) {
            case BACK: performGlobalAction(GLOBAL_ACTION_BACK); break;
            case HOME: performGlobalAction(GLOBAL_ACTION_HOME); break;
            case NONE: handler.postDelayed(check, Feeds.Guard.SETTLE); return; // look again once the last step settled
        }
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
            if (pipGuard.next(SystemClock.elapsedRealtime()) == Feeds.Guard.Action.NONE) {
                handler.postDelayed(check, Feeds.Guard.SETTLE);
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

    private Feeds.Feed blockedFeedIn(AccessibilityNodeInfo root) {
        if (root.getPackageName() == null) return null;
        Feeds.Feed feed = Feeds.forPackage(root.getPackageName().toString());
        return feed != null && Feeds.blocked(this, feed) && Feeds.showing(screen(root), feed) ? feed : null;
    }

    private void acted(Feeds.Feed feed) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastToast > 4_000) {
            lastToast = now;
            Toast.makeText(this, feed.label + " sind gesperrt", Toast.LENGTH_SHORT).show();
        }
        handler.postDelayed(check, Feeds.Guard.SETTLE); // did it leave the feed?
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
        handler.removeCallbacks(check);
        return super.onUnbind(intent);
    }
}
