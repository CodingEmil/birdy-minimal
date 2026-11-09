package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import android.widget.ScrollView;

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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

// Drives the real MainActivity: swipes, typing and lifecycle, with fake installed apps.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring and drawing, so sizes match a phone
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

    // Writes a PNG when run with -Dshots.dir=...; otherwise does nothing.
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

    // --- Groups on the home screen ---

    private static String key(String label) {
        String pkg = "com.example." + label.toLowerCase().replaceAll("[^a-z0-9]", "");
        return pkg + "/" + pkg + ".Main";
    }

    private static List<String> homeRows(MainActivity a) {
        android.widget.LinearLayout fav = a.findViewById(R.id.favorites);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < fav.getChildCount(); i++) out.add(((android.widget.TextView) fav.getChildAt(i)).getText().toString());
        return out;
    }

    private static View homeRow(MainActivity a, String text) {
        android.widget.LinearLayout fav = a.findViewById(R.id.favorites);
        for (int i = 0; i < fav.getChildCount(); i++) {
            if (((android.widget.TextView) fav.getChildAt(i)).getText().toString().equals(text)) return fav.getChildAt(i);
        }
        throw new AssertionError("no home row " + text + " in " + homeRows(a));
    }

    // Long-press an app in the open list, as the finger would, and leave its menu open.
    private static void longPressInList(MainActivity a, String label) {
        ListView list = a.findViewById(R.id.list);
        int pos = -1;
        for (int i = 0; i < list.getCount(); i++) {
            Object item = list.getItemAtPosition(i);
            if (item != null && item.toString().equals(label)) pos = i;
        }
        if (pos < 0) throw new AssertionError("not in list: " + label);
        list.setSelection(pos);
        idle();
        View row = list.getChildAt(pos - list.getFirstVisiblePosition());
        list.getOnItemLongClickListener().onItemLongClick(list, row, pos, 0);
    }

    private static List<String> menuTitles() {
        android.view.Menu m = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) out.add(m.getItem(i).getTitle().toString());
        return out;
    }

    private static void pick(String title) {
        android.widget.PopupMenu popup = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        android.view.Menu m = popup.getMenu();
        for (int i = 0; i < m.size(); i++) {
            if (m.getItem(i).getTitle().toString().equals(title)) {
                shadowOf(popup).getOnMenuItemClickListener().onMenuItemClick(m.getItem(i));
                idle();
                return;
            }
        }
        throw new AssertionError("no menu item " + title + " in " + menuTitles());
    }

    private static android.app.AlertDialog dialog() {
        return (android.app.AlertDialog) org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }

    private static List<String> dialogItems() {
        android.widget.ListView l = dialog().getListView();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.getAdapter().getCount(); i++) out.add(l.getAdapter().getItem(i).toString());
        return out;
    }

    private static void enterName(String name) {
        EditText field = dialog().findViewById(R.id.group_name);
        field.setText(name);
        dialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
    }

    private void seedGroup(String name, String... members) {
        StringBuilder line = new StringBuilder("1\t" + name);
        for (String m : members) line.append('\t').append(key(m));
        prefs.edit().putString("groups", line.toString()).putString("favorites", "group:1").commit();
    }

    private String savedGroups() { return prefs.getString("groups", ""); }

    @Test
    public void createGroupFromAPinnedAppThenAddFromTheList() throws Exception {
        prefs.edit().putString("favorites", key("Spotify")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Spotify").performLongClick();
        assertTrue(menuTitles().contains("Zu Gruppe hinzufügen…"));
        pick("Zu Gruppe hinzufügen…");
        assertEquals(Arrays.asList("Neue Gruppe…"), dialogItems());
        shadowOf(dialog()).clickOnItem(0);
        idle();
        enterName("Musik");
        assertEquals("the app is copied, so it stays pinned too", Arrays.asList("Spotify", "Musik  1"), homeRows(a));
        assertEquals("1\tMusik\t" + key("Spotify"), savedGroups());

        // second app, from the full list
        swipeUp(a);
        longPressInList(a, "Podcasts");
        pick("Zu Gruppe hinzufügen…");
        assertEquals(Arrays.asList("Musik", "Neue Gruppe…"), dialogItems());
        shadowOf(dialog()).clickOnItem(0);
        idle();
        assertEquals("Zu „Musik“ hinzugefügt", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        a.onBackPressed();
        idle();
        assertEquals(Arrays.asList("Spotify", "Musik  2"), homeRows(a));
        assertTrue("still in the list", shown(a).contains("Podcasts"));
    }

    @Test
    public void theStatusBarIsHiddenUnlessSwitchedOff() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        assertTrue("hidden by default", a.statusBarHidden);
        assertFalse(statusBarVisible(a));
        prefs.edit().putBoolean(MainActivity.HIDE_STATUS_BAR, false).commit();
        idle();
        assertFalse("switched off in the settings: shows at once", a.statusBarHidden);
        assertTrue(statusBarVisible(a));
        a.onWindowFocusChanged(true);
        assertTrue("focus does not hide it again", statusBarVisible(a));
        prefs.edit().putBoolean(MainActivity.HIDE_STATUS_BAR, true).commit();
        idle();
        assertFalse(statusBarVisible(a));
    }

    private static boolean statusBarVisible(MainActivity a) {
        android.view.WindowInsets insets = a.getWindow().getDecorView().getRootWindowInsets();
        return insets == null || insets.isVisible(android.view.WindowInsets.Type.statusBars());
    }

    @Test
    public void namesCanBeShownInSmallLettersOnly() throws Exception {
        prefs.edit().putString("groups", "1\tMusik\t" + key("Spotify"))
                .putString("favorites", "group:1\n" + key("WhatsApp")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        assertEquals("as the apps call themselves by default", Arrays.asList("Musik  1", "WhatsApp"), homeRows(a));
        prefs.edit().putBoolean(MainActivity.LOWERCASE, true).commit();
        idle();
        assertEquals("switched on in the settings: home follows at once", Arrays.asList("musik  1", "whatsapp"), homeRows(a));
        assertTrue(shown(a).contains("google maps"));
        assertFalse(shown(a).contains("Google Maps"));
        type(a, "GOO");
        assertEquals("search still finds them", "google maps", shown(a).get(0));
        assertEquals("the group keeps its name as typed", "Musik", Groups.parse(prefs.getString("groups", "")).get("1").name);
        prefs.edit().putBoolean(MainActivity.LOWERCASE, false).commit();
        idle();
        assertTrue("off again: capitals are back", homeRows(a).contains("WhatsApp"));
    }

    @Test
    public void groupListsOnlyGroupsTheAppIsNotInYet() throws Exception {
        prefs.edit().putString("groups", "1\tMusik\t" + key("Spotify") + "\n2\tSocial")
                .putString("favorites", "group:1\ngroup:2\n" + key("Spotify")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Spotify").performLongClick();
        pick("Zu Gruppe hinzufügen…");
        assertEquals(Arrays.asList("Social", "Neue Gruppe…"), dialogItems());
    }

    @Test
    public void tapFoldsTheGroupOpenAndClosed() throws Exception {
        prefs.edit().putBoolean("top", true).commit();
        seedGroup("Musik", "Spotify", "Podcasts");
        installApps(LABELS);
        MainActivity a = start().get();
        assertEquals(Arrays.asList("Musik  2"), homeRows(a));
        homeRow(a, "Musik  2").performClick();
        assertEquals("members alphabetical, under the header", Arrays.asList("Musik  2", "Podcasts", "Spotify"), homeRows(a));
        shot(a, "9-group-open");
        homeRow(a, "Musik  2").performClick();
        assertEquals(Arrays.asList("Musik  2"), homeRows(a));
        homeRow(a, "Musik  2").performClick();
        homeRow(a, "Spotify").performClick();
        idle();
        assertTrue("tapping a member opens it", prefs.getString("launches", "").contains(key("Spotify")));
    }

    @Test
    public void backFoldsTheGroupAndHomeButtonToo() throws Exception {
        seedGroup("Musik", "Spotify");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  1").performClick();
        a.onBackPressed();
        idle();
        assertEquals(Arrays.asList("Musik  1"), homeRows(a));
        homeRow(a, "Musik  1").performClick();
        a.onNewIntent(new android.content.Intent());
        idle();
        assertEquals(Arrays.asList("Musik  1"), homeRows(a));
    }

    @Test
    public void leavingHomeFoldsTheGroup() throws Exception {
        seedGroup("Musik", "Spotify");
        installApps(LABELS);
        ActivityController<MainActivity> c = start();
        MainActivity a = c.get();
        homeRow(a, "Musik  1").performClick();
        c.pause().stop();
        c.restart().start().resume();
        idle();
        assertEquals(Arrays.asList("Musik  1"), homeRows(a));
        homeRow(a, "Musik  1").performClick();
        swipeUp(a);
        a.onBackPressed();
        idle();
        assertEquals("folded while the list was open", Arrays.asList("Musik  1"), homeRows(a));
    }

    @Test
    public void removeAnAppFromTheGroupKeepsTheApp() throws Exception {
        seedGroup("Musik", "Spotify", "Podcasts");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  2").performClick();
        homeRow(a, "Spotify").performLongClick();
        assertEquals("Aus „Musik“ entfernen", menuTitles().get(0));
        pick("Aus „Musik“ entfernen");
        assertEquals(Arrays.asList("Musik  1", "Podcasts"), homeRows(a));
        assertEquals("1\tMusik\t" + key("Podcasts"), savedGroups());
        swipeUp(a);
        assertTrue(shown(a).contains("Spotify"));
    }

    @Test
    public void checklistAddsAndRemovesManyAppsAtOnce() throws Exception {
        seedGroup("Reisen", "Bahn");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Reisen  1").performLongClick();
        assertEquals(Arrays.asList("Apps auswählen…", "Umbenennen", "Gruppe löschen"), menuTitles());
        pick("Apps auswählen…");
        List<String> items = dialogItems();
        assertEquals("all apps, alphabetical", "Bahn", items.get(0));
        android.widget.ListView l = dialog().getListView();
        assertTrue("current members are ticked", l.isItemChecked(items.indexOf("Bahn")));
        shadowOf(dialog()).clickOnItem(items.indexOf("Bahn"));       // untick
        shadowOf(dialog()).clickOnItem(items.indexOf("Google Maps")); // tick
        shadowOf(dialog()).clickOnItem(items.indexOf("Wetter"));
        dialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("opens to show the result", Arrays.asList("Reisen  2", "Google Maps", "Wetter"), homeRows(a));
    }

    @Test
    public void renameAndDelete() throws Exception {
        seedGroup("Musik", "Spotify");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  1").performLongClick();
        pick("Umbenennen");
        assertEquals("Musik", ((EditText) dialog().findViewById(R.id.group_name)).getText().toString());
        enterName("  Hören\t ");
        assertEquals(Arrays.asList("Hören  1"), homeRows(a));
        homeRow(a, "Hören  1").performLongClick();
        pick("Gruppe löschen");
        dialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue(homeRows(a).isEmpty());
        assertEquals(View.VISIBLE, a.findViewById(R.id.hint).getVisibility());
        assertEquals("", savedGroups());
        assertEquals("", prefs.getString("favorites", ""));
    }

    @Test
    public void renameAnAppAndBringItsNameBack() throws Exception {
        prefs.edit().putString("favorites", key("Spotify")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Spotify").performLongClick();
        pick("Umbenennen…");
        assertEquals("Spotify", ((EditText) dialog().findViewById(R.id.group_name)).getText().toString());
        enterName(" Musik ");
        assertEquals(Arrays.asList("Musik"), homeRows(a));
        assertEquals(key("Spotify") + "\tMusik", prefs.getString(AppNames.KEY, ""));
        homeRow(a, "Musik").performLongClick();
        pick("Umbenennen…");
        enterName("");
        assertEquals(Arrays.asList("Spotify"), homeRows(a));
        assertEquals("", prefs.getString(AppNames.KEY, ""));
    }

    @Test
    public void emptyGroupExplainsWhatToDo() throws Exception {
        seedGroup("Neu");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Neu  0").performClick();
        assertEquals(2, homeRows(a).size());
        assertTrue(homeRows(a).get(1).startsWith("Leer."));
    }

    @Test
    public void groupsCountTowardsTheSevenHomeSlots() throws Exception {
        StringBuilder fav = new StringBuilder("group:1");
        for (String l : Arrays.asList("Bahn", "Banking", "Chrome", "Dateien", "Duolingo", "Fotos")) fav.append('\n').append(key(l));
        prefs.edit().putString("groups", "1\tMusik").putString("favorites", fav.toString()).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Fotos").performLongClick();
        pick("Zu Gruppe hinzufügen…");
        shadowOf(dialog()).clickOnItem(dialogItems().indexOf("Neue Gruppe…"));
        idle();
        assertEquals("Der Startscreen ist voll. Entferne zuerst eine App oder Gruppe.",
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals("1\tMusik", savedGroups());
        swipeUp(a);
        longPressInList(a, "Spotify");
        android.view.Menu m = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu();
        assertFalse("no free slot for another pin", m.findItem(1).isEnabled());
    }

    @Test
    public void bigOpenGroupScrollsAndKeepsSwipesWorking() throws Exception {
        String[] many = new String[30];
        for (int i = 0; i < many.length; i++) many[i] = String.format("App %02d", i);
        seedGroup("Alles", many);
        installApps(many);
        MainActivity a = start().get();
        ScrollView scroll = a.findViewById(R.id.favorites_scroll);
        homeRow(a, "Alles  30").performClick();
        idle();
        assertTrue("too long for the space, so it scrolls", scroll.canScrollVertically(1));
        int[] at = new int[2];
        scroll.getLocationInWindow(at);
        float y = at[1] + scroll.getHeight() * 0.8f;
        swipe(a, y, y - 400);
        assertEquals("a swipe up inside the group scrolls it instead of opening the list",
                View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
        assertEquals("no notifications either", 0, CountingStatusBar.expanded);
        scroll.scrollTo(0, 100000);
        idle();
        swipe(a, y, y - 900);
        assertEquals("at the end of the group, swiping up opens the list again",
                View.VISIBLE, a.findViewById(R.id.drawer).getVisibility());
    }

    @Test
    public void groupSitsBetweenPinnedAppsAndUnfoldsInPlace() throws Exception {
        prefs.edit().putString("groups", "1\tMusik\t" + key("Spotify") + "\t" + key("Podcasts") + "\t" + key("YouTube"))
                .putString("favorites", key("Kamera") + "\ngroup:1\n" + key("WhatsApp") + "\n" + key("Google Maps")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        assertEquals(Arrays.asList("Kamera", "Musik  3", "WhatsApp", "Google Maps"), homeRows(a));
        shot(a, "10-home-group-closed");
        homeRow(a, "Musik  3").performClick();
        idle();
        assertEquals(Arrays.asList("Kamera", "Musik  3", "Podcasts", "Spotify", "YouTube", "WhatsApp", "Google Maps"), homeRows(a));
        shot(a, "11-home-group-open");
    }

    // --- Time limits from the launcher ---

    @org.junit.After
    public void resetClock() { Usage.clock = java.time.Clock.systemDefaultZone(); }

    @Test
    public void longPressOffersTimeLimitForThatApp() throws Exception {
        prefs.edit().putString("favorites", key("YouTube")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "YouTube").performLongClick();
        pick("Zeitlimit…");
        android.content.Intent edit = shadowOf(a).getNextStartedActivity();
        assertEquals(LimitActivity.class.getName(), edit.getComponent().getClassName());
        assertEquals("com.example.youtube", edit.getStringExtra(LimitActivity.PKG));
        assertEquals("YouTube", edit.getStringExtra(LimitActivity.LABEL));
    }

    @Test
    public void tappingABlockedAppInBirdyShowsTheBlockScreenInstead() throws Exception {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        Usage.clock = java.time.Clock.fixed(java.time.LocalDateTime.of(2026, 11, 4, 22, 0).atZone(zone).toInstant(), zone);
        prefs.edit().putBoolean("top", true).putString("favorites", key("YouTube") + "\n" + key("Wetter"))
                .putString("limits", "com.example.youtube\t0\t\t1260-420").commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "YouTube").performClick();
        idle();
        android.content.Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(BlockActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("Schonzeit abends", next.getStringExtra(BlockActivity.TITLE));
        assertEquals("not launched", "", prefs.getString("launches", ""));
        homeRow(a, "Wetter").performClick();
        idle();
        assertTrue("apps without limits open as usual", prefs.getString("launches", "").contains(key("Wetter")));
    }

    @Test
    public void tappingAnAppThatAsksFirstShowsTheQuestion() throws Exception {
        prefs.edit().putBoolean("top", true).putString("favorites", key("YouTube"))
                .putString("limits", "com.example.youtube\t0\t\t\t1\t0").commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "YouTube").performClick();
        idle();
        android.content.Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(SessionActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("", prefs.getString("launches", ""));
    }

    // --- Arranging the home screen ---

    private static android.widget.LinearLayout arrangeRow(MainActivity a, int index) {
        return (android.widget.LinearLayout) ((android.widget.LinearLayout) a.findViewById(R.id.favorites)).getChildAt(index);
    }

    private static List<String> arrangeLabels(MainActivity a) {
        android.widget.LinearLayout fav = a.findViewById(R.id.favorites);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < fav.getChildCount(); i++) {
            View child = fav.getChildAt(i);
            if (child instanceof android.widget.LinearLayout) {
                out.add(((android.widget.TextView) ((android.widget.LinearLayout) child).getChildAt(0)).getText().toString());
            }
        }
        return out;
    }

    private static void tapArrow(MainActivity a, int row, boolean up) {
        arrangeRow(a, row).getChildAt(up ? 1 : 2).performClick();
        idle();
    }

    private void seedHome() {
        prefs.edit().putString("groups", "1\tMusik\t" + key("Spotify"))
                .putString("favorites", key("Kamera") + "\ngroup:1\n" + key("WhatsApp") + "\n" + key("Google Maps")).commit();
    }

    @Test
    public void arrangeMovesAppsAndGroupsWithArrows() throws Exception {
        seedHome();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Kamera").performLongClick();
        pick("Startscreen anordnen");
        assertEquals(Arrays.asList("Kamera", "Musik  1", "WhatsApp", "Google Maps"), arrangeLabels(a));
        assertFalse("first entry cannot go up", arrangeRow(a, 0).getChildAt(1).isEnabled());
        assertFalse("last entry cannot go down", arrangeRow(a, 3).getChildAt(2).isEnabled());
        tapArrow(a, 0, false); // Kamera down
        assertEquals(Arrays.asList("Musik  1", "Kamera", "WhatsApp", "Google Maps"), arrangeLabels(a));
        tapArrow(a, 3, true); // Google Maps up
        tapArrow(a, 2, true); // and up again
        assertEquals(Arrays.asList("Musik  1", "Google Maps", "Kamera", "WhatsApp"), arrangeLabels(a));
        shot(a, "16-arrange");
        assertEquals("saved right away", "group:1\n" + key("Google Maps") + "\n" + key("Kamera") + "\n" + key("WhatsApp"),
                prefs.getString("favorites", ""));
        a.findViewById(R.id.arrange_done).performClick();
        idle();
        assertEquals(Arrays.asList("Musik  1", "Google Maps", "Kamera", "WhatsApp"), homeRows(a));
    }

    @Test
    public void groupMenuOffersArrangingAndBackEndsIt() throws Exception {
        seedHome();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  1").performLongClick();
        assertTrue(menuTitles().contains("Startscreen anordnen"));
        pick("Startscreen anordnen");
        assertEquals(4, arrangeLabels(a).size());
        a.onBackPressed();
        idle();
        assertEquals(Arrays.asList("Kamera", "Musik  1", "WhatsApp", "Google Maps"), homeRows(a));
        assertEquals(View.INVISIBLE, a.findViewById(R.id.drawer).getVisibility());
    }

    @Test
    public void openingTheListOrLeavingEndsArranging() throws Exception {
        seedHome();
        installApps(LABELS);
        ActivityController<MainActivity> c = start();
        MainActivity a = c.get();
        homeRow(a, "Kamera").performLongClick();
        pick("Startscreen anordnen");
        swipeUp(a);
        a.onBackPressed();
        idle();
        assertEquals(Arrays.asList("Kamera", "Musik  1", "WhatsApp", "Google Maps"), homeRows(a));
        homeRow(a, "Kamera").performLongClick();
        pick("Startscreen anordnen");
        c.pause().stop();
        c.restart().start().resume();
        idle();
        assertEquals(4, homeRows(a).size());
    }

    @Test
    public void uninstalledPinsKeepTheirPlaceWhenMoving() throws Exception {
        prefs.edit().putString("favorites", key("Kamera") + "\n" + key("Gone App") + "\n" + key("WhatsApp")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "WhatsApp").performLongClick();
        pick("Startscreen anordnen");
        assertEquals(Arrays.asList("Kamera", "WhatsApp"), arrangeLabels(a));
        tapArrow(a, 1, true);
        assertEquals(key("WhatsApp") + "\n" + key("Gone App") + "\n" + key("Kamera"), prefs.getString("favorites", ""));
    }

    @Test
    public void appsInsideAGroupCanBeArrangedAndKeepTheirOrder() throws Exception {
        seedGroup("Musik", "Spotify", "Podcasts", "YouTube");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  3").performLongClick();
        pick("Apps anordnen");
        assertEquals("alphabetical until arranged", Arrays.asList("Podcasts", "Spotify", "YouTube"), arrangeLabels(a));
        assertEquals("the group header stays on top", "Musik  3",
                ((android.widget.TextView) ((android.widget.LinearLayout) a.findViewById(R.id.favorites)).getChildAt(0)).getText().toString());
        tapArrow(a, 3, true); // YouTube up (row 0 is the header)
        tapArrow(a, 2, true); // and up again
        assertEquals(Arrays.asList("YouTube", "Podcasts", "Spotify"), arrangeLabels(a));
        assertEquals("1\tMusik\t" + Groups.MANUAL + "\t" + key("YouTube") + "\t" + key("Podcasts") + "\t" + key("Spotify"),
                savedGroups());
        a.findViewById(R.id.arrange_done).performClick();
        idle();
        assertEquals("the group stays open in the new order",
                Arrays.asList("Musik  3", "YouTube", "Podcasts", "Spotify"), homeRows(a));

        homeRow(a, "Musik  3").performLongClick();
        pick("Apps auswählen…");
        shadowOf(dialog()).clickOnItem(dialogItems().indexOf("Bahn"));
        dialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("a new app goes to the end, the others keep their place",
                Arrays.asList("Musik  4", "YouTube", "Podcasts", "Spotify", "Bahn"), homeRows(a));
    }

    @Test
    public void appInAGroupOffersArrangingTheGroup() throws Exception {
        seedGroup("Musik", "Spotify", "Podcasts");
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Musik  2").performClick();
        idle();
        homeRow(a, "Spotify").performLongClick();
        pick("Gruppe anordnen");
        assertEquals(Arrays.asList("Podcasts", "Spotify"), arrangeLabels(a));
        a.onBackPressed();
        idle();
        assertEquals(Arrays.asList("Musik  2", "Podcasts", "Spotify"), homeRows(a));
    }

    @Test
    public void nothingToArrangeWithOneEntry() throws Exception {
        prefs.edit().putString("favorites", key("Kamera")).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        homeRow(a, "Kamera").performLongClick();
        assertFalse(menuTitles().contains("Startscreen anordnen"));
    }

    // --- Searching the app picker of the time limits page ---

    @Test
    public void appPickerForLimitsCanBeSearched() throws Exception {
        installApps(LABELS);
        LimitsActivity page = Robolectric.buildActivity(LimitsActivity.class).setup().get();
        page.findViewById(R.id.limits_add).performClick();
        android.app.AlertDialog d = dialog();
        EditText search = d.findViewById(R.id.app_search);
        ListView list = d.findViewById(R.id.app_list);
        assertEquals(LABELS.length, list.getAdapter().getCount());
        search.setText("s");
        idle();
        List<String> shownApps = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) shownApps.add(list.getAdapter().getItem(i).toString());
        assertEquals("names starting with the query first", Arrays.asList("Signal", "Slack", "Spotify", "Play Store"), shownApps);
        search.setText("sp");
        idle();
        search.onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
        android.content.Intent edit = shadowOf(page).getNextStartedActivity();
        assertEquals(LimitActivity.class.getName(), edit.getComponent().getClassName());
        assertEquals("com.example.spotify", edit.getStringExtra(LimitActivity.PKG));
        assertEquals("Spotify", edit.getStringExtra(LimitActivity.LABEL));
    }

    @Test
    public void tappingAnAppInThePickerOpensItsLimits() throws Exception {
        installApps(LABELS);
        LimitsActivity page = Robolectric.buildActivity(LimitsActivity.class).setup().get();
        page.findViewById(R.id.limits_add).performClick();
        android.app.AlertDialog d = dialog();
        ((EditText) d.findViewById(R.id.app_search)).setText("wett");
        idle();
        ListView list = d.findViewById(R.id.app_list);
        assertEquals(1, list.getAdapter().getCount());
        list.performItemClick(list.getAdapter().getView(0, null, list), 0, 0);
        idle();
        assertEquals("com.example.wetter", shadowOf(page).getNextStartedActivity().getStringExtra(LimitActivity.PKG));
    }

    // --- Contacts and web search in the drawer ---

    private static final List<ContactSearch.Contact> PEOPLE = Arrays.asList(
            new ContactSearch.Contact("Anna Schmidt", android.net.Uri.parse("content://com.android.contacts/contacts/lookup/a/1")),
            new ContactSearch.Contact("Mama", android.net.Uri.parse("content://com.android.contacts/contacts/lookup/m/2")),
            new ContactSearch.Contact("Max Mustermann", android.net.Uri.parse("content://com.android.contacts/contacts/lookup/x/3")),
            new ContactSearch.Contact("Lisa Maier", android.net.Uri.parse("content://com.android.contacts/contacts/lookup/l/4")));

    private void contactsOn(boolean granted) {
        ContactSearch.source = (context, query) -> PEOPLE; // ranking and limits happen in ContactSearch
        prefs.edit().putBoolean("contacts", true).commit();
        if (granted) shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS);
    }

    @org.junit.After
    public void resetContacts() { ContactSearch.source = ContactSearch.PROVIDER; }

    private static List<String> suggestions(MainActivity a, int id) {
        android.widget.LinearLayout box = a.findViewById(id);
        List<String> out = new ArrayList<>();
        if (box.getVisibility() != View.VISIBLE) return out;
        for (int i = 0; i < box.getChildCount(); i++) out.add(((android.widget.TextView) box.getChildAt(i)).getText().toString());
        return out;
    }

    private static int suggestionColor(MainActivity a, int id, int index) {
        return ((android.widget.TextView) ((android.widget.LinearLayout) a.findViewById(id)).getChildAt(index)).getCurrentTextColor();
    }

    private static void enter(MainActivity a) {
        ((EditText) a.findViewById(R.id.search)).onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
    }

    @Test
    public void contactsShowBelowTopAppsAndEnterPrefersTheApp() throws Exception {
        contactsOn(true);
        prefs.edit().putBoolean("top", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "ma");
        assertEquals(Arrays.asList("Google Maps", "K-9 Mail"), shown(a));
        assertEquals("apps on top, so contacts sit above the search field, closest one lowest",
                Arrays.asList("Lisa Maier  Kontakt", "Max Mustermann  Kontakt", "Mama  Kontakt"), suggestions(a, R.id.suggestions_bottom));
        assertTrue(suggestions(a, R.id.suggestions_top).isEmpty());
        assertEquals("an app matches, so every contact is grey", 0xFF808080, suggestionColor(a, R.id.suggestions_bottom, 2));
        shot(a, "17-contacts");
        enter(a);
        assertTrue(prefs.getString("launches", "").contains(key("Google Maps")));
    }

    @Test
    public void withResultsAtTheBottomContactsGoToTheTop() throws Exception {
        contactsOn(true);
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "ma");
        assertEquals(Arrays.asList("Mama  Kontakt", "Max Mustermann  Kontakt", "Lisa Maier  Kontakt"), suggestions(a, R.id.suggestions_top));
        assertTrue(suggestions(a, R.id.suggestions_bottom).isEmpty());
    }

    @Test
    public void withoutAMatchingAppEnterOpensTheFirstContact() throws Exception {
        contactsOn(true);
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "anna");
        assertTrue(shown(a).isEmpty());
        assertEquals(Arrays.asList("Anna Schmidt  Kontakt"), suggestions(a, R.id.suggestions_bottom));
        assertEquals("the Enter target is white", android.graphics.Color.WHITE, suggestionColor(a, R.id.suggestions_bottom, 0));
        enter(a);
        android.content.Intent open = shadowOf(a).getNextStartedActivity();
        assertEquals(android.content.Intent.ACTION_VIEW, open.getAction());
        assertEquals(PEOPLE.get(0).uri, open.getData());
    }

    @Test
    public void tappingAContactOpensIt() throws Exception {
        contactsOn(true);
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "max");
        ((android.widget.LinearLayout) a.findViewById(R.id.suggestions_bottom)).getChildAt(0).performClick();
        assertEquals(PEOPLE.get(2).uri, shadowOf(a).getNextStartedActivity().getData());
    }

    @Test
    public void contactsStayHiddenWhenOffOrNotAllowed() throws Exception {
        contactsOn(false); // switched on, but Android has not granted access
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "max");
        assertTrue(suggestions(a, R.id.suggestions_bottom).stream().noneMatch(t -> t.contains("Kontakt")));
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS);
        prefs.edit().putBoolean("contacts", false).commit();
        type(a, "maxi");
        type(a, "max");
        assertTrue(suggestions(a, R.id.suggestions_bottom).stream().noneMatch(t -> t.contains("Kontakt")));
    }

    @Test
    public void nothingMatchesSoEnterSearchesTheWebInChrome() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "wetter morgen berlin");
        assertTrue(shown(a).isEmpty());
        assertEquals(Arrays.asList("„wetter morgen berlin“  im Web suchen"), suggestions(a, R.id.suggestions_bottom));
        shot(a, "18-web");
        enter(a);
        android.content.Intent web = shadowOf(a).getNextStartedActivity();
        assertEquals(android.content.Intent.ACTION_VIEW, web.getAction());
        assertEquals("com.android.chrome", web.getPackage());
        assertEquals("https://www.google.com/search?q=wetter%20morgen%20berlin", web.getDataString());
    }

    @Test
    public void webSearchOnlyWhenNothingElseMatchesAndWhenOn() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "wet");
        assertTrue("an app matches", suggestions(a, R.id.suggestions_bottom).isEmpty());
        prefs.edit().putBoolean("web", false).commit();
        idle();
        type(a, "xyzq");
        assertTrue(suggestions(a, R.id.suggestions_bottom).isEmpty());
        enter(a);
        assertNull(shadowOf(a).getNextStartedActivity());
        type(a, "");
        assertEquals(View.GONE, a.findViewById(R.id.suggestions_bottom).getVisibility());
    }

    @Test
    public void contactsSettingAsksForPermissionFirst() throws Exception {
        ListSettingsActivity setup = Robolectric.buildActivity(ListSettingsActivity.class).setup().get();
        android.widget.Switch sw = setup.findViewById(R.id.contacts);
        assertFalse(sw.isChecked());
        sw.setChecked(true);
        org.robolectric.shadows.ShadowActivity.PermissionsRequest req = shadowOf(setup).getLastRequestedPermission();
        assertEquals(android.Manifest.permission.READ_CONTACTS, req.requestedPermissions[0]);
        assertFalse("not on until Android grants it", prefs.getBoolean("contacts", false));
        setup.onRequestPermissionsResult(req.requestCode, req.requestedPermissions, new int[] {android.content.pm.PackageManager.PERMISSION_DENIED});
        assertFalse(sw.isChecked());
        assertFalse(prefs.getBoolean("contacts", false));
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS);
        setup.onRequestPermissionsResult(req.requestCode, req.requestedPermissions, new int[] {android.content.pm.PackageManager.PERMISSION_GRANTED});
        assertTrue(sw.isChecked());
        assertTrue(prefs.getBoolean("contacts", false));
    }

    @Test
    public void groupsSurviveARestartAndSkipUninstalledApps() throws Exception {
        seedGroup("Musik", "Spotify", "Gone App");
        installApps(LABELS);
        start();
        MainActivity again = start().get(); // a fresh activity reads the saved state
        assertEquals(Arrays.asList("Musik  1"), homeRows(again));
        assertTrue("the uninstalled app stays saved for a reinstall", savedGroups().contains(key("Gone App")));
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

    // The row Enter would open: the only white one among grey ones. Null when no row is grey (not searching).
    private static String highlighted(MainActivity a) {
        ListView list = a.findViewById(R.id.list);
        List<String> white = new ArrayList<>();
        int grey = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            if (!(list.getChildAt(i) instanceof android.widget.TextView)) continue;
            android.widget.TextView row = (android.widget.TextView) list.getChildAt(i);
            if (row.getText().length() == 0) continue;
            if (row.getCurrentTextColor() == android.graphics.Color.WHITE) white.add(row.getText().toString());
            else grey++;
        }
        if (grey == 0 && white.size() > 1) return null;
        if (white.size() > 1) throw new AssertionError("more than one white row: " + white);
        return white.isEmpty() ? null : white.get(0);
    }

    @Test
    public void topMatchIsHighlightedAtTheTop() throws Exception {
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        assertEquals("nothing highlighted without a search", null, highlighted(a));
        type(a, "s");
        assertEquals("Signal", highlighted(a));
        shot(a, "7-highlight-top");
        type(a, "sp");
        assertEquals("Spotify", highlighted(a));
        type(a, "");
        assertEquals(null, highlighted(a));
    }

    @Test
    public void topMatchIsHighlightedAtTheBottom() throws Exception {
        prefs.edit().putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "k");
        assertEquals("K-9 Mail", highlighted(a));
        ListView list = a.findViewById(R.id.list);
        assertEquals("the highlighted row is the one right above the search field",
                "K-9 Mail", ((android.widget.TextView) list.getChildAt(list.getChildCount() - 1)).getText().toString());
        android.widget.TextView other = (android.widget.TextView) list.getChildAt(list.getChildCount() - 2);
        assertEquals("the other matches are grey", 0xFF808080, other.getCurrentTextColor());
        shot(a, "8-highlight-bottom");
    }

    @Test
    public void enterOpensTheHighlightedApp() throws Exception {
        prefs.edit().putBoolean("top", true).putBoolean("bottom", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "wh");
        assertEquals("WhatsApp", highlighted(a));
        ((EditText) a.findViewById(R.id.search)).onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
        assertTrue(prefs.getString("launches", "").contains("com.example.whatsapp/"));
    }

    @Test
    public void enterWithNoMatchDoesNothing() throws Exception {
        prefs.edit().putBoolean("top", true).commit();
        installApps(LABELS);
        MainActivity a = start().get();
        swipeUp(a);
        type(a, "xyz");
        assertEquals(null, highlighted(a));
        ((EditText) a.findViewById(R.id.search)).onEditorAction(EditorInfo.IME_ACTION_GO);
        idle();
        assertEquals("", prefs.getString("launches", ""));
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
