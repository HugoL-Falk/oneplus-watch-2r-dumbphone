package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccentTest {

    private static final int DARK_BG = 0xFF101018;

    private static float[] hsv(int c) { return Accent.hsv(c); }

    @Test public void greyscaleCoverIsSoftWhite() {
        int c = Accent.choose(new int[]{0xFF808080, 0xFF202020, 0xFFEEEEEE}, new float[]{0.5f, 0.3f, 0.2f}, DARK_BG);
        assertEquals(Accent.SOFT_WHITE, c);
    }

    @Test public void noSwatchesIsSoftWhite() {
        assertEquals(Accent.SOFT_WHITE, Accent.choose(new int[0], new float[0], DARK_BG));
    }

    @Test public void keepsTheCoversHue() {
        int swatch = 0xFF7B2CBF; // purple
        int c = Accent.choose(new int[]{swatch}, new float[]{0.4f}, DARK_BG);
        assertEquals(hsv(swatch)[0], hsv(c)[0], 2f);
    }

    @Test public void boostsMutedColoursAndCapsSaturation() {
        int muted = 0xFF8A7F9A; // low saturation lavender-grey (s ~ 0.18)
        float sMuted = hsv(Accent.choose(new int[]{muted}, new float[]{0.4f}, DARK_BG))[1];
        assertTrue(sMuted >= 0.35f - 0.01f);                  // clearly coloured, not near-white
        int neon = 0xFFFF00AA;
        float sNeon = hsv(Accent.choose(new int[]{neon}, new float[]{0.4f}, DARK_BG))[1];
        assertTrue(sNeon <= 0.75f + 0.01f);
    }

    @Test public void isBrightened() {
        int c = Accent.choose(new int[]{0xFF3A5A7A}, new float[]{0.4f}, DARK_BG);
        assertTrue(hsv(c)[2] >= 0.92f - 0.01f);
    }

    @Test public void ignoresTinyShares() {
        // 1 % magenta speck, rest grey: soft white, not magenta
        int c = Accent.choose(new int[]{0xFFFF00AA, 0xFF777777}, new float[]{0.01f, 0.99f}, DARK_BG);
        assertEquals(Accent.SOFT_WHITE, c);
    }

    @Test public void prefersMutedOverNeon() {
        int neon = 0xFFFF0040, muted = 0xFF6E8FA8; // similar shares
        int c = Accent.choose(new int[]{neon, muted}, new float[]{0.2f, 0.2f}, DARK_BG);
        assertEquals(hsv(muted)[0], hsv(c)[0], 2f);
    }

    @Test public void sameHueAsBackgroundGetsLighterTint() {
        int orange = 0xFFD9682B, orangeBg = 0xFF5A2A12; // all-orange cover, dimmed
        int onDark = Accent.choose(new int[]{orange}, new float[]{0.8f}, DARK_BG);
        int onOrange = Accent.choose(new int[]{orange}, new float[]{0.8f}, orangeBg);
        assertTrue(hsv(onOrange)[1] < hsv(onDark)[1] * 0.6f);
    }
}
