package dev.birdy.minimal;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    static final String PREFS = "birdy"; // shared with SetupActivity, which edits the settings keys "top", "newest", "type", "bottom" and "shade"
    private static final int MAX_FAVORITES = 7; // apps and groups together
    private static final int TOP_COUNT = 5;
    private static final long TOP_WINDOW_MS = 3L * 24 * 60 * 60 * 1000; // "most used" looks back 3 days
    private static final long SETTLE_MS = 340;
    private static final PathInterpolator SETTLE_CURVE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private LauncherApps launcherApps;
    private SharedPreferences prefs;
    private final Map<String, App> apps = new HashMap<>(); // by flattened ComponentName
    private final List<String> favorites = new ArrayList<>(); // app keys and "group:<id>" entries, in home order
    private Groups groups;
    private String openGroup; // id of the group unfolded on the home screen, or null
    private View openFirst, openLast; // its header and last row, to scroll them into view
    private boolean arranging; // home shows up/down arrows next to each entry
    private final List<App> all = new ArrayList<>(); // in list order; the adapter shows these or the search matches
    private App bestMatch; // what Enter opens and the list highlights: the first search match; null when not searching
    private List<ContactSearch.Contact> contactHits = new ArrayList<>(); // shown beside the apps while searching
    private boolean webOffered; // nothing matched: Enter searches the web
    private LinearLayout suggestionsTop, suggestionsBottom;
    private ArrayAdapter<App> adapter;
    private ColorMatrixColorFilter grayscale;
    private Collator collator;

    private View home, drawer, hint, topSection;
    private LinearLayout topRows;
    private ListView list;
    private EditText search;
    private LinearLayout favoritesView;
    private ScrollView favoritesScroll;
    private ImageView left, right;

    // Drawer transition: progress 0 = home, 1 = app list. Finger drags drive it, ValueAnimator settles it.
    private float progress;
    private boolean drawerOpen; // where we are heading
    private ValueAnimator settle;
    private final VelocityTracker velocity = VelocityTracker.obtain();
    private float downX, downY, downProgress;
    private boolean dragging, listAtTop, shadePulled, homeCanScrollUp, homeCanScrollDown;
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
        else if ("bottom".equals(key) || "contacts".equals(key) || "web".equals(key)) applySearch(true);
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
        groups = Groups.parse(prefs.getString("groups", ""));

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
        favoritesScroll = findViewById(R.id.favorites_scroll);
        hint = findViewById(R.id.hint);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);

        adapter = new ArrayAdapter<App>(this, R.layout.item) {
            // While searching, the top match stays white and the others turn grey. Rows are recycled, so set it every time.
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView row = (TextView) super.getView(pos, convert, parent);
                boolean dim = bestMatch != null && getItem(pos) != bestMatch;
                row.setTextColor(getColorStateList(dim ? R.color.item_text_dim : R.color.item_text));
                return row;
            }
        };
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
        suggestionsTop = findViewById(R.id.suggestions_top);
        suggestionsBottom = findViewById(R.id.suggestions_bottom);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applySearch(true);
                renderTop(); // hidden while searching
            }
        });
        // Enter opens the best app, else the first contact, else searches the web.
        search.setOnEditorActionListener((v, action, event) -> {
            if (bestMatch != null) launch(bestMatch);
            else if (!contactHits.isEmpty()) openContact(contactHits.get(0));
            else if (webOffered) webSearch(search.getText().toString().trim());
            return true;
        });

        // Android 13+ delivers Back here; older versions call onBackPressed().
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
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
        back();
    }

    // Back folds an open group first, then closes the app list.
    private void back() {
        if (!drawerOpen && progress == 0 && arranging) stopArranging();
        else if (!drawerOpen && progress == 0 && openGroup != null) closeGroup();
        else animateTo(false);
    }

    // After an update: what's new (once). Otherwise: limits without the grants they need do nothing; say so once.
    @Override
    protected void onResume() {
        super.onResume();
        if (!WhatsNew.showAfterUpdate(this)) Grants.warnOnceFromHome(this); // one dialog at a time
    }

    // Home pressed while already home.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        stopArranging();
        closeGroup();
        animateTo(false);
    }

    // Coming back from an app always lands on the home screen, without replaying the animation.
    @Override
    protected void onStop() {
        super.onStop();
        if (settle != null) settle.cancel();
        drawerOpen = false;
        hideKeyboard();
        setDrawerProgress(0);
        stopArranging();
        closeGroup();
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
            shadePulled = false;
            // An unfolded group can make the home list scroll; then it keeps the swipes it can use.
            boolean onHomeList = !drawerOpen && progress == 0 && contains(favoritesScroll, ev);
            homeCanScrollUp = onHomeList && favoritesScroll.canScrollVertically(-1);
            homeCanScrollDown = onHomeList && favoritesScroll.canScrollVertically(1);
            velocity.clear();
        }
        velocity.addMovement(ev);
        float dy = ev.getY() - downY;

        // Down on the home screen pulls the notification shade, as on Pixel phones. The rest of the gesture is ours.
        if (shadePulled) return true;
        if (!dragging && !drawerOpen && progress == 0 && action == MotionEvent.ACTION_MOVE
                && dy > touchSlop && dy > Math.abs(ev.getX() - downX) && !homeCanScrollUp && prefs.getBoolean("shade", true)) {
            shadePulled = true;
            cancelChildTouch(ev);
            expandNotifications();
            return true;
        }

        if (!dragging && action == MotionEvent.ACTION_MOVE
                && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(ev.getX() - downX)
                && (drawerOpen ? dy > 0 && listAtTop : dy < 0 && !homeCanScrollDown)) {
            dragging = true;
            downY = ev.getY(); // track from here so the content does not jump by the slop
            dy = 0;
            if (settle != null) settle.cancel();
            if (drawerOpen) hideKeyboard();
            cancelChildTouch(ev);
        }
        if (!dragging) return super.dispatchTouchEvent(ev);

        setDrawerProgress(Math.max(0, Math.min(1, downProgress - dy / dragDistance)));
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            velocity.computeCurrentVelocity(1000);
            float vy = velocity.getYVelocity();
            animateTo(Math.abs(vy) > flingVelocity ? vy < 0 : progress > 0.5f);
        }
        return true;
    }

    private static boolean contains(View view, MotionEvent ev) {
        int[] at = new int[2];
        view.getLocationInWindow(at);
        return ev.getX() >= at[0] && ev.getX() < at[0] + view.getWidth()
                && ev.getY() >= at[1] && ev.getY() < at[1] + view.getHeight();
    }

    // Children drop this touch, so no tap, long press or scroll fires.
    private void cancelChildTouch(MotionEvent ev) {
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    // Not public API, but what launchers use; needs EXPAND_STATUS_BAR, which is granted at install without a prompt.
    private void expandNotifications() {
        try {
            Object statusBar = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar);
        } catch (ReflectiveOperationException | RuntimeException ignored) { // a device that blocks it: the swipe does nothing
        }
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
        settle.addUpdateListener(a -> setDrawerProgress((float) a.getAnimatedValue()));
        settle.start();
    }

    // Fade-through: home fades out and recedes first, then the list fades in while rising, so texts never overlap.
    // Not named setProgress: Activity already has a setProgress(int), and setProgress(0) silently called that one.
    private void setDrawerProgress(float p) {
        if (p > 0 && drawer.getVisibility() != View.VISIBLE) {
            renderTop(); // counts changed since the last open
            list.setSelection(0);
        }
        progress = p;
        if (p == 1) { // hidden behind the list now; tidy home for the return
            closeGroup();
            stopArranging();
        }
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
        List<App> shown = new ArrayList<>(), later = new ArrayList<>();
        for (App app : all) {
            int rank = Search.rank(app.label, query);
            if (rank == Search.START) shown.add(app);
            else if (rank == Search.WORD) later.add(app);
        }
        shown.addAll(later);
        boolean searching = !query.trim().isEmpty();
        bestMatch = searching && !shown.isEmpty() ? shown.get(0) : null;
        boolean fromBottom = searching && prefs.getBoolean("bottom", false);
        if (fromBottom) Collections.reverse(shown);
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(shown);
        list.setStackFromBottom(fromBottom);
        adapter.notifyDataSetChanged();
        if (jump) list.setSelection(fromBottom ? list.getCount() - 1 : 0);
        renderSuggestions(query, searching);
    }

    // Contacts (optional) and, when nothing matches, a web search, on the side away from the app results:
    // at the top when apps stack up from the search field, otherwise right above the search field. The row
    // Enter would take is white, the others grey; above the search field it is the lowest one.
    private void renderSuggestions(String query, boolean searching) {
        suggestionsTop.removeAllViews();
        suggestionsBottom.removeAllViews();
        contactHits = searching ? ContactSearch.find(this, query, prefs.getBoolean("contacts", false)) : new ArrayList<>();
        webOffered = searching && bestMatch == null && contactHits.isEmpty() && prefs.getBoolean("web", true);
        boolean atTop = prefs.getBoolean("bottom", false);
        LinearLayout box = atTop ? suggestionsTop : suggestionsBottom;
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < contactHits.size(); i++) {
            ContactSearch.Contact contact = contactHits.get(i);
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item, box, false);
            SpannableStringBuilder text = new SpannableStringBuilder(contact.name).append("  ");
            int start = text.length();
            text.append("Kontakt");
            text.setSpan(new ForegroundColorSpan(0xFF6B6B6B), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.6f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(text);
            boolean target = bestMatch == null && i == 0;
            row.setTextColor(getColorStateList(target ? R.color.item_text : R.color.item_text_dim));
            row.setOnClickListener(v -> openContact(contact));
            rows.add(row);
        }
        if (webOffered) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item, box, false);
            SpannableStringBuilder text = new SpannableStringBuilder("„" + query.trim() + "“  ");
            int start = text.length();
            text.append("im Web suchen");
            text.setSpan(new ForegroundColorSpan(0xFF6B6B6B), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.6f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(text);
            row.setMaxLines(2); // long queries wrap instead of losing their end
            row.setOnClickListener(v -> webSearch(query.trim()));
            rows.add(row);
        }
        if (!atTop) Collections.reverse(rows); // the Enter target sits closest to the search field
        for (View row : rows) box.addView(row);
        suggestionsTop.setVisibility(suggestionsTop.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        suggestionsBottom.setVisibility(suggestionsBottom.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void openContact(ContactSearch.Contact contact) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, contact.uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Kontakt lässt sich nicht öffnen", Toast.LENGTH_SHORT).show();
        }
    }

    // In Chrome if it is installed, otherwise in the default browser.
    private void webSearch(String query) {
        Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome"));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException none) {
                Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Keys of uninstalled apps stay saved but are skipped, so a pin survives app updates and reinstalls.
    private void renderHome() {
        favoritesView.removeAllViews();
        openFirst = openLast = null;
        if (arranging) renderArranging();
        else for (String key : favorites) {
            if (Groups.isKey(key)) {
                Groups.Group group = groups.get(Groups.idOf(key));
                if (group == null) continue;
                TextView header = groupRow(group);
                favoritesView.addView(header);
                if (group.id.equals(openGroup)) {
                    openFirst = header;
                    addGroupApps(group);
                    openLast = favoritesView.getChildAt(favoritesView.getChildCount() - 1);
                }
                continue;
            }
            App app = apps.get(key);
            if (app == null) continue;
            TextView row = row(favoritesView, app);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            favoritesView.addView(row);
        }
        hint.setVisibility(homeEntries() == 0 ? View.VISIBLE : View.GONE);
        bindIcon(left, apps.get(prefs.getString("left", "")));
        bindIcon(right, apps.get(prefs.getString("right", "")));
    }

    // Pinned apps that are installed plus groups: what counts against MAX_FAVORITES.
    private int homeEntries() {
        int n = 0;
        for (String key : favorites) {
            if (Groups.isKey(key) ? groups.get(Groups.idOf(key)) != null : apps.containsKey(key)) n++;
        }
        return n;
    }

    // Group name like a pinned app, with the number of its apps in small grey; tap folds it open below.
    private TextView groupRow(Groups.Group group) {
        List<App> members = members(group);
        TextView row = (TextView) getLayoutInflater().inflate(R.layout.item, favoritesView, false);
        row.setText(groupText(group));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        row.setContentDescription("Gruppe " + group.name + ", " + members.size() + " Apps");
        row.setOnClickListener(v -> toggleGroup(group));
        row.setOnLongClickListener(v -> { showGroupMenu(v, group); return true; });
        return row;
    }

    private CharSequence groupText(Groups.Group group) {
        SpannableStringBuilder text = new SpannableStringBuilder(group.name).append("  ");
        int start = text.length();
        text.append(String.valueOf(members(group).size()));
        text.setSpan(new ForegroundColorSpan(0xFF6B6B6B), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.6f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    // --- Arranging the home screen: each entry gets up and down arrows; "Fertig" or Back ends it. ---

    // Favorites entries that show on home (installed apps and existing groups), in order.
    private List<String> homeKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : favorites) {
            if (Groups.isKey(key) ? groups.get(Groups.idOf(key)) != null : apps.containsKey(key)) keys.add(key);
        }
        return keys;
    }

    private void startArranging() {
        openGroup = null;
        arranging = true;
        if (drawerOpen) animateTo(false);
        renderHome();
    }

    private void stopArranging() {
        if (!arranging) return;
        arranging = false;
        renderHome();
    }

    private void renderArranging() {
        List<String> keys = homeKeys();
        int arrow = Math.round(48 * density), end = Math.round(16 * density);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(0, 0, end, 0);
            TextView label = (TextView) getLayoutInflater().inflate(R.layout.item, row, false);
            label.setText(Groups.isKey(key) ? groupText(groups.get(Groups.idOf(key))) : apps.get(key).label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            label.setPaddingRelative(label.getPaddingStart(), label.getPaddingTop(), 0, label.getPaddingBottom());
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(arrowButton(R.drawable.ic_up, "Nach oben", i > 0, () -> move(key, -1)), new LinearLayout.LayoutParams(arrow, arrow));
            row.addView(arrowButton(R.drawable.ic_down, "Nach unten", i < keys.size() - 1, () -> move(key, 1)), new LinearLayout.LayoutParams(arrow, arrow));
            favoritesView.addView(row);
        }
        TextView done = (TextView) getLayoutInflater().inflate(R.layout.item, favoritesView, false);
        done.setId(R.id.arrange_done);
        done.setText("Fertig");
        done.setFontVariationSettings("'wght' 500");
        done.setOnClickListener(v -> stopArranging());
        favoritesView.addView(done);
    }

    private ImageView arrowButton(int icon, String description, boolean enabled, Runnable action) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(0xFFB3B3B3, PorterDuff.Mode.SRC_IN);
        int pad = Math.round(12 * density);
        b.setPadding(pad, pad, pad, pad);
        b.setContentDescription(description);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.2f);
        if (enabled) b.setOnClickListener(v -> action.run());
        return b;
    }

    // Swaps the entry with its visible neighbour; uninstalled pins in between keep their place.
    private void move(String key, int direction) {
        List<String> keys = homeKeys();
        int i = keys.indexOf(key), j = i + direction;
        if (i < 0 || j < 0 || j >= keys.size()) return;
        Collections.swap(favorites, favorites.indexOf(key), favorites.indexOf(keys.get(j)));
        saveHome();
    }

    // Indented and smaller under the group header, alphabetical.
    private void addGroupApps(Groups.Group group) {
        int indent = Math.round(56 * density), pad = Math.round(32 * density);
        List<App> members = members(group);
        for (App app : members) {
            TextView row = row(favoritesView, app);
            row.setPaddingRelative(indent, Math.round(8 * density), pad, Math.round(8 * density));
            row.setOnLongClickListener(v -> { showMenu(v, app, group); return true; });
            favoritesView.addView(row);
        }
        if (members.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Leer. Lange auf die Gruppe drücken, um Apps auszuwählen.");
            empty.setTextColor(0xFF8C8C8C);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setPaddingRelative(indent, Math.round(6 * density), pad, Math.round(10 * density));
            favoritesView.addView(empty);
        }
    }

    // Installed apps of a group, alphabetical; uninstalled ones stay saved, like pins.
    private List<App> members(Groups.Group group) {
        List<App> out = new ArrayList<>();
        for (String key : group.apps) {
            App app = apps.get(key);
            if (app != null) out.add(app);
        }
        out.sort((a, b) -> collator.compare(a.label, b.label));
        return out;
    }

    private void toggleGroup(Groups.Group group) {
        openGroup = group.id.equals(openGroup) ? null : group.id;
        renderHome();
        if (openFirst == null) return;
        favoritesScroll.post(() -> { // after layout: show the whole group, header first if it is taller than the space
            if (openFirst == null) return;
            Rect rect = new Rect(0, openFirst.getTop(), favoritesView.getWidth(), openLast.getBottom());
            if (rect.height() > favoritesScroll.getHeight()) rect.bottom = rect.top + favoritesScroll.getHeight();
            favoritesScroll.requestChildRectangleOnScreen(favoritesView, rect, false);
        });
    }

    private void closeGroup() {
        if (openGroup == null) return;
        openGroup = null;
        renderHome();
        favoritesScroll.scrollTo(0, 0);
    }

    private void saveHome() {
        prefs.edit()
                .putString("favorites", String.join("\n", favorites))
                .putString("groups", groups.serialize())
                .apply();
        renderHome();
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
        showMenu(anchor, app, null);
    }

    // inGroup: the open group the app was long-pressed in, or null.
    private void showMenu(View anchor, App app, Groups.Group inGroup) {
        boolean pinned = favorites.contains(app.key);
        boolean isLeft = app.key.equals(prefs.getString("left", ""));
        boolean isRight = app.key.equals(prefs.getString("right", ""));
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        if (inGroup != null) menu.add(0, 7, 0, "Aus „" + inGroup.name + "“ entfernen");
        menu.add(0, 1, 0, pinned ? "Vom Startscreen entfernen" : "Auf den Startscreen")
                .setEnabled(pinned || homeEntries() < MAX_FAVORITES);
        menu.add(0, 6, 0, "Zu Gruppe hinzufügen…");
        if (pinned && homeEntries() > 1) menu.add(0, 9, 0, "Startscreen anordnen");
        menu.add(0, 2, 0, isLeft ? "Unten links entfernen" : "Unten links");
        menu.add(0, 3, 0, isRight ? "Unten rechts entfernen" : "Unten rechts");
        menu.add(0, 8, 0, "Zeitlimit…");
        menu.add(0, 4, 0, "App-Info");
        if ((app.info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) menu.add(0, 5, 0, "Deinstallieren");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (pinned) favorites.remove(app.key); else favorites.add(app.key);
                    saveHome();
                    break;
                case 6:
                    chooseGroup(app);
                    break;
                case 7:
                    inGroup.apps.remove(app.key);
                    saveHome();
                    break;
                case 8:
                    LimitActivity.open(this, app.info.getComponentName().getPackageName(), app.label);
                    break;
                case 9:
                    startArranging();
                    return true;
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

    private void showGroupMenu(View anchor, Groups.Group group) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, "Apps auswählen…");
        menu.add(0, 2, 0, "Umbenennen");
        menu.add(0, 3, 0, "Gruppe löschen");
        if (homeEntries() > 1) menu.add(0, 4, 0, "Startscreen anordnen");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: selectApps(group); break;
                case 2: askName("Gruppe umbenennen", group.name, "Speichern", name -> { group.name = name; saveHome(); }); break;
                case 3: confirmDelete(group); break;
                case 4: startArranging(); break;
            }
            return true;
        });
        popup.show();
    }

    // Long-press menu → "Zu Gruppe hinzufügen…": the groups the app is not in yet, plus a new one.
    private void chooseGroup(App app) {
        List<Groups.Group> options = new ArrayList<>();
        for (Groups.Group g : groups.all()) {
            if (!g.apps.contains(app.key) && favorites.contains(Groups.key(g.id))) options.add(g);
        }
        String[] items = new String[options.size() + 1];
        for (int i = 0; i < options.size(); i++) items[i] = options.get(i).name;
        items[options.size()] = "Neue Gruppe…";
        new AlertDialog.Builder(this)
                .setTitle(app.label + " hinzufügen zu")
                .setItems(items, (d, which) -> {
                    if (which < options.size()) {
                        options.get(which).apps.add(app.key);
                        saveHome();
                        Toast.makeText(this, "Zu „" + options.get(which).name + "“ hinzugefügt", Toast.LENGTH_SHORT).show();
                    } else {
                        newGroup(app);
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // A new group lands on the home screen, so it needs a free slot there.
    private void newGroup(App first) {
        if (homeEntries() >= MAX_FAVORITES) {
            Toast.makeText(this, "Der Startscreen ist voll. Entferne zuerst eine App oder Gruppe.", Toast.LENGTH_LONG).show();
            return;
        }
        askName("Neue Gruppe", "", "Erstellen", name -> {
            Groups.Group group = groups.create(name);
            if (first != null) group.apps.add(first.key);
            favorites.add(Groups.key(group.id));
            saveHome();
            Toast.makeText(this, "Gruppe „" + group.name + "“ ist auf dem Startscreen", Toast.LENGTH_SHORT).show();
        });
    }

    // Checklist of all apps; the fastest way to fill or tidy a group.
    private void selectApps(Groups.Group group) {
        List<App> all = new ArrayList<>(apps.values());
        all.sort((a, b) -> collator.compare(a.label, b.label));
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            checked[i] = group.apps.contains(all.get(i).key);
        }
        new AlertDialog.Builder(this)
                .setTitle(group.name)
                .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Fertig", (d, which) -> {
                    group.apps.removeIf(apps::containsKey); // keeps saved keys of apps that are not installed right now
                    for (int i = 0; i < all.size(); i++) if (checked[i]) group.apps.add(all.get(i).key);
                    openGroup = group.id;
                    saveHome();
                })
                .show();
    }

    private void confirmDelete(Groups.Group group) {
        new AlertDialog.Builder(this)
                .setTitle("„" + group.name + "“ löschen?")
                .setMessage("Nur die Gruppe verschwindet. Die Apps bleiben in der Liste und an ihren anderen Plätzen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, which) -> {
                    favorites.remove(Groups.key(group.id));
                    groups.delete(group.id);
                    if (group.id.equals(openGroup)) openGroup = null;
                    saveHome();
                })
                .show();
    }

    private void askName(String title, String initial, String action, Consumer<String> done) {
        EditText input = new EditText(this);
        input.setId(R.id.group_name);
        input.setText(initial);
        input.setSelection(initial.length());
        input.setHint("Name");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF6B6B6B);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(24 * density);
        box.setPaddingRelative(pad, Math.round(8 * density), pad, 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(action, (d, which) -> done.accept(Groups.cleanName(input.getText().toString())))
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void launch(App app) {
        // Also checked by BlockService in strict mode; this covers launches from Birdy without it.
        String pkg = app.info.getComponentName().getPackageName();
        Limits.Block block = Usage.check(this, Usage.load(this).get(pkg));
        if (block != null) {
            if (block.ask) SessionActivity.show(this, pkg, block); else BlockActivity.show(this, pkg, block);
            return;
        }
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
