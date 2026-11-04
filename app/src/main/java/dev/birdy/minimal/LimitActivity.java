package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

// Time limits of one app (by package): a daily limit and a morning and evening quiet time. Saved on every change.
public class LimitActivity extends Activity {
    static final String PKG = "pkg", LABEL = "label";
    static final int[] PAUSES = {0, 5, 10, 15, 30, 60, 120};
    static final int MORNING_START = 5 * 60, MORNING_END = 9 * 60, EVENING_START = 21 * 60, EVENING_END = 0;

    private String pkg;
    private TextView daily, morningTime, eveningTime, used, pause;
    private Switch morning, evening, ask;
    private String warnedFor = ""; // which grants were missing when this screen last warned
    private boolean guiding; // sent to a settings page from the warning; check again on return

    static void open(Context context, String pkg, String label) {
        context.startActivity(new Intent(context, LimitActivity.class).putExtra(PKG, pkg).putExtra(LABEL, label));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.limit);
        pkg = getIntent().getStringExtra(PKG);
        ((TextView) findViewById(R.id.limit_app)).setText(getIntent().getStringExtra(LABEL));
        used = findViewById(R.id.limit_used);
        daily = findViewById(R.id.limit_daily);
        morning = findViewById(R.id.limit_morning);
        evening = findViewById(R.id.limit_evening);
        morningTime = findViewById(R.id.limit_morning_time);
        eveningTime = findViewById(R.id.limit_evening_time);
        ask = findViewById(R.id.limit_ask);
        pause = findViewById(R.id.limit_pause);
        pause.setOnClickListener(v -> pickPause());

