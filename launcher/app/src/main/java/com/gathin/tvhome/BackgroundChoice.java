package com.gathin.tvhome;

/** Small, validated background preference model; no new theme format. */
final class BackgroundChoice {
    static final String MODE = "background_mode";
    static final String PALETTE = "background_palette";
    static final String ALWAYS = "background_always";
    static final String IMAGE = "custom-background";
    // Append only: stored palette indices from previous versions must retain their meaning.
    static final int[] COLORS = {0, 0xFF3064AD, 0xFF7046A0, 0xFF248B80, 0xFFA85B34, 0xFFAD4267,
            0xFF458EB5, 0xFF464D9B, 0xFF247653, 0xFF778243, 0xFFAA873D, 0xFF80364E, 0xFF57616E};

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
