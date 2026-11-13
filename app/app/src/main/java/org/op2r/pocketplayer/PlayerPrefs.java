package org.op2r.pocketplayer;

import android.content.Context;
import android.content.SharedPreferences;

/** Player settings, shared by Pocket Player and Pocket Tools. Survive restarts and reboots. */
final class PlayerPrefs {

    /** Spectrum style: full ring (default), lower arc, small equalizer above the title, or layered waves along the edge. */
    enum Style {
        RING, ARC, EQUALIZER, LAYERS;

        static Style fromKey(String key) {
            for (Style s : values()) if (s.name().equals(key)) return s;
            return LAYERS; // default
        }
    }

    /** Colour of the spectrum and the volume fill: white, or the cover's vivid colour. */
    enum Tint {
        WHITE, COVER;

        static Tint fromKey(String key) {
            for (Tint t : values()) if (t.name().equals(key)) return t;
            return WHITE;
        }
    }

    private static final String FILE = "player", STYLE = "style", TINT = "tint";

    private PlayerPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Calls {@code onChange} whenever a player setting changes (e.g. from the menu). */
    static SharedPreferences.OnSharedPreferenceChangeListener listen(Context c, Runnable onChange) {
        SharedPreferences.OnSharedPreferenceChangeListener l = (p, key) -> onChange.run();
        prefs(c).registerOnSharedPreferenceChangeListener(l);
        return l;
    }

    static void unlisten(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        if (l != null) prefs(c).unregisterOnSharedPreferenceChangeListener(l);
    }

    static Style style(Context c) {
        return Style.fromKey(prefs(c).getString(STYLE, null));
    }

    static void setStyle(Context c, Style s) {
        prefs(c).edit().putString(STYLE, s.name()).apply();
    }

    static Tint tint(Context c) {
        return Tint.fromKey(prefs(c).getString(TINT, null));
    }

    static void setTint(Context c, Tint t) {
        prefs(c).edit().putString(TINT, t.name()).apply();
    }
}
