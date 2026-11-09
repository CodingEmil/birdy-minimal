package dev.birdy.minimal;

import java.util.LinkedHashMap;
import java.util.Map;

// Names the user gave apps ("Umbenennen…"), by app key. Saved as one line per app: key TAB name.
// Keys of uninstalled apps stay saved, like pins, so a name survives a reinstall.
final class AppNames {
    static final String KEY = "names";

    private AppNames() {}

    static Map<String, String> parse(String saved) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String line : saved.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) names.put(parts[0], parts[1]);
        }
        return names;
    }

    static String serialize(Map<String, String> names) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (out.length() > 0) out.append('\n');
            out.append(e.getKey()).append('\t').append(e.getValue());
        }
        return out.toString();
    }

    // Tabs and line breaks would break the saved format.
    static String clean(String name) { return name.replaceAll("[\\t\\r\\n]+", " ").trim(); }
}
