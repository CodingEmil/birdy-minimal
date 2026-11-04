package dev.birdy.minimal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Per-app limits as small, independent rules. A rule answers two questions: does it block right now, and how long
// until it could start blocking. New triggers (launch counts, weekdays, focus mode, ...) are new Rule classes;
// the enforcement in BlockService and MainActivity only ever talks to Rule.
//
// Saved as one line per app: package TAB daily minutes TAB morning window TAB evening window,
// a window being "start-end" in minutes of the day ("" when off). End before start runs past midnight.
final class Limits {
    static final long MINUTE = 60_000L, DAY = 24 * 60 * MINUTE;

    // Why an app is blocked, in words for the block screen.
    static final class Block {
        final String title, detail;

        Block(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }

    // usedMs: foreground time today; msOfDay: local time since midnight.
    interface Rule {
        Block check(long usedMs, long msOfDay);

        // Earliest time from now at which check() could start blocking, assuming the app stays in front.
        long msUntilBlock(long usedMs, long msOfDay);
    }

    static final class DailyLimit implements Rule {
        final int minutes;

        DailyLimit(int minutes) { this.minutes = minutes; }

        @Override public Block check(long usedMs, long msOfDay) {
            return usedMs >= minutes * MINUTE
                    ? new Block("Tageslimit erreicht", duration(minutes) + " für heute aufgebraucht. Morgen geht es wieder.")
                    : null;
        }

        @Override public long msUntilBlock(long usedMs, long msOfDay) {
            return Math.max(0, minutes * MINUTE - usedMs);
        }
    }

    static final class Window implements Rule {
        final String name; // "morgens", "abends"
        final int start, end; // minutes of the day

        Window(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        boolean contains(long msOfDay) {
            long m = msOfDay / MINUTE;
            return start <= end ? m >= start && m < end : m >= start || m < end;
        }

        @Override public Block check(long usedMs, long msOfDay) {
            return contains(msOfDay) ? new Block("Schonzeit " + name, "Gesperrt bis " + clock(end) + " Uhr.") : null;
        }

        @Override public long msUntilBlock(long usedMs, long msOfDay) {
            if (contains(msOfDay)) return 0;
            return Math.floorMod(start * MINUTE - msOfDay, DAY);
        }

        String spec() { return start + "-" + end; }

        static Window parse(String name, String spec) {
            String[] p = spec.split("-");
            if (p.length != 2) return null;
            try {
                int s = Integer.parseInt(p[0]), e = Integer.parseInt(p[1]);
                if (s < 0 || s >= 1440 || e < 0 || e >= 1440 || s == e) return null;
                return new Window(name, s, e);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    static final class AppLimit {
        final String pkg;
        int dailyMinutes; // 0 = no daily limit
        Window morning, evening; // null = off

        AppLimit(String pkg) { this.pkg = pkg; }

        List<Rule> rules() { // quiet times first: their message says when it opens again
            List<Rule> rules = new ArrayList<>();
            if (morning != null) rules.add(morning);
            if (evening != null) rules.add(evening);
            if (dailyMinutes > 0) rules.add(new DailyLimit(dailyMinutes));
            return rules;
        }

        boolean isEmpty() { return dailyMinutes <= 0 && morning == null && evening == null; }

        Block check(long usedMs, long msOfDay) {
            for (Rule rule : rules()) {
                Block b = rule.check(usedMs, msOfDay);
                if (b != null) return b;
            }
            return null;
        }

        long msUntilBlock(long usedMs, long msOfDay) {
            long next = Long.MAX_VALUE;
            for (Rule rule : rules()) next = Math.min(next, rule.msUntilBlock(usedMs, msOfDay));
            return next;
        }

        // Short summary for lists: "1 h · morgens 05:00–09:00 · abends 21:00–00:00".
        String summary() {
            List<String> parts = new ArrayList<>();
            if (dailyMinutes > 0) parts.add(duration(dailyMinutes) + " am Tag");
            if (morning != null) parts.add("morgens " + clock(morning.start) + "–" + clock(morning.end));
            if (evening != null) parts.add("abends " + clock(evening.start) + "–" + clock(evening.end));
            return String.join(" · ", parts);
        }
    }

    private final Map<String, AppLimit> byPkg = new LinkedHashMap<>();

    static Limits parse(String saved) {
        Limits limits = new Limits();
        for (String line : saved.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length < 4 || p[0].isEmpty()) continue;
            AppLimit a = new AppLimit(p[0]);
            try {
                a.dailyMinutes = Math.max(0, Integer.parseInt(p[1]));
            } catch (NumberFormatException e) {
                a.dailyMinutes = 0;
            }
            a.morning = p[2].isEmpty() ? null : Window.parse("morgens", p[2]);
            a.evening = p[3].isEmpty() ? null : Window.parse("abends", p[3]);
            if (!a.isEmpty()) limits.byPkg.put(a.pkg, a);
        }
        return limits;
    }

    String serialize() {
        StringBuilder out = new StringBuilder();
        for (AppLimit a : byPkg.values()) {
            if (a.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(a.pkg).append('\t').append(a.dailyMinutes).append('\t')
                    .append(a.morning == null ? "" : a.morning.spec()).append('\t')
                    .append(a.evening == null ? "" : a.evening.spec());
        }
        return out.toString();
    }

    AppLimit get(String pkg) { return byPkg.get(pkg); }

    AppLimit getOrCreate(String pkg) {
        AppLimit a = byPkg.get(pkg);
        if (a == null) byPkg.put(pkg, a = new AppLimit(pkg));
        return a;
    }

    List<AppLimit> all() {
        List<AppLimit> out = new ArrayList<>();
        for (AppLimit a : byPkg.values()) if (!a.isEmpty()) out.add(a);
        return out;
    }

    static String clock(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
