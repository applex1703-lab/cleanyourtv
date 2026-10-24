package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MemoryCleanPolicyTest {
    private boolean eligible(String pkg, boolean system, boolean vpn, boolean launchable, boolean shared) {
        return MemoryCleanPolicy.eligible(pkg, launchable, system, vpn, shared, "com.gathin.tvhome.v2");
    }
    @Test public void ordinaryAppsAndPreinstalledYoutubeCanClose() {
        assertTrue(eligible("rtb.mobile.android", false, false, true, false));
        assertTrue(eligible("com.example.newgame", false, false, true, false));
        assertTrue(eligible("com.google.android.youtube.tv", true, false, true, false));
    }
    @Test public void vpnAndSystemComponentsAreProtected() {
        assertFalse(eligible("com.nebula.karing", false, true, true, false));
        assertFalse(eligible("com.google.android.katniss", true, false, true, false));
        assertFalse(eligible("com.tcl.tv", false, false, true, false));
        assertFalse(eligible("com.example.service", false, false, false, false));
        assertFalse(eligible("com.example.shared", false, false, true, true));
        assertFalse(eligible("com.gathin.tvhome", false, false, true, false));
        assertFalse(eligible("com.gathin.tvhome.v2", false, false, true, false));
    }
}
