package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AppOpsManager;
import android.app.Application;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Switch;
import android.widget.TextView;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowUsageStatsManager;

// Strict mode end to end: the accessibility service sees apps come to the front and covers blocked ones.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 36, qualifiers = "w411dp-h891dp-xxhdpi")
public class TimeLimitTest {
    private static final String INSTA = "com.example.insta", TUBE = "com.example.tube", KEYBOARD = "com.example.keyboard";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private Application app;
    private SharedPreferences prefs;
    private BlockService service;
    private long startUptime;
    private Instant start;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        install(INSTA, "Insta", true);
        install(TUBE, "Tube", true);
        install(KEYBOARD, "Tastatur", false);
        shadowOf(app.getSystemService(AppOpsManager.class)) // Robolectric allows every op by default; a fresh install does not
                .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.getPackageName(), AppOpsManager.MODE_IGNORED);
    }

    @After
    public void tearDown() {
        Usage.clock = Clock.systemDefaultZone();
    }

    // The clock moves with Robolectric's looper, so timers in the service see time pass.
    private void now(int hour, int minute) {
        start = LocalDateTime.of(2026, 11, 4, hour, minute).atZone(ZONE).toInstant();
        startUptime = SystemClock.uptimeMillis();
        Usage.clock = new Clock() {
            @Override public ZoneId getZone() { return ZONE; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return start.plusMillis(SystemClock.uptimeMillis() - startUptime); }
        };
    }

    private long at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 4, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    private void install(String pkg, String label, boolean launchable) {
        PackageInfo info = new PackageInfo();
        info.packageName = pkg;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = pkg;
        info.applicationInfo.nonLocalizedLabel = label;
        shadowOf(app.getPackageManager()).installPackage(info);
        if (!launchable) return;
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = pkg;
        ri.activityInfo.name = pkg + ".Main";
        ri.activityInfo.applicationInfo = info.applicationInfo;
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri);
    }

    private void grantUsageAccess() {
        shadowOf(app.getSystemService(AppOpsManager.class))
                .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.getPackageName(), AppOpsManager.MODE_ALLOWED);
    }

    private void usage(String pkg, int type, long time) {
        shadowOf(app.getSystemService(UsageStatsManager.class)).addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg).setClass(pkg + ".Main").setTimeStamp(time).setEventType(type).build());
    }

    private void limits(String saved) { prefs.edit().putString("limits", saved).commit(); }

    private void front(String pkg) {
        if (service == null) service = Robolectric.setupService(BlockService.class);
        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);
        service.onAccessibilityEvent(e);
    }

    // Writes a PNG when run with -Dshots.dir=...; otherwise does nothing.
    private static void shot(android.app.Activity a, String name) throws Exception {
        if (System.getProperty("shots.dir") == null) return;
        android.view.View root = a.getWindow().getDecorView();
        for (int id : new int[] {R.id.block_content, R.id.session_content}) { // show the end of the fade-in
            android.view.View content = root.findViewById(id);
            if (content == null) continue;
            content.animate().cancel();
            content.setAlpha(1f);
            content.setTranslationY(0f);
        }
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        java.io.File dir = new java.io.File(System.getProperty("shots.dir"));
        dir.mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void idle(Duration d) { shadowOf(Looper.getMainLooper()).idleFor(d); }

    // The block screen the service opened last, or null.
    private Intent blocked() {
        Intent next = shadowOf(app).getNextStartedActivity();
        while (next != null && !BlockActivity.class.getName().equals(next.getComponent() == null ? null : next.getComponent().getClassName())) {
            next = shadowOf(app).getNextStartedActivity();
        }
        return next;
    }

    // Opening from Recents: Android may hand over only Recents' own window change, not the app's.
    private void frontWithoutItsOwnEvent(String pkg) {
        usage(pkg, Usage.RESUMED, Usage.now());
        front("com.android.systemui"); // not installed here, so not an app: Recents / the shade
    }

    @Test
    public void appFromRecentsDuringThePauseIsCoveredEvenWhenOnlyRecentsReported() {
        grantUsageAccess();
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t10");
        front(INSTA);
        choose(sessionScreen(started(SessionActivity.class)), "10 min");
        front(INSTA);
        idle(Duration.ofMinutes(11)); // session over: covered, pause until 14:20
        assertEquals("Pause", blocked().getStringExtra(BlockActivity.TITLE));
        front(app.getPackageName()); // "Zum Startscreen": Birdy in front
        idle(Duration.ofMinutes(1));
        shadowOf(app).clearNextStartedActivities();

        frontWithoutItsOwnEvent(INSTA);
        assertNull("waits for the app to settle", blocked());
        idle(Duration.ofMillis(BlockService.SETTLE + 50));
        Intent pause = blocked();
        assertNotNull("Recents does not get around the pause", pause);
        assertEquals("Pause", pause.getStringExtra(BlockActivity.TITLE));
    }

    @Test
    public void withoutUsageAccessOnlyTheAppsOwnEventCounts() {
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t10");
        usage(INSTA, Usage.RESUMED, Usage.now());
        front("com.android.systemui");
        idle(Duration.ofSeconds(1));
        assertNull(started(SessionActivity.class));
        front(INSTA);
        assertNotNull(started(SessionActivity.class));
    }

    private void front(String pkg, Class<?> activity) {
        if (service == null) service = Robolectric.setupService(BlockService.class);
        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);
        e.setClassName(activity.getName());
        service.onAccessibilityEvent(e);
    }

    private int blockScreensStarted() {
        int n = 0;
        Intent next;
        while ((next = shadowOf(app).getNextStartedActivity()) != null) {
            if (next.getComponent() != null && BlockActivity.class.getName().equals(next.getComponent().getClassName())) n++;
        }
        return n;
    }

    @Test
    public void aBurstOfWindowChangesOpensTheBlockScreenOnce() {
        now(22, 0);
        limits(INSTA + "\t0\t\t1260-420");
        front(INSTA); // splash
        front(INSTA); // main window
        front(app.getPackageName(), BlockActivity.class); // the block screen itself appears
        front(INSTA); // a late change of the app behind it
        assertEquals("one block screen, not one per window change", 1, blockScreensStarted());
    }

    @Test
    public void comingBackAfterLeavingTheBlockScreenCoversAgain() {
        now(22, 0);
        limits(INSTA + "\t0\t\t1260-420");
        front(INSTA);
        front(app.getPackageName(), BlockActivity.class);
        front(app.getPackageName(), MainActivity.class); // "Zum Startscreen"
        front(INSTA); // opened again right away
        assertEquals(2, blockScreensStarted());
        front("com.android.systemui"); // Recents
        front(INSTA);
        assertEquals("via Recents too", 1, blockScreensStarted());
    }

    @Test
    public void usedUpDailyLimitBlocksAsSoonAsTheAppComesToFront() {
        grantUsageAccess();
        now(12, 0);
        limits(INSTA + "\t30\t\t");
        usage(INSTA, Usage.RESUMED, at(9, 0));
        usage(INSTA, Usage.PAUSED, at(9, 31));
        front(INSTA);
        Intent block = blocked();
        assertNotNull("blocked however it was opened", block);
        assertEquals(INSTA, block.getStringExtra(BlockActivity.PKG));
        assertEquals("Tageslimit erreicht", block.getStringExtra(BlockActivity.TITLE));
        assertTrue((block.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void limitRunningOutWhileInsideTheAppBlocksMidway() {
        grantUsageAccess();
        now(12, 0);
        limits(INSTA + "\t30\t\t");
        usage(INSTA, Usage.RESUMED, at(9, 0));
        usage(INSTA, Usage.PAUSED, at(9, 20)); // 20 min used
        usage(INSTA, Usage.RESUMED, at(12, 0)); // open again now
        front(INSTA);
        assertNull("10 minutes left", blocked());
        idle(Duration.ofMinutes(9));
        assertNull(blocked());
        idle(Duration.ofMinutes(2));
        Intent block = blocked();
        assertNotNull("covered once the 30 minutes are used up", block);
        assertEquals("Tageslimit erreicht", block.getStringExtra(BlockActivity.TITLE));
    }

    @Test
    public void quietTimeBlocksEvenWithoutUsageAccess() {
        now(6, 30);
        limits(INSTA + "\t0\t300-540\t");
        front(INSTA);
        Intent block = blocked();
        assertEquals("Schonzeit morgens", block.getStringExtra(BlockActivity.TITLE));
        assertEquals("Gesperrt bis 09:00 Uhr.", block.getStringExtra(BlockActivity.DETAIL));
    }

    @Test
    public void quietTimeStartingWhileInsideTheAppBlocks() {
        now(20, 55);
        limits(INSTA + "\t0\t\t1260-420");
        front(INSTA);
        assertNull(blocked());
        idle(Duration.ofMinutes(6));
        assertEquals("Schonzeit abends", blocked().getStringExtra(BlockActivity.TITLE));
    }

    @Test
    public void keyboardOrShadeDoNotHideTheAppInFront() {
        now(20, 58);
        limits(INSTA + "\t0\t\t1260-420");
        front(INSTA);
        front(KEYBOARD); // typing inside Insta
        idle(Duration.ofMinutes(3));
        assertNotNull("Insta is still the app in front", blocked());
    }

    @Test
    public void otherAppsAndBirdyStayOpen() {
        now(6, 30);
        limits(INSTA + "\t0\t300-540\t");
        front(TUBE);
        front(app.getPackageName());
        idle(Duration.ofMinutes(5));
        assertNull(blocked());
    }

    @Test
    public void switchingAwayCancelsThePendingBlock() {
        now(20, 55);
        limits(INSTA + "\t0\t\t1260-420");
        front(INSTA);
        front(TUBE);
        idle(Duration.ofMinutes(10));
        assertNull("Insta is no longer in front", blocked());
    }

    @Test
    public void appWithinItsLimitsIsNotBlocked() {
        grantUsageAccess();
        now(12, 0);
        limits(INSTA + "\t60\t300-540\t1260-420");
        usage(INSTA, Usage.RESUMED, at(10, 0));
        usage(INSTA, Usage.PAUSED, at(10, 30));
        front(INSTA);
        assertNull(blocked());
    }

    @Test
    public void blockScreenNamesTheAppAndOnlyLeadsHome() throws Exception {
        Intent intent = new Intent(app, BlockActivity.class)
                .putExtra(BlockActivity.PKG, INSTA)
                .putExtra(BlockActivity.TITLE, "Schonzeit abends")
                .putExtra(BlockActivity.DETAIL, "Gesperrt bis 07:00 Uhr.");
        BlockActivity a = Robolectric.buildActivity(BlockActivity.class, intent).setup().get();
        assertEquals("Insta", ((TextView) a.findViewById(R.id.block_app)).getText().toString());
        assertEquals("Schonzeit abends", ((TextView) a.findViewById(R.id.block_title)).getText().toString());
        assertEquals("Gesperrt bis 07:00 Uhr.", ((TextView) a.findViewById(R.id.block_detail)).getText().toString());
        shot(a, "12-block");
        a.onBackPressed();
        Intent home = shadowOf(a).getNextStartedActivity();
        assertTrue(home.hasCategory(Intent.CATEGORY_HOME));
        assertTrue(a.isFinishing());
    }

    @Test
    public void limitScreenSavesQuietTimesAndDailyLimit() throws Exception {
        Intent intent = new Intent(app, LimitActivity.class).putExtra(LimitActivity.PKG, INSTA).putExtra(LimitActivity.LABEL, "Insta");
        LimitActivity a = Robolectric.buildActivity(LimitActivity.class, intent).setup().get();
        assertEquals("Insta", ((TextView) a.findViewById(R.id.limit_app)).getText().toString());
        assertEquals("Tageslimit: aus  →", ((TextView) a.findViewById(R.id.limit_daily)).getText().toString());
        ((Switch) a.findViewById(R.id.limit_morning)).setChecked(true);
        assertEquals("a first limit also asks before each visit", INSTA + "\t0\t300-540\t\t1\t0", prefs.getString("limits", ""));
        assertEquals("05:00 bis 09:00 Uhr  →", ((TextView) a.findViewById(R.id.limit_morning_time)).getText().toString());
        ((Switch) a.findViewById(R.id.limit_evening)).setChecked(true);
        assertEquals(INSTA + "\t0\t300-540\t1260-0\t1\t0", prefs.getString("limits", ""));
        a.findViewById(R.id.limit_daily).performClick();
        ((android.app.AlertDialog) ShadowAlertDialog.getLatestAlertDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle(); // dialog buttons report their click through the message queue
        assertEquals("1 hour is the suggested start", INSTA + "\t60\t300-540\t1260-0\t1\t0", prefs.getString("limits", ""));
        assertEquals("Tageslimit: 1 h  →", ((TextView) a.findViewById(R.id.limit_daily)).getText().toString());
        shadowOf(Looper.getMainLooper()).idle();
        shot(a, "13-limit");
        ((Switch) a.findViewById(R.id.limit_morning)).setChecked(false);
        assertEquals(INSTA + "\t60\t\t1260-0\t1\t0", prefs.getString("limits", ""));
        a.findViewById(R.id.limit_clear).performClick();
        assertEquals("", prefs.getString("limits", ""));
        assertTrue(a.isFinishing());
    }

    @Test
    public void limitScreenShowsWhatStrictModeStillNeeds() {
        Intent intent = new Intent(app, LimitActivity.class).putExtra(LimitActivity.PKG, INSTA).putExtra(LimitActivity.LABEL, "Insta");
        LimitActivity a = Robolectric.buildActivity(LimitActivity.class, intent).setup().get();
        assertEquals("Nutzungsdatenzugriff erlauben  →", ((TextView) a.findViewById(R.id.strict_usage)).getText().toString());
        assertEquals("Strenger Modus einschalten  →", ((TextView) a.findViewById(R.id.strict_service)).getText().toString());
        a.findViewById(R.id.strict_service).performClick();
        assertNull("first the disclosure, not the settings", shadowOf(a).getNextStartedActivity());
        android.app.AlertDialog disclosure = (android.app.AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Strenger Modus nutzt eine Bedienungshilfe", shadowOf(disclosure).getTitle().toString());
        disclosure.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(a).getNextStartedActivity().getAction());

        grantUsageAccess();
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                "com.other/.Svc:" + app.getPackageName() + "/" + BlockService.class.getName());
        Robolectric.buildActivity(LimitActivity.class, intent).setup(); // fresh screen reads the grants
        LimitActivity b = Robolectric.buildActivity(LimitActivity.class, intent).setup().get();
        assertEquals("Nutzungsdatenzugriff: erlaubt ✓", ((TextView) b.findViewById(R.id.strict_usage)).getText().toString());
        assertEquals("Strenger Modus: an ✓", ((TextView) b.findViewById(R.id.strict_service)).getText().toString());
        assertEquals(android.view.View.GONE, b.findViewById(R.id.strict_service_hint).getVisibility());
    }

    @Test
    public void limitsHaveTheirOwnSettingsPage() throws Exception {
        limits(INSTA + "\t45\t300-540\t\n" + TUBE + "\t0\t\t1260-420\t1\t10\ncom.not.installed\t10\t\t");
        SetupActivity setup = Robolectric.buildActivity(SetupActivity.class).setup().get();
        assertEquals("2 Apps mit Limit · strenger Modus aus",
                ((TextView) setup.findViewById(R.id.setup_limits_summary)).getText().toString());
        setup.findViewById(R.id.setup_limits).performClick();
        assertEquals(LimitsActivity.class.getName(), shadowOf(setup).getNextStartedActivity().getComponent().getClassName());

        LimitsActivity page = Robolectric.buildActivity(LimitsActivity.class).setup().get();
        android.widget.LinearLayout list = page.findViewById(R.id.limits_list);
        assertEquals(2, list.getChildCount());
        assertEquals("Insta\n45 min am Tag · morgens 05:00–09:00", ((TextView) list.getChildAt(0)).getText().toString());
        assertEquals("Tube\nabends 21:00–07:00 · fragt, 10 min Pause", ((TextView) list.getChildAt(1)).getText().toString());
        assertEquals(android.view.View.GONE, page.findViewById(R.id.limits_empty).getVisibility());
        shot(page, "15-limits");
        list.getChildAt(1).performClick();
        Intent edit = shadowOf(page).getNextStartedActivity();
        assertEquals(TUBE, edit.getStringExtra(LimitActivity.PKG));
        assertEquals("Tube", edit.getStringExtra(LimitActivity.LABEL));
    }

    @Test
    public void limitsPageWithoutLimitsInvitesToAddOne() {
        LimitsActivity page = Robolectric.buildActivity(LimitsActivity.class).setup().get();
        assertEquals(android.view.View.VISIBLE, page.findViewById(R.id.limits_empty).getVisibility());
        assertEquals("Strenger Modus einschalten  →", ((TextView) page.findViewById(R.id.strict_service)).getText().toString());
    }

    @Test
    public void usageAccessCheckFollowsTheGrant() {
        assertFalse(Usage.hasUsageAccess(app));
        grantUsageAccess();
        assertTrue(Usage.hasUsageAccess(app));
        assertFalse(Usage.strictModeOn(app));
    }

    // --- Warnings when a grant is missing ---

    private void enableStrictMode() {
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                app.getPackageName() + "/" + BlockService.class.getName());
    }

    private static android.app.AlertDialog latestDialog() {
        return (android.app.AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
    }

    private static String message(android.app.AlertDialog d) {
        return shadowOf(d).getMessage().toString();
    }

    private static void press(android.app.AlertDialog d, int which) {
        d.getButton(which).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private org.robolectric.android.controller.ActivityController<LimitActivity> limitScreen() {
        Intent intent = new Intent(app, LimitActivity.class).putExtra(LimitActivity.PKG, INSTA).putExtra(LimitActivity.LABEL, "Insta");
        return Robolectric.buildActivity(LimitActivity.class, intent).setup();
    }

    private void setDailyLimit(LimitActivity a) {
        a.findViewById(R.id.limit_daily).performClick();
        press(latestDialog(), DialogInterface.BUTTON_POSITIVE); // 1 h
    }

    @Test
    public void dailyLimitWithoutGrantsWarnsAndLeadsToUsageAccess() {
        LimitActivity a = limitScreen().get();
        setDailyLimit(a);
        android.app.AlertDialog warning = latestDialog();
        assertEquals("Achtung: Zeitlimit noch nicht wirksam", shadowOf(warning).getTitle().toString());
        assertTrue(message(warning).contains("Nutzungsdatenzugriff: Ohne ihn kennt Birdy die Nutzungszeit nicht"));
        assertTrue(message(warning).contains("Strenger Modus: Ohne ihn sperrt Birdy nur"));
        press(warning, DialogInterface.BUTTON_POSITIVE);
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, shadowOf(a).getNextStartedActivity().getAction());
    }

    @Test
    public void sameMissingGrantsWarnOnlyOncePerScreen() {
        LimitActivity a = limitScreen().get();
        setDailyLimit(a);
        android.app.AlertDialog first = latestDialog();
        press(first, DialogInterface.BUTTON_NEGATIVE); // "Später"
        ((Switch) a.findViewById(R.id.limit_morning)).setChecked(true);
        assertTrue("no second popup for the same thing", latestDialog() == first);
    }

    @Test
    public void quietTimeAloneOnlyNeedsStrictMode() {
        LimitActivity a = limitScreen().get();
        ((Switch) a.findViewById(R.id.limit_evening)).setChecked(true);
        android.app.AlertDialog warning = latestDialog();
        assertFalse("no daily limit, so usage access does not matter", message(warning).contains("Nutzungsdatenzugriff"));
        assertTrue(message(warning).contains("Strenger Modus"));
        assertTrue(message(warning).contains("Eingeschränkte Einstellungen zulassen"));
        press(warning, DialogInterface.BUTTON_NEUTRAL);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(a).getNextStartedActivity().getAction());
    }

    @Test
    public void setupGuidesFromOneGrantToTheNextAndConfirms() {
        org.robolectric.android.controller.ActivityController<LimitActivity> c = limitScreen();
        LimitActivity a = c.get();
        setDailyLimit(a);
        press(latestDialog(), DialogInterface.BUTTON_POSITIVE); // to usage access
        grantUsageAccess();
        c.pause().resume(); // back from the settings page
        android.app.AlertDialog next = latestDialog();
        assertFalse(message(next).contains("Nutzungsdatenzugriff"));
        assertTrue(message(next).contains("Strenger Modus"));
        press(next, DialogInterface.BUTTON_POSITIVE);
        press(latestDialog(), DialogInterface.BUTTON_POSITIVE); // "Zustimmen" in the disclosure
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(a).getNextStartedActivity().getAction());
        enableStrictMode();
        c.pause().resume();
        assertEquals("Alles bereit: Zeitlimits sind wirksam ✓", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals("Nutzungsdatenzugriff: erlaubt ✓", ((TextView) a.findViewById(R.id.strict_usage)).getText().toString());
    }

    @Test
    public void noWarningWhenEverythingIsSet() {
        grantUsageAccess();
        enableStrictMode();
        LimitActivity a = limitScreen().get();
        a.findViewById(R.id.limit_daily).performClick();
        android.app.AlertDialog picker = latestDialog();
        press(picker, DialogInterface.BUTTON_POSITIVE);
        assertTrue(latestDialog() == picker);
        assertEquals(INSTA + "\t60\t\t\t1\t0", prefs.getString("limits", ""));
    }

    @Test
    public void homeWarnsOnceWhenLimitsCannotWork() {
        limits(INSTA + "\t30\t\t");
        Robolectric.buildActivity(MainActivity.class).setup();
        android.app.AlertDialog warning = latestDialog();
        assertEquals("Achtung: Zeitlimit noch nicht wirksam", shadowOf(warning).getTitle().toString());

        ShadowAlertDialog.reset();
        Robolectric.buildActivity(MainActivity.class).setup(); // back home again: already said
        assertNull(ShadowAlertDialog.getLatestAlertDialog());

        grantUsageAccess();
        enableStrictMode();
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
        assertFalse("all fine: the next problem gets a new warning", prefs.contains(Grants.WARNED));

        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
        Robolectric.buildActivity(MainActivity.class).setup(); // e.g. switched off after an update
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        assertTrue(message(latestDialog()).contains("Strenger Modus"));
    }

    @Test
    public void homeStaysQuietWithoutLimits() {
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    // --- Sessions: ask how long, then block, then pause ---

    // The last started screen of the given class (skipping others), or null.
    private Intent started(Class<?> screen) {
        Intent next, found = null;
        while ((next = shadowOf(app).getNextStartedActivity()) != null) {
            if (next.getComponent() != null && screen.getName().equals(next.getComponent().getClassName())) found = next;
        }
        return found;
    }

    private SessionActivity sessionScreen(Intent intent) {
        return Robolectric.buildActivity(SessionActivity.class, intent).setup().get();
    }

    // The length tiles sit in rows of two inside session_choices.
    private static java.util.List<TextView> tiles(SessionActivity a) {
        android.widget.LinearLayout list = a.findViewById(R.id.session_choices);
        java.util.List<TextView> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            android.widget.LinearLayout row = (android.widget.LinearLayout) list.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) out.add((TextView) row.getChildAt(j));
        }
        return out;
    }

    private static java.util.List<String> choices(SessionActivity a) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (TextView t : tiles(a)) out.add(t.getText().toString());
        return out;
    }

    private static void choose(SessionActivity a, String length) {
        for (TextView t : tiles(a)) {
            if (t.getText().toString().equals(length)) { t.performClick(); return; }
        }
        throw new AssertionError("no choice " + length + " in " + choices(a));
    }

    @Test
    public void openingAsksHowLongThenOpensTheAppForThatLong() throws Exception {
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t10");
        front(INSTA);
        Intent ask = started(SessionActivity.class);
        assertNotNull("asked before the app may stay open", ask);
        assertNull(started(BlockActivity.class));
        SessionActivity a = sessionScreen(ask);
        assertEquals("Bewusst öffnen", ((TextView) a.findViewById(R.id.session_title)).getText().toString());
        assertEquals("Insta", ((TextView) a.findViewById(R.id.session_app)).getText().toString());
        assertEquals("Wie lange möchtest du jetzt hier sein?", ((TextView) a.findViewById(R.id.session_question)).getText().toString());
        assertEquals(java.util.Arrays.asList("5 min", "10 min", "15 min", "30 min"), choices(a));
        assertEquals("Danach ist Insta 10 min gesperrt.", ((TextView) a.findViewById(R.id.session_note)).getText().toString());
        shot(a, "14-session");
        choose(a, "10 min");
        Intent launch = shadowOf(a).getNextStartedActivity();
        assertEquals(INSTA, launch.getPackage() != null ? launch.getPackage() : launch.getComponent().getPackageName());
        assertTrue(a.isFinishing());
        assertTrue(prefs.getString("sessions", "").startsWith(INSTA + "\t"));
    }

    @Test
    public void sessionRunsOutThenPausesThenAsksAgain() {
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t10");
        front(INSTA);
        choose(sessionScreen(started(SessionActivity.class)), "10 min");
        shadowOf(app).clearNextStartedActivities();
        front(INSTA); // the app opens: inside the session now
        assertNull(started(SessionActivity.class));
        idle(Duration.ofMinutes(9));
        assertNull("still inside the 10 minutes", started(BlockActivity.class));
        idle(Duration.ofMinutes(2));
        Intent pause = started(BlockActivity.class);
        assertNotNull("covered when the 10 minutes are up", pause);
        assertEquals("Pause", pause.getStringExtra(BlockActivity.TITLE));
        assertEquals("Deine Session ist vorbei. Wieder frei ab 14:20 Uhr.", pause.getStringExtra(BlockActivity.DETAIL));

        idle(Duration.ofMinutes(4)); // 14:15, during the pause
        front(INSTA);
        assertEquals("Pause", started(BlockActivity.class).getStringExtra(BlockActivity.TITLE));
        idle(Duration.ofMinutes(6)); // 14:21, pause over
        front(INSTA);
        assertNotNull("asks for the next session", started(SessionActivity.class));
    }

    @Test
    public void withoutPauseItOffersAnotherRound() {
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t0");
        front(INSTA);
        choose(sessionScreen(started(SessionActivity.class)), "5 min");
        front(INSTA);
        idle(Duration.ofSeconds(5 * 60 + 2));
        Intent again = started(SessionActivity.class);
        assertNotNull(again);
        assertEquals("Deine Zeit ist um", again.getStringExtra(BlockActivity.TITLE));
        assertEquals("Noch eine Runde?", again.getStringExtra(BlockActivity.DETAIL));
    }

    @Test
    public void choicesStopAtWhatIsLeftToday() {
        grantUsageAccess();
        now(14, 0);
        limits(INSTA + "\t30\t\t\t1\t0");
        usage(INSTA, Usage.RESUMED, at(9, 0));
        usage(INSTA, Usage.PAUSED, at(9, 18)); // 12 min left
        front(INSTA);
        SessionActivity a = sessionScreen(started(SessionActivity.class));
        assertEquals(java.util.Arrays.asList("5 min", "10 min", "12 min"), choices(a));
        assertEquals("Heute noch 12 min.", ((TextView) a.findViewById(R.id.session_note)).getText().toString());
    }

    @Test
    public void quietTimeBeatsTheQuestion() {
        now(22, 0);
        limits(INSTA + "\t0\t\t1260-420\t1\t0");
        front(INSTA);
        Intent shown = shadowOf(app).getNextStartedActivity();
        assertEquals("blocked, not asked", BlockActivity.class.getName(), shown.getComponent().getClassName());
        assertEquals("Schonzeit abends", shown.getStringExtra(BlockActivity.TITLE));
    }

    @Test
    public void notNowGoesHome() {
        now(14, 0);
        limits(INSTA + "\t0\t\t\t1\t0");
        front(INSTA);
        SessionActivity a = sessionScreen(started(SessionActivity.class));
        a.findViewById(R.id.session_cancel).performClick();
        assertTrue(shadowOf(a).getNextStartedActivity().hasCategory(Intent.CATEGORY_HOME));
        assertTrue(a.isFinishing());
        assertEquals("no session started", "", prefs.getString("sessions", ""));
    }

    @Test
    public void limitScreenSetsAskAndPause() {
        LimitActivity a = limitScreen().get();
        ((Switch) a.findViewById(R.id.limit_ask)).setChecked(true);
        assertEquals(INSTA + "\t0\t\t\t1\t0", prefs.getString("limits", ""));
        assertEquals("Pause danach: keine  →", ((TextView) a.findViewById(R.id.limit_pause)).getText().toString());
        a.findViewById(R.id.limit_pause).performClick();
        android.app.AlertDialog d = latestDialog();
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < d.getListView().getAdapter().getCount(); i++) items.add(d.getListView().getAdapter().getItem(i).toString());
        assertEquals(java.util.Arrays.asList("Keine", "5 min", "10 min", "15 min", "30 min", "1 h", "2 h"), items);
        shadowOf(d).clickOnItem(2);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(INSTA + "\t0\t\t\t1\t10", prefs.getString("limits", ""));
        assertEquals("Pause danach: 10 min  →", ((TextView) a.findViewById(R.id.limit_pause)).getText().toString());
        ((Switch) a.findViewById(R.id.limit_ask)).setChecked(false);
        assertEquals(android.view.View.GONE, a.findViewById(R.id.limit_pause).getVisibility());
    }

    // --- Google Play: prominent disclosure before the accessibility grant ---

    @Test
    public void disclosureSaysWhatStrictModeDoesAndDoesNot() {
        LimitActivity a = limitScreen().get();
        a.findViewById(R.id.strict_service).performClick();
        android.app.AlertDialog d = latestDialog();
        String text = message(d);
        assertTrue(text.contains("nur, um zu erkennen, welche App gerade im Vordergrund ist"));
        assertTrue(text.contains("keine Bildschirminhalte, keine Eingaben und keine Texte"));
        assertTrue(text.contains("überträgt nichts"));
        assertEquals("Ablehnen", d.getButton(DialogInterface.BUTTON_NEGATIVE).getText().toString());
        assertEquals("Zustimmen", d.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
        press(d, DialogInterface.BUTTON_NEUTRAL);
        Intent privacy = shadowOf(a).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, privacy.getAction());
        assertEquals(Grants.PRIVACY_URL, privacy.getDataString());
    }

    @Test
    public void decliningTheDisclosureOpensNothing() {
        LimitActivity a = limitScreen().get();
        a.findViewById(R.id.strict_service).performClick();
        press(latestDialog(), DialogInterface.BUTTON_NEGATIVE);
        assertNull(shadowOf(a).getNextStartedActivity());
    }

    @Test
    public void warningLeadsThroughTheDisclosureToo() {
        grantUsageAccess();
        LimitActivity a = limitScreen().get();
        ((Switch) a.findViewById(R.id.limit_evening)).setChecked(true);
        press(latestDialog(), DialogInterface.BUTTON_POSITIVE); // "Jetzt einrichten"
        assertNull(shadowOf(a).getNextStartedActivity());
        assertEquals("Strenger Modus nutzt eine Bedienungshilfe", shadowOf(latestDialog()).getTitle().toString());
    }

    @Test
    public void privacyPolicyIsOneTapFromTheBirdyApp() {
        SetupActivity setup = Robolectric.buildActivity(SetupActivity.class).setup().get();
        setup.findViewById(R.id.privacy).performClick();
        assertEquals(Grants.PRIVACY_URL, shadowOf(setup).getNextStartedActivity().getDataString());
    }
}
