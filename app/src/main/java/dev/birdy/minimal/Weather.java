package dev.birdy.minimal;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.regex.Pattern;

/**
 * Temperature next to the date, read from the weather app's own widget. Android offers no weather API,
 * but a launcher may host widgets: this one stays invisible and only its text is used, so Birdy needs
 * neither internet nor location.
 * shortcut: only Pixel Weather and Samsung Weather are known, add other providers when someone needs them.
 */
final class Weather {
    static final String KEY = "weather_widget";
    static final int NONE = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final ComponentName PIXEL = new ComponentName("com.google.android.apps.weather",
            "com.google.android.apps.weather.widget.CurrentForecastWidgetReceiver");
    // Samsung Weather has several widgets; the small ones show the current temperature as text. The cover widget is
    // for flip-phone cover screens and the others vary by One UI version, so they only come after these.
    private static final String SAMSUNG_PACKAGE = "com.sec.android.daemonapp";
    private static final String[] SAMSUNG_PREFERRED = {
            "com.sec.android.daemonapp.appwidget.WeatherAppWidget2x1",
            "com.sec.android.daemonapp.appwidget.WeatherAppWidget"};
    private static final Pattern TEMPERATURE = Pattern.compile("[-−]?\\d+\\s*(°[CF]?|℃|℉)");
    private static final Pattern NUMBER = Pattern.compile("[-−]?\\d+");
    private static final Pattern UNIT = Pattern.compile("°[CF]?|℃|℉");
    private static final int HOST_ID = 1;
    private static final int SIZE_DP = 200; // within the widget's size range, so it shows its full layout

    private final Context context;
    private final AppWidgetHost host;
    private final ViewGroup parent;
    private final TextView label;
    private AppWidgetHostView widget;
    private TextView source;

    Weather(Context context, ViewGroup parent, TextView label) {
        this.context = context;
        this.parent = parent;
        this.label = label;
        host = host(context, this);
        label.setOnClickListener(v -> open());
    }

    private void open() {
        Intent app = context.getPackageManager().getLaunchIntentForPackage(widget.getAppWidgetInfo().provider.getPackageName());
        if (app != null) {
            context.startActivity(app);
            return;
        }
        // Samsung's weather app can be without a launcher entry, so press the widget's own button instead.
        for (View v = source; v != null && v != widget; v = (View) v.getParent()) {
            if (v.performClick()) return;
        }
    }

    /** The weather widget on this phone, or null if there is none we know. */
    static ComponentName provider(Context context) {
        ComponentName samsung = null;
        int samsungRank = Integer.MAX_VALUE;
        for (AppWidgetProviderInfo p : AppWidgetManager.getInstance(context).getInstalledProviders()) {
            if (PIXEL.equals(p.provider)) return p.provider;
            if (!SAMSUNG_PACKAGE.equals(p.provider.getPackageName())) continue;
            int rank = samsungRank(p.provider.getClassName());
            if (rank < samsungRank) {
                samsung = p.provider;
                samsungRank = rank;
            }
        }
        return samsung;
    }

    // Lower is better: the preferred widgets in order, then any other, then the cover widget.
    private static int samsungRank(String className) {
        for (int i = 0; i < SAMSUNG_PREFERRED.length; i++) if (SAMSUNG_PREFERRED[i].equals(className)) return i;
        return className.contains("Cover") ? SAMSUNG_PREFERRED.length + 1 : SAMSUNG_PREFERRED.length;
    }

    private static AppWidgetHost host(Context context, Weather weather) {
        return new AppWidgetHost(context.getApplicationContext(), HOST_ID) {
            @Override
            protected AppWidgetHostView onCreateView(Context c, int id, AppWidgetProviderInfo info) {
                return new AppWidgetHostView(c) {
                    @Override
                    public void updateAppWidget(RemoteViews views) {
                        super.updateAppWidget(views);
                        if (weather != null) post(weather::read);
                    }
                };
            }
        };
    }

