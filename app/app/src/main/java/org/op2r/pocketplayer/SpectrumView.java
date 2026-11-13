package org.op2r.pocketplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * The "music is playing" element, drawn over the cover: a ring of bars around the rim (RING),
 * a thin progress line plus bars on the lower arc (ARC), or four small bars above the title
 * (EQUALIZER). Animates only while attached, visible and playing.
 */
public class SpectrumView extends View {

    private static final int ARC_BARS = 40, EQ_BARS = 4, LAYER_POINTS = 240;
    private final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Eases to 1 while playing and to 0 when paused, so the waves settle instead of freezing. */
    private float energy;
    private static final double ARC_SPAN_DEG = 120;
    private final android.graphics.Path wavePath = new android.graphics.Path();

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float dp;
    private final long start = SystemClock.uptimeMillis();

    private PlayerPrefs.Style style = PlayerPrefs.Style.RING;
    private int color = Color.WHITE;
    private boolean playing, visible;
    private float progress;
    private float equalizerAnchor = -1;
    private boolean framePending;
    private final Runnable nextFrame = () -> {
        framePending = false;
        invalidate();
    };

    public SpectrumView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeCap(Paint.Cap.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    void setStyle(PlayerPrefs.Style s) { style = s; invalidate(); }
    void setColor(int argb) { color = argb | 0xFF000000; invalidate(); }
    /** For callers that draw this view themselves (the watch face) and never attach it. */
    void setDrawnExternally(boolean v) { visible = v; }
    void setPlaying(boolean p) { playing = p; invalidate(); }
    void setProgress(float f) { timelineSet = false; progress = Math.max(0f, Math.min(1f, f)); invalidate(); }

    /**
     * Song position as reported by the media session. The view extrapolates it on every frame,
     * so the progress front moves smoothly instead of in half-second steps.
     */
    void setTimeline(long position, long updateTime, float speed, long duration, boolean playing) {
        tlPos = position; tlUpdate = updateTime; tlSpeed = speed; tlDuration = duration; tlPlaying = playing;
        timelineSet = true;
        invalidate();
    }

    private boolean timelineSet, tlPlaying;
    private long tlPos, tlUpdate, tlDuration;
    private float tlSpeed;
    /** Y of the bottom of the small equalizer (style EQUALIZER), in px. */
    void setEqualizerAnchor(float yPx) { equalizerAnchor = yPx; invalidate(); }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(nextFrame);
        framePending = false;
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        invalidate();
    }

    private int withAlpha(float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long drawStart = SystemClock.uptimeMillis();
        if (timelineSet) {
            progress = TimeFormat.fraction(TimeFormat.position(tlPos, tlUpdate, tlSpeed, tlPlaying,
                    SystemClock.elapsedRealtime(), tlDuration), tlDuration);
        }
        double t = (SystemClock.uptimeMillis() - start) / 1000.0;
        boolean moving = playing && visible;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
        switch (style) {
            case ARC: drawArc(canvas, cx, cy, r, t, moving); break;
            case EQUALIZER: drawEqualizer(canvas, cx, cy, r, t, moving); break;
            case LAYERS: drawLayers(canvas, cx, cy, r); break;
            default: drawRing(canvas, cx, cy, r, t, moving);
        }
        float target = moving ? 1f : 0f;
        energy += (target - energy) * 0.06f;
        boolean settling = Math.abs(target - energy) > 0.01f;
        // 30 fps cap: schedule the next frame after the rest of this frame's time budget. Only one
        // frame may be scheduled at a time, or every outside invalidate() would start its own chain.
        if ((moving || (settling && visible)) && isAttachedToWindow() && !framePending) {
            framePending = true;
            postOnAnimationDelayed(nextFrame, SpectrumModel.frameDelay(SystemClock.uptimeMillis() - drawStart));
        }
    }

