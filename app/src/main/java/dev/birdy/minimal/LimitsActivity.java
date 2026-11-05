package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The time limits settings page: all apps with limits (tap to edit), adding one, and strict mode's grants.
public class LimitsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.limits);
        findViewById(R.id.limits_add).setOnClickListener(v -> pickApp());
    }

    @Override
    protected void onResume() { // back from an app's limits or a settings page
        super.onResume();
        render();
        LimitActivity.bindStrictStatus(this, findViewById(android.R.id.content));
    }

    // One row per app with limits: name, then what is set.
    private void render() {
        LinearLayout list = findViewById(R.id.limits_list);
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        for (Limits.AppLimit a : Usage.load(this).all()) {
            String label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(a.pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                continue; // not installed right now; the limit stays saved
            }
            TextView row = new TextView(this, null, 0, R.style.Birdy_Line);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
            row.setLayoutParams(lp);
            android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(label).append('\n');
            int start = text.length();
            text.append(a.summary());
            text.setSpan(new android.text.style.ForegroundColorSpan(0xFF8C8C8C), start, text.length(), 0);
            text.setSpan(new android.text.style.RelativeSizeSpan(0.82f), start, text.length(), 0);
            row.setText(text);
            row.setTextColor(getColorStateList(R.color.item_text));
            row.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            row.setOnClickListener(v -> LimitActivity.open(this, a.pkg, label));
            list.addView(row);
        }
        findViewById(R.id.limits_empty).setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // All apps with a launcher entry, alphabetical, except Birdy itself; a search field on top narrows them,
    // names starting with the query first (as in the app list), and Enter takes the first.
    private void pickApp() {
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (LauncherActivityInfo info : getSystemService(LauncherApps.class).getActivityList(null, Process.myUserHandle())) {
            String pkg = info.getComponentName().getPackageName();
            if (!pkg.equals(getPackageName())) byPkg.putIfAbsent(pkg, info.getLabel().toString());
        }
        List<Map.Entry<String, String>> apps = new ArrayList<>(byPkg.entrySet());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.getValue(), b.getValue()));

        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        EditText search = new EditText(this);
        search.setId(R.id.app_search);
        search.setHint("App suchen");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0xFF6B6B6B);

        List<Map.Entry<String, String>> shown = new ArrayList<>(apps);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                View row = super.getView(pos, convert, parent);
                row.setPaddingRelative(pad, row.getPaddingTop(), pad, row.getPaddingBottom());
                return row;
            }
        };
        ListView list = new ListView(this);
        list.setId(R.id.app_list);
        list.setDivider(null);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String q = search.getText().toString();
            List<Map.Entry<String, String>> later = new ArrayList<>();
            shown.clear();
            for (Map.Entry<String, String> app : apps) {
                int rank = Search.rank(app.getValue(), q);
                if (rank == Search.START) shown.add(app);
                else if (rank == Search.WORD) later.add(app);
            }
            shown.addAll(later);
            adapter.clear();
            for (Map.Entry<String, String> app : shown) adapter.add(app.getValue());
        };
        filter.run();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        field.setMarginStart(pad);
        field.setMarginEnd(pad);
        field.topMargin = Math.round(8 * density);
        box.addView(search, field);
        box.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(360 * density)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("App auswählen")
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .create();
        list.setOnItemClickListener((parent, view, pos, id) -> {
            dialog.dismiss();
            LimitActivity.open(this, shown.get(pos).getKey(), shown.get(pos).getValue());
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filter.run(); }
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (!shown.isEmpty()) {
                dialog.dismiss();
                LimitActivity.open(this, shown.get(0).getKey(), shown.get(0).getValue());
            }
            return true;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
        search.requestFocus();
    }
}
