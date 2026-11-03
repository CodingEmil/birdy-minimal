package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class LaunchLogTest {
    private static final long DAY = 24 * 60 * 60 * 1000L, WINDOW = 3 * DAY, NOW = 10 * DAY;

    @Test
    public void ranksByCountThenByMostRecent() {
        String log = "";
        log = LaunchLog.append(log, "a", NOW - 3, WINDOW);
        log = LaunchLog.append(log, "b", NOW - 2, WINDOW);
        log = LaunchLog.append(log, "b", NOW - 1, WINDOW);
        log = LaunchLog.append(log, "c", NOW, WINDOW);
        assertEquals(Arrays.asList("b", "c", "a"), LaunchLog.rank(log, NOW, WINDOW));
    }

    @Test
    public void forgetsLaunchesOlderThanTheWindow() {
        String log = LaunchLog.append("", "old", NOW - WINDOW - 1, WINDOW);
        log = LaunchLog.append(log, "new", NOW, WINDOW);
        assertFalse(log.contains("old"));
        assertEquals(Collections.singletonList("new"), LaunchLog.rank(log, NOW, WINDOW));
    }
}
