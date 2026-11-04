package dev.birdy.minimal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
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
        for (int minutes : lengths) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item, choices, false);
            row.setText(Limits.duration(minutes));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            row.setPaddingRelative(0, row.getPaddingTop(), 0, row.getPaddingBottom());
            row.setOnClickListener(v -> {
                Usage.startSession(this, limit, minutes);
                open();
            });
            choices.addView(row);
        }
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
