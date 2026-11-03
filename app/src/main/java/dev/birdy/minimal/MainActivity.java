package dev.birdy.minimal;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextClock;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    static final String PREFS = "birdy"; // shared with SetupActivity, which edits the settings keys "top", "newest", "type" and "bottom"
    private static final int MAX_FAVORITES = 7;
    private static final int TOP_COUNT = 5;
    private static final long TOP_WINDOW_MS = 3L * 24 * 60 * 60 * 1000; // "most used" looks back 3 days
    private static final long SETTLE_MS = 340;
    private static final PathInterpolator SETTLE_CURVE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private LauncherApps launcherApps;
    private SharedPreferences prefs;
    private final Map<String, App> apps = new HashMap<>(); // by flattened ComponentName
    private final List<String> favorites = new ArrayList<>();
    private final List<App> all = new ArrayList<>(); // in list order; the adapter shows these or the search matches
    private App bestMatch; // what Enter opens: the first match in list order, wherever it is shown
    private ArrayAdapter<App> adapter;
    private ColorMatrixColorFilter grayscale;
    private Collator collator;

    private View home, drawer, hint, topSection;
    private LinearLayout topRows;
    private ListView list;
    private EditText search;
    private LinearLayout favoritesView;
    private ImageView left, right;

    // Drawer transition: progress 0 = home, 1 = app list. Finger drags drive it, ValueAnimator settles it.
    private float progress;
    private boolean drawerOpen; // where we are heading
    private ValueAnimator settle;
    private final VelocityTracker velocity = VelocityTracker.obtain();
    private float downX, downY, downProgress;
    private boolean dragging, listAtTop;
    private int touchSlop;
    private float dragDistance, flingVelocity, density;

    private final LauncherApps.Callback packagesChanged = new LauncherApps.Callback() {
        @Override public void onPackageAdded(String pkg, UserHandle user) { load(); }
        @Override public void onPackageRemoved(String pkg, UserHandle user) { load(); }
        @Override public void onPackageChanged(String pkg, UserHandle user) { load(); }
        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) { load(); }
        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) { load(); }
    };

    // Held in a field: SharedPreferences only keeps a weak reference to listeners.
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (p, key) -> {
        if ("top".equals(key) || "newest".equals(key)) load();
        else if ("bottom".equals(key)) applySearch(true);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        launcherApps = getSystemService(LauncherApps.class);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        collator = Collator.getInstance(); // locale-aware, so Ä/Ö/Ü sort next to A/O/U
        String saved = prefs.getString("favorites", "");
        if (!saved.isEmpty()) favorites.addAll(Arrays.asList(saved.split("\n")));

        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        dragDistance = 260 * density; // finger travel for a full open
        flingVelocity = 400 * density; // px/s above which the swipe direction wins over the position

        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0);
        grayscale = new ColorMatrixColorFilter(gray);

        TextClock date = findViewById(R.id.date);
        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);

        home = findViewById(R.id.home);
        drawer = findViewById(R.id.drawer);
        favoritesView = findViewById(R.id.favorites);
        hint = findViewById(R.id.hint);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);

        adapter = new ArrayAdapter<>(this, R.layout.item);
        list = findViewById(R.id.list);
        View header = getLayoutInflater().inflate(R.layout.top, list, false);
        topSection = header.findViewById(R.id.top);
        topRows = header.findViewById(R.id.top_rows);
        list.addHeaderView(header, null, false);
        list.setAdapter(adapter);
        // Positions include the header, so ask the list rather than the adapter.
        list.setOnItemClickListener((parent, view, pos, id) -> launch((App) parent.getItemAtPosition(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            showMenu(view, (App) parent.getItemAtPosition(pos));
            return true;
        });
        DotScrollbar rail = findViewById(R.id.rail);
        list.setOnScrollListener(new AbsListView.OnScrollListener() { // also fires after layout, e.g. when a search filters
            @Override public void onScrollStateChanged(AbsListView view, int state) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) { rail.show(first, visible, total); }
        });

        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applySearch(true);
                renderTop(); // hidden while searching
            }
        });
        // Enter on the keyboard opens the best match.
        search.setOnEditorActionListener((v, action, event) -> {
            if (!search.getText().toString().trim().isEmpty() && bestMatch != null) launch(bestMatch);
            return true;
        });

        // Android 13+ delivers Back here; older versions call onBackPressed().
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> animateTo(false));
        }

        load();
        launcherApps.registerCallback(packagesChanged);
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged);
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        launcherApps.unregisterCallback(packagesChanged);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        animateTo(false);
    }

    // Home pressed while already home.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        animateTo(false);
    }

    // Coming back from an app always lands on the home screen, without replaying the animation.
    @Override
    protected void onStop() {
        super.onStop();
        if (settle != null) settle.cancel();
        drawerOpen = false;
        hideKeyboard();
        setProgress(0);
    }

    // Vertical drags anywhere move the drawer: up from home opens, down from the top of the list closes.
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            downProgress = progress;
            listAtTop = !list.canScrollVertically(-1);
            dragging = false;
            velocity.clear();
        }
        velocity.addMovement(ev);
        float dy = ev.getY() - downY;

        if (!dragging && action == MotionEvent.ACTION_MOVE
                && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(ev.getX() - downX)
                && (drawerOpen ? dy > 0 && listAtTop : dy < 0)) {
            dragging = true;
            downY = ev.getY(); // track from here so the content does not jump by the slop
            dy = 0;
            if (settle != null) settle.cancel();
            if (drawerOpen) hideKeyboard();
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL); // children drop this touch, so no tap or scroll fires
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
        if (!dragging) return super.dispatchTouchEvent(ev);

        setProgress(Math.max(0, Math.min(1, downProgress - dy / dragDistance)));
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            velocity.computeCurrentVelocity(1000);
            float vy = velocity.getYVelocity();
            animateTo(Math.abs(vy) > flingVelocity ? vy < 0 : progress > 0.5f);
        }
        return true;
    }

    private void animateTo(boolean open) {
        drawerOpen = open;
        if (open && prefs.getBoolean("type", false)) showKeyboard(); // rises together with the list
        if (!open) hideKeyboard();
        if (settle != null) settle.cancel();
        float target = open ? 1 : 0;
        if (progress == target) return;
        settle = ValueAnimator.ofFloat(progress, target);
        settle.setDuration((long) (SETTLE_MS * Math.max(0.5f, Math.abs(target - progress))));
        settle.setInterpolator(SETTLE_CURVE);
        settle.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        settle.start();
    }

    // Fade-through: home fades out and recedes first, then the list fades in while rising, so texts never overlap.
    private void setProgress(float p) {
        if (p > 0 && drawer.getVisibility() != View.VISIBLE) {
            renderTop(); // counts changed since the last open
            list.setSelection(0);
        }
        progress = p;
        drawer.setVisibility(p > 0 ? View.VISIBLE : View.INVISIBLE);
        drawer.setAlpha(Math.max(0, (p - 0.3f) / 0.7f));
        drawer.setTranslationY((1 - p) * 72 * density);
        float scale = 1 - 0.06f * p;
        home.setAlpha(Math.max(0, 1 - p / 0.3f));
        home.setScaleX(scale);
        home.setScaleY(scale);
        home.setTranslationY(-p * 40 * density);
        if (p == 0 && search.length() > 0) search.setText("");
    }

    private void showKeyboard() {
        search.requestFocus();
        search.post(() -> getSystemService(InputMethodManager.class).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
    }

    private void hideKeyboard() {
        search.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void load() {
        boolean newest = prefs.getBoolean("newest", false);
        List<App> sorted = new ArrayList<>();
        apps.clear();
        for (LauncherActivityInfo info : launcherApps.getActivityList(null, Process.myUserHandle())) {
            App app = new App(info);
            if (newest) app.installed = info.getFirstInstallTime(); // a binder call each, so only when sorting by it
            sorted.add(app);
            apps.put(app.key, app);
        }
        sorted.sort(newest
                ? (a, b) -> Long.compare(b.installed, a.installed)
                : (a, b) -> collator.compare(a.label, b.label));
        all.clear();
        all.addAll(sorted);
        applySearch(false); // keeps an active search applied, without moving the scroll position
        renderHome();
        renderTop();
    }

    // While searching with "bottom" on, matches stack up from the search field with the best one right above it,
    // where the thumb is. Otherwise the list reads top-down as usual.
    private void applySearch(boolean jump) {
        String query = search.getText().toString();
        List<App> shown = new ArrayList<>();
        for (App app : all) if (Search.matches(app.label, query)) shown.add(app);
        bestMatch = shown.isEmpty() ? null : shown.get(0);
        boolean fromBottom = !query.trim().isEmpty() && prefs.getBoolean("bottom", false);
        if (fromBottom) Collections.reverse(shown);
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(shown);
        list.setStackFromBottom(fromBottom);
        adapter.notifyDataSetChanged();
        if (jump) list.setSelection(fromBottom ? list.getCount() - 1 : 0);
    }

    // Keys of uninstalled apps stay saved but are skipped, so a pin survives app updates and reinstalls.
    private void renderHome() {
        favoritesView.removeAllViews();
        for (String key : favorites) {
            App app = apps.get(key);
            if (app == null) continue;
            TextView row = row(favoritesView, app);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            favoritesView.addView(row);
        }
        hint.setVisibility(favoritesView.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        bindIcon(left, apps.get(prefs.getString("left", "")));
        bindIcon(right, apps.get(prefs.getString("right", "")));
    }

    // Most launched apps (via Birdy, last 3 days) above the full list; empty when off, unused or searching.
    private void renderTop() {
        topRows.removeAllViews();
        if (prefs.getBoolean("top", false) && search.length() == 0) {
            for (String key : LaunchLog.rank(prefs.getString("launches", ""), System.currentTimeMillis(), TOP_WINDOW_MS)) {
                App app = apps.get(key);
                if (app == null) continue;
                TextView row = row(topRows, app);
                row.setFontVariationSettings("'wght' 450");
                topRows.addView(row);
                if (topRows.getChildCount() == TOP_COUNT) break;
            }
        }
        topSection.setVisibility(topRows.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private TextView row(ViewGroup parent, App app) {
        TextView row = (TextView) getLayoutInflater().inflate(R.layout.item, parent, false);
        row.setText(app.label);
        row.setOnClickListener(v -> launch(app));
        row.setOnLongClickListener(v -> { showMenu(v, app); return true; });
        return row;
    }

    // White monochrome glyph where the app ships one (Android 13+), otherwise the icon in grayscale.
    private void bindIcon(ImageView view, App app) {
        view.setVisibility(app == null ? View.INVISIBLE : View.VISIBLE);
        if (app == null) return;
        Drawable icon = app.info.getIcon(0);
        Drawable mono = Build.VERSION.SDK_INT >= 33 && icon instanceof AdaptiveIconDrawable
                ? ((AdaptiveIconDrawable) icon).getMonochrome() : null;
        if (mono != null) {
            view.setImageDrawable(mono); // drawn on the 108dp adaptive canvas, so it needs no padding
            view.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            view.setPadding(0, 0, 0, 0);
        } else {
            view.setImageDrawable(icon);
            view.setColorFilter(grayscale);
            int pad = (int) (15 * density);
            view.setPadding(pad, pad, pad, pad);
        }
        view.setContentDescription(app.label);
        view.setOnClickListener(v -> launch(app));
        view.setOnLongClickListener(v -> { showMenu(v, app); return true; });
    }

    private void showMenu(View anchor, App app) {
        boolean pinned = favorites.contains(app.key);
        boolean isLeft = app.key.equals(prefs.getString("left", ""));
        boolean isRight = app.key.equals(prefs.getString("right", ""));
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, pinned ? "Vom Startscreen entfernen" : "Auf den Startscreen")
                .setEnabled(pinned || favoritesView.getChildCount() < MAX_FAVORITES);
        menu.add(0, 2, 0, isLeft ? "Unten links entfernen" : "Unten links");
        menu.add(0, 3, 0, isRight ? "Unten rechts entfernen" : "Unten rechts");
        menu.add(0, 4, 0, "App-Info");
        if ((app.info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) menu.add(0, 5, 0, "Deinstallieren");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (pinned) favorites.remove(app.key); else favorites.add(app.key);
                    prefs.edit().putString("favorites", String.join("\n", favorites)).apply();
                    break;
                case 2:
                    prefs.edit().putString("left", isLeft ? "" : app.key).apply();
                    break;
                case 3:
                    prefs.edit().putString("right", isRight ? "" : app.key).apply();
                    break;
                case 4:
                    launcherApps.startAppDetailsActivity(app.info.getComponentName(), app.info.getUser(), null, null);
                    break;
                case 5: // system dialog; unlike App-Info it also works for Birdy while Birdy is the active launcher
                    startActivity(new Intent(Intent.ACTION_DELETE,
                            Uri.fromParts("package", app.info.getComponentName().getPackageName(), null)));
                    break;
            }
            renderHome();
            return true;
        });
        popup.show();
    }

    private void launch(App app) {
        try {
            launcherApps.startMainActivity(app.info.getComponentName(), app.info.getUser(), null, null);
        } catch (RuntimeException e) { // app removed or disabled since the list was built
            load();
            return;
        }
        if (prefs.getBoolean("top", false)) { // only collect usage while the feature is on
            prefs.edit().putString("launches", LaunchLog.append(
                    prefs.getString("launches", ""), app.key, System.currentTimeMillis(), TOP_WINDOW_MS)).apply();
        }
    }

    private static final class App {
        final LauncherActivityInfo info;
        final String key;
        final String label;
        long installed; // first install time, only filled when sorting by it

        App(LauncherActivityInfo info) {
            this.info = info;
            key = info.getComponentName().flattenToString();
            label = info.getLabel().toString();
        }

        @Override
        public String toString() { return label; } // ArrayAdapter renders and filters by this
    }
}
