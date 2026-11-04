package dev.birdy.minimal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
