package dev.birdy.minimal;

import java.util.Locale;

// App search: a query matches when a label, or any word in it, starts with the query (case-insensitive).
final class Search {
    private Search() {}

    static boolean matches(String label, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        String l = label.toLowerCase(Locale.ROOT);
        if (l.startsWith(q)) return true;
        for (int i = 0; i < l.length() - 1; i++) {
            if (!Character.isLetterOrDigit(l.charAt(i)) && l.startsWith(q, i + 1)) return true;
        }
        return false;
    }
}
