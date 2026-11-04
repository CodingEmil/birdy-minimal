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
// Saved as one line per app: package TAB daily minutes TAB morning window TAB evening window TAB ask (1/0)
// TAB pause minutes; a window is "start-end" in minutes of the day ("" when off), end before start runs past
// midnight. Lines from before sessions existed have only the first four fields.
final class Limits {
    static final long MINUTE = 60_000L, DAY = 24 * 60 * MINUTE;

    // Why an app may not open right now, in words for the screen that covers it. ask: instead of a plain block,
    // ask how long this session should be (SessionActivity).
    static final class Block {
        final String title, detail;
        final boolean ask;

        Block(String title, String detail) { this(title, detail, false); }

        Block(String title, String detail, boolean ask) {
            this.title = title;
            this.detail = detail;
            this.ask = ask;
        }
    }

    // Everything a rule may look at: foreground time today, local time since midnight, wall clock, and the
    // current session (its planned end and the end of the pause after it; 0 when none).
    static final class Moment {
        final long usedMs, msOfDay, now, sessionEnd, pauseEnd;

        Moment(long usedMs, long msOfDay, long now, long sessionEnd, long pauseEnd) {
            this.usedMs = usedMs;
            this.msOfDay = msOfDay;
            this.now = now;
            this.sessionEnd = sessionEnd;
            this.pauseEnd = pauseEnd;
        }

        static Moment of(long usedMs, long msOfDay) { return new Moment(usedMs, msOfDay, msOfDay, 0, 0); }

        // Local clock time of a wall-clock instant today or tomorrow, as minutes of the day.
        int minuteOfDay(long epochMs) { return (int) (Math.floorMod(msOfDay + (epochMs - now), DAY) / MINUTE); }
    }

    interface Rule {
        Block check(Moment m);

        // Earliest time from now at which check() could start blocking, assuming the app stays in front.
        long msUntilBlock(Moment m);
    }

    static final class DailyLimit implements Rule {
        final int minutes;

        DailyLimit(int minutes) { this.minutes = minutes; }

        @Override public Block check(Moment m) {
            return m.usedMs >= minutes * MINUTE
                    ? new Block("Tageslimit erreicht", duration(minutes) + " für heute aufgebraucht. Morgen geht es wieder.")
                    : null;
        }

        @Override public long msUntilBlock(Moment m) {
            return Math.max(0, minutes * MINUTE - m.usedMs);
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

        @Override public Block check(Moment m) {
            return contains(m.msOfDay) ? new Block("Schonzeit " + name, "Gesperrt bis " + clock(end) + " Uhr.") : null;
        }

        @Override public long msUntilBlock(Moment m) {
            if (contains(m.msOfDay)) return 0;
            return Math.floorMod(start * MINUTE - m.msOfDay, DAY);
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

    // Open on purpose: each visit starts by choosing its length. When it is over the app is blocked, then
    // rests for pauseMinutes (whatever the daily limit says), then asks again.
    static final class Session implements Rule {
        final int pauseMinutes;

        Session(int pauseMinutes) { this.pauseMinutes = pauseMinutes; }

        @Override public Block check(Moment m) {
            if (m.now < m.sessionEnd) return null;
            if (m.now < m.pauseEnd) {
                return new Block("Pause", "Deine Session ist vorbei. Wieder frei ab " + clock(m.minuteOfDay(m.pauseEnd)) + " Uhr.");
            }
            return m.sessionEnd > 0 && m.now - m.sessionEnd < MINUTE
                    ? new Block("Deine Zeit ist um", "Noch eine Runde?", true)
                    : new Block("Bewusst öffnen", "Wie lange möchtest du jetzt hier sein?", true);
        }

        @Override public long msUntilBlock(Moment m) {
            return Math.max(0, m.sessionEnd - m.now);
        }
    }

    static final class AppLimit {
        final String pkg;
        int dailyMinutes; // 0 = no daily limit
        Window morning, evening; // null = off
        boolean ask; // ask for the session length before each visit
        int pauseMinutes; // rest after a session; 0 = none

        AppLimit(String pkg) { this.pkg = pkg; }

        // Hard blocks first (quiet times say when they open, the daily limit says "tomorrow"); the session last,
        // so it never asks for a length the other rules would not allow anyway.
        List<Rule> rules() {
            List<Rule> rules = new ArrayList<>();
            if (morning != null) rules.add(morning);
            if (evening != null) rules.add(evening);
            if (dailyMinutes > 0) rules.add(new DailyLimit(dailyMinutes));
            if (ask) rules.add(new Session(pauseMinutes));
            return rules;
        }

        boolean isEmpty() { return dailyMinutes <= 0 && morning == null && evening == null && !ask; }

        Block check(Moment m) {
            for (Rule rule : rules()) {
                Block b = rule.check(m);
                if (b != null) return b;
            }
            return null;
        }

        long msUntilBlock(Moment m) {
            long next = Long.MAX_VALUE;
            for (Rule rule : rules()) next = Math.min(next, rule.msUntilBlock(m));
            return next;
        }

        // Short summary for lists: "1 h · morgens 05:00–09:00 · abends 21:00–00:00".
        String summary() {
            List<String> parts = new ArrayList<>();
            if (dailyMinutes > 0) parts.add(duration(dailyMinutes) + " am Tag");
            if (morning != null) parts.add("morgens " + clock(morning.start) + "–" + clock(morning.end));
            if (evening != null) parts.add("abends " + clock(evening.start) + "–" + clock(evening.end));
            if (ask) parts.add(pauseMinutes > 0 ? "fragt, " + duration(pauseMinutes) + " Pause" : "fragt vor dem Öffnen");
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
            a.ask = p.length > 4 && "1".equals(p[4]);
            try {
                a.pauseMinutes = p.length > 5 ? Math.max(0, Integer.parseInt(p[5])) : 0;
            } catch (NumberFormatException e) {
                a.pauseMinutes = 0;
            }
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
                    .append(a.evening == null ? "" : a.evening.spec()).append('\t')
                    .append(a.ask ? "1" : "0").append('\t').append(a.pauseMinutes);
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
