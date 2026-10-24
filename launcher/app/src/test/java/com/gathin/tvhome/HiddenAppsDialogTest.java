package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HiddenAppsDialogTest {
    @Test public void hiddenShortcutRemainsAvailableToRestore() {
        assertTrue(HiddenAppsDialog.eligible(false, false, true, false, false));
        assertTrue(HiddenAppsDialog.eligible(true, false, false, false, false));
        assertTrue(HiddenAppsDialog.eligible(false, true, false, false, false));
    }

    @Test public void catalogueDoesNotAddUnrelatedPhoneAppsOrOverrideTheme() {
        assertFalse(HiddenAppsDialog.eligible(false, false, false, false, false));
        assertFalse(HiddenAppsDialog.eligible(true, true, true, true, false));
        assertFalse(HiddenAppsDialog.eligible(true, true, true, false, true));
    }
}
