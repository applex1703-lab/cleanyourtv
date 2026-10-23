package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackgroundChoiceTest {
    @Test public void pickerHasNoDuplicateColorsAndMapsLegacyIndices() {
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        for (int index : BackgroundChoice.PICKER_PALETTES) assertTrue(unique.add(BackgroundChoice.COLORS[index]));
        for (int index = 0; index < BackgroundChoice.COLORS.length; index++) {
            int picker = BackgroundChoice.pickerIndex(index);
            assertEquals(BackgroundChoice.COLORS[index], BackgroundChoice.COLORS[BackgroundChoice.PICKER_PALETTES[picker]]);
        }
        assertEquals(0, BackgroundChoice.pickerIndex(99));
    }
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
