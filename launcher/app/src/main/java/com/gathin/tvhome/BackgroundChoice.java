package com.gathin.tvhome;

/** Small, validated background preference model; no new theme format. */
final class BackgroundChoice {
    static final String MODE = "background_mode";
    static final String PALETTE = "background_palette";
    static final String ALWAYS = "background_always";
    static final String IMAGE = "custom-background";
    // Single tint per preset: spatial falloff and brightness belong to the original ambient mask.
    // Legacy indices remain valid; duplicate historical entries map to the curated picker.
    static final int[] COLORS = {0, 0xFF2854A0, 0xFF9A48DA, 0xFF246B50, 0xFFE87838, 0xFF8B1826,
            0xFF2854A0, 0xFF9A48DA, 0xFF246B50, 0xFFD8AD45, 0xFFD8AD45, 0xFF8B1826, 0xFF2854A0};
    static final int[] PICKER_PALETTES = {0, 3, 2, 4, 1, 5, 10};

    static int pickerIndex(int palette) {
        int color = COLORS[palette(palette)];
        for (int i = 0; i < PICKER_PALETTES.length; i++) {
            if (COLORS[PICKER_PALETTES[i]] == color) return i;
        }
        return 0;
    }

    private BackgroundChoice() { }

    static int palette(int value) {
        return value >= 0 && value < COLORS.length ? value : 0;
    }

    static int color(int palette, int appColor) {
        int selected = palette(palette);
        return selected == 0 ? appColor : COLORS[selected];
    }

    static boolean visible(boolean dock, boolean always) {
        return dock || always;
    }
}
