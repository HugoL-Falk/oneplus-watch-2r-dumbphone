package org.op2r.pocketplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * Container that fades its children out toward the round edge of the screen, so scrolling
 * (marquee) text dissolves along the watch's curve instead of being cut off straight.
 */
public class RoundFade extends FrameLayout {

    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Text is fully faded {@code inset} inside the rim, over {@code fade}. */
    private final float fade, inset;

    public RoundFade(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        fade = 18 * d;
        inset = 6 * d;
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /** Width of a horizontal line {@code yFromTop} below the top of a circle of {@code radius}. */
    static float chord(float radius, float yFromTop) {
        float dy = radius - yFromTop;
        if (yFromTop < 0 || Math.abs(dy) >= radius) return 0f;
        return (float) (2 * Math.sqrt(radius * radius - dy * dy));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = Math.min(w, h) / 2f;
        if (r <= 0) return;
        mask.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{0xFF000000, 0xFF000000, 0x00000000, 0x00000000},
                new float[]{0f, Math.max(0f, (r - inset - fade) / r), Math.max(0f, (r - inset) / r), 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mask);
        canvas.restoreToCount(save);
    }
}
