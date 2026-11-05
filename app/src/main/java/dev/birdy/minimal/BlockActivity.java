package dev.birdy.minimal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

// Covers a blocked app. Leaving it always goes to the home screen, never back into the app.
public class BlockActivity extends Activity {
    static final String PKG = "pkg", TITLE = "title", DETAIL = "detail";

    static void show(Context context, String pkg, Limits.Block block) {
        context.startActivity(new Intent(context, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(PKG, pkg)
                .putExtra(TITLE, block.title)
                .putExtra(DETAIL, block.detail));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.block);
        findViewById(R.id.block_home).setOnClickListener(v -> goHome());
        bind(getIntent());
        reveal(findViewById(R.id.block_content)); // only when it appears, not when an open one gets new text
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        String pkg = intent.getStringExtra(PKG);
        ((TextView) findViewById(R.id.block_app)).setText(label(pkg));
        ((TextView) findViewById(R.id.block_title)).setText(intent.getStringExtra(TITLE));
        ((TextView) findViewById(R.id.block_detail)).setText(intent.getStringExtra(DETAIL));
    }

    // The content rises a little and fades in, so the screen arrives calmly instead of popping over the app.
    static void reveal(View content) {
        content.animate().cancel();
        content.setAlpha(0f);
        content.setTranslationY(16 * content.getResources().getDisplayMetrics().density);
        content.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private String label(String pkg) {
        if (pkg == null) return "";
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
