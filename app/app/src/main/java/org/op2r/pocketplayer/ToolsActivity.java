package org.op2r.pocketplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The player's menu (opened from its menu button): sensors switch, lock now, player settings,
 * Spotify and connected devices. Runs in its own task and closes when left, so the watch keeps
 * waking into Pocket Player.
 */
public class ToolsActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView sensorsValue, styleValue, tintValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xB3000000); // 70 % black: the player and its cover show through
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(40), dp(40), dp(40), dp(48));
        scroll.addView(list);

        TextView heading = text(15, R.font.figtree_bold, 1f);
        heading.setText("Menu");
        list.addView(heading);

        sensorsValue = row(list, "Sensors", v -> flipSensors());
        row(list, "Lock now", v -> lockNow()).setText("Screen off and lock");
        styleValue = row(list, "Spectrum", v -> {
            PlayerPrefs.Style[] all = PlayerPrefs.Style.values();
            PlayerPrefs.setStyle(this, all[(PlayerPrefs.style(this).ordinal() + 1) % all.length]);
            renderSettings();
        });
        tintValue = row(list, "Colour", v -> {
            PlayerPrefs.Tint[] all = PlayerPrefs.Tint.values();
            PlayerPrefs.setTint(this, all[(PlayerPrefs.tint(this).ordinal() + 1) % all.length]);
            renderSettings();
        });
        row(list, "Open Spotify", v -> openSpotify()).setText("Playlists and library");
        row(list, "Connected devices", v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))).setText("Headphones and speakers");
        setContentView(scroll);
    }

    @Override
    protected void onStart() {
        super.onStart();
        renderSettings();
        readSensors();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish(); // never become the screen the watch wakes into
    }

    // ---------------------------------------------------------------- actions

    private void readSensors() {
        sensorsValue.setText("…");
        new Thread(() -> {
            String mode = SensorRestrict.mode(this);
            Boolean wrist = SensorRestrict.lastOnWrist(SensorRestrict.dumpText(this));
            main.post(() -> showSensors(mode, wrist));
        }).start();
    }

    private void flipSensors() {
        sensorsValue.setText("…");
        new Thread(() -> {
            String before = SensorRestrict.mode(this);
            if (before.equals("NORMAL") || before.equals("RESTRICTED")) {
                SensorRestrict.set(this, before.equals("NORMAL"));
            }
            String now = SensorRestrict.mode(this);
            Boolean wrist = SensorRestrict.lastOnWrist(SensorRestrict.dumpText(this));
            main.post(() -> showSensors(now, wrist));
        }).start();
    }

    /**
     * ON = normal sensors. OFF = restricted: the watch is frozen on the last wrist reading.
     * Frozen "on wrist" = no PIN on wake; frozen "off wrist" = PIN on every wake. To freeze it
     * on wrist, flip to OFF while the back of the watch is against your wrist or palm.
     */
    private void showSensors(String mode, Boolean onWrist) {
        if (mode.equals("RESTRICTED")) {
            if (onWrist == null) sensorsValue.setText("OFF · wrist state unknown");
            else sensorsValue.setText(onWrist ? "OFF · frozen on wrist (no PIN)" : "OFF · frozen off wrist (PIN on wake)");
        } else if (mode.equals("NORMAL")) {
            sensorsValue.setText("ON · normal (PIN when taken off)");
        } else {
            sensorsValue.setText(mode);
        }
    }

    /** Puts Pocket Player in front first, so the next wake shows the player, then locks. */
    private void lockNow() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        main.postDelayed(() -> LockActivity.lock(getApplicationContext()), 300);
    }

    private void openSpotify() {
        // Explicit component: Android 11+ package visibility hides Spotify from
        // getLaunchIntentForPackage, but starting its known activity works.
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(MainActivity.SPOTIFY, "com.spotify.wear.main.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "Spotify not found", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void renderSettings() {
        switch (PlayerPrefs.style(this)) {
            case ARC: styleValue.setText("Lower arc"); break;
            case EQUALIZER: styleValue.setText("Small equalizer"); break;
            case LAYERS: styleValue.setText("Layered waves"); break;
            default: styleValue.setText("Full ring");
        }
        tintValue.setText(PlayerPrefs.tint(this) == PlayerPrefs.Tint.COVER ? "Cover colour" : "White");
    }

    // ---------------------------------------------------------------- UI

    /** A tappable row: label above, current value below. Returns the value view. */
    private TextView row(LinearLayout list, String label, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnClickListener(click);
        TextView l = text(15, R.font.figtree_semibold, 1f);
        l.setText(label);
        TextView v = text(13, R.font.figtree_medium, 0.7f);
        row.addView(l);
        row.addView(v);
        list.addView(row);
        return v;
    }

    private TextView text(int sp, int font, float alpha) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setAlpha(alpha);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(getResources().getFont(font));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
