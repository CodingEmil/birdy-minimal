package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class UsageTest {
    private static final long MIN = 60_000L;
    private final List<Usage.Event> events = new ArrayList<>();

    private UsageTest add(int type, String pkg, String cls, long minute) {
        long t = minute * MIN;
        events.add(new Usage.Event() {
            @Override public int type() { return type; }
            @Override public String pkg() { return pkg; }
            @Override public String cls() { return cls; }
            @Override public long time() { return t; }
        });
        return this;
    }

    private long minutes(String pkg, long nowMinute) { return Usage.foregroundMs(events, pkg, 0, nowMinute * MIN) / MIN; }

    @Test
    public void sumsSessions() {
        add(Usage.RESUMED, "a", "A", 10).add(Usage.PAUSED, "a", "A", 25)
                .add(Usage.RESUMED, "b", "B", 25).add(Usage.PAUSED, "b", "B", 30)
                .add(Usage.RESUMED, "a", "A", 40).add(Usage.PAUSED, "a", "A", 45);
        assertEquals(20, minutes("a", 100));
        assertEquals(5, minutes("b", 100));
        assertEquals(0, minutes("c", 100));
    }

    @Test
    public void countsTheOpenSessionUpToNow() {
        add(Usage.RESUMED, "a", "A", 10);
        assertEquals(15, minutes("a", 25));
    }

    @Test
    public void switchingActivitiesInsideTheAppIsOneSession() {
        add(Usage.RESUMED, "a", "Main", 10)
                .add(Usage.RESUMED, "a", "Detail", 12) // resumes before the first one pauses
                .add(Usage.PAUSED, "a", "Main", 12)
                .add(Usage.PAUSED, "a", "Detail", 20);
        assertEquals(10, minutes("a", 100));
    }

    @Test
    public void openAtMidnightCountsFromMidnight() {
        add(Usage.PAUSED, "a", "A", 7);
        assertEquals(7, minutes("a", 100));
    }

    @Test
    public void screenOffEndsTheSession() {
        add(Usage.RESUMED, "a", "A", 10).add(Usage.SCREEN_OFF, "android", "", 15);
        assertEquals(5, minutes("a", 100));
    }

    @Test
    public void stoppedAlsoEndsIt() {
        add(Usage.RESUMED, "a", "A", 10).add(Usage.STOPPED, "a", "A", 18);
        assertEquals(8, minutes("a", 100));
    }
}
