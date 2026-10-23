package com.gathin.tvhome;

/** Small, validated background preference model; no new theme format. */
final class BackgroundChoice {
    static final String MODE = "background_mode";
    static final String PALETTE = "background_palette";
    static final String ALWAYS = "background_always";
    static final String IMAGE = "custom-background";
    // Keep old indices readable while exposing six distinct two-colour gradients.
    static final int[] COLORS = {0, 0xFF123DB8, 0xFF2359E8, 0xFF087F83, 0xFFF17A28, 0xFF711C3A,
            0xFF123DB8, 0xFF2359E8, 0xFF087F83, 0xFFCB952B, 0xFFCB952B, 0xFF711C3A, 0xFF123DB8};
    static final int[] SECOND_COLORS = {0, 0xFF00C6D7, 0xFFB62ACD, 0xFF28BB67, 0xFFC72972, 0xFFE94032,
            0xFF00C6D7, 0xFFB62ACD, 0xFF28BB67, 0xFF442617, 0xFF442617, 0xFFE94032, 0xFF00C6D7};
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
