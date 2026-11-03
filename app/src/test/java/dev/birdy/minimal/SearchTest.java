package dev.birdy.minimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchTest {
    @Test
    public void matchesStartOfLabelOrOfAnyWord() {
        assertTrue(Search.matches("Google Maps", "goo"));
        assertTrue(Search.matches("Google Maps", "MAP"));
        assertTrue(Search.matches("K-9 Mail", "9"));
        assertTrue(Search.matches("Anything", "  "));
        assertFalse(Search.matches("Google Maps", "aps"));
        assertFalse(Search.matches("Kamera", "mera"));
    }
}
