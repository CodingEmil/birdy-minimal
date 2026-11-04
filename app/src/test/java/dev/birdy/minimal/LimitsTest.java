package dev.birdy.minimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LimitsTest {
    private static final long MIN = Limits.MINUTE;

    private static long at(int h, int m) { return (h * 60L + m) * MIN; }

    @Test
    public void windowWithinADay() {
        Limits.Window w = new Limits.Window("morgens", 5 * 60, 9 * 60);
        assertFalse(w.contains(at(4, 59)));
        assertTrue(w.contains(at(5, 0)));
        assertTrue(w.contains(at(8, 59)));
        assertFalse("end is exclusive", w.contains(at(9, 0)));
        assertEquals(at(1, 0), w.msUntilBlock(Limits.Moment.of(0, at(4, 0))));
        assertEquals(0, w.msUntilBlock(Limits.Moment.of(0, at(6, 0))));
        assertEquals("next morning", at(20, 0), w.msUntilBlock(Limits.Moment.of(0, at(9, 0))));
    }

    @Test
    public void windowAcrossMidnight() {
        Limits.Window w = new Limits.Window("abends", 21 * 60, 7 * 60);
        assertTrue(w.contains(at(23, 30)));
        assertTrue(w.contains(at(0, 0)));
        assertTrue(w.contains(at(6, 59)));
        assertFalse(w.contains(at(7, 0)));
        assertFalse(w.contains(at(20, 59)));
        Limits.Block b = w.check(Limits.Moment.of(0, at(22, 0)));
        assertEquals("Schonzeit abends", b.title);
        assertEquals("Gesperrt bis 07:00 Uhr.", b.detail);
    }

    @Test
    public void windowUntilMidnight() {
        Limits.Window w = new Limits.Window("abends", 21 * 60, 0);
        assertTrue(w.contains(at(23, 59)));
        assertFalse(w.contains(at(0, 0)));
        assertEquals("Gesperrt bis 00:00 Uhr.", w.check(Limits.Moment.of(0, at(21, 0))).detail);
    }

    @Test
    public void dailyLimit() {
        Limits.DailyLimit d = new Limits.DailyLimit(90);
        assertNull(d.check(Limits.Moment.of(89 * MIN, at(12, 0))));
        Limits.Block b = d.check(Limits.Moment.of(90 * MIN, at(12, 0)));
        assertEquals("Tageslimit erreicht", b.title);
        assertTrue(b.detail.startsWith("1 h 30 min für heute aufgebraucht"));
        assertEquals(30 * MIN, d.msUntilBlock(Limits.Moment.of(60 * MIN, at(12, 0))));
        assertEquals(0, d.msUntilBlock(Limits.Moment.of(120 * MIN, at(12, 0))));
    }

    @Test
    public void appLimitCombinesItsRules() {
        Limits.AppLimit a = new Limits.AppLimit("com.x");
        assertTrue(a.isEmpty());
        assertNull(a.check(Limits.Moment.of(999 * MIN, at(12, 0))));
        assertEquals(Long.MAX_VALUE, a.msUntilBlock(Limits.Moment.of(0, at(12, 0))));
        a.dailyMinutes = 60;
        a.evening = new Limits.Window("abends", 21 * 60, 7 * 60);
        assertNull(a.check(Limits.Moment.of(10 * MIN, at(12, 0))));
        assertEquals("time runs out before the evening", 50 * MIN, a.msUntilBlock(Limits.Moment.of(10 * MIN, at(12, 0))));
        assertEquals("evening comes first", 30 * MIN, a.msUntilBlock(Limits.Moment.of(0, at(20, 30))));
        assertEquals("quiet time explains when it opens", "Schonzeit abends", a.check(Limits.Moment.of(70 * MIN, at(22, 0))).title);
        assertEquals("Tageslimit erreicht", a.check(Limits.Moment.of(70 * MIN, at(12, 0))).title);
    }

    @Test
    public void roundTrip() {
        Limits limits = new Limits();
        Limits.AppLimit a = limits.getOrCreate("com.insta");
        a.dailyMinutes = 45;
        a.morning = new Limits.Window("morgens", 300, 540);
        Limits.AppLimit b = limits.getOrCreate("com.tube");
        b.evening = new Limits.Window("abends", 1260, 420);
        limits.getOrCreate("com.empty"); // nothing set: not saved
        String saved = limits.serialize();
        assertEquals("com.insta\t45\t300-540\t\t0\t0\ncom.tube\t0\t\t1260-420\t0\t0", saved);
        Limits back = Limits.parse(saved);
        assertEquals(45, back.get("com.insta").dailyMinutes);
        assertEquals(540, back.get("com.insta").morning.end);
        assertNull(back.get("com.insta").evening);
        assertEquals(1260, back.get("com.tube").evening.start);
        assertNull(back.get("com.empty"));
        assertEquals(2, back.all().size());
    }

    @Test
    public void brokenInputIsIgnored() {
        Limits l = Limits.parse("a\tx\t1-2\t\nb\t10\t5-5\t9999-1\n\nc\t0\t\t\nd");
        assertNotNull("bad number means no daily limit, the window still counts", l.get("a"));
        assertEquals(0, l.get("a").dailyMinutes);
        assertEquals(10, l.get("b").dailyMinutes);
        assertNull("empty and out-of-range windows are dropped", l.get("b").morning);
        assertNull(l.get("b").evening);
        assertNull("nothing set", l.get("c"));
        assertNull(l.get("d"));
    }

    @Test
    public void words() {
        assertEquals("45 min", Limits.duration(45));
        assertEquals("2 h", Limits.duration(120));
        assertEquals("1 h 5 min", Limits.duration(65));
        assertEquals("07:05", Limits.clock(425));
        Limits.AppLimit a = new Limits.AppLimit("x");
        a.dailyMinutes = 60;
        a.morning = new Limits.Window("morgens", 300, 540);
        a.evening = new Limits.Window("abends", 1260, 0);
        assertEquals("1 h am Tag · morgens 05:00–09:00 · abends 21:00–00:00", a.summary());
    }

    // now = 12:00 on the wall clock, here as plain minutes, so minuteOfDay maps 1:1.
    private static Limits.Moment session(long sessionEnd, long pauseEnd) {
        long now = at(12, 0);
        return new Limits.Moment(0, now, now, sessionEnd, pauseEnd);
    }

    @Test
    public void sessionAsksFirstThenRunsThenPausesThenAsksAgain() {
        Limits.Session s = new Limits.Session(15);
        Limits.Block first = s.check(session(0, 0));
        assertTrue("no session yet: ask", first.ask);
        assertEquals("Bewusst öffnen", first.title);
        assertNull("inside the session", s.check(session(at(12, 10), at(12, 25))));
        assertEquals(10 * MIN, s.msUntilBlock(session(at(12, 10), at(12, 25))));
        Limits.Block pause = s.check(session(at(11, 50), at(12, 5)));
        assertFalse("pause is a hard block", pause.ask);
        assertEquals("Pause", pause.title);
        assertEquals("Deine Session ist vorbei. Wieder frei ab 12:05 Uhr.", pause.detail);
        Limits.Block again = s.check(session(at(11, 0), at(11, 15)));
        assertTrue("after the pause it asks again", again.ask);
        assertEquals("Bewusst öffnen", again.title);
    }

    @Test
    public void sessionJustOverWithoutPauseOffersAnotherRound() {
        Limits.Block b = new Limits.Session(0).check(session(at(12, 0) - 10_000, at(12, 0) - 10_000));
        assertTrue(b.ask);
        assertEquals("Deine Zeit ist um", b.title);
    }

    @Test
    public void hardRulesComeBeforeTheSessionQuestion() {
        Limits.AppLimit a = new Limits.AppLimit("x");
        a.ask = true;
        a.dailyMinutes = 30;
        long now = at(12, 0);
        assertTrue(a.check(new Limits.Moment(10 * MIN, now, now, 0, 0)).ask);
        assertEquals("used up: no question, just blocked", "Tageslimit erreicht", a.check(new Limits.Moment(30 * MIN, now, now, 0, 0)).title);
        assertEquals("the session ends before the daily limit", 5 * MIN, a.msUntilBlock(new Limits.Moment(10 * MIN, now, now, now + 5 * MIN, 0)));
    }

    @Test
    public void sessionSettingsRoundTripAndOldLinesStillLoad() {
        Limits limits = new Limits();
        Limits.AppLimit a = limits.getOrCreate("com.x");
        a.ask = true;
        a.pauseMinutes = 30;
        assertFalse("asking alone is a limit", a.isEmpty());
        Limits back = Limits.parse(limits.serialize());
        assertTrue(back.get("com.x").ask);
        assertEquals(30, back.get("com.x").pauseMinutes);
        assertEquals("fragt, 30 min Pause", back.get("com.x").summary());
        Limits old = Limits.parse("com.y\t20\t\t");
        assertFalse(old.get("com.y").ask);
        assertEquals(0, old.get("com.y").pauseMinutes);
    }
}