    private void radial(Canvas c, float cx, float cy, double deg, float rOuter, float rInner) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        c.drawLine(cx + sx * rOuter, cy + sy * rOuter, cx + sx * rInner, cy + sy * rInner, bar);
    }

    private void drawRing(Canvas c, float cx, float cy, float r, double t, boolean moving) {
        bar.setShader(null);
        bar.setStrokeWidth(1.5f * dp);
        float rOuter = r - 2 * dp - bar.getStrokeWidth() / 2;
        float split = 360f * progress;
        for (float deg : SpectrumModel.ringAngles(progress)) {
            int idx = Math.round(deg / SpectrumModel.PLAYED_STEP_DEG);
            float h = 2.5f * dp + SpectrumModel.level(idx, t, moving) * 7 * dp;
            float top = SpectrumModel.ringBarAlpha(deg, split, deg < split);
            double a = Math.toRadians(deg);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            float x0 = cx + sx * rOuter, y0 = cy + sy * rOuter, x1 = cx + sx * (rOuter - h), y1 = cy + sy * (rOuter - h);
            bar.setShader(new LinearGradient(x0, y0, x1, y1, withAlpha(top), withAlpha(top * 0.25f), Shader.TileMode.CLAMP));
            c.drawLine(x0, y0, x1, y1, bar);
        }
        bar.setShader(null);
    }

    /** Bars along the bottom of the screen, filling from left (~8 o'clock) to right (~4 o'clock) with the song. No rim ring. */
    private void drawArc(Canvas c, float cx, float cy, float r, double t, boolean moving) {
        bar.setStrokeWidth(2 * dp);
        float rOuter = r - 3 * dp;
        float mid = (ARC_BARS - 1) / 2f;
        for (int i = 0; i < ARC_BARS; i++) {
            float m = Math.abs(i - mid) / mid;
            float h = 3 * dp + SpectrumModel.level(i, t, moving) * 20 * dp * (1 - 0.4f * m);
            double deg = 180 + ARC_SPAN_DEG / 2 - i * (ARC_SPAN_DEG / (ARC_BARS - 1));
            double a = Math.toRadians(deg);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            float x0 = cx + sx * rOuter, y0 = cy + sy * rOuter, x1 = cx + sx * (rOuter - h), y1 = cy + sy * (rOuter - h);
            float top = SpectrumModel.arcBarAlpha(i, ARC_BARS, progress);
            bar.setShader(new LinearGradient(x0, y0, x1, y1, withAlpha(top), withAlpha(top * 0.3f), Shader.TileMode.CLAMP));
            c.drawLine(x0, y0, x1, y1, bar);
        }
        bar.setShader(null);
    }

    /**
     * Three filled, layered waves hugging the edge from 12 o'clock clockwise. Alive where the song
     * has played, a thin calm band ahead of it; thin at both ends of the lap.
     */
    private void drawLayers(Canvas c, float cx, float cy, float r) {
        double t = (SystemClock.uptimeMillis() - start) / 1000.0;
        float[] maxDepth = {27 * dp, 20 * dp, 12 * dp};
        float[] alpha = {0.45f, 0.45f, 0.55f};
        layerPaint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 3; k++) {
            wavePath.reset();
            wavePath.setFillType(android.graphics.Path.FillType.EVEN_ODD);
            for (int i = 0; i <= LAYER_POINTS; i++) {
                float f = i / (float) LAYER_POINTS;
                double deg = 360.0 * f;
                float depth = (1.5f + 0.5f * k) * dp
                        + SpectrumModel.edgeLive(f, progress) * energy * SpectrumModel.edgeWave(deg, t, k) * maxDepth[k];
                float rr = r + 1 * dp - depth;
                double a = Math.toRadians(deg);
                float x = cx + (float) Math.sin(a) * rr, y = cy - (float) Math.cos(a) * rr;
                if (i == 0) wavePath.moveTo(x, y); else wavePath.lineTo(x, y);
            }
            wavePath.close();
            wavePath.addCircle(cx, cy, r + 4 * dp, android.graphics.Path.Direction.CW);
            int base = k == 0 ? darker(color) : color;
            layerPaint.setColor(Color.argb(Math.round(255 * alpha[k]), Color.red(base), Color.green(base), Color.blue(base)));
            c.drawPath(wavePath, layerPaint);
        }
    }

    private static int darker(int c) {
        return Color.rgb(Math.round(Color.red(c) * 0.7f), Math.round(Color.green(c) * 0.7f), Math.round(Color.blue(c) * 0.7f));
    }

    /** Four bars above the title, plus a progress ring around the rim. */
    private void drawEqualizer(Canvas c, float cx, float cy, float r, double t, boolean moving) {
        line.setStrokeWidth(4.5f * dp);
        float lr = r - 3f * dp;
        oval.set(cx - lr, cy - lr, cx + lr, cy + lr);
        line.setColor(withAlpha(0.2f));
        c.drawArc(oval, -90 + 360 * progress, 360 * (1 - progress), false, line);
        if (progress > 0) {
            line.setColor(withAlpha(0.85f));
            c.drawArc(oval, -90, 360 * progress, false, line);
        }
        if (equalizerAnchor < 0) return;
        bar.setShader(null);
        bar.setStrokeWidth(3 * dp);
        bar.setColor(withAlpha(1f));
        float gap = 3 * dp, w = 3 * dp;
        float x = cx - (EQ_BARS * w + (EQ_BARS - 1) * gap) / 2 + w / 2;
        for (int i = 0; i < EQ_BARS; i++) {
            float h = 4 * dp + SpectrumModel.level(i, t, moving) * 12 * dp;
            c.drawLine(x, equalizerAnchor - w / 2, x, equalizerAnchor - h + w / 2, bar);
            x += w + gap;
        }
    }
}
