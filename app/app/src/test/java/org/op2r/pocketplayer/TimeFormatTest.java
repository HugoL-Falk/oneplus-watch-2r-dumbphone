package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeFormatTest {

    @Test public void formatsMinutesSeconds() {
        assertEquals("1:04", TimeFormat.clock(64_000));
        assertEquals("0:00", TimeFormat.clock(0));
    }

    @Test public void unknownDurationShowsDashes() {
        assertEquals("1:04 / --:--", TimeFormat.label(64_000, 0));
        assertEquals("--:-- / --:--", TimeFormat.label(-1, -1));
    }

    @Test public void extrapolatesWhilePlaying() {
        assertEquals(12_000, TimeFormat.position(10_000, 1_000, 1f, true, 3_000, 156_396));
    }

    @Test public void frozenWhilePaused() {
        assertEquals(10_000, TimeFormat.position(10_000, 1_000, 1f, false, 60_000, 156_396));
    }

    @Test public void clampsPastEnd() {
        assertEquals(156_396, TimeFormat.position(150_000, 0, 1f, true, 20_000, 156_396));
    }

    @Test public void fractionZeroWhenDurationUnknown() {
        assertEquals(0f, TimeFormat.fraction(5_000, 0), 0f);
        assertEquals(0.5f, TimeFormat.fraction(50, 100), 1e-6);
    }
}
