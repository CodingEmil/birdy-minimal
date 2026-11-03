package dev.birdy.minimal;

import java.util.Locale;

// App search, case-insensitive. A label whose start matches beats one where only a later word matches,
// so "s" puts Signal and Spotify before Play Store.
final class Search {
    static final int NONE = -1, START = 0, WORD = 1;

    private Search() {}

    static int rank(String label, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return START;
        String l = label.toLowerCase(Locale.ROOT);
        if (l.startsWith(q)) return START;
        for (int i = 0; i < l.length() - 1; i++) {
            if (!Character.isLetterOrDigit(l.charAt(i)) && l.startsWith(q, i + 1)) return WORD;
        }
        return NONE;
    }
}
