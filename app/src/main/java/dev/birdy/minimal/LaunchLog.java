package dev.birdy.minimal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Launch history for "most used": one "<millis> <component>" line per launch, kept only for a time window.
final class LaunchLog {
    private LaunchLog() {}

    // Adds a launch and drops lines that fell out of the window.
    static String append(String log, String key, long now, long window) {
        StringBuilder kept = new StringBuilder();
        for (String line : log.split("\n")) {
            if (inWindow(line, now, window)) kept.append(line).append('\n');
        }
        return kept.append(now).append(' ').append(key).toString();
    }

    // Keys by launch count within the window, most first; ties go to the more recent launch.
    static List<String> rank(String log, long now, long window) {
        Map<String, Integer> count = new HashMap<>(), last = new HashMap<>();
        String[] lines = log.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!inWindow(lines[i], now, window)) continue;
            String key = lines[i].substring(lines[i].indexOf(' ') + 1);
            count.merge(key, 1, Integer::sum);
            last.put(key, i);
        }
        List<String> keys = new ArrayList<>(count.keySet());
        keys.sort((a, b) -> {
            int byCount = Integer.compare(count.get(b), count.get(a));
            return byCount != 0 ? byCount : Integer.compare(last.get(b), last.get(a));
        });
        return keys;
    }

    private static boolean inWindow(String line, long now, long window) {
        int space = line.indexOf(' ');
        return space > 0 && now - Long.parseLong(line.substring(0, space)) < window;
    }
}
