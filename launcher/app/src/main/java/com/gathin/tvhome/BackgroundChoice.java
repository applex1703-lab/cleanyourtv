package com.gathin.tvhome;

/** Small, validated background preference model; no new theme format. */
final class BackgroundChoice {
    static final String MODE = "background_mode";
    static final String PALETTE = "background_palette";
    static final String ALWAYS = "background_always";
    static final String IMAGE = "custom-background";
    // Distinct hue families rather than several nearly identical blue/green/red variants.
    // Keep legacy indices valid; the picker exposes only the curated selection below.
    static final int[] COLORS = {0, 0xFF689AC8, 0xFFA38AC9, 0xFF467C60, 0xFFD19A78, 0xFFB4778B,
            0xFF689AC8, 0xFFA38AC9, 0xFF467C60, 0xFFB9A374, 0xFFB9A374, 0xFFB4778B, 0xFF78818B};
    static final int[] PICKER_PALETTES = {0, 1, 2, 3, 4, 5, 10, 12};

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
