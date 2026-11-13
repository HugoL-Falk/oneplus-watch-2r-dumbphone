package org.op2r.pocketplayer;

/** Song time maths and labels. Pure Java so it can be unit-tested on the JVM. */
final class TimeFormat {

    static final String UNKNOWN = "--:--";

    private TimeFormat() {}

    /** "m:ss", or "--:--" for a negative (unknown) time. */
    static String clock(long ms) {
        if (ms < 0) return UNKNOWN;
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /**
     * Current position: the session only reports it on state changes, so extrapolate
     * from its last update while playing. Clamped to [0, duration] when the duration is known.
     */
    static long position(long pos, long updateTime, float speed, boolean playing, long now, long duration) {
        long p = playing ? pos + (long) ((now - updateTime) * speed) : pos;
        if (p < 0) p = 0;
        if (duration > 0 && p > duration) p = duration;
        return p;
    }

    /** "1:04 / 2:36"; unknown parts become "--:--". */
    static String label(long position, long duration) {
        return clock(position) + " / " + (duration > 0 ? clock(duration) : UNKNOWN);
    }

    /** Played share of the song, 0..1; 0 when the duration is unknown. */
    static float fraction(long position, long duration) {
        if (duration <= 0 || position <= 0) return 0f;
        return Math.min(1f, (float) position / duration);
    }
}