    /** New widget id for the settings switch; bind it with {@link #bind} or the system's bind dialog. */
    static int allocate(Context context) {
        return host(context, null).allocateAppWidgetId();
    }

    static boolean bind(Context context, int id) {
        return AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, provider(context));
    }

    static void delete(Context context, int id) {
        host(context, null).deleteAppWidgetId(id);
    }

    /**
     * Earlier versions took any Samsung widget, possibly one without the temperature as text. Moves a saved widget
     * to the better one where Android allows it without asking and it needs no setup; otherwise keeps the old one.
     */
    static int upgrade(Context context, int id) {
        if (id == NONE) return id;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null || !SAMSUNG_PACKAGE.equals(info.provider.getPackageName())) return id;
        ComponentName best = provider(context); // lists all widgets, so only for Samsung's
        if (best == null || best.equals(info.provider)) return id;
        int fresh = allocate(context);
        AppWidgetProviderInfo freshInfo = manager.bindAppWidgetIdIfAllowed(fresh, best) ? manager.getAppWidgetInfo(fresh) : null;
        if (freshInfo == null || freshInfo.configure != null) {
            delete(context, fresh);
            return id;
        }
        delete(context, id);
        return fresh;
    }

    static Intent bindDialog(Context context, int id) {
        return new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider(context));
    }

    /** Opens the widget's own setup if it has one. False means there is nothing to wait for. */
    static boolean configure(Activity activity, int id, int requestCode) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(id);
        if (info == null || info.configure == null) return false;
        try {
            host(activity, null).startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null);
            return true;
        } catch (ActivityNotFoundException e) { // not startable from outside; the widget may still work
            return false;
        }
    }

    void start(int id) {
        AppWidgetProviderInfo info = id == NONE ? null
                : AppWidgetManager.getInstance(context).getAppWidgetInfo(id); // null once unbound, e.g. the app was removed
        if (widget != null && (info == null || widget.getAppWidgetId() != id)) {
            parent.removeView(widget);
            widget = null;
            label.setVisibility(View.GONE);
        }
        if (info == null) return;
        if (widget == null) {
            widget = host.createView(context.getApplicationContext(), id, info);
            int size = Math.round(SIZE_DP * context.getResources().getDisplayMetrics().density);
            widget.updateAppWidgetSize(null, SIZE_DP, SIZE_DP, SIZE_DP, SIZE_DP);
            widget.setVisibility(View.INVISIBLE);
            parent.addView(widget, 0, new FrameLayout.LayoutParams(size, size));
        }
        host.startListening();
    }

    void stop() {
        host.stopListening();
    }

    private void read() {
        source = temperature(widget, null);
        label.setText(source == null ? "" : reading(source));
        label.setVisibility(source == null ? View.GONE : View.VISIBLE);
    }

    // The current temperature is the largest bare "12°" in the widget; "H:14°" and error texts don't match.
    // Some widgets (likely Samsung's) draw the number and the "°" in two views side by side; then the number counts.
    static TextView temperature(View view, TextView best) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView && isTemperature((TextView) child, i + 1 < group.getChildCount() ? group.getChildAt(i + 1) : null)
                        && (best == null || ((TextView) child).getTextSize() > best.getTextSize())) best = (TextView) child;
                else best = temperature(child, best);
            }
        } else if (view instanceof TextView && isTemperature((TextView) view, null)
                && (best == null || ((TextView) view).getTextSize() > best.getTextSize())) {
            best = (TextView) view;
        }
        return best;
    }

    private static boolean isTemperature(TextView text, View next) {
        String value = text.getText().toString().trim();
        if (TEMPERATURE.matcher(value).matches()) return true;
        return NUMBER.matcher(value).matches() && next instanceof TextView
                && UNIT.matcher(((TextView) next).getText().toString().trim()).matches();
    }

    // What the label shows: the temperature without padding, with "°" when the widget drew it separately.
    static String reading(TextView source) {
        String value = source.getText().toString().trim();
        return NUMBER.matcher(value).matches() ? value + "°" : value;
    }
}
