package org.op2r.pocketplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.wear.watchface.CanvasType;
import androidx.wear.watchface.ComplicationSlot;
import androidx.wear.watchface.ComplicationSlotsManager;
import androidx.wear.watchface.DrawMode;
import androidx.wear.watchface.ListenableCanvasRenderer2;
import androidx.wear.watchface.ListenableWatchFaceService;
import androidx.wear.watchface.TapEvent;
import androidx.wear.watchface.TapType;
import androidx.wear.watchface.WatchFace;
import androidx.wear.watchface.WatchFaceType;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;
import androidx.wear.watchface.style.UserStyleSchema;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pocket Player as a watch face: same cover, text, buttons and edge animation as the app, drawn
 * on the face's canvas. Faces only get plain taps, so the buttons are tap zones.
 */
public class PlayerFaceService extends ListenableWatchFaceService {
    private static final String TAG = "PocketFace";

    @NonNull @Override
    protected UserStyleSchema createUserStyleSchema() {
        return new UserStyleSchema(Collections.emptyList());
    }

    @NonNull @Override
    protected ListenableFuture<WatchFace> createWatchFaceFuture(
            @NonNull SurfaceHolder holder, @NonNull WatchState watchState,
            @NonNull ComplicationSlotsManager slots, @NonNull CurrentUserStyleRepository styles) {
        final FaceRenderer renderer = new FaceRenderer(holder, styles, watchState);
        WatchFace face = new WatchFace(WatchFaceType.DIGITAL, renderer);
        face.setTapListener(new WatchFace.TapListener() {
            @Override
            public void onTapEvent(int tapType, @NonNull TapEvent tapEvent, @Nullable ComplicationSlot slot) {
                if (tapType == TapType.UP) renderer.onTap(tapEvent.getXPos(), tapEvent.getYPos());
            }
        });
        return Futures.immediateFuture(face);
    }

    private final class FaceRenderer extends ListenableCanvasRenderer2<FaceRenderer.Assets> {
        class Assets implements androidx.wear.watchface.Renderer.SharedAssets {
            @Override public void onDestroy() {}
        }

        // ---- layout, in dp (same numbers as MainActivity)
        private static final int TOP_DP = 30, SIDE_W = 44, MID_W = 62, ROW1_H = 62, ROW2_H = 44, COL_GAP = 12;
        private static final int CLOCK_BASELINE_FROM_RIM = 25; // dp
        private static final float BUTTON_ALPHA = 0.7f, ART_ALPHA = 0.6f;
        private static final int SCRIM = 0x40000000, SLIDE_MS = 350, FADE_MS = 450, TEXT_WAIT_MS = 1500, VOLUME_SHOW_MS = 1500;

        private final Context ctx = PlayerFaceService.this;
        private final float dp = ctx.getResources().getDisplayMetrics().density;
        private final float sp = ctx.getResources().getDisplayMetrics().scaledDensity;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService artThread = Executors.newSingleThreadExecutor();
        private final MediaSessionManager sessions = getSystemService(MediaSessionManager.class);
        private final AudioManager audio = getSystemService(AudioManager.class);
        private MediaController controller;

        private final SpectrumView spectrum = new SpectrumView(ctx);
        private final IconView prev = icon(IconView.Glyph.PREV, 28, 6, SIDE_W, ROW1_H);
        private final IconView play = icon(IconView.Glyph.PLAY, 36, 11, MID_W, ROW1_H);
        private final IconView next = icon(IconView.Glyph.NEXT, 28, 6, SIDE_W, ROW1_H);
        private final IconView minus = icon(IconView.Glyph.MINUS, 20, 10, SIDE_W, ROW2_H);
        private final IconView menu = icon(IconView.Glyph.MENU, 22, 9, MID_W, ROW2_H);
        private final IconView plus = icon(IconView.Glyph.PLUS, 20, 10, SIDE_W, ROW2_H);

