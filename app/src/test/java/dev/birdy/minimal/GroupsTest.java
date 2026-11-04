package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class GroupsTest {
    @Test
    public void roundTripsThroughTheSavedString() {
        Groups groups = new Groups();
        Groups.Group work = groups.create("Arbeit");
        work.apps.add("com.a/.Main");
        work.apps.add("com.b/.Main");
        groups.create("Leer");
        Groups back = Groups.parse(groups.serialize());
        assertEquals("Arbeit", back.get(work.id).name);
        assertEquals(Arrays.asList("com.a/.Main", "com.b/.Main"), back.get(work.id).apps);
        assertTrue(back.get("2").apps.isEmpty());
        assertEquals(2, back.all().size());
    }

    @Test
    public void emptyOrBrokenInputGivesNoGroups() {
        assertEquals(0, Groups.parse("").all().size());
        assertEquals(0, Groups.parse("\n\tname\nonlyid").all().size());
    }

    @Test
    public void idsAreReusedAfterDelete() {
        Groups groups = new Groups();
        groups.create("a");
        groups.create("b");
        groups.delete("1");
        assertNull(groups.get("1"));
        assertEquals("1", groups.create("c").id);
    }

    @Test
    public void namesStaySafeForTheFormat() {
        assertEquals("Spiele Neu", Groups.cleanName(" Spiele\tNeu\n"));
        assertEquals("Gruppe", Groups.cleanName("   "));
        Groups groups = new Groups();
        groups.create("a\tb");
        assertEquals("a b", Groups.parse(groups.serialize()).get("1").name);
    }

    @Test
    public void favoriteKeys() {
        assertTrue(Groups.isKey(Groups.key("3")));
        assertEquals("3", Groups.idOf(Groups.key("3")));
        assertEquals(false, Groups.isKey("com.a/.Main"));
    }
}
