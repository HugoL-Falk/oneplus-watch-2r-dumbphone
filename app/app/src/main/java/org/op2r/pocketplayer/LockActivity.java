package org.op2r.pocketplayer;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

/** No UI: turns the screen off and locks the watch, then closes. */
public class LockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true); // may start over the PIN screen, then turns the screen off
        if (!lock(this)) {
            Toast.makeText(this, "Lock: device admin not active", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    /** Returns false if the device admin has not been activated yet. */
    static boolean lock(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        ComponentName admin = new ComponentName(context, AdminReceiver.class);
        if (dpm == null || !dpm.isAdminActive(admin)) {
            return false;
        }
        dpm.lockNow();
        return true;
    }
}
