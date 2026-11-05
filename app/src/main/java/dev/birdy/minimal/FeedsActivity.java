package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;

// "Shorts & Reels": a switch per feed, under it the time a day before the block, and the accessibility service.
public class FeedsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings_feeds);
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        bindFeedSwitch(R.id.block_shorts, R.id.allow_shorts, Feeds.Feed.SHORTS, prefs);
        bindFeedSwitch(R.id.block_reels, R.id.allow_reels, Feeds.Feed.REELS, prefs);
        findViewById(R.id.feeds_setup).setOnClickListener(v -> Grants.discloseFeeds(this));
    }

    // Re-checked on every return, e.g. from the accessibility settings; today's minutes move on too.
    @Override
    protected void onResume() {
        super.onResume();
        renderFeedsSetup();
        renderAllowance(findViewById(R.id.allow_shorts), Feeds.Feed.SHORTS);
        renderAllowance(findViewById(R.id.allow_reels), Feeds.Feed.REELS);
    }

    // Turning a feed on without its accessibility service asks for that service right away.
    private void bindFeedSwitch(int id, int allowanceId, Feeds.Feed feed, SharedPreferences prefs) {
        Switch view = findViewById(id);
        TextView allowance = findViewById(allowanceId);
        view.setChecked(Feeds.blocked(this, feed));
        view.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(feed.pref, on).apply();
            renderFeedsSetup();
            renderAllowance(allowance, feed);
            if (on && !Feeds.serviceOn(this)) Grants.discloseFeeds(this);
        });
        allowance.setOnClickListener(v -> chooseAllowance(allowance, feed));
        renderAllowance(allowance, feed);
    }

    private void renderAllowance(TextView view, Feeds.Feed feed) {
        view.setVisibility(Feeds.blocked(this, feed) ? View.VISIBLE : View.GONE);
        int minutes = Feeds.allowedMinutes(this, feed);
        String text = "Pro Tag: " + Feeds.allowanceLabel(minutes);
        if (minutes > 0) text += " · heute " + Limits.duration((int) (Feeds.usedTodayMs(this, feed) / Feeds.MINUTE));
        view.setText(text + "  →");
    }

    // The usual times, and last "Eigene Zeit…" for any number of minutes.
    private void chooseAllowance(TextView view, Feeds.Feed feed) {
        int current = Feeds.allowedMinutes(this, feed);
        int presets = Feeds.ALLOWANCES.length;
        String[] labels = new String[presets + 1];
        int checked = presets; // a typed-in time
        for (int i = 0; i < presets; i++) {
            labels[i] = Feeds.ALLOWANCES[i] == 0 ? "Sofort sperren" : Limits.duration(Feeds.ALLOWANCES[i]) + " am Tag";
            if (Feeds.ALLOWANCES[i] == current) checked = i;
        }
        labels[presets] = checked == presets ? "Eigene Zeit: " + Limits.duration(current) + " am Tag" : "Eigene Zeit…";
        new AlertDialog.Builder(this)
                .setTitle(feed.label + " pro Tag")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == presets) {
                        askMinutes(view, feed, current);
                        return;
                    }
                    Feeds.setAllowedMinutes(this, feed, Feeds.ALLOWANCES[which]);
                    renderAllowance(view, feed);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void askMinutes(TextView view, Feeds.Feed feed, int current) {
        float dp = getResources().getDisplayMetrics().density;
        EditText input = new EditText(this);
        input.setId(R.id.allow_minutes);
        input.setHint("Minuten");
        if (current > 0) {
            input.setText(String.valueOf(current));
            input.setSelection(input.length());
        }
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF6B6B6B);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(24 * dp);
        box.setPaddingRelative(pad, Math.round(8 * dp), pad, 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Minuten pro Tag")
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, which) -> {
                    int minutes;
                    try {
                        minutes = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) { // empty or too long: keep what was set
                        return;
                    }
                    Feeds.setAllowedMinutes(this, feed, Math.max(0, Math.min(minutes, Feeds.MAX_MINUTES)));
                    renderAllowance(view, feed);
                })
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    // "Bedienungshilfe einschalten" shows while a feed is blocked but the service is off.
    private void renderFeedsSetup() {
        int visibility = Feeds.anyBlocked(this) && !Feeds.serviceOn(this) ? View.VISIBLE : View.GONE;
        findViewById(R.id.feeds_setup).setVisibility(visibility);
        findViewById(R.id.feeds_setup_hint).setVisibility(visibility);
    }
}
