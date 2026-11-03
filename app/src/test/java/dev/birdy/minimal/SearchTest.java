package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SearchTest {
    @Test
    public void startOfLabelBeatsStartOfLaterWord() {
        assertEquals(Search.START, Search.rank("Google Maps", "goo"));
        assertEquals(Search.START, Search.rank("Spotify", "S"));
        assertEquals(Search.WORD, Search.rank("Play Store", "s"));
        assertEquals(Search.WORD, Search.rank("Google Maps", "MAP"));
        assertEquals(Search.WORD, Search.rank("K-9 Mail", "9"));
    }

    @Test
    public void blankQueryMatchesEverything() {
        assertEquals(Search.START, Search.rank("Anything", "  "));
    }

    @Test
    public void middleOfAWordDoesNotMatch() {
        assertEquals(Search.NONE, Search.rank("Google Maps", "aps"));
        assertEquals(Search.NONE, Search.rank("Kamera", "mera"));
    }
}
