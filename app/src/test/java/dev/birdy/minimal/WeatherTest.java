package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34) // runs on Java 17 too
public class WeatherTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private TextView text(String value, float size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout widget(TextView... texts) {
        LinearLayout outer = new LinearLayout(context), inner = new LinearLayout(context);
        for (TextView t : texts) inner.addView(t);
        outer.addView(inner);
        return outer;
    }

    @Test public void picksLargestBareTemperature() {
        TextView now = text("12°", 40);
        assertEquals(now, Weather.temperature(widget(text("7°", 12), now, text("H:14°", 12)), null));
    }

    @Test public void readsBelowZero() {
        TextView now = text("\u22123°", 40);
        assertEquals(now, Weather.temperature(widget(now), null));
    }

    @Test public void readsSingleCharacterCelsius() {
        TextView now = text("12℃", 40);
        assertEquals(now, Weather.temperature(widget(now), null));
    }

    @Test public void readsPaddedTemperature() {
        TextView now = text(" 12° ", 40);
        assertEquals(now, Weather.temperature(widget(now), null));
        assertEquals("12°", Weather.reading(now));
    }

    @Test public void readsNumberAndDegreeDrawnSeparately() {
        TextView now = text("12", 40);
        assertEquals(now, Weather.temperature(widget(text("Berlin", 14), now, text("°", 20)), null));
        assertEquals("12°", Weather.reading(now));
    }

    @Test public void bareNumberWithoutDegreeIsNoTemperature() {
        assertNull(Weather.temperature(widget(text("12", 40), text("Berlin", 14)), null));
    }

    private ComponentName install(String pkg, String cls) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(pkg, cls);
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addInstalledProvider(info);
        return info.provider;
    }

    @Test public void noKnownWidgetMeansNoProvider() {
        install("com.example.other", "Widget");
        assertNull(Weather.provider(context));
    }

    @Test public void findsAnySamsungWeatherWidget() {
        install("com.example.other", "Widget");
        ComponentName samsung = install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.AnyName");
        assertEquals(samsung, Weather.provider(context));
    }

    @Test public void prefersSamsungsSmallWeatherWidget() {
        install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherCoverAppWidget");
        install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherForecastAppWidget");
        ComponentName small = install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherAppWidget2x1");
        install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherAppWidget");
        assertEquals(small, Weather.provider(context));
    }

    @Test public void samsungCoverWidgetOnlyAsLastResort() {
        install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherCoverAppWidget");
        ComponentName other = install("com.sec.android.daemonapp", "com.sec.android.daemonapp.appwidget.WeatherAestheticAppWidget");
        assertEquals(other, Weather.provider(context));
    }

    @Test public void pixelWidgetWinsOverOtherWidgetsOfThatApp() {
        install("com.google.android.apps.weather", "com.google.android.apps.weather.widget.FullForecastWidgetReceiver");
        ComponentName pixel = install("com.google.android.apps.weather", "com.google.android.apps.weather.widget.CurrentForecastWidgetReceiver");
        assertEquals(pixel, Weather.provider(context));
    }

    @Test public void errorStateHasNoTemperature() {
        assertNull(Weather.temperature(widget(text("Ort nicht verfügbar", 12), text("Einstellungen", 12)), null));
    }
}
