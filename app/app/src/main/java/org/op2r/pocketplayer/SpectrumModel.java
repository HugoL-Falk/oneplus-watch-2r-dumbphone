package org.op2r.pocketplayer;

/**
 * Bar heights for the "music is playing" element. A steady rhythm, not the real audio:
 * it only says "playing". Pure Java so it can be unit-tested.
 */
final class SpectrumModel {

    private SpectrumModel() {}

    /** Deterministic pseudo-random number 0..1 for an integer seed (a 64-bit mix, no state). */
    private static double hash(long n) {
        long h = n * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /**
     * Height of bar {@code bar} at time {@code t} seconds, 0..1. Always 0 when not playing.
     *
     * <p>Each bar follows its own smooth random walk: it picks a new random target 1.5 to 4.5 times a
     * second (its own tempo), and glides to it. Neighbouring bars share nothing, so no wave or
     * repeating pattern shows up across the spectrum. Deterministic, so the same moment looks the same.
     */
    static float level(int bar, double t, boolean playing) {
        if (!playing) return 0f;
        double tempo = 1.5 + 3.0 * hash(bar * 31L + 7);          // targets per second
        double x = t * tempo + 50.0 * hash(bar * 17L + 3);       // own time offset
        long i = (long) Math.floor(x);
        double f = x - i;
        f = f * f * (3 - 2 * f);                                  // smoothstep between targets
        double a = hash(bar * 104729L + i), b = hash(bar * 104729L + i + 1);
        double v = a + (b - a) * f;
        return (float) Math.pow(v, 1.3);                          // mostly mid heights, now and then a peak
    }

    static final float EDGE_TAPER = 0.06f; // share of the lap over which the waves swell in and out
    private static final double[] LAYER_SPEED = {1.0, -1.3, 1.8}, LAYER_PHASE = {0, 1.9, 3.7};

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    /**
     * How "alive" the edge waves are at lap position {@code f} (0..1 clockwise from 12 o'clock):
     * 1 where the song has played, 0 (calm band) ahead of it, with soft tapers at the start of the
     * lap, at the end of the lap, and at the song's current position.
     */
    static float edgeLive(float f, float progress) {
        float ahead = f - progress;
        float front = ahead <= 0 ? 1f : 1f - smooth(ahead / EDGE_TAPER);
        return smooth(f / EDGE_TAPER) * smooth((1 - f) / EDGE_TAPER) * front;
    }

    /** Wave height 0..1 of edge layer {@code layer} (0..2) at angle {@code deg}, time {@code t} s. */
    static float edgeWave(double deg, double t, int layer) {
        double a = Math.toRadians(deg);
        double w = 0.55 + 0.45 * (0.5 * Math.sin(a * (5 + 2 * layer) + t * LAYER_SPEED[layer] + LAYER_PHASE[layer])
                + 0.5 * Math.sin(a * 11 - t * (1.4 + 0.5 * layer)));
        return (float) Math.max(0, Math.min(1, w));
    }

    static final float PLAYED_STEP_DEG = 3f, REST_STEP_DEG = 6f; // 120 and 60 bars per turn

    /**
     * Bar angles for the full ring, clockwise from 12 o'clock, in degrees. The played part of the
     * song is packed twice as tight as the part still to come. Both parts sit on fixed grids, so
     * bars don't crawl as the song advances.
     */
    static float[] ringAngles(float progress) {
        float split = 360f * Math.max(0f, Math.min(1f, progress));
        int played = (int) Math.ceil(split / PLAYED_STEP_DEG);
        int firstRest = (int) Math.ceil(split / REST_STEP_DEG);
        int rest = Math.max(0, (int) Math.ceil(360f / REST_STEP_DEG) - firstRest);
        float[] out = new float[played + rest];
        for (int i = 0; i < played; i++) out[i] = i * PLAYED_STEP_DEG;
        for (int j = 0; j < rest; j++) out[played + j] = (firstRest + j) * REST_STEP_DEG;
        return out;
    }

    /**
     * The visualizer redraws at most 30 times a second: plenty for ambient motion, half the work of 60.
     * The screen refreshes every 16.7 ms; asking for the next frame after about 20 ms makes it fire on
     * the second refresh from now, so the redraws land exactly 33 ms apart. (A 33 ms delay misses that
     * refresh by a hair and waits for the next one: 22 fps.)
     */
    static final long FRAME_MS = 20;

    /** Milliseconds to wait before the next frame, given how long drawing this one took (at least 1). */
    static long frameDelay(long drawMs) {
        return Math.max(1L, FRAME_MS - drawMs);
    }

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    /**
     * Alpha of a full-ring bar at {@code deg} with the progress front at {@code split} degrees.
     * Played bars (85 %) fade in as the front passes them; bars ahead (35 %) fade out just before
     * the front reaches them, so the front glides instead of jumping bar by bar.
     */
    static float ringBarAlpha(float deg, float split, boolean onPlayedGrid) {
        if (onPlayedGrid) return 0.35f + 0.5f * clamp01((split - deg) / PLAYED_STEP_DEG);
        return 0.35f * clamp01((deg - split) / REST_STEP_DEG);
    }

    /** Alpha of half-ring bar {@code i} of {@code n}: 100 % played, 50 % ahead, blended across the front. */
    static float arcBarAlpha(int i, int n, float progress) {
        return 0.5f + 0.5f * clamp01(progress * n - i);
    }

    /** True if bar {@code bar} of {@code bars} lies in the played part of the song. */
    static boolean played(int bar, int bars, float fraction) {
        return bar < Math.round(bars * fraction);
    }
}
