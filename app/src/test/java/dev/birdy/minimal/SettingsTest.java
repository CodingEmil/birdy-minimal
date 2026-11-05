package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// The Birdy app: a short overview, each group of settings on its own page.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class SettingsTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    @Test
    public void eachRowOpensItsPage() throws Exception {
        SetupActivity setup = Robolectric.buildActivity(SetupActivity.class).setup().get();
        TimeLimitTest.shot(setup, "16-settings");
        int[] rows = {R.id.setup_home, R.id.setup_list, R.id.setup_limits, R.id.setup_feeds, R.id.setup_help};
        Class<?>[] pages = {HomeSettingsActivity.class, ListSettingsActivity.class, LimitsActivity.class,
                FeedsActivity.class, HelpActivity.class};
        for (int i = 0; i < rows.length; i++) {
            setup.findViewById(rows[i]).performClick();
            assertEquals(pages[i].getName(), shadowOf(setup).getNextStartedActivity().getComponent().getClassName());
        }
    }

    @Test
    public void onceBirdyIsTheHomeAppTheHowToSwitchStepsGo() {
        SetupActivity setup = Robolectric.buildActivity(SetupActivity.class).setup().get(); // its only home app here
        assertEquals("Aktiv als Launcher", text(setup, R.id.status));
        assertEquals(View.GONE, setup.findViewById(R.id.onboarding).getVisibility());
        assertEquals("Launcher wechseln", text(setup, R.id.action));
    }

    @Test
    public void theOverviewSaysWhatShortsAndReelsGet() {
        SetupActivity setup = Robolectric.buildActivity(SetupActivity.class).setup().get();
        assertEquals("Shorts und Reels sperren, sofort oder nach einer Zeit am Tag.", text(setup, R.id.setup_feeds_summary));
        prefs.edit().putBoolean("blockShorts", true).putBoolean("blockReels", true).putInt("blockReelsMinutes", 30).commit();
        SetupActivity again = Robolectric.buildActivity(SetupActivity.class).setup().get();
        assertEquals("Shorts sofort gesperrt · Reels 30 min · Bedienungshilfe fehlt", text(again, R.id.setup_feeds_summary));
    }

    @Test
    public void switchesKeepTheirSettingsOnTheirPages() throws Exception {
        HomeSettingsActivity home = Robolectric.buildActivity(HomeSettingsActivity.class).setup().get();
        TimeLimitTest.shot(home, "17-settings-home");
        Switch statusBar = home.findViewById(R.id.hide_status_bar);
        assertEquals("hidden by default", true, statusBar.isChecked());
        statusBar.setChecked(false);
        assertEquals(false, prefs.getBoolean(MainActivity.HIDE_STATUS_BAR, true));
        Switch lowercase = home.findViewById(R.id.lowercase);
        assertEquals("off by default", false, lowercase.isChecked());
        lowercase.setChecked(true);
        assertEquals(true, prefs.getBoolean(MainActivity.LOWERCASE, false));
        Switch shade = home.findViewById(R.id.shade);
        assertEquals("on by default", true, shade.isChecked());
        shade.setChecked(false);
        assertEquals(false, prefs.getBoolean("shade", true));

        ListSettingsActivity list = Robolectric.buildActivity(ListSettingsActivity.class).setup().get();
        TimeLimitTest.shot(list, "18-settings-list");
        ((Switch) list.findViewById(R.id.bottom)).setChecked(true);
        assertEquals(true, prefs.getBoolean("bottom", false));
        assertEquals("web search on by default", true, ((Switch) list.findViewById(R.id.web)).isChecked());

        prefs.edit().putBoolean("blockShorts", true).putInt("blockShortsMinutes", 15).commit();
        TimeLimitTest.shot(Robolectric.buildActivity(FeedsActivity.class).setup().get(), "19-settings-feeds");
        TimeLimitTest.shot(Robolectric.buildActivity(HelpActivity.class).setup().get(), "20-settings-help");
    }
}
