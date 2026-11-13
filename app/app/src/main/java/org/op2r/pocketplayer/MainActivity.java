package org.op2r.pocketplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pocket Player: Spotify controls over the album cover, with song time, volume bar and the edge spectrum. */
public class MainActivity extends Activity {

    static final String TAG = "OP2R";
    static final String SPOTIFY = "com.spotify.music";
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService artThread = Executors.newSingleThreadExecutor();
    private MediaSessionManager sessions;
    private AudioManager audio;
    private MediaController controller;

    /** Cover at 60 % over black = the spec's 40 % dim. */
    private static final float ART_ALPHA = 0.6f;
    private static final int SHADOW = 0x73000000;
    private static final int TICK_MS = 500;
    private static final int SLIDE_MS = 350, FADE_MS = 450;
    /** Two layers so the old cover stays until the new one is ready, then slides or fades. */
    private ImageView artFront, artBack;
    /** +1 after a tap on next, -1 after previous; only counts for 4 s, otherwise crossfade. */
    private int pendingDirection;
    private long pendingDirectionAt;
    /** Text waits for the new cover, at most TEXT_WAIT_MS (slow network or offline). */
    private static final int TEXT_WAIT_MS = 1500;
    private String shownTrack, pendingTrack, pendingTitle, pendingArtist;
    private final Runnable textFallback = () -> applyPendingText(consumeDirection());
    private TextView title, artist;
    private IconView playButton;
    private ProgressBar volumeBar;
    private GradientDrawable volumeFill;
    private TextView timeLabel;
    private SpectrumView spectrum;
    /** Everything above the two cover layers; brought to the front after each cover swap. */
    private FrameLayout overlay;
    /** Text and buttons; faded out while the menu is open. */
    private FrameLayout controls;
    private int coverAccent = Accent.WHITE;
    private Bitmap accentFor;
    private PlayerPrefs.Tint tint = PlayerPrefs.Tint.WHITE;
    private final Runnable tick = new Runnable() {
        @Override public void run() { renderTime(); main.postDelayed(this, TICK_MS); }
    };
    private String artKey; // "artist|album" currently shown or being fetched

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { render(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { render(); }
        @Override public void onSessionDestroyed() { setController(null); pickController(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            list -> pickController();

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { renderVolume(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessions = getSystemService(MediaSessionManager.class);
        audio = getSystemService(AudioManager.class);
        setContentView(buildUi());
        // Clears old .jpg covers from v0.2 and applies the size / age limits.
        artThread.execute(() -> ArtFetcher.trim(new java.io.File(getFilesDir(), "art")));
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent(), main);
        } catch (SecurityException e) {
            title.setText("No media access");
            artist.setText("grant the listener via adb");
        }
        registerReceiver(volumeReceiver, new IntentFilter(VOLUME_CHANGED), Context.RECEIVER_EXPORTED);
        pickController();
        renderVolume();
        applySettings();
        // The menu is see-through, so this activity stays started behind it and onStart isn't
        // called again when the menu closes: listen for setting changes instead.
        prefsListener = PlayerPrefs.listen(this, () -> main.post(this::applySettings));
        main.post(tick);
    }

    /** Leaving for the see-through menu: hide the controls so only the cover shows behind it. */
    @Override
    protected void onPause() {
        super.onPause();
        controls.animate().alpha(0f).setDuration(150).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        controls.animate().alpha(1f).setDuration(150).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        unregisterReceiver(volumeReceiver);
        main.removeCallbacks(tick);
        PlayerPrefs.unlisten(this, prefsListener);
        prefsListener = null;
        setController(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        artThread.shutdownNow();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        artBack = artLayer();
        artFront = artLayer();
        root.addView(artBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(artFront, new FrameLayout.LayoutParams(-1, -1));

        overlay = new FrameLayout(this);
        // Soft dark layer between the cover and the UI so the controls stand out.
        View scrim = new View(this);
        scrim.setBackgroundColor(SCRIM);
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        spectrum = new SpectrumView(this);
        overlay.addView(spectrum, new FrameLayout.LayoutParams(-1, -1));

        // Top: title, artist (both scroll sideways when too long), song time
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        title = text(18, R.font.figtree_bold, 1f);
        artist = text(13, R.font.figtree_medium, 0.85f);
        marquee(title);
        marquee(artist);
        timeLabel = text(12, R.font.figtree_semibold, 0.8f);
        timeLabel.setText(TimeFormat.label(-1, -1));
        // Each line is as wide as the round screen at its height (+ a little), so text only
        // scrolls when it really doesn't fit, and RoundFade dissolves it along the curve.
        top.addView(title, new LinearLayout.LayoutParams(lineWidth(TOP_DP + 12), -2));
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(lineWidth(TOP_DP + 24 + 2 + 9), -2);
        artistLp.topMargin = dp(2);
        top.addView(artist, artistLp);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-1, -2);
        timeLp.topMargin = dp(3);
        top.addView(timeLabel, timeLp);
        controls = new FrameLayout(this);
        RoundFade fadeEdge = new RoundFade(this);
        fadeEdge.addView(top, at(dp(TOP_DP)));
        controls.addView(fadeEdge, new FrameLayout.LayoutParams(-1, -1));
        // Small equalizer at the bottom, under the button rows (the top is for the title block).
        spectrum.setEqualizerAnchor(getResources().getDisplayMetrics().heightPixels / 2f + dp(EQ_BOTTOM_DP));

        // Centre line: prev · play/pause · next (columns shared with the row below)
        LinearLayout transport = columns(
                icon(IconView.Glyph.PREV, 28, 6, v -> transport('p'), SIDE_W, ROW1_H),
                playButton = icon(IconView.Glyph.PLAY, 36, 11, v -> transport('t'), MID_W, ROW1_H),
                icon(IconView.Glyph.NEXT, 28, 6, v -> transport('n'), SIDE_W, ROW1_H));
        controls.addView(transport, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // Row below: - under prev, menu under play, + under next
        LinearLayout row2 = columns(
                icon(IconView.Glyph.MINUS, 20, 10, v -> volume(AudioManager.ADJUST_LOWER), SIDE_W, ROW2_H),
                icon(IconView.Glyph.MENU, 22, 9, v -> startActivity(new Intent(this, ToolsActivity.class)), MID_W, ROW2_H),
                icon(IconView.Glyph.PLUS, 20, 10, v -> volume(AudioManager.ADJUST_RAISE), SIDE_W, ROW2_H));
        int half = Math.round(getResources().getDisplayMetrics().heightPixels / 2f);
        FrameLayout.LayoutParams row2Lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        row2Lp.topMargin = half + dp(ROW1_H) / 2 + dp(2);
        controls.addView(row2, row2Lp);

        // Floating volume bar: only visible for a moment after - or +
        volumeBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volumeBar.setProgressDrawable(barDrawable());
        volumeBar.setAlpha(0f);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(70), dp(4), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        barLp.topMargin = row2Lp.topMargin + dp(ROW2_H) + dp(4);
        controls.addView(volumeBar, barLp);
        overlay.addView(controls, new FrameLayout.LayoutParams(-1, -1));

        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    /** Top of the title block; 30 dp keeps it close to the play button and the equalizer clear of the ring. */
    private static final int TOP_DP = 30;
    /** Buttons are slightly see-through so the cover shows beneath them. */
    private static final float BUTTON_ALPHA = 0.7f;
    /** Bottom of the small equalizer bars, below the centre; clear of the buttons and the rim. */
    static final int EQ_BOTTOM_DP = 102;
    private static final int SIDE_W = 44, MID_W = 62, ROW1_H = 62, ROW2_H = 44, COL_GAP = 12;
    private static final int SCRIM = 0x40000000; // extra 25 % black behind the UI
    private static final int VOLUME_SHOW_MS = 1500;
    private final Runnable hideVolume = () -> volumeBar.animate().alpha(0f).setDuration(300).start();

    /** Three equal-width columns so the two rows line up. */
    private LinearLayout columns(View left, View middle, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ((LinearLayout.LayoutParams) left.getLayoutParams()).rightMargin = dp(COL_GAP);
        ((LinearLayout.LayoutParams) right.getLayoutParams()).leftMargin = dp(COL_GAP);
        row.addView(left);
        row.addView(middle);
        row.addView(right);
        return row;
    }

    /**
     * Width for one text line at {@code yDp} from the top: the round screen's width there minus
     * 2 x 10 dp, so long text scrolls (marquee) and fades out a little in from the edge.
     */
    private int lineWidth(float yDp) {
        float radiusDp = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels) / 2f / density();
        return Math.max(dp(60), dp(Math.round(RoundFade.chord(radiusDp, yDp))) - dp(20));
    }

    private void marquee(TextView t) {
        t.setHorizontalFadingEdgeEnabled(true);
        t.setFadingEdgeLength(dp(14));
        t.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        t.setMarqueeRepeatLimit(-1);
        t.setHorizontallyScrolling(true);
        t.setSelected(true);
    }

    /** Full-width block whose top sits {@code topPx} below the top of the screen. */
    private FrameLayout.LayoutParams at(int topPx) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = topPx;
        return lp;
    }

    private TextView text(int sp, int font, float alpha) {
        TextView t = new TextView(this);
        t.setTextColor(Accent.SOFT_WHITE);
        t.setAlpha(alpha);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(1.5f * density(), 0, 0.5f * density(), SHADOW);
        t.setTypeface(getResources().getFont(font));
        return t;
    }

    /** Glyph of {@code glyphDp} in a {@code wDp} x {@code hDp} touch box. */
    private IconView icon(IconView.Glyph g, int glyphDp, int padDp, View.OnClickListener click, int wDp, int hDp) {
        IconView v = new IconView(this);
        v.setGlyph(g);
        v.setAlpha(BUTTON_ALPHA);
        int padX = Math.max(0, (dp(wDp) - dp(glyphDp)) / 2 - dp(2));
        int padY = Math.max(0, (dp(hDp) - dp(glyphDp)) / 2 - dp(2));
        v.setPadding(padX, padY, padX, padY);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        if (click != null) v.setOnClickListener(click);
        return v;
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(4));
        return d;
    }

    private LayerDrawable barDrawable() {
        GradientDrawable track = round(0x40FFFFFF);
        volumeFill = round(Accent.SOFT_WHITE);
        ClipDrawable fill = new ClipDrawable(volumeFill, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{track, fill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- media

    private ComponentName listenerComponent() {
        return new ComponentName(this, MediaListener.class);
    }

    private void pickController() {
        List<MediaController> list;
        try {
            list = sessions.getActiveSessions(listenerComponent());
        } catch (SecurityException e) {
            return;
        }
        MediaController pick = null;
        for (MediaController c : list) {
            if (SPOTIFY.equals(c.getPackageName())) pick = c;
        }
        setController(pick);
    }

    private void setController(MediaController c) {
        if (controller != null) controller.unregisterCallback(controllerCallback);
        controller = c;
        if (controller != null) controller.registerCallback(controllerCallback, main);
        render();
    }

    private void render() {
        if (controller == null) {
            main.removeCallbacks(textFallback);
            pendingTrack = null;
            shownTrack = null;
            title.setText("Spotify not playing");
            artist.setText("");
            artKey = null;
            swapArt(null);
            playButton.setGlyph(IconView.Glyph.PLAY);
            renderTime();
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
                    // Same album: the cover stays, only the text moves.
                    showText(t, a, consumeDirection());
                } else {
                    // New album: hold the text back so it moves together with the cover.
                    pendingTrack = track;
                    pendingTitle = t;
                    pendingArtist = a;
                    main.removeCallbacks(textFallback);
                    main.postDelayed(textFallback, TEXT_WAIT_MS);
                    Bitmap embedded = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (embedded != null) {
                        artKey = a + "|" + al;
                        swapArt(embedded);
                    } else {
                        loadArt(a, al);
                    }
                }
            }
        }
        PlaybackState s = controller.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        playButton.setGlyph(playing ? IconView.Glyph.PAUSE : IconView.Glyph.PLAY);
        renderTime();
    }

    /** Song time label, spectrum progress and playing state. Runs every TICK_MS while visible. */
    private void renderTime() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (s == null) {
            timeLabel.setText(TimeFormat.label(-1, -1));
            spectrum.setPlaying(false);
            spectrum.setProgress(0f);
            return;
        }
        boolean playing = s.getState() == PlaybackState.STATE_PLAYING;
        long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = TimeFormat.position(s.getPosition(), s.getLastPositionUpdateTime(), s.getPlaybackSpeed(),
                playing, SystemClock.elapsedRealtime(), duration);
        timeLabel.setText(TimeFormat.label(pos, duration));
        spectrum.setPlaying(playing);
        spectrum.setTimeline(s.getPosition(), s.getLastPositionUpdateTime(), s.getPlaybackSpeed(), duration, playing);
    }

    private android.content.SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private void applySettings() {
        spectrum.setStyle(PlayerPrefs.style(this));
        tint = PlayerPrefs.tint(this);
        applyTint();
    }

    /** Spectrum and volume fill: the cover's vivid colour, or white. */
    private void applyTint() {
        int c = tint == PlayerPrefs.Tint.COVER ? coverAccent : Accent.WHITE;
        spectrum.setColor(c);
        volumeFill.setColor(c);
    }

    private void loadArt(String artistName, String album) {
        String key = artistName + "|" + album;
        if (key.equals(artKey)) return; // already shown or on its way
        artKey = key;
        // The old cover stays visible while the new one is looked up.
        artThread.execute(() -> {
            Bitmap b = ArtFetcher.get(this, artistName, album);
            main.post(() -> {
                if (key.equals(artKey)) swapArt(b); // null = no cover found: fade to dark
            });
        });
    }

    private ImageView artLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    /**
     * Brings in a new cover: slides from the right after "next", from the left after
     * "previous", crossfades otherwise (e.g. track changed from the headphones).
     * A null bitmap fades the current cover out to black.
     */
    private void swapArt(Bitmap b) {
        int dir = consumeDirection();
        applyPendingText(dir);
        ImageView out = artFront, in = artBack;
        out.animate().cancel();
        in.animate().cancel();

        if (b == null) {
            out.animate().alpha(0f).translationX(0).setDuration(FADE_MS).start();
            accentFor = null;
            coverAccent = Accent.WHITE;
            applyTint();
            return;
        }
        accentFor = b;
        artThread.execute(() -> {
            int c = Accent.fromCover(b, 1 - ART_ALPHA);
            main.post(() -> {
                if (accentFor == b) { coverAccent = c; applyTint(); }
            });
        });
        float w = artFront.getWidth() > 0 ? artFront.getWidth() : dp(233);
        in.setImageBitmap(b);
        in.bringToFront();
        in.setTranslationX(0);
        if (dir == 0 || out.getAlpha() == 0f) {
            in.setAlpha(0f);
            in.animate().alpha(ART_ALPHA).setDuration(FADE_MS).start();
            out.animate().alpha(0f).setDuration(FADE_MS).start();
        } else {
            in.setAlpha(ART_ALPHA);
            in.setTranslationX(dir * w);
            in.animate().translationX(0).setDuration(SLIDE_MS).start();
            out.animate().translationX(-dir * w).alpha(0f).setDuration(SLIDE_MS).start();
        }
        // The spectrum and controls must stay on top of both cover layers.
        overlay.bringToFront();
        artFront = in;
        artBack = out;
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString();
    }

    /** +1 / -1 if next / previous was tapped in the last 4 s, else 0. Resets it. */
    private int consumeDirection() {
        int dir = android.os.SystemClock.uptimeMillis() - pendingDirectionAt < 4000 ? pendingDirection : 0;
        pendingDirection = 0;
        return dir;
    }

    private void applyPendingText(int dir) {
        main.removeCallbacks(textFallback);
        if (pendingTrack == null) return;
        String t = pendingTitle, a = pendingArtist;
        pendingTrack = null;
        showText(t, a, dir);
    }

    /** Slides the title and artist out and the new ones in, in step with the cover. */
    private void showText(String t, String a, int dir) {
        shownTrack = t + "|" + a;
        float w = artFront.getWidth() > 0 ? artFront.getWidth() / 2f : dp(116);
        int half = (dir == 0 ? FADE_MS : SLIDE_MS) / 2;
        for (TextView v : new TextView[]{title, artist}) {
            String text = v == title ? t : a;
            v.animate().cancel();
            if (v.getText().length() == 0) { // first render: no animation
                v.setText(text);
                v.setAlpha(1f);
                v.setTranslationX(0);
                continue;
            }
            v.animate().alpha(0f).translationX(-dir * w).setDuration(half).withEndAction(() -> {
                v.setText(text);
                v.setTranslationX(dir * w);
                v.animate().alpha(1f).translationX(0).setDuration(half).start();
            }).start();
        }
    }

    private void setDirection(int dir) {
        pendingDirection = dir;
        pendingDirectionAt = android.os.SystemClock.uptimeMillis();
    }

    private void transport(char what) {
        if (controller == null) {
            if (what == 't') startMusic();
            return;
        }
        MediaController.TransportControls t = controller.getTransportControls();
        switch (what) {
            case 'p': setDirection(-1); t.skipToPrevious(); break;
            case 'n': setDirection(1); t.skipToNext(); break;
            default:
                PlaybackState s = controller.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause();
                else t.play();
        }
    }

    /**
     * No Spotify session: send a "play" media key. The system routes it to the last media app's
     * button receiver (Spotify), which resumes its last queue, like a headphone button would.
     */
    private void startMusic() {
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
        audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
        Log.i(TAG, "start music: sent MEDIA_PLAY");
    }

    /** Flags 0: no system volume pop-up; the floating bar shows the level for a moment. */
    private void volume(int direction) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        renderVolume();
        main.removeCallbacks(hideVolume);
        volumeBar.animate().cancel();
        volumeBar.animate().alpha(1f).setDuration(120).start();
        main.postDelayed(hideVolume, VOLUME_SHOW_MS);
    }

    private void renderVolume() {
        volumeBar.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeBar.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        Log.i(TAG, "volume " + volumeBar.getProgress() + "/" + volumeBar.getMax());
    }
}
