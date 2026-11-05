package dev.birdy.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "Neu in Birdy 1.4" once after an update. The text is this version's CHANGELOG section, built into the app as
// assets/whatsnew.txt (see app/build.gradle.kts): "### Neu" headings and "- **Titel:** Text" bullets. The popup
// shows each heading as a small grey label and each bullet as a white title with its grey explanation below.
// A fresh install shows nothing, it only remembers the version.
final class WhatsNew {
    static final String SEEN = "seenVersion";

    interface Source {
        String text(Context context);
    }

    static final Source ASSET = WhatsNew::asset;
    static Source source = ASSET; // replaced in tests

    static final class Item {
        final String title, detail; // detail may be empty

        Item(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    static final class Section {
        final String name; // "Neu", "Verbessert", ... or "" for bullets before any heading
        final List<Item> items = new ArrayList<>();

        Section(String name) { this.name = name; }
    }

    private static final Pattern LEAD = Pattern.compile("^\\*\\*(.+?)\\*\\*\\s*(.*)$");

    private WhatsNew() {}

    // Returns true when the popup is shown, so the caller can hold back other dialogs.
    static boolean showAfterUpdate(Activity activity) {
        PackageInfo info;
        try {
            info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        @SuppressWarnings("deprecation")
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        long seen = prefs.getLong(SEEN, -1);
        if (seen >= code) return false;
        prefs.edit().putLong(SEEN, code).apply();
        boolean updated = seen >= 0 || info.lastUpdateTime > info.firstInstallTime; // also updates from before this popup existed
        List<Section> sections = parse(source.text(activity));
        if (!updated || sections.isEmpty() || "dev".equals(info.versionName)) return false;
        new AlertDialog.Builder(activity)
                .setTitle("Neu in Birdy " + info.versionName)
                .setView(content(activity, sections))
                .setPositiveButton("Schön", null)
                .show();
        return true;
    }

    // "### Name" starts a section, "- " a bullet, other lines continue the bullet before. "**Titel:** Text" splits
    // into title and detail; a bullet without a bold lead is all title. Markdown marks do not show.
    static List<Section> parse(String text) {
        List<Section> out = new ArrayList<>();
        Section current = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("### ")) {
                current = new Section(line.substring(4).trim());
                out.add(current);
                continue;
            }
            if (current == null) {
                current = new Section("");
                out.add(current);
            }
            if (line.startsWith("- ") || line.startsWith("• ")) {
                String body = line.substring(2).trim();
                Matcher m = LEAD.matcher(body);
                String title = m.matches() ? m.group(1).trim() : body, detail = m.matches() ? m.group(2).trim() : "";
                title = title.replaceAll("[:.]+$", "").trim();
                current.items.add(new Item(clean(title), clean(detail)));
            } else if (!current.items.isEmpty()) {
                Item last = current.items.remove(current.items.size() - 1);
                String detail = last.detail.isEmpty() ? clean(line) : last.detail + " " + clean(line);
                current.items.add(new Item(last.title, detail));
            } else {
                current.items.add(new Item(clean(line), ""));
            }
        }
        out.removeIf(s -> s.items.isEmpty());
        return out;
    }

    private static String clean(String s) {
        return s.replace("**", "").replace("`", "").trim();
    }

    private static ScrollView content(Context context, List<Section> sections) {
        float dp = context.getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(context);
        list.setId(R.id.whatsnew_list);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPaddingRelative(Math.round(24 * dp), Math.round(4 * dp), Math.round(24 * dp), Math.round(8 * dp));
        boolean first = true;
        for (Section section : sections) {
            if (!section.name.isEmpty()) {
                TextView label = new TextView(context);
                label.setText(section.name);
                label.setAllCaps(true);
                label.setLetterSpacing(0.18f);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                label.setTextColor(0xFF8C8C8C);
                label.setFontVariationSettings("'wght' 500");
                label.setPaddingRelative(0, Math.round((first ? 4 : 22) * dp), 0, Math.round(2 * dp));
                list.addView(label);
            }
            first = false;
            for (Item item : section.items) {
                TextView title = new TextView(context);
                title.setText(item.title);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                title.setTextColor(0xFFFFFFFF);
                title.setFontVariationSettings("'wght' 500");
                title.setPaddingRelative(0, Math.round(12 * dp), 0, 0);
                list.addView(title);
                if (item.detail.isEmpty()) continue;
                TextView detail = new TextView(context);
                detail.setText(item.detail);
                detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                detail.setTextColor(0xFFA6A6A6);
                detail.setLineSpacing(2 * dp, 1f);
                detail.setFontVariationSettings("'wght' 350");
                detail.setPaddingRelative(0, Math.round(2 * dp), 0, 0);
                list.addView(detail);
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        return scroll;
    }

    private static String asset(Context context) {
        try (InputStream in = context.getAssets().open("whatsnew.txt")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }
}
