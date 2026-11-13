package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpectrumModelTest {

    @Test public void silentWhenPaused() {
        for (int i = 0; i < 64; i++) assertEquals(0f, SpectrumModel.level(i, 3.2, false), 0f);
    }

    @Test public void staysInRange() {
        for (int i = 0; i < 64; i++) {
            for (double t = 0; t < 5; t += 0.13) {
                float v = SpectrumModel.level(i, t, true);
                assertTrue(v >= 0f && v <= 1f);
            }
        }
    }

    @Test public void moves() {
        assertNotEquals(SpectrumModel.level(3, 1.0, true), SpectrumModel.level(3, 1.4, true), 1e-4);
    }

    @Test public void ringPlayedPartIsTwiceAsDense() {
        float[] a = SpectrumModel.ringAngles(0.5f);
        int played = 0, rest = 0;
        for (float d : a) { if (d < 180f) played++; else rest++; }
        assertEquals(60, played); // 120 per turn
        assertEquals(30, rest);   // 60 per turn
    }

    @Test public void ringAnglesAreStableAndSorted() {
        float[] a = SpectrumModel.ringAngles(0.3f), b = SpectrumModel.ringAngles(0.31f);
        assertEquals(0f, a[0], 0f);
        for (int i = 1; i < a.length; i++) assertTrue(a[i] > a[i - 1]);
        // the part still to come sits on the same fixed 6-degree grid
        assertEquals(a[a.length - 1], b[b.length - 1], 0f);
        assertEquals(60, SpectrumModel.ringAngles(0f).length);
        assertEquals(120, SpectrumModel.ringAngles(1f).length);
    }

    @Test public void edgeLiveIsThinAtBothEndsOfTheLap() {
        assertEquals(0f, SpectrumModel.edgeLive(0f, 1f), 1e-6f);   // 12 o'clock, start
        assertEquals(0f, SpectrumModel.edgeLive(1f, 1f), 1e-6f);   // 12 o'clock, end of the song
        assertEquals(1f, SpectrumModel.edgeLive(0.25f, 0.5f), 1e-6f);
    }

    @Test public void edgeLiveIsCalmAheadOfTheSong() {
        assertEquals(0f, SpectrumModel.edgeLive(0.8f, 0.5f), 1e-6f);
        float front = SpectrumModel.edgeLive(0.53f, 0.5f);
        assertTrue(front > 0f && front < 1f);                     // soft front, no hard edge
    }

    @Test public void edgeWaveStaysInRangeAndMoves() {
        for (int layer = 0; layer < 3; layer++)
            for (double deg = 0; deg < 360; deg += 5) {
                float w = SpectrumModel.edgeWave(deg, 1.7, layer);
                assertTrue(w >= 0f && w <= 1f);
            }
        assertEquals(SpectrumModel.edgeWave(0, 1.3, 1), SpectrumModel.edgeWave(360, 1.3, 1), 1e-4); // closed loop
        assertNotEquals(SpectrumModel.edgeWave(40, 1.0, 0), SpectrumModel.edgeWave(40, 1.3, 0), 1e-4);
    }

    @Test public void ringBarsFadeAcrossTheProgressFront() {
        assertEquals(0.85f, SpectrumModel.ringBarAlpha(0f, 90f, true), 1e-6f);    // well played
        float justPlayed = SpectrumModel.ringBarAlpha(89f, 90f, true);
        assertTrue(justPlayed > 0.35f && justPlayed < 0.85f);                    // fading in, not a jump
        assertEquals(0.35f, SpectrumModel.ringBarAlpha(100f, 90f, false), 1e-6f); // well ahead
        assertTrue(SpectrumModel.ringBarAlpha(91f, 90f, false) < 0.35f);          // about to be passed: fading out
    }

    @Test public void arcBarsFadeAcrossTheProgressFront() {
        assertEquals(1f, SpectrumModel.arcBarAlpha(0, 40, 0.5f), 1e-6f);
        assertEquals(0.5f, SpectrumModel.arcBarAlpha(39, 40, 0.5f), 1e-6f);
        float front = SpectrumModel.arcBarAlpha(20, 40, 0.5125f);                // half way across bar 20
        assertEquals(0.75f, front, 1e-3f);
    }

    @Test public void frameDelayCapsAt30fps() {
        assertEquals(20L, SpectrumModel.frameDelay(0));      // instant draw: wait just under two screen refreshes
        assertEquals(10L, SpectrumModel.frameDelay(10));     // draw took 10 ms: wait the rest
        assertEquals(1L, SpectrumModel.frameDelay(20));      // slow draw: go again at once
        assertEquals(1L, SpectrumModel.frameDelay(90));
    }

    private static double[] series(int bar, double from, double to, double step) {
        int n = (int) ((to - from) / step);
        double[] v = new double[n];
        for (int k = 0; k < n; k++) v[k] = SpectrumModel.level(bar, from + k * step, true);
        return v;
    }

    private static double pearson(double[] a, int offA, double[] b, int offB, int n) {
        double ma = 0, mb = 0;
        for (int k = 0; k < n; k++) { ma += a[offA + k]; mb += b[offB + k]; }
        ma /= n; mb /= n;
        double sab = 0, saa = 0, sbb = 0;
        for (int k = 0; k < n; k++) {
            double x = a[offA + k] - ma, y = b[offB + k] - mb;
            sab += x * y; saa += x * x; sbb += y * y;
        }
        return sab / Math.sqrt(saa * sbb);
    }

    /** Strongest correlation between two series when one is shifted by -maxLag..+maxLag samples. */
    private static double worstShiftedCorrelation(double[] a, double[] b, int maxLag) {
        int n = a.length - 2 * maxLag;
        double w = 0;
        for (int lag = -maxLag; lag <= maxLag; lag++) w = Math.max(w, Math.abs(pearson(a, maxLag, b, maxLag + lag, n)));
        return w;
    }

    /**
     * The old rhythm was a fixed sine per bar: each bar repeated itself almost exactly (correlation 0.99)
     * and bars five apart were near copies of each other (0.89), which reads as a regular pattern.
     */
    @Test public void noBarRepeatsItself() {
        for (int bar = 0; bar < 40; bar += 3) {
            double[] s = series(bar, 0, 120, 0.05);
            for (int lag = 20; lag <= 200; lag++) {                  // look 1 to 10 s ahead
                int n = s.length - lag;
                double c = pearson(s, 0, s, lag, n);
                assertTrue("bar " + bar + " repeats after " + lag * 0.05 + " s: " + c, Math.abs(c) < 0.5);
            }
        }
    }

    @Test public void barsFiveApartAreNotCopiesOfEachOther() {
        for (int bar = 0; bar < 35; bar += 5) {
            double c = worstShiftedCorrelation(series(bar, 0, 120, 0.05), series(bar + 5, 0, 120, 0.05), 20);
            assertTrue("bar " + bar + " vs " + (bar + 5) + ": " + c, c < 0.5);
        }
    }

    @Test public void neighbouringBarsDoNotFormAWave() {
        for (int bar = 0; bar < 39; bar += 3) {
            double c = worstShiftedCorrelation(series(bar, 0, 120, 0.05), series(bar + 1, 0, 120, 0.05), 20);
            assertTrue("bar " + bar + " vs " + (bar + 1) + ": " + c, c < 0.5);
        }
    }

    @Test public void eachBarWandersAcrossItsWholeRange() {
        for (int bar = 0; bar < 40; bar += 3) {
            double lo = 1, hi = 0;
            for (double v : series(bar, 0, 60, 0.05)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            assertTrue("bar " + bar + " low " + lo, lo < 0.2);
            assertTrue("bar " + bar + " high " + hi, hi > 0.8);
        }
    }

    @Test public void levelIsDeterministicSoTheSameMomentLooksTheSame() {
        assertEquals(SpectrumModel.level(7, 12.345, true), SpectrumModel.level(7, 12.345, true), 0f);
    }

    @Test public void levelChangesSmoothlyWithoutJumps() {
        for (int bar = 0; bar < 40; bar += 5) {
            double prev = SpectrumModel.level(bar, 0, true);
            for (double t = 0.033; t < 30; t += 0.033) {  // one frame at 30 fps
                double v = SpectrumModel.level(bar, t, true);
                assertTrue("bar " + bar + " t " + t, Math.abs(v - prev) < 0.35);
                prev = v;
            }
        }
    }

    @Test public void playedSplit() {
        assertTrue(SpectrumModel.played(25, 64, 0.41f));
        assertFalse(SpectrumModel.played(27, 64, 0.41f));
        assertFalse(SpectrumModel.played(0, 64, 0f));
    }
}
