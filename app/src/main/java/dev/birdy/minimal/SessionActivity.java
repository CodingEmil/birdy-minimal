package dev.birdy.minimal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// Asked before an app with sessions opens: how long this visit should be. Picking a length starts the session
// and opens the app; when it is over, BlockService covers the app again (and holds the pause, if one is set).
public class SessionActivity extends Activity {
    static final int[] LENGTHS = {5, 10, 15, 30};

    private String pkg;

    static void show(Context context, String pkg, Limits.Block block) {
        context.startActivity(new Intent(context, SessionActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.PKG, pkg)
                .putExtra(BlockActivity.TITLE, block.title)
                .putExtra(BlockActivity.DETAIL, block.detail));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.session);
        findViewById(R.id.session_cancel).setOnClickListener(v -> goHome());
        bind(getIntent());
        BlockActivity.reveal(findViewById(R.id.session_content)); // only when it appears
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        pkg = intent.getStringExtra(BlockActivity.PKG);
        Limits.AppLimit limit = Usage.load(this).get(pkg);
        if (limit == null) { // limits removed meanwhile: nothing to ask
            open();
            return;
        }
        String label = label(pkg);
        ((TextView) findViewById(R.id.session_title)).setText(intent.getStringExtra(BlockActivity.TITLE));
        ((TextView) findViewById(R.id.session_app)).setText(label);
        ((TextView) findViewById(R.id.session_question)).setText(intent.getStringExtra(BlockActivity.DETAIL));

        int left = Integer.MAX_VALUE; // minutes left today, if a daily limit counts
        List<String> notes = new ArrayList<>();
        if (limit.dailyMinutes > 0 && Usage.hasUsageAccess(this)) {
            left = (int) Math.max(1, limit.dailyMinutes - Usage.todayMs(this, pkg) / Limits.MINUTE);
            notes.add("Heute noch " + Limits.duration(left) + ".");
        }
        if (limit.pauseMinutes > 0) notes.add("Danach ist " + label + " " + Limits.duration(limit.pauseMinutes) + " gesperrt.");
        TextView note = findViewById(R.id.session_note);
        note.setText(String.join(" ", notes));
        note.setVisibility(notes.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

        LinearLayout choices = findViewById(R.id.session_choices);
        choices.removeAllViews();
        List<Integer> lengths = new ArrayList<>();
        for (int m : LENGTHS) if (m <= left) lengths.add(m);
        if (left < LENGTHS[LENGTHS.length - 1] && !lengths.contains(left)) lengths.add(left); // the rest of today
        float dp = getResources().getDisplayMetrics().density;
        int gap = Math.round(12 * dp);
        LinearLayout row = null;
        for (int i = 0; i < lengths.size(); i++) {
            int minutes = lengths.get(i);
            if (i % 2 == 0) { // two tiles per row; an odd last one takes the whole row
                row = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(84 * dp));
                if (i > 0) lp.topMargin = gap;
                choices.addView(row, lp);
            }
            TextView tile = tile(minutes);
            tile.setOnClickListener(v -> {
                Usage.startSession(this, limit, minutes);
                open();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            if (i % 2 == 1) lp.setMarginStart(gap);
            row.addView(tile, lp);
        }
    }

    // "10 min" with a big thin number and a small grey unit; "1 h 30 min" keeps its parts the same way.
    private TextView tile(int minutes) {
        String text = Limits.duration(minutes);
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        java.util.regex.Matcher unit = java.util.regex.Pattern.compile("[^0-9\\s]+").matcher(text);
        while (unit.find()) {
            s.setSpan(new RelativeSizeSpan(0.42f), unit.start(), unit.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(0xFF8C8C8C), unit.start(), unit.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView tile = new TextView(this);
        tile.setText(s);
        tile.setContentDescription(text);
        tile.setGravity(Gravity.CENTER);
        tile.setBackgroundResource(R.drawable.tile);
        tile.setTextColor(0xFFFFFFFF);
        tile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        tile.setFontVariationSettings("'wght' 300");
        tile.setFontFeatureSettings("tnum");
        tile.setIncludeFontPadding(false);
        return tile;
    }

    private void open() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
