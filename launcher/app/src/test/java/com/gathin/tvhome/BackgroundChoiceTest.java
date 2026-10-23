package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackgroundChoiceTest {
    @Test public void autoUsesAppColorAndPaletteUsesConstant() {
        assertEquals(0xFF123456, BackgroundChoice.color(0, 0xFF123456));
        assertEquals(BackgroundChoice.COLORS[1], BackgroundChoice.color(1, 0xFF123456));
    }
    @Test public void invalidPaletteFallsBackToAuto() {
        assertEquals(0, BackgroundChoice.palette(-1));
        assertEquals(0, BackgroundChoice.palette(99));
    }
    @Test public void dockScopeDoesNotLeakIntoLowerRows() {
        assertTrue(BackgroundChoice.visible(true, false));
        assertFalse(BackgroundChoice.visible(false, false));
        assertTrue(BackgroundChoice.visible(false, true));
    }
}
