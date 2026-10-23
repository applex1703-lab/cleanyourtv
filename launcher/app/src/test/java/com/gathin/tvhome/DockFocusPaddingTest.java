package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class DockFocusPaddingTest {
    @Test public void denseDocksContainFocusedTileHaloAndGap() {
        float density = 2f;
        int inner = 1696;
        for (int columns = 5; columns <= 7; columns++) {
            int width = AppAdapter.tileWidthPx(inner, columns, density);
            float room = (inner / (float) columns - width) / 2f;
            float pad = DockDecoration.focusPaddingPx(width, 1.10f, density, room);
            float halo = width * 0.10f / 2f + 6 * density;
            assertTrue("columns=" + columns, pad >= halo + 3 * density);
            assertTrue(pad <= room - density);
        }
    }
    @Test public void paddingNeverExtendsOutsideAvailableRoom() {
        assertEquals(0f, DockDecoration.focusPaddingPx(360, 1.1f, 2f, 0), 0.001f);
    }
}
