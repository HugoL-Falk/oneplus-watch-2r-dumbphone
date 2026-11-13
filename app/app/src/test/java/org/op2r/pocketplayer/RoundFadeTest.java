package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RoundFadeTest {

    @Test public void chordIsFullWidthThroughTheCentre() {
        assertEquals(233f, RoundFade.chord(116.5f, 116.5f), 1e-3f);
    }

    @Test public void chordNarrowsTowardTheTop() {
        // title line centred 38 dp from the top of a 233 dp round screen
        assertEquals(172.4f, RoundFade.chord(116.5f, 38f), 0.5f);
    }

    @Test public void chordIsZeroOutsideTheCircle() {
        assertEquals(0f, RoundFade.chord(116.5f, -5f), 0f);
    }
}
