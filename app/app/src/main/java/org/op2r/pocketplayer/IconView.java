package org.op2r.pocketplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A white glyph with a soft shadow, optionally inside a thin ring (the play/pause button). */
public class IconView extends View {

    enum Glyph {
        PREV("M6 5h2v14H6zM20 5v14L9 12z"),
        NEXT("M16 5h2v14h-2zM4 5v14l11-7z"),
        PLAY("M8 5v14l11-7z"),
        PAUSE("M7 5h4v14H7zM13 5h4v14h-4z"),
        MINUS("M5 11h14v2H5z"),
        PLUS("M11 5h2v14h-2zM5 11h14v2H5z"),
        SPEAKER("M4 9h4l5-4v14l-5-4H4z"),
        MENU("M5 7h14v2H5zM5 11h14v2H5zM5 15h14v2H5z");

        final String data;
        Glyph(String data) { this.data = data; }
    }

    private static final int SHADOW = 0x73000000; // 45 % black

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;
    private Glyph glyph = Glyph.PLAY;
    private boolean ringOn;
    private float iconScale = 1f;

    public IconView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null); // needed for setShadowLayer on paths
        fill.setStyle(Paint.Style.FILL);
        fill.setShadowLayer(1.5f * dp, 0, 0.5f * dp, SHADOW);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * dp);
        ring.setShadowLayer(1.5f * dp, 0, 0.5f * dp, SHADOW);
        setColor(Accent.SOFT_WHITE); // warm off-white: plain white read too sharp
    }

    void setGlyph(Glyph g) { glyph = g; rebuild(); invalidate(); }
    void setRing(boolean on) { ringOn = on; iconScale = on ? 0.45f : 1f; rebuild(); invalidate(); }
    void setColor(int argb) { fill.setColor(argb); ring.setColor(argb); invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) { rebuild(); }

    private void rebuild() {
        path.reset();
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float pad = 2 * dp; // room for the shadow
        // View padding enlarges the touch area without enlarging the glyph.
        float cw = w - getPaddingLeft() - getPaddingRight(), ch = h - getPaddingTop() - getPaddingBottom();
        float box = (Math.min(cw, ch) - 2 * pad) * iconScale;
        parse(glyph.data, path);
        Matrix m = new Matrix();
        m.setScale(box / 24f, box / 24f);
        m.postTranslate(getPaddingLeft() + (cw - box) / 2f, getPaddingTop() + (ch - box) / 2f);
        path.transform(m);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ringOn) {
            float r = Math.min(getWidth(), getHeight()) / 2f - 2 * dp - ring.getStrokeWidth() / 2;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, ring);
        }
        canvas.drawPath(path, fill);
    }

    private static final Pattern TOKEN = Pattern.compile("[MmHhVvLlZz]|-?\\d*\\.?\\d+");

    /** Minimal SVG path parser for the commands the glyphs use: M, L, H, V (and relative), Z. */
    static void parse(String d, Path p) {
        Matcher mt = TOKEN.matcher(d);
        java.util.List<String> tok = new java.util.ArrayList<>();
        while (mt.find()) tok.add(mt.group());
        float x = 0, y = 0, sx = 0, sy = 0;
        char cmd = 'M';
        int i = 0;
        while (i < tok.size()) {
            String s = tok.get(i);
            if (Character.isLetter(s.charAt(0))) { cmd = s.charAt(0); i++; if (cmd == 'Z' || cmd == 'z') { p.close(); x = sx; y = sy; } continue; }
            switch (cmd) {
                case 'M': x = f(tok, i++); y = f(tok, i++); p.moveTo(x, y); sx = x; sy = y; cmd = 'L'; break;
                case 'm': x += f(tok, i++); y += f(tok, i++); p.moveTo(x, y); sx = x; sy = y; cmd = 'l'; break;
                case 'L': x = f(tok, i++); y = f(tok, i++); p.lineTo(x, y); break;
                case 'l': x += f(tok, i++); y += f(tok, i++); p.lineTo(x, y); break;
                case 'H': x = f(tok, i++); p.lineTo(x, y); break;
                case 'h': x += f(tok, i++); p.lineTo(x, y); break;
                case 'V': y = f(tok, i++); p.lineTo(x, y); break;
                case 'v': y += f(tok, i++); p.lineTo(x, y); break;
                default: i++;
            }
        }
    }

    private static float f(java.util.List<String> tok, int i) { return Float.parseFloat(tok.get(i)); }
}
