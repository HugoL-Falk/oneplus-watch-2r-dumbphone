package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerPrefsKeysTest {

    @Test public void roundTrips() {
        assertEquals(PlayerPrefs.Style.ARC, PlayerPrefs.Style.fromKey("ARC"));
        assertEquals(PlayerPrefs.Style.LAYERS, PlayerPrefs.Style.fromKey("LAYERS"));
        assertEquals(PlayerPrefs.Style.LAYERS, PlayerPrefs.Style.fromKey("WAVE")); // removed style falls back to the default
        assertEquals(PlayerPrefs.Tint.COVER, PlayerPrefs.Tint.fromKey("COVER"));
    }

    @Test public void unknownKeyFallsBack() {
        assertEquals(PlayerPrefs.Style.LAYERS, PlayerPrefs.Style.fromKey("SPIRAL"));
        assertEquals(PlayerPrefs.Style.LAYERS, PlayerPrefs.Style.fromKey(null));
        assertEquals(PlayerPrefs.Tint.WHITE, PlayerPrefs.Tint.fromKey(""));
    }
}
