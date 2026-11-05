package dev.birdy.minimal;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.List;

// Endless short-video feeds inside apps: YouTube Shorts and Instagram Reels. FeedService looks at the view ids of
// the app in front and, when the full-screen player of a blocked feed shows, goes to the home screen.
// The ids are the apps' own and may change with their updates; then add the new one here.
final class Feeds {
    static final String YOUTUBE = "com.google.android.youtube", INSTAGRAM = "com.instagram.android";

    enum Feed {
        SHORTS(YOUTUBE, "blockShorts", "YouTube Shorts",
                "reel_recycler", "reel_player_page_container", "reel_watch_player"),
        REELS(INSTAGRAM, "blockReels", "Instagram Reels",
                "clips_viewer_view_pager", "clips_viewer_container", "clips_video_container");

        final String pkg, pref, label;
        final String[] ids;

        Feed(String pkg, String pref, String label, String... ids) {
            this.pkg = pkg;
            this.pref = pref;
            this.label = label;
            this.ids = ids;
        }
    }

    // What FeedService sees of the app in front; a fake in tests.
    interface Screen {
        int height();

        List<Rect> visible(String fullViewId); // on-screen bounds of the visible views with that id
    }

    private Feeds() {}

    static Feed forPackage(String pkg) {
        for (Feed f : Feed.values()) if (f.pkg.equals(pkg)) return f;
        return null;
    }

    static boolean blocked(Context context, Feed feed) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getBoolean(feed.pref, false);
    }

    static boolean anyBlocked(Context context) {
        for (Feed f : Feed.values()) if (blocked(context, f)) return true;
        return false;
    }

    // The player fills most of the screen; a small preview of the feed (e.g. the Shorts shelf on YouTube's home)
    // does not count.
    static boolean showing(Screen screen, Feed feed) {
        int min = screen.height() / 2;
        for (String id : feed.ids) {
            for (Rect r : screen.visible(feed.pkg + ":id/" + id)) if (r.height() >= min) return true;
        }
        return false;
    }

    static final long DRAG_MS = 450;

    // Start and end of the drag that closes a picture-in-picture window: from its middle down to the close target
    // Android shows at the bottom centre while a PiP window is dragged (about 100 dp above the bottom edge).
    static int[] dismissDrag(Rect pip, int screenWidth, int screenHeight, float density) {
        return new int[] {pip.centerX(), pip.centerY(), screenWidth / 2, screenHeight - Math.round(100 * density)};
    }

    static boolean serviceOn(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(context, FeedService.class);
        for (String s : enabled.split(":")) if (me.equals(ComponentName.unflattenFromString(s))) return true;
        return false;
    }

    // A short wait lets the last step out of a blocked feed take effect before acting again.
    static final class Guard {
        static final long SETTLE = 700;
        private long last = -SETTLE;

        boolean settled(long now) {
            if (now - last < SETTLE) return false;
            last = now;
            return true;
        }
    }
}
