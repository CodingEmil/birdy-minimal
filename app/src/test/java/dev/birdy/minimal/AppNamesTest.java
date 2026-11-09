package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class AppNamesTest {
    @Test
    public void roundTripsThroughTheSavedString() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("com.a/.Main", "Mail");
        names.put("com.b/.Main", "Fotos von Oma");
        assertEquals(names, AppNames.parse(AppNames.serialize(names)));
    }

    @Test
    public void emptyOrBrokenInputGivesNoNames() {
        assertTrue(AppNames.parse("").isEmpty());
        assertTrue(AppNames.parse("onlykey\n\tname\nkey\t").isEmpty());
    }

    @Test
    public void cleanKeepsTheSavedFormatIntact() {
        assertEquals("a b", AppNames.clean("  a\t\nb "));
        assertEquals("", AppNames.clean(" \t "));
    }
}
