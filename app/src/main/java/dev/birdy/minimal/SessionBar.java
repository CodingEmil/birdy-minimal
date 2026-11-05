package dev.birdy.minimal;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

// The time left in a session: white is what remains, and it drains from left to right, black behind it so it shows
// on any app. Always drawn from the clock, so it is right after leaving and coming back. No animator: those jump to
// the end when the system's animations are off. It redraws only when the edge reaches the next pixel.
// The same bar shows the day's time left in Shorts or Reels while their player is open, when that runs out before
// the app's session (see first()).
final class SessionBar extends View {
    static final int BAR_DP = 3;
    static final long SAME = 2_000L; // a feed's end moves a little with each second counted; not worth a new bar

    private final Paint remaining = new Paint();
    final long start, end; // wall clock (Usage.now())

    SessionBar(Context context, long start, long end) {
        super(context);
        this.start = start;
        this.end = end;
        remaining.setColor(Color.WHITE);
    }

    // First pixel of the white part.
    static int edge(int width, long start, long end, long now) {
        if (now <= start) return 0;
        if (now >= end) return width;
        return (int) (width * (now - start) / (end - start));
    }

    // Of two timers {start, end} (null when none), the one to show: whichever runs out first.
    static long[] first(long[] a, long[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return b[1] < a[1] ? b : a;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        long now = Usage.now();
        canvas.drawColor(Color.BLACK);
        canvas.drawRect(edge(width, start, end, now), 0, width, getHeight(), remaining);
        if (now < end) postInvalidateDelayed(Math.max(16, (end - start) / Math.max(1, width)));
    }

    // The bar's window on top of the app in front, owned by one accessibility service. Not a touch target, touches go
    // through to the app.
    static final class Overlay {
        private final AccessibilityService service;
        SessionBar view; // visible to tests

        Overlay(AccessibilityService service) { this.service = service; }

        // Shows the timer {start, end}, or nothing for null. A bar for (nearly) the same timer stays as it is.
        void show(long[] timer) {
            if (view != null && (timer == null || Math.abs(view.end - timer[1]) > SAME || Math.abs(view.start - timer[0]) > SAME)) {
                service.getSystemService(WindowManager.class).removeView(view);
                view = null;
            }
            if (timer == null || view != null) return;
            view = new SessionBar(service, timer[0], timer[1]);
            // No FLAG_LAYOUT_IN_SCREEN: the window stays below the status bar, and at the very top once an app hides it.
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, Math.round(BAR_DP * service.getResources().getDisplayMetrics().density),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP;
            service.getSystemService(WindowManager.class).addView(view, params);
        }
    }
}