        private final Paint artPaint = new Paint(Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titleP = textPaint(18, R.font.figtree_bold, 1f), artistP = textPaint(13, R.font.figtree_medium, 0.85f);
        private final Paint timeP = textPaint(12, R.font.figtree_semibold, 0.8f), clockP = textPaint(12, R.font.figtree_semibold, 0.8f);
        private final Paint mask = new Paint();
        private final Paint bigClock = clockPaint();

        // ---- cover and text state
        private Bitmap cover, prevCover, accentFor;
        private long coverAt = -1000000;
        private int coverDir, pendingDir;
        private long pendingDirAt;
        private String artKey, shownTrack, pendingTrack, pendingTitle, pendingArtist;
        private String title = "Spotify not playing", artist = "";
        private long textAt = -1000000;
        private int textDir;
        private int coverAccent = Accent.WHITE;
        private PlayerPrefs.Tint tint = PlayerPrefs.Tint.WHITE;
        private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
        private long volumeShownAt = -1000000;
        private long lastPlayingAt = -1000000;
        private boolean laidOut;
        private int width = 1, height = 1;

        private final Runnable textFallback = () -> applyPendingText(consumeDir());
        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata m) { onMedia(); }
            @Override public void onPlaybackStateChanged(PlaybackState s) { onMedia(); }
            @Override public void onSessionDestroyed() { setController(null); pickController(); }
        };
        private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = l -> pickController();
        private final android.content.BroadcastReceiver lockReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { invalidate(); }
        };

        FaceRenderer(SurfaceHolder holder, CurrentUserStyleRepository styles, WatchState ws) {
            super(holder, styles, ws, CanvasType.SOFTWARE, 33L, false);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            spectrum.setDrawnExternally(true);
            artPaint.setAlpha(255);
            try {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, new ComponentName(ctx, MediaListener.class), main);
            } catch (SecurityException e) {
                title = "No media access";
            }
            pickController();
            prefsListener = PlayerPrefs.listen(ctx, () -> main.post(this::applySettings));
            ctx.registerReceiver(lockReceiver, new android.content.IntentFilter(Intent.ACTION_USER_PRESENT));
            ctx.registerReceiver(lockReceiver, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
            ctx.registerReceiver(lockReceiver, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF));
            applySettings();
            artThread.execute(() -> ArtFetcher.trim(new java.io.File(ctx.getFilesDir(), "art")));
        }

        // ------------------------------------------------------------ media

        private void pickController() {
            List<MediaController> list;
            try {
                list = sessions.getActiveSessions(new ComponentName(ctx, MediaListener.class));
            } catch (SecurityException e) {
                return;
            }
            MediaController pick = null;
            for (MediaController c : list) if (MainActivity.SPOTIFY.equals(c.getPackageName())) pick = c;
            setController(pick);
        }

        private void setController(MediaController c) {
            if (controller != null) controller.unregisterCallback(callback);
            controller = c;
            if (controller != null) controller.registerCallback(callback, main);
            onMedia();
        }

        private void onMedia() {
            if (controller == null) {
                main.removeCallbacks(textFallback);
                pendingTrack = null;
                shownTrack = null;
                title = "Spotify not playing";
                artist = "";
                artKey = null;
                swapCover(null);
                invalidate();
                return;
            }
            MediaMetadata m = controller.getMetadata();
            if (m != null) {
                String t = str(m.getText(MediaMetadata.METADATA_KEY_TITLE));
                String a = str(m.getText(MediaMetadata.METADATA_KEY_ARTIST));
                String al = str(m.getText(MediaMetadata.METADATA_KEY_ALBUM));
                String track = t + "|" + a;
                if (!track.equals(shownTrack) && !track.equals(pendingTrack)) {
                    if ((a + "|" + al).equals(artKey)) {
                        showText(t, a, consumeDir());
                    } else {
                        pendingTrack = track;
                        pendingTitle = t;
                        pendingArtist = a;
                        main.removeCallbacks(textFallback);
                        main.postDelayed(textFallback, TEXT_WAIT_MS);
                        Bitmap embedded = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if (embedded != null) {
                            artKey = a + "|" + al;
                            swapCover(embedded);
                        } else {
                            loadArt(a, al);
                        }
                    }
                }
            }
            invalidate();
        }

        private void loadArt(String artistName, String album) {
            final String key = artistName + "|" + album;
            if (key.equals(artKey)) return;
            artKey = key;
            artThread.execute(() -> {
                Bitmap b = ArtFetcher.get(ctx, artistName, album);
                main.post(() -> { if (key.equals(artKey)) swapCover(b); });
            });
        }

        private void swapCover(Bitmap b) {
            int dir = consumeDir();
            applyPendingText(dir);
            prevCover = cover;
            cover = b;
            coverDir = dir;
            coverAt = SystemClock.uptimeMillis();
            accentFor = b;
            if (b == null) {
                coverAccent = Accent.WHITE;
                applySettings();
            } else {
                artThread.execute(() -> {
                    int c = Accent.fromCover(b, 1 - ART_ALPHA);
                    main.post(() -> { if (accentFor == b) { coverAccent = c; applySettings(); } });
                });
            }
            invalidate();
        }

        private void applyPendingText(int dir) {
            main.removeCallbacks(textFallback);
            if (pendingTrack == null) return;
            String t = pendingTitle, a = pendingArtist;
            pendingTrack = null;
            showText(t, a, dir);
        }

        private void showText(String t, String a, int dir) {
            shownTrack = t + "|" + a;
            boolean first = title.isEmpty() || title.equals("Spotify not playing");
            title = t;
            artist = a;
            textDir = dir;
            textAt = first ? -1000000 : SystemClock.uptimeMillis();
            invalidate();
        }

        private int consumeDir() {
            int d = SystemClock.uptimeMillis() - pendingDirAt < 4000 ? pendingDir : 0;
            pendingDir = 0;
            return d;
        }

        private void applySettings() {
            spectrum.setStyle(PlayerPrefs.style(ctx));
            tint = PlayerPrefs.tint(ctx);
            spectrum.setColor(tint == PlayerPrefs.Tint.COVER ? coverAccent : Accent.WHITE);
            invalidate();
        }

        private void startMusic() {
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
        }

        // ------------------------------------------------------------ taps

        void onTap(int x, int y) {
            if (isLocked()) return; // locked: clock only, no controls
            float cx = width / 2f, cy = height / 2f, side = dp(MID_W / 2 + COL_GAP + SIDE_W / 2);
            float r1 = cy, r2 = cy + dp(ROW1_H / 2 + 2 + ROW2_H / 2);
            float slop = dp(6);
            String hit = null;
            if (inBox(x, y, cx - side, r1, SIDE_W, ROW1_H, slop)) hit = "prev";
            else if (inBox(x, y, cx, r1, MID_W, ROW1_H, slop)) hit = "toggle";
            else if (inBox(x, y, cx + side, r1, SIDE_W, ROW1_H, slop)) hit = "next";
            else if (inBox(x, y, cx - side, r2, SIDE_W, ROW2_H, slop)) hit = "minus";
            else if (inBox(x, y, cx, r2, MID_W, ROW2_H, slop)) hit = "menu";
            else if (inBox(x, y, cx + side, r2, SIDE_W, ROW2_H, slop)) hit = "plus";
            if (hit == null) return;
            Log.i(TAG, "tap " + hit);
            switch (hit) {
                case "minus": volume(AudioManager.ADJUST_LOWER); break;
                case "plus": volume(AudioManager.ADJUST_RAISE); break;
                case "menu":
                    Intent i = new Intent(ctx, ToolsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(i); } catch (RuntimeException e) { Log.w(TAG, "menu start failed", e); }
                    break;
                default: transport(hit);
            }
            invalidate();
        }

        private boolean inBox(float x, float y, float cx, float cy, int wDp, int hDp, float slop) {
            return Math.abs(x - cx) <= dp(wDp) / 2f + slop && Math.abs(y - cy) <= dp(hDp) / 2f;
        }

        private void transport(String what) {
            if (controller == null) {
                if (what.equals("toggle")) startMusic();
                return;
            }
            MediaController.TransportControls t = controller.getTransportControls();
            if (what.equals("prev")) { pendingDir = -1; pendingDirAt = SystemClock.uptimeMillis(); t.skipToPrevious(); }
            else if (what.equals("next")) { pendingDir = 1; pendingDirAt = SystemClock.uptimeMillis(); t.skipToNext(); }
            else {
                PlaybackState s = controller.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
            }
        }

        private void volume(int direction) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            volumeShownAt = SystemClock.uptimeMillis();
        }

        // ------------------------------------------------------------ drawing

        @NonNull @Override public ListenableFuture<kotlin.Unit> initFuture() { return Futures.immediateFuture(kotlin.Unit.INSTANCE); }

        @NonNull @Override public ListenableFuture<Assets> createSharedAssetsFuture() { return Futures.immediateFuture(new Assets()); }

        @Override
        public void onDestroy() {
            if (controller != null) controller.unregisterCallback(callback);
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            PlayerPrefs.unlisten(ctx, prefsListener);
            ctx.unregisterReceiver(lockReceiver);
            artThread.shutdownNow();
            super.onDestroy();
        }

        @Override
        public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime time, @NonNull Assets assets) {
            width = bounds.width();
            height = bounds.height();
            if (getRenderParameters().getDrawMode() == DrawMode.AMBIENT) {
                renderAmbient(canvas, bounds, time);
                return;
            }
            layoutOnce();
            long now = SystemClock.uptimeMillis();
            canvas.drawColor(Color.BLACK);

            PlaybackState s = controller == null ? null : controller.getPlaybackState();
            MediaMetadata m = controller == null ? null : controller.getMetadata();
            boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (playing) lastPlayingAt = now;
            long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);

            boolean animating = drawCover(canvas, now);
            fill.setColor(SCRIM);
            canvas.drawRect(bounds, fill);

            // edge animation
            spectrum.setPlaying(playing);
            if (s != null) spectrum.setTimeline(s.getPosition(), s.getLastPositionUpdateTime(), s.getPlaybackSpeed(), duration, playing);
            else { spectrum.setProgress(0f); }
            spectrum.draw(canvas);

            boolean locked = isLocked(); // locked: same screen, no buttons or volume bar, text a little lower
            animating |= drawTexts(canvas, now, s, duration, playing, time, locked);
            if (!locked) drawButtons(canvas, playing);
            if (!locked) animating |= drawVolume(canvas, now);

            boolean settling = now - lastPlayingAt < 2500;
            long delay = (playing || settling || animating) ? 33L : 500L;
            if (getInteractiveDrawModeUpdateDelayMillis() != delay) setInteractiveDrawModeUpdateDelayMillis(delay);
        }

        /** True while the watch needs a PIN: then the face shows only the clock. */
        private boolean isLocked() {
            return getSystemService(android.app.KeyguardManager.class).isDeviceLocked();
        }

        private void renderAmbient(Canvas canvas, Rect bounds, ZonedDateTime time) {
            canvas.drawColor(Color.BLACK);
            clockP.setTextSize(sp * 40);
            clockP.setAlpha(150);
            canvas.drawText(String.format("%02d:%02d", time.getHour(), time.getMinute()), bounds.exactCenterX(), bounds.exactCenterY() - (clockP.ascent() + clockP.descent()) / 2f, clockP);
            clockP.setTextSize(sp * 12);
            clockP.setAlpha(204);
        }

        private void layoutOnce() {
            if (laidOut) return;
            spectrum.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            spectrum.layout(0, 0, width, height);
            spectrum.setEqualizerAnchor(height / 2f + dp(MainActivity.EQ_BOTTOM_DP)); // equalizer style: bars at the bottom
            for (IconView v : new IconView[]{prev, play, next, minus, menu, plus}) {
                v.measure(View.MeasureSpec.makeMeasureSpec(v.getLayoutParams().width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.getLayoutParams().height, View.MeasureSpec.EXACTLY));
                v.layout(0, 0, v.getLayoutParams().width, v.getLayoutParams().height);
            }
            laidOut = true;
        }

        private float ease(float p) { return 1f - (1f - p) * (1f - p); }

        /** Returns true while a cover transition is running. */
        private boolean drawCover(Canvas c, long now) {
            long dt = now - coverAt;
            int dur = coverDir == 0 ? FADE_MS : SLIDE_MS;
            float p = Math.min(1f, Math.max(0f, dt / (float) dur));
            boolean run = p < 1f;
            float e = ease(p);
            boolean slide = coverDir != 0 && prevCover != null;
            if (prevCover != null && run) {
                artPaint.setAlpha(Math.round(255 * ART_ALPHA * (slide ? 1f - e * 0.0f : 1f - e)));
                drawBitmap(c, prevCover, slide ? -coverDir * width * e : 0f);
            }
            if (cover != null) {
                artPaint.setAlpha(Math.round(255 * ART_ALPHA * (slide || !run ? 1f : e)));
                drawBitmap(c, cover, slide && run ? coverDir * width * (1f - e) : 0f);
            }
            if (!run) prevCover = null;
            return run;
        }

        private void drawBitmap(Canvas c, Bitmap b, float dx) {
            int bw = b.getWidth(), bh = b.getHeight(), side = Math.min(bw, bh);
            Rect src = new Rect((bw - side) / 2, (bh - side) / 2, (bw + side) / 2, (bh + side) / 2);
            RectF dst = new RectF(dx, 0, dx + width, height);
            c.drawBitmap(b, src, dst, artPaint);
        }

        private boolean drawTexts(Canvas c, long now, PlaybackState s, long duration, boolean playing, ZonedDateTime time, boolean locked) {
            float cx = width / 2f, radius = Math.min(width, height) / 2f;

            float tp = Math.min(1f, (now - textAt) / (float) FADE_MS);
            boolean anim = tp < 1f;
            float e = ease(tp);
            float titleH = titleP.descent() - titleP.ascent(), artistH = artistP.descent() - artistP.ascent(), timeH = timeP.descent() - timeP.ascent();
            // Locked (no buttons): centre the block between the clock on the rim and the screen's centre line.
            float blockH = titleH + dp(2) + artistH + dp(3) + timeH;
            float titleTop = locked ? (dp(CLOCK_BASELINE_FROM_RIM) + height / 2f) / 2f - blockH / 2f : dp(TOP_DP + 4);
            float artistTop = titleTop + titleH + dp(2);
            float timeTop = artistTop + artistH + dp(3);

            long pos = s == null ? -1 : TimeFormat.position(s.getPosition(), s.getLastPositionUpdateTime(), s.getPlaybackSpeed(),
                    playing, SystemClock.elapsedRealtime(), duration);
            String timeText = TimeFormat.label(s == null ? -1 : pos, s == null ? -1 : duration);

            int save = c.saveLayerAlpha(0, 0, width, height, 255);
            float slideX = textDir * width / 2f * (1f - e);
            boolean m1 = marquee(c, title, titleP, cx + slideX, titleTop, chordWidth(radius, titleTop + dp(12)), now, e);
            boolean m2 = marquee(c, artist, artistP, cx + slideX, artistTop, chordWidth(radius, artistTop + dp(10)), now, e);
            timeP.setAlpha(Math.round(204 * (controller == null ? 0f : 1f)));
            c.drawText(timeText, cx, timeTop - timeP.ascent(), timeP);
            // dissolve toward the round edge
            mask.setShader(roundMask(radius));
            c.drawRect(0, 0, width, height, mask);
            c.restoreToCount(save);
            drawClock(c, time);
            return anim || m1 || m2;
        }

        private final android.graphics.Path clockArc = new android.graphics.Path();

        /** Wall clock along the top rim: text follows the curve of the screen. */
        private void drawClock(Canvas c, ZonedDateTime time) {
            float cx = width / 2f, cy = height / 2f;
            float r = Math.min(cx, cy) - dp(CLOCK_BASELINE_FROM_RIM); // baseline circle, concentric with the rim
            clockArc.reset();
            clockArc.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f); // 9 o'clock over the top to 3
            String text = String.format("%02d:%02d", time.getHour(), time.getMinute());
            bigClock.setTextAlign(Paint.Align.LEFT); // path text is placed from its start: centre it by hand
            float start = (float) (Math.PI * r / 2) - bigClock.measureText(text) / 2f;
            c.drawTextOnPath(text, clockArc, start, 0f, bigClock);
        }

        private float chordWidth(float radius, float yPx) {
            return Math.max(dp(60), RoundFade.chord(radius, yPx) - dp(20));
        }

        private android.graphics.RadialGradient roundMask(float r) {
            float fade = 18 * dp, inset = 6 * dp;
            return new android.graphics.RadialGradient(width / 2f, height / 2f, r,
                    new int[]{0xFF000000, 0xFF000000, 0x00000000, 0x00000000},
                    new float[]{0f, Math.max(0f, (r - inset - fade) / r), Math.max(0f, (r - inset) / r), 1f}, Shader.TileMode.CLAMP);
        }

        /** Draws one line centred, or scrolling sideways with soft ends if wider than {@code win}. */
        private boolean marquee(Canvas c, String text, Paint p, float cx, float top, float win, long now, float alphaE) {
            if (text.isEmpty()) return false;
            p.setAlpha(baseAlpha(p, alphaE));
            float base = top - p.ascent();
            float w = p.measureText(text);
            if (w <= win) {
                c.drawText(text, cx, base, p);
                return false;
            }
            float gap = dp(36), speed = dp(28), pause = 1.6f;
            float period = (w + gap) / speed;
            float t = ((now - Math.max(textAt, -1)) / 1000f) % (period + pause);
            float off = t < pause ? 0f : (t - pause) * speed;
            float left = cx - win / 2f;
            int save = c.saveLayerAlpha(left - 2, top - dp(4), left + win + 2, top + (p.descent() - p.ascent()) + dp(4), 255);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(text, left - off, base, p);
            c.drawText(text, left - off + w + gap, base, p);
            p.setTextAlign(Paint.Align.CENTER);
            float f = dp(14);
            Paint edge = new Paint();
            edge.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            edge.setShader(new LinearGradient(left, 0, left + win, 0, new int[]{0, 0xFF000000, 0xFF000000, 0},
                    new float[]{0f, f / win, 1f - f / win, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(left - 2, top - dp(4), left + win + 2, top + (p.descent() - p.ascent()) + dp(4), edge);
            c.restoreToCount(save);
            return true;
        }

        private int baseAlpha(Paint p, float e) {
            return Math.round((p == titleP ? 255 : p == artistP ? 217 : 204) * e);
        }

        private void drawButtons(Canvas c, boolean playing) {
            play.setGlyph(playing ? IconView.Glyph.PAUSE : IconView.Glyph.PLAY);
            float cx = width / 2f, cy = height / 2f, side = dp(MID_W / 2 + COL_GAP + SIDE_W / 2);
            float r2 = cy + dp(ROW1_H / 2 + 2 + ROW2_H / 2);
            put(c, prev, cx - side, cy);
            put(c, play, cx, cy);
            put(c, next, cx + side, cy);
            put(c, minus, cx - side, r2);
            put(c, menu, cx, r2);
            put(c, plus, cx + side, r2);
        }

        private void put(Canvas c, IconView v, float cx, float cy) {
            int save = c.saveLayerAlpha(cx - v.getWidth() / 2f - 4, cy - v.getHeight() / 2f - 4, cx + v.getWidth() / 2f + 4, cy + v.getHeight() / 2f + 4, Math.round(255 * BUTTON_ALPHA));
            c.translate(cx - v.getWidth() / 2f, cy - v.getHeight() / 2f);
            v.draw(c);
            c.restoreToCount(save);
        }

        private boolean drawVolume(Canvas c, long now) {
            long dt = now - volumeShownAt;
            if (dt > VOLUME_SHOW_MS + 300) return false;
            float a = dt < VOLUME_SHOW_MS ? 1f : 1f - (dt - VOLUME_SHOW_MS) / 300f;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            float cx = width / 2f, top = height / 2f + dp(ROW1_H / 2 + 2 + ROW2_H) + dp(4);
            float w = dp(70), h = dp(4);
            fill.setColor(Color.argb(Math.round(64 * a), 255, 255, 255));
            c.drawRoundRect(cx - w / 2, top, cx + w / 2, top + h, dp(4), dp(4), fill);
            int col = tint == PlayerPrefs.Tint.COVER ? coverAccent : Accent.SOFT_WHITE;
            fill.setColor(Color.argb(Math.round(255 * a), Color.red(col), Color.green(col), Color.blue(col)));
            c.drawRoundRect(cx - w / 2, top, cx - w / 2 + w * cur / Math.max(1, max), top + h, dp(4), dp(4), fill);
            return true;
        }

        @Override
        public void renderHighlightLayer(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime time, @NonNull Assets assets) {
            canvas.drawColor(Color.TRANSPARENT);
        }

        // ------------------------------------------------------------ helpers

        private int dp(int v) { return Math.round(v * dp); }
        private float dp(float v) { return v * dp; }

        private Paint textPaint(int size, int font, float alpha) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextSize(size * sp);
            p.setTypeface(ctx.getResources().getFont(font));
            p.setColor(Accent.SOFT_WHITE);
            p.setAlpha(Math.round(255 * alpha));
            p.setTextAlign(Paint.Align.CENTER);
            p.setShadowLayer(1.5f * dp, 0, 0.5f * dp, 0x73000000);
            return p;
        }

        private Paint clockPaint() {
            Paint p = textPaint(17, R.font.figtree_bold, 0.95f);
            p.setFakeBoldText(true);
            p.setLetterSpacing(0.06f);
            return p;
        }

        private IconView icon(IconView.Glyph g, int glyphDp, int padDp, int wDp, int hDp) {
            IconView v = new IconView(ctx);
            v.setGlyph(g);
            int padX = Math.max(0, (dp(wDp) - dp(glyphDp)) / 2 - dp(2));
            int padY = Math.max(0, (dp(hDp) - dp(glyphDp)) / 2 - dp(2));
            v.setPadding(padX, padY, padX, padY);
            v.setLayoutParams(new android.view.ViewGroup.LayoutParams(dp(wDp), dp(hDp)));
            return v;
        }

        private String str(CharSequence c) { return c == null ? "" : c.toString(); }
    }
}
