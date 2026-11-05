package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

// Shorts & Reels: recognising the full-screen players, and going Back, then Home if Back does not get out.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FeedsTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("feeds_used", Context.MODE_PRIVATE).edit().clear().commit();
    }

    // A screen 2400 px high with the given views (full view id → bounds).
    private static Feeds.Screen screen(Object... idsAndRects) {
        Map<String, List<Rect>> views = new HashMap<>();
        for (int i = 0; i < idsAndRects.length; i += 2) {
            views.computeIfAbsent((String) idsAndRects[i], k -> new ArrayList<>()).add((Rect) idsAndRects[i + 1]);
        }
        return new Feeds.Screen() {
            @Override public int height() { return 2400; }
            @Override public List<Rect> visible(String id) { return views.getOrDefault(id, new ArrayList<>()); }
        };
    }

    @Test
    public void fullScreenPlayerCountsAPreviewDoesNot() {
        Rect full = new Rect(0, 0, 1080, 2300), shelf = new Rect(0, 900, 1080, 1500);
        assertTrue(Feeds.showing(screen("com.google.android.youtube:id/reel_recycler", full), Feeds.Feed.SHORTS));
        assertFalse("the Shorts shelf on YouTube's home stays",
                Feeds.showing(screen("com.google.android.youtube:id/reel_recycler", shelf), Feeds.Feed.SHORTS));
        assertTrue(Feeds.showing(screen("com.instagram.android:id/clips_viewer_view_pager", full), Feeds.Feed.REELS));
        assertFalse("ids of the other app do not count",
                Feeds.showing(screen("com.google.android.youtube:id/reel_recycler", full), Feeds.Feed.REELS));
        assertFalse(Feeds.showing(screen(), Feeds.Feed.SHORTS));
    }

    @Test
    public void pictureInPictureIsDraggedFromItsMiddleToTheCloseTarget() {
        Rect pip = new Rect(600, 1600, 1000, 1900); // bottom right, as Android places it
        int[] drag = Feeds.dismissDrag(pip, 1080, 2400, 3f);
        assertEquals(800, drag[0]);
        assertEquals(1750, drag[1]);
        assertEquals("bottom centre", 540, drag[2]);
        assertEquals(2100, drag[3]);
        assertTrue("a PiP window full of Shorts counts as showing them",
                Feeds.showing(new Feeds.Screen() {
                    @Override public int height() { return 300; }
                    @Override public List<Rect> visible(String id) {
                        List<Rect> out = new ArrayList<>();
                        if (id.endsWith("reel_recycler")) out.add(new Rect(600, 1600, 1000, 1900));
                        return out;
                    }
                }, Feeds.Feed.SHORTS));
    }

    @Test
    public void packagesMapToTheirFeed() {
        assertEquals(Feeds.Feed.SHORTS, Feeds.forPackage("com.google.android.youtube"));
        assertEquals(Feeds.Feed.REELS, Feeds.forPackage("com.instagram.android"));
        assertNull(Feeds.forPackage("com.whatsapp"));
    }

    @Test
    public void actsOnceThenWaitsForTheStepToSettle() {
        Feeds.Guard g = new Feeds.Guard();
        assertTrue("acts right away, even just after boot", g.settled(0));
        assertFalse("waits for the last step to take effect", g.settled(300));
        assertTrue(g.settled(700));
    }

    @Test
    public void switchesAreOffByDefaultAndAskForTheServiceWhenTurnedOn() {
        FeedsActivity a = Robolectric.buildActivity(FeedsActivity.class).setup().get();
        Switch shorts = a.findViewById(R.id.block_shorts);
        assertFalse(shorts.isChecked());
        assertFalse(Feeds.blocked(a, Feeds.Feed.SHORTS));
        shorts.setChecked(true);
        assertTrue(Feeds.blocked(a, Feeds.Feed.SHORTS));
        AlertDialog d = (AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Shorts & Reels nutzt eine Bedienungshilfe", shadowOf(d).getTitle().toString());
        assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.feeds_setup).getVisibility());
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(a).getNextStartedActivity().getAction());
        assertFalse("Reels stay as they were", Feeds.blocked(a, Feeds.Feed.REELS));
    }

    @Test
    public void withTheServiceOnThereIsNothingToSetUp() {
        android.provider.Settings.Secure.putString(RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new android.content.ComponentName(RuntimeEnvironment.getApplication(), FeedService.class).flattenToString());
        prefs.edit().putBoolean("blockReels", true).commit();
        FeedsActivity a = Robolectric.buildActivity(FeedsActivity.class).setup().get();
        assertTrue(((Switch) a.findViewById(R.id.block_reels)).isChecked());
        assertEquals(android.view.View.GONE, a.findViewById(R.id.feeds_setup).getVisibility());
        ((Switch) a.findViewById(R.id.block_shorts)).setChecked(true);
        assertNull("no disclosure needed", ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test
    public void anAllowanceLetsTheFeedPlayUntilTheDaysTimeIsSpent() {
        Context c = RuntimeEnvironment.getApplication();
        prefs.edit().putBoolean("blockShorts", true).commit();
        assertTrue("without an allowance it is blocked right away", Feeds.usedUp(c, Feeds.Feed.SHORTS));
        Feeds.setAllowedMinutes(c, Feeds.Feed.SHORTS, 15);
        assertFalse(Feeds.usedUp(c, Feeds.Feed.SHORTS));
        Feeds.addUsed(c, Feeds.Feed.SHORTS, 14 * Feeds.MINUTE);
        assertFalse(Feeds.usedUp(c, Feeds.Feed.SHORTS));
        Feeds.addUsed(c, Feeds.Feed.SHORTS, Feeds.MINUTE);
        assertTrue(Feeds.usedUp(c, Feeds.Feed.SHORTS));
        assertEquals("Reels count on their own", 0, Feeds.usedTodayMs(c, Feeds.Feed.REELS));
        prefs.edit().putBoolean("blockShorts", false).commit();
        assertFalse("switched off, nothing is blocked", Feeds.usedUp(c, Feeds.Feed.SHORTS));
    }

    @Test
    public void yesterdaysTimeDoesNotCount() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("feeds_used", Context.MODE_PRIVATE).edit()
                .putLong("blockReelsDay", java.time.LocalDate.now().minusDays(1).toEpochDay())
                .putLong("blockReelsMs", 3 * 60 * Feeds.MINUTE).commit();
        assertEquals(0, Feeds.usedTodayMs(c, Feeds.Feed.REELS));
        Feeds.addUsed(c, Feeds.Feed.REELS, 1_000);
        assertEquals(1_000, Feeds.usedTodayMs(c, Feeds.Feed.REELS));
    }

    @Test
    public void theAllowanceIsPickedUnderTheSwitch() {
        android.provider.Settings.Secure.putString(RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                new android.content.ComponentName(RuntimeEnvironment.getApplication(), FeedService.class).flattenToString());
        FeedsActivity a = Robolectric.buildActivity(FeedsActivity.class).setup().get();
        android.widget.TextView allow = a.findViewById(R.id.allow_shorts);
        assertEquals("hidden while the switch is off", android.view.View.GONE, allow.getVisibility());
        ((Switch) a.findViewById(R.id.block_shorts)).setChecked(true);
        assertEquals(android.view.View.VISIBLE, allow.getVisibility());
        assertEquals("Pro Tag: sofort gesperrt  →", allow.getText().toString());
        allow.performClick();
        AlertDialog d = (AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("YouTube Shorts pro Tag", shadowOf(d).getTitle().toString());
        shadowOf(d).clickOnItem(2); // 30 min
        assertEquals(30, Feeds.allowedMinutes(a, Feeds.Feed.SHORTS));
        assertEquals("Pro Tag: 30 min · heute 0 min  →", allow.getText().toString());
        assertEquals("Reels keep theirs", 0, Feeds.allowedMinutes(a, Feeds.Feed.REELS));
    }

    @Test
    public void anyTimeCanBeTypedIn() {
        prefs.edit().putBoolean("blockReels", true).commit();
        FeedsActivity a = Robolectric.buildActivity(FeedsActivity.class).setup().get();
        android.widget.TextView allow = a.findViewById(R.id.allow_reels);
        allow.performClick();
        AlertDialog list = (AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("no 3 h preset, typing comes last", Feeds.ALLOWANCES.length + 1, list.getListView().getCount());
        shadowOf(list).clickOnItem(Feeds.ALLOWANCES.length);
        AlertDialog ask = (AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
        ((android.widget.EditText) ask.findViewById(R.id.allow_minutes)).setText("45");
        ask.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(45, Feeds.allowedMinutes(a, Feeds.Feed.REELS));
        assertEquals("Pro Tag: 45 min · heute 0 min  →", allow.getText().toString());
    }
}
