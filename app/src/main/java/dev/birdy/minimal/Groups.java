package dev.birdy.minimal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// App groups on the home screen. A group holds copies of app keys: the apps stay in the list, may sit in several
// groups and may be pinned on their own too. Saved as one line per group: id TAB name TAB key TAB key ...
// On the home screen a group is the favorites entry "group:<id>".
final class Groups {
    static final String PREFIX = "group:";

    static final class Group {
        final String id;
        String name;
        final List<String> apps = new ArrayList<>();

        Group(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final Map<String, Group> byId = new LinkedHashMap<>();

    static Groups parse(String saved) {
        Groups groups = new Groups();
        for (String line : saved.split("\n")) {
            String[] parts = line.split("\t");
            if (parts.length < 2 || parts[0].isEmpty()) continue;
            Group g = new Group(parts[0], parts[1]);
            for (int i = 2; i < parts.length; i++) if (!parts[i].isEmpty() && !g.apps.contains(parts[i])) g.apps.add(parts[i]);
            groups.byId.put(g.id, g);
        }
        return groups;
    }

    String serialize() {
        StringBuilder out = new StringBuilder();
        for (Group g : byId.values()) {
            if (out.length() > 0) out.append('\n');
            out.append(g.id).append('\t').append(g.name);
            for (String key : g.apps) out.append('\t').append(key);
        }
        return out.toString();
    }

    Group get(String id) { return byId.get(id); }

    Collection<Group> all() { return byId.values(); }

    Group create(String name) {
        int n = 1;
        while (byId.containsKey(String.valueOf(n))) n++;
        Group g = new Group(String.valueOf(n), cleanName(name));
        byId.put(g.id, g);
        return g;
    }

    void delete(String id) { byId.remove(id); }

    // Tabs and line breaks would break the saved format; an empty name gets a default.
    static String cleanName(String name) {
        String clean = name.replaceAll("[\\t\\r\\n]+", " ").trim();
        return clean.isEmpty() ? "Gruppe" : clean;
    }

    static boolean isKey(String favorite) { return favorite.startsWith(PREFIX); }

    static String key(String id) { return PREFIX + id; }

    static String idOf(String favorite) { return favorite.substring(PREFIX.length()); }
}
