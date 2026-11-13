package org.op2r.pocketplayer;

import android.graphics.Bitmap;

import androidx.palette.graphics.Palette;

import java.util.List;

/**
 * Accent colour for the spectrum and the volume bar. Only colours that are really in the
 * cover, muted but brightened ("muted but bright beats vivid"), never a shifted hue.
 */
final class Accent {

    static final int WHITE = 0xFFFFFFFF;
    /** Warm off-white used for the UI and for greyscale covers. */
    static final int SOFT_WHITE = 0xFFEDEDE8;

    private static final float MIN_SHARE = 0.03f, MIN_SAT = 0.12f, IDEAL_SAT = 0.45f;
    /** Cover colour: saturation boosted x1.4, at least 0.35 and at most 0.75, so it does not read as white. */
    private static final float SAT_BOOST = 1.4f, MIN_OUT_SAT = 0.35f, MAX_SAT = 0.75f, MIN_VALUE = 0.92f, SAME_HUE_DEG = 30f;

    private Accent() {}

    /**
     * @param swatches colours found in the cover (ARGB)
     * @param shares   share of the cover each swatch covers, 0..1
     * @param background average colour of the dimmed cover behind the UI
     */
    static int choose(int[] swatches, float[] shares, int background) {
        int pick = 0;
        float best = -1;
        boolean found = false;
        for (int i = 0; i < swatches.length; i++) {
            float[] c = hsv(swatches[i]);
            if (c[1] < MIN_SAT || shares[i] < MIN_SHARE) continue;
            float score = shares[i] * 2 + (1 - Math.abs(c[1] - IDEAL_SAT)) + c[2] * 0.3f;
            if (score > best) { best = score; pick = swatches[i]; found = true; }
        }
        if (!found) return SOFT_WHITE;
        float[] c = hsv(pick);
        float sat = Math.max(MIN_OUT_SAT, Math.min(c[1] * SAT_BOOST, MAX_SAT));
        float[] bg = hsv(background);
        float gap = Math.abs(c[0] - bg[0]);
        gap = Math.min(gap, 360 - gap);
        // only a visibly coloured background (not near-black) can swallow the accent
        if (bg[1] > 0.25f && bg[2] > 0.2f && gap < SAME_HUE_DEG) sat *= 0.5f;
        return fromHsv(c[0], sat, Math.max(c[2], MIN_VALUE));
    }

    /** Runs Palette on the cover (call off the main thread). {@code dim} = black overlay fraction. */
    static int fromCover(Bitmap cover, float dim) {
        if (cover == null) return SOFT_WHITE;
        List<Palette.Swatch> list = Palette.from(cover).maximumColorCount(16).generate().getSwatches();
        int total = 0;
        for (Palette.Swatch s : list) total += s.getPopulation();
        int[] rgb = new int[list.size()];
        float[] share = new float[list.size()];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = list.get(i).getRgb() | 0xFF000000;
            share[i] = total == 0 ? 0 : list.get(i).getPopulation() / (float) total;
        }
        int avg = Bitmap.createScaledBitmap(cover, 1, 1, true).getPixel(0, 0);
        int r = Math.round(((avg >> 16) & 0xFF) * (1 - dim)), g = Math.round(((avg >> 8) & 0xFF) * (1 - dim)),
                b = Math.round((avg & 0xFF) * (1 - dim));
        return choose(rgb, share, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    /** {hue 0..360, saturation 0..1, value 0..1}; plain Java so it runs in JVM tests. */
    static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int fromHsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}
