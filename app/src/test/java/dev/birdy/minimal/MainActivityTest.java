package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

// Drives the real MainActivity: swipes, typing and lifecycle, with fake installed apps.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h891dp-xxhdpi", shadows = {MainActivityTest.FixedLabel.class, MainActivityTest.CountingStatusBar.class})
public class MainActivityTest {
    // getLabel() runs an ICU visibility check that, depending on the Robolectric SDK jar, can reject every label
    // and fall back to the package name. The test apps' names are plain text, so read them directly.
    @Implements(LauncherActivityInfo.class)
    public static class FixedLabel {
        @RealObject private LauncherActivityInfo real;

        @Implementation
        protected CharSequence getLabel() { return real.getActivityInfo().nonLocalizedLabel; }
    }

    // Robolectric does not track expandNotificationsPanel(), so count the calls here.
    @Implements(value = android.app.StatusBarManager.class, isInAndroidSdk = false)
    public static class CountingStatusBar {
        static int expanded;

        @Implementation
        protected void expandNotificationsPanel() { expanded++; }
    }

    private static final String[] LABELS = {
            "Kamera", "Kalender", "Kontakte", "Karten", "Kindle", "Klarna", "K-9 Mail", "Spotify", "Signal",
            "Slack", "Einstellungen", "Uhr", "WhatsApp", "Wetter", "Telegram", "Threema", "YouTube", "Gmail",
            "Google Maps", "Chrome", "Notizen", "Rechner", "Galerie", "Dateien", "Play Store", "Netflix",
            "Outlook", "PayPal", "Duolingo", "Banking", "Bahn", "Musik", "Podcasts", "Fotos", "Files"};

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        CountingStatusBar.expanded = 0;
    }

    private void installApps(String... labels) throws Exception {
        LauncherApps la = context.getSystemService(LauncherApps.class);
        for (String label : labels) {
            String pkg = "com.example." + label.toLowerCase().replaceAll("[^a-z0-9]", "");
            ActivityInfo ai = new ActivityInfo();
            ai.packageName = pkg;
            ai.name = pkg + ".Main";
            ai.nonLocalizedLabel = label;
            ai.applicationInfo = new ApplicationInfo();
            ai.applicationInfo.packageName = pkg;
            ai.applicationInfo.flags = 0;
            shadowOf(la).addActivity(Process.myUserHandle(), launcherActivityInfo(ai));
        }
    }

    // LauncherActivityInfo has only hidden constructors; build it the way the system does.
    private LauncherActivityInfo launcherActivityInfo(ActivityInfo ai) throws Exception {
        Class<?> states = Class.forName("android.content.pm.IncrementalStatesInfo");
        Object st = states.getConstructor(boolean.class, float.class, long.class).newInstance(false, 1f, 0L);
        Class<?> internal = Class.forName("android.content.pm.LauncherActivityInfoInternal");
        Object in;
        try { // Android 16 added a boolean
            in = internal.getConstructor(ActivityInfo.class, states, android.os.UserHandle.class, boolean.class)
                    .newInstance(ai, st, Process.myUserHandle(), false);
        } catch (NoSuchMethodException e) {
            in = internal.getConstructor(ActivityInfo.class, states, android.os.UserHandle.class)
                    .newInstance(ai, st, Process.myUserHandle());
        }
        Constructor<LauncherActivityInfo> c = LauncherActivityInfo.class.getDeclaredConstructor(Context.class, internal);
        c.setAccessible(true);
        return c.newInstance(context, in);
    }

    private ActivityController<MainActivity> start() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        idle();
        return c;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    // A quick upward flick in the middle of the screen, as a finger would do it.
    private static void swipe(MainActivity a, float fromY, float toY) {
        long t = SystemClock.uptimeMillis();
        float x = 540;
        a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, fromY, 0));
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            float y = fromY + (toY - fromY) * i / steps;
            a.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 12L, MotionEvent.ACTION_MOVE, x, y, 0));
        }
        a.dispatchTouchEvent(MotionEvent.obtain(t, t + steps * 12L + 5, MotionEvent.ACTION_UP, x, toY, 0));
        idle();
    }

    private static void swipeUp(MainActivity a) { swipe(a, 1800, 900); }

    private static void type(MainActivity a, String text) {
        EditText search = a.findViewById(R.id.search);
        search.setText(text);
        idle();
    }

    private static List<String> shown(MainActivity a) {
        ListView list = a.findViewById(R.id.list);
        List<String> out = new ArrayList<>();
        for (int i = list.getHeaderViewsCount(); i < list.getCount(); i++) out.add(list.getItemAtPosition(i).toString());
        return out;
    }

    private boolean keyboardShown() {
        return shadowOf(context.getSystemService(InputMethodManager.class)).isSoftInputVisible();
    }

    // Writes a PNG when run with -Dshots.dir=... and robolectric.graphicsMode=NATIVE; otherwise does nothing.
    private static void shot(MainActivity a, String name) throws Exception {
        if (System.getProperty("shots.dir") == null) return;
        View root = a.getWindow().getDecorView();
        Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        File dir = new File(System.getProperty("shots.dir"));
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    // --- Swipe up / keyboard ---

    @Test
    public void swipeUpOpensListWithoutKeyboardByDefault() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        shot(a, "1-home");
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        swipeUp(a);
        assertEquals(View.VISIBLE, a.findViewById(R.id.drawer).getVisibility());
        assertEquals(1f, a.findViewById(R.id.drawer).getAlpha(), 0.001f);
        assertFalse(a.findViewById(R.id.search).hasFocus());
        assertFalse(keyboardShown());
        shot(a, "2-list-default");
    }

    @Test
    public void swipeUpOpensKeyboardWhenSettingOn() throws Exception {
        prefs.edit().putBoolean("type", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        assertFalse("keyboard must not show on the home screen", keyboardShown());
        swipeUp(a);
        assertTrue(a.findViewById(R.id.search).hasFocus());
        assertTrue(keyboardShown());
    }

    @Test
    public void closingHidesKeyboardAgain() throws Exception {
        prefs.edit().putBoolean("type", true).commit();
        installApps(LABELS);
        ActivityController<MainActivity> c = start();
        MainActivity a = c.get();
        swipeUp(a);
        assertTrue(keyboardShown());
        a.onBackPressed();
        idle();
        assertFalse(keyboardShown());
        assertFalse(a.findViewById(R.id.search).hasFocus());
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        // and opening again brings it back
        swipeUp(a);
        assertTrue(keyboardShown());
    }

    @Test
    public void swipeDownFromListClosesItAndHidesKeyboard() throws Exception {
        prefs.edit().putBoolean("type", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        swipe(a, 600, 1500);
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        assertFalse(keyboardShown());
    }

    @Test
    public void keyboardSettingTakesEffectWithoutRestart() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        assertFalse(keyboardShown());
        a.onBackPressed();
        idle();
        prefs.edit().putBoolean("type", true).commit();
        swipeUp(a);
        assertTrue(keyboardShown());
    }

    // Regression: onStop called Activity.setProgress(int) instead of Birdy's own method, so the list stayed open.
    @Test
    public void comingBackFromAnAppLandsOnHome() throws Exception {
        installApps(LABELS);
        ActivityController<MainActivity> c = start();
        MainActivity a = c.get();
        swipeUp(a);
        type(a, "k");
        c.pause().stop(); // an app was opened
        idle();
        c.restart().start().resume(); // back to Birdy, e.g. via the back gesture
        idle();
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        assertEquals(1f, a.findViewById(R.id.home).getAlpha(), 0.001f);
        assertEquals("", ((EditText) a.findViewById(R.id.search)).getText().toString());
    }

    @Test
    public void comingBackFromAnAppShowsNoKeyboard() throws Exception {
        prefs.edit().putBoolean("type", true).commit();
        installApps(LABELS);
        ActivityController<MainActivity> c = start();
        MainActivity a = c.get();
        swipeUp(a);
        assertTrue(keyboardShown());
        c.pause().stop();
        idle();
        assertFalse(keyboardShown());
        c.restart().start().resume();
        idle();
        assertFalse(keyboardShown());
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        swipeUp(a);
        assertTrue("next swipe opens it again", keyboardShown());
    }

    // --- Swipe down for notifications ---

    @Test
    public void swipeDownOnHomeOpensNotificationsOnce() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipe(a, 900, 1800);
        assertEquals(1, CountingStatusBar.expanded);
        assertEquals("the app list stays closed", View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        assertEquals(1f, a.findViewById(R.id.home).getAlpha(), 0.001f);
    }

    @Test
    public void swipeDownDoesNothingWhenSettingOff() throws Exception {
        prefs.edit().putBoolean("shade", false).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipe(a, 900, 1800);
        assertEquals(0, CountingStatusBar.expanded);
    }

    @Test
    public void swipeDownInOpenListClosesItInsteadOfNotifications() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        swipe(a, 600, 1500);
        assertEquals(0, CountingStatusBar.expanded);
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        swipe(a, 900, 1800); // now on home again
        assertEquals(1, CountingStatusBar.expanded);
    }

    @Test
    public void tapAndSwipeUpDoNotOpenNotifications() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipe(a, 1000, 1005); // a tap with a tiny wobble
        swipeUp(a);
        assertEquals(0, CountingStatusBar.expanded);
    }

    @Test
    public void sidewaysSwipeDoesNotOpenNotifications() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        long t = SystemClock.uptimeMillis();
        a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200, 1000, 0));
        a.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 600, 1060, 0));
        a.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 900, 1100, 0));
        idle();
        assertEquals(0, CountingStatusBar.expanded);
    }

    // --- Search results position ---

    @Test
    public void resultsReadTopDownByDefault() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        ListView list = a.findViewById(R.id.list);
        assertFalse(list.isStackFromBottom());
        assertEquals(Arrays.asList("K-9 Mail", "Kalender", "Kamera", "Karten", "Kindle", "Klarna", "Kontakte"), shown(a));
        View first = list.getChildAt(1); // 0 is the (collapsed) header
        assertTrue("first match sits at the top, y=" + first.getTop(), first.getTop() < list.getHeight() / 4);
        shot(a, "3-search-top");
    }

    @Test
    public void resultsSitAtBottomWithBestMatchClosestToSearchField() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        ListView list = a.findViewById(R.id.list);
        assertTrue(list.isStackFromBottom());
        assertEquals(Arrays.asList("Kontakte", "Klarna", "Kindle", "Karten", "Kamera", "Kalender", "K-9 Mail"), shown(a));
        View last = list.getChildAt(list.getChildCount() - 1);
        assertEquals("K-9 Mail", ((android.widget.TextView) last).getText().toString());
        int gap = list.getHeight() - list.getPaddingBottom() - last.getBottom();
        assertTrue("best match sits right above the search field, gap=" + gap, gap >= 0 && gap < 4);
        View topMost = list.getChildAt(1);
        assertTrue("results do not start at the top, y=" + topMost.getTop(), topMost.getTop() > list.getHeight() / 3);
        shot(a, "4-search-bottom");
    }

    @Test
    public void bestMatchVisibleWhenManyResultsAtBottom() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        String[] many = new String[60];
        for (int i = 0; i < many.length; i++) many[i] = String.format("App %02d", i);
        installApps(many);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "app");
        ListView list = a.findViewById(R.id.list);
        assertEquals(60, shown(a).size());
        assertEquals("App 00", list.getItemAtPosition(list.getCount() - 1).toString());
        assertEquals("best match is on screen", list.getCount() - 1, list.getLastVisiblePosition());
        assertTrue("the rest is reachable by scrolling up", list.canScrollVertically(-1));
        type(a, "app 0");
        assertEquals(10, shown(a).size());
        assertEquals(list.getCount() - 1, list.getLastVisiblePosition());
        shot(a, "5-many-bottom");
    }

    @Test
    public void enterOpensBestMatchInBothModes() throws Exception {
        for (boolean bottom : new boolean[] {false, true}) {
            prefs.edit().clear().putBoolean("top", true).putBoolean("bottom", bottom).commit();
            installApps(LABELS);
            MainActivity a = start().get();
            swipeUp(a);
            type(a, "ka");
            EditText search = a.findViewById(R.id.search);
            search.onEditorAction(EditorInfo.IME_ACTION_GO);
            idle();
            String log = prefs.getString("launches", "");
            assertTrue("bottom=" + bottom + " launched: " + log, log.contains("com.example.kalender/"));
        }
    }

    @Test
    public void whitespaceQueryDoesNotLaunchAnything() throws Exception {
        prefs.edit().putBoolean("top", true).putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "  ");
        ((EditText) a.findViewById(R.id.search)).onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
        assertEquals("", prefs.getString("launches", ""));
        assertFalse("blank query keeps the normal list", ((ListView) a.findViewById(R.id.list)).isStackFromBottom());
    }

    @Test
    public void clearingSearchGoesBackToNormalList() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        type(a, "");
        ListView list = a.findViewById(R.id.list);
        assertFalse(list.isStackFromBottom());
        assertEquals(LABELS.length, shown(a).size());
        assertEquals("Bahn", shown(a).get(0));
        assertEquals(0, list.getFirstVisiblePosition());
    }

    @Test
    public void enterPrefersNameStartOverLaterWord() throws Exception {
        prefs.edit().putBoolean("top", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "s");
        assertEquals(Arrays.asList("Signal", "Slack", "Spotify", "Play Store"), shown(a));
        ((EditText) a.findViewById(R.id.search)).onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
        assertTrue(prefs.getString("launches", "").contains("com.example.signal/"));
    }

    @Test
    public void noMatchesShowsEmptyList() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "xyz");
        assertTrue(shown(a).isEmpty());
    }

    @Test
    public void bottomSettingAppliesLiveDuringSearch() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        assertEquals("K-9 Mail", shown(a).get(0));
        prefs.edit().putBoolean("bottom", true).commit();
        idle();
        assertEquals("K-9 Mail", shown(a).get(shown(a).size() - 1));
        assertTrue(((ListView) a.findViewById(R.id.list)).isStackFromBottom());
    }

    @Test
    public void searchClearedWhenListCloses() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        a.onBackPressed();
        idle();
        swipeUp(a);
        assertEquals("", ((EditText) a.findViewById(R.id.search)).getText().toString());
        assertFalse(((ListView) a.findViewById(R.id.list)).isStackFromBottom());
        assertEquals(LABELS.length, shown(a).size());
    }

    @Test
    public void mostUsedHiddenWhileSearchingAndShownAgainAfter() throws Exception {
        long now = System.currentTimeMillis();
        String log = LaunchLog.append("", "com.example.spotify/com.example.spotify.Main", now, 3L * 24 * 3600 * 1000);
        prefs.edit().putBoolean("top", true).putBoolean("bottom", true).putString("launches", log).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        assertEquals(View.VISIBLE, a.findViewById(R.id.top).getVisibility());
        type(a, "k");
        assertEquals(View.GONE, a.findViewById(R.id.top).getVisibility());
        type(a, "");
        assertEquals(View.VISIBLE, a.findViewById(R.id.top).getVisibility());
    }

    @Test
    public void bothSettingsTogether() throws Exception {
        prefs.edit().putBoolean("type", true).putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        assertTrue(keyboardShown());
        type(a, "s");
        // names starting with "s" sit closest to the thumb, the word match "Play Store" above them
        assertEquals(Arrays.asList("Play Store", "Spotify", "Slack", "Signal"), shown(a));
        shot(a, "6-both");
    }

    @Test
    public void newAppInstalledDuringSearchKeepsSearchApplied() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        installApps("Kaufland");
        LauncherApps la = context.getSystemService(LauncherApps.class);
        shadowOf(la).notifyPackageAdded("com.example.kaufland");
        idle();
        List<String> s = shown(a);
        assertTrue(s.contains("Kaufland"));
        assertEquals(8, s.size());
        assertTrue(((ListView) a.findViewById(R.id.list)).isStackFromBottom());
    }
}
