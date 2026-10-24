package com.gathin.tvhome;

/** Conservative eligibility: ordinary launchable apps, never services or vendor components. */
final class MemoryCleanPolicy {
    private MemoryCleanPolicy() { }

    static boolean eligible(String pkg, boolean launchable, boolean system, boolean vpn,
                            boolean sharedSystemUid, String self) {
        if (!launchable || vpn || sharedSystemUid || pkg.equals(self)
                || pkg.startsWith("com.gathin.tvhome") || pkg.startsWith("com.tcl.")
                || pkg.startsWith("com.android.") || pkg.equals("android")
                || pkg.equals("local.tv.memoryprobe")) return false;
        // The preinstalled YouTube player is an ordinary user-facing target, not a core Google service.
        return !system || pkg.equals("com.google.android.youtube.tv");
    }
}
