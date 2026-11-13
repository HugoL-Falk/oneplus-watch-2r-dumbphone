package org.op2r.pocketplayer;

import android.content.Context;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Same as `adb shell dumpsys sensorservice restrict op2r.nomatch` / `... enable`,
 * done from the app. Needs android.permission.DUMP (granted via adb, survives reboots).
 * Restrict mode itself is reset by every reboot.
 */
final class SensorRestrict {

    static final String TAG = "OP2R";
    static final String NO_MATCH = "op2r.nomatch";

    private SensorRestrict() {}

    /** Returns "RESTRICTED", "NORMAL", or an error string. */
    static String mode(Context c) {
        try {
            File out = dump(c, new String[0]);
            try (BufferedReader r = new BufferedReader(new FileReader(out))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("Mode :")) {
                        return line.contains("RESTRICTED") ? "RESTRICTED" : "NORMAL";
                    }
                }
            }
            return "UNKNOWN";
        } catch (Throwable t) {
            Log.w(TAG, "restrict: mode failed", t);
            return "ERROR " + t.getClass().getSimpleName();
        }
    }

    /** The whole sensor-service dump as text, or null if it can't be read. */
    static String dumpText(Context c) {
        try {
            File out = dump(c, new String[0]);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(out))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.w(TAG, "restrict: dump failed", t);
            return null;
        }
    }

    private static final java.util.regex.Pattern READING =
            java.util.regex.Pattern.compile("\\)\\s+(-?[0-9.]+),");

    /**
     * Last off-body reading (LLOB: 1 = on wrist, 0 = off) from the dump. In restrict mode the system
     * stays frozen on this value: on wrist = no PIN on wake, off wrist = PIN on every wake.
     * Null if the dump has no readings.
     */
    static Boolean lastOnWrist(String dump) {
        if (dump == null) return null;
        int start = dump.indexOf("LLOB: last");
        if (start < 0) return null;
        Boolean last = null;
        String[] lines = dump.substring(start).split("\n");
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.isEmpty() || !Character.isWhitespace(l.charAt(0))) break;
            java.util.regex.Matcher m = READING.matcher(l);
            if (m.find()) last = Float.parseFloat(m.group(1)) >= 0.5f;
        }
        return last;
    }

    static boolean set(Context c, boolean restricted) {
        try {
            dump(c, restricted ? new String[]{"restrict", NO_MATCH} : new String[]{"enable"});
            String now = mode(c);
            Log.i(TAG, "restrict: requested " + (restricted ? "RESTRICTED" : "NORMAL") + ", now " + now);
            return now.equals(restricted ? "RESTRICTED" : "NORMAL");
        } catch (Throwable t) {
            Log.w(TAG, "restrict: set failed", t);
            return false;
        }
    }

    private static File dump(Context c, String[] args) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class)
                .invoke(null, "sensorservice");
        if (binder == null) throw new IllegalStateException("sensorservice not found");
        File out = new File(c.getCacheDir(), "sensorservice.txt");
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(out,
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                        | ParcelFileDescriptor.MODE_READ_WRITE)) {
            binder.dump(pfd.getFileDescriptor(), args);
        }
        return out;
    }
}