        daily.setOnClickListener(v -> pickDaily());
        morningTime.setOnClickListener(v -> pickWindow("morgens"));
        eveningTime.setOnClickListener(v -> pickWindow("abends"));
        findViewById(R.id.limit_clear).setOnClickListener(v -> {
            edit(a -> { a.dailyMinutes = 0; a.morning = null; a.evening = null; a.ask = false; a.pauseMinutes = 0; });
            finish();
        });
        bindStrictStatus(this, findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() { // back from a settings page: the grants may have changed
        super.onResume();
        render();
        bindStrictStatus(this, findViewById(android.R.id.content));
        if (guiding) { // lead on to the next missing grant, or confirm
            guiding = false;
            Limits.AppLimit a = current();
            if (!a.isEmpty() && !warn(a) && Grants.allSet(this, a.dailyMinutes > 0)) {
                android.widget.Toast.makeText(this, "Alles bereit: Zeitlimits sind wirksam ✓", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Warns when this app's limits need a grant that is missing; once per missing combination on this screen.
    private boolean warn(Limits.AppLimit a) {
        boolean needsUsage = a.dailyMinutes > 0;
        String missing = (Grants.usageMissing(this, needsUsage) ? "u" : "") + (Grants.strictMissing(this) ? "s" : "");
        if (missing.isEmpty() || missing.equals(warnedFor)) return false;
        warnedFor = missing;
        return Grants.warn(this, needsUsage, () -> guiding = true);
    }

    private Limits.AppLimit current() {
        Limits.AppLimit a = Usage.load(this).get(pkg);
        return a != null ? a : new Limits.AppLimit(pkg);
    }

    private interface Change { void apply(Limits.AppLimit a); }

    private void edit(Change change) {
        Limits limits = Usage.load(this);
        Limits.AppLimit a = limits.getOrCreate(pkg);
        boolean first = a.isEmpty();
        change.apply(a);
        if (first && !a.isEmpty() && !a.ask) a.ask = true; // a new limit asks before each visit by default
        Usage.save(this, limits);
        render();
        if (!a.isEmpty()) warn(a);
    }

    private void render() {
        Limits.AppLimit a = current();
        daily.setText("Tageslimit: " + (a.dailyMinutes > 0 ? Limits.duration(a.dailyMinutes) : "aus") + "  →");
        morning.setOnCheckedChangeListener(null); // setChecked would call the listener and save again
        evening.setOnCheckedChangeListener(null);
        ask.setOnCheckedChangeListener(null);
        morning.setChecked(a.morning != null);
        evening.setChecked(a.evening != null);
        ask.setChecked(a.ask);
        ask.setOnCheckedChangeListener((v, on) -> edit(x -> x.ask = on));
        pause.setVisibility(a.ask ? View.VISIBLE : View.GONE);
        pause.setText("Pause danach: " + (a.pauseMinutes > 0 ? Limits.duration(a.pauseMinutes) : "keine") + "  →");
        morning.setOnCheckedChangeListener((v, on) -> edit(x -> x.morning = on ? new Limits.Window("morgens", MORNING_START, MORNING_END) : null));
        evening.setOnCheckedChangeListener((v, on) -> edit(x -> x.evening = on ? new Limits.Window("abends", EVENING_START, EVENING_END) : null));
        morningTime.setVisibility(a.morning != null ? View.VISIBLE : View.GONE);
        eveningTime.setVisibility(a.evening != null ? View.VISIBLE : View.GONE);
        if (a.morning != null) morningTime.setText(Limits.clock(a.morning.start) + " bis " + Limits.clock(a.morning.end) + " Uhr  →");
        if (a.evening != null) eveningTime.setText(Limits.clock(a.evening.start) + " bis " + Limits.clock(a.evening.end) + " Uhr  →");
        used.setText(Usage.hasUsageAccess(this)
                ? "Heute genutzt: " + Limits.duration((int) (Usage.todayMs(this, pkg) / Limits.MINUTE))
                : "Nutzungszeit unbekannt: Nutzungsdatenzugriff fehlt (unten).");
    }

    private void pickPause() {
        String[] labels = new String[PAUSES.length];
        for (int i = 0; i < PAUSES.length; i++) labels[i] = PAUSES[i] == 0 ? "Keine" : Limits.duration(PAUSES[i]);
        new AlertDialog.Builder(this)
                .setTitle("Pause nach einer Session")
                .setItems(labels, (d, which) -> edit(a -> a.pauseMinutes = PAUSES[which]))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // Hours and minutes in 5-minute steps; "Aus" removes the daily limit.
    private void pickDaily() {
        int current = current().dailyMinutes > 0 ? current().dailyMinutes : 60;
        String[] steps = new String[12];
        for (int i = 0; i < 12; i++) steps[i] = String.format(java.util.Locale.ROOT, "%02d", i * 5);
        NumberPicker hours = picker(0, 23, null), minutes = picker(0, 11, steps);
        hours.setValue(current / 60);
        minutes.setValue((current % 60) / 5);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(hours);
        TextView h = new TextView(this);
        h.setText(" h   ");
        row.addView(h);
        row.addView(minutes);
        TextView m = new TextView(this);
        m.setText(" min");
        row.addView(m);
        new AlertDialog.Builder(this)
                .setTitle("Tageslimit")
                .setView(row)
                .setNeutralButton("Aus", (d, w) -> edit(a -> a.dailyMinutes = 0))
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, w) -> {
                    int total = hours.getValue() * 60 + minutes.getValue() * 5;
                    edit(a -> a.dailyMinutes = total);
                })
                .show();
    }

    private NumberPicker picker(int min, int max, String[] labels) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        if (labels != null) p.setDisplayedValues(labels);
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return p;
    }

    // Two steps: start, then end.
    private void pickWindow(String name) {
        Limits.AppLimit a = current();
        Limits.Window w = "morgens".equals(name) ? a.morning : a.evening;
        if (w == null) return;
        TimePickerDialog begin = new TimePickerDialog(this, (v1, h1, m1) -> {
            int start = h1 * 60 + m1;
            TimePickerDialog end = new TimePickerDialog(this, (v2, h2, m2) -> {
                int stop = h2 * 60 + m2;
                if (stop == start) return; // an empty window would block nothing
                Limits.Window chosen = new Limits.Window(name, start, stop);
                edit(x -> { if ("morgens".equals(name)) x.morning = chosen; else x.evening = chosen; });
            }, w.end / 60, w.end % 60, true);
            end.setTitle("Gesperrt bis");
            end.show();
        }, w.start / 60, w.start % 60, true);
        begin.setTitle("Gesperrt ab");
        begin.show();
    }

    // Shared with SetupActivity: state of both grants, each row opens its settings page.
    static void bindStrictStatus(Activity activity, View root) {
        boolean usage = Usage.hasUsageAccess(activity), service = Usage.strictModeOn(activity);
        TextView usageRow = root.findViewById(R.id.strict_usage), serviceRow = root.findViewById(R.id.strict_service);
        usageRow.setText(usage ? "Nutzungsdatenzugriff: erlaubt ✓" : "Nutzungsdatenzugriff erlauben  →");
        serviceRow.setText(service ? "Strenger Modus: an ✓" : "Strenger Modus einschalten  →");
        usageRow.setOnClickListener(v -> Grants.open(activity, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        serviceRow.setOnClickListener(v -> {
            if (service) Grants.open(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); // to switch it off
            else Grants.discloseStrictMode(activity, null);
        });
        root.findViewById(R.id.strict_service_hint).setVisibility(service ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.strict_app_info).setVisibility(service ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.strict_app_info).setOnClickListener(v -> Grants.open(activity, Grants.appInfo(activity)));
    }
}
