package org.op2r.pocketplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SensorRestrictTest {

    private static final String DUMP =
            "LLOB: last 10 events\n"
            + "\t 8 (ts=66536.661490560, wall=17:47:58.904) 1.00, \n"
            + "\t 9 (ts=66539.064743059, wall=17:48:01.312) 0.00, \n"
            + "\t10 (ts=66539.862795663, wall=17:48:02.110) 1.00, \n"
            + "Active sensors:\n";

    @Test public void lastReadingOnWrist() {
        assertEquals(Boolean.TRUE, SensorRestrict.lastOnWrist(DUMP));
    }

    @Test public void lastReadingOffWrist() {
        assertEquals(Boolean.FALSE, SensorRestrict.lastOnWrist(DUMP.replace("17:48:02.110) 1.00", "17:48:02.110) 0.00")));
    }

    @Test public void noReadingsIsUnknown() {
        assertNull(SensorRestrict.lastOnWrist("Mode : NORMAL\nActive sensors:\n"));
    }
}
