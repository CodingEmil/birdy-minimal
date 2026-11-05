package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

// "Neu in Birdy" once after an update, never after a fresh install.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WhatsNewTest {
    private static final String NOTES = "### Neu\n- **Kontakte in der Suche:** Passende Kontakte erscheinen.\n"
            + "- **Startscreen anordnen:** Mit ↑ und ↓ verschieben.\n### Behoben\n- Liste blieb offen.";

    private Application app;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        WhatsNew.source = context -> NOTES;
    }

    @After
    public void tearDown() {
        WhatsNew.source = WhatsNew.ASSET;
    }

    private void version(String name, long code, boolean updated) {
        PackageInfo info = shadowOf(app.getPackageManager()).getInternalMutablePackageInfo(app.getPackageName());
        info.versionName = name;
        info.setLongVersionCode(code);
        info.firstInstallTime = 1_000;
        info.lastUpdateTime = updated ? 2_000 : 1_000;
    }

    private static AlertDialog dialog() {
        return (AlertDialog) ShadowAlertDialog.getLatestAlertDialog();
    }

    private static java.util.List<String> texts(AlertDialog d) {
        android.widget.LinearLayout list = d.findViewById(R.id.whatsnew_list);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) out.add(((android.widget.TextView) list.getChildAt(i)).getText().toString());
        return out;
    }

    @Test
    public void parsesTitlesDetailsAndContinuationLines() {
        java.util.List<WhatsNew.Section> s = WhatsNew.parse(
                "### Neu\n- **Gruppen:** Apps bündeln,\n  auch mehrfach.\n- **Kurz**\n- Ohne `Fett`\n### Intern\n\n### Leer\n");
        assertEquals("sections without items drop out", 1, s.size());
        assertEquals("Neu", s.get(0).name);
        assertEquals("Gruppen", s.get(0).items.get(0).title);
        assertEquals("Apps bündeln, auch mehrfach.", s.get(0).items.get(0).detail);
        assertEquals("Kurz", s.get(0).items.get(1).title);
        assertEquals("", s.get(0).items.get(1).detail);
        assertEquals("Ohne Fett", s.get(0).items.get(2).title);
        assertTrue(WhatsNew.parse("  \n").isEmpty());
    }

    @Test
    public void freshInstallShowsNothingButRemembersTheVersion() {
        version("1.4", 40, false);
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(dialog());
        assertEquals(40, prefs.getLong(WhatsNew.SEEN, -1));
    }

    @Test
    public void updateShowsTheNotesOnce() {
        prefs.edit().putLong(WhatsNew.SEEN, 30).commit();
        version("1.4", 40, true);
        Robolectric.buildActivity(MainActivity.class).setup();
        AlertDialog d = dialog();
        assertNotNull(d);
        assertEquals("Neu in Birdy 1.4", shadowOf(d).getTitle().toString());
        assertEquals(java.util.Arrays.asList("Neu", "Kontakte in der Suche", "Passende Kontakte erscheinen.",
                "Startscreen anordnen", "Mit ↑ und ↓ verschieben.", "Behoben", "Liste blieb offen."), texts(d));
        ShadowAlertDialog.reset();
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull("only once", dialog());
    }

    @Test
    public void updateFromAVersionWithoutThisPopupShowsItToo() {
        version("1.4", 40, true); // nothing remembered yet, but the app was updated
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNotNull(dialog());
    }

    @Test
    public void devBuildsAndEmptyNotesShowNothing() {
        prefs.edit().putLong(WhatsNew.SEEN, 30).commit();
        version("dev", 40, true);
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(dialog());

        prefs.edit().putLong(WhatsNew.SEEN, 30).commit();
        version("1.4", 41, true);
        WhatsNew.source = context -> "  ";
        Robolectric.buildActivity(MainActivity.class).setup();
        assertNull(dialog());
    }

    @Test
    public void grantsWarningWaitsForTheNextVisit() {
        prefs.edit().putLong(WhatsNew.SEEN, 30).putString("limits", "com.x\t0\t300-540\t\t1\t0").commit();
        version("1.4", 40, true);
        Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals("Neu in Birdy 1.4", shadowOf(dialog()).getTitle().toString());
        ShadowAlertDialog.reset();
        Robolectric.buildActivity(MainActivity.class).setup();
        assertTrue(shadowOf(dialog()).getTitle().toString().startsWith("Achtung"));
    }
}
