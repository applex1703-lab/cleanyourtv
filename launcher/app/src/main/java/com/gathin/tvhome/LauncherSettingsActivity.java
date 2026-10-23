package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_DOCK_EDIT = "com.gathin.tvhome.extra.DOCK_EDIT_FROM_SETTINGS";
    static final String EXTRA_RETURN_TO_PANEL = "com.gathin.tvhome.extra.RETURN_TO_PANEL";
    static final String EXTRA_OPEN_APPS_TAB = "com.gathin.tvhome.extra.OPEN_APPS_TAB";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private Switch animationsChoice;
    private Switch advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;
    private View settingsScrim;
    private View panelClose;
    private View[] tabs;
    private View[] tabContents;
    private int tabIndex;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        View settingsPanel = findViewById(R.id.settingsPanel);
        int maxWidth = Math.round(540 * getResources().getDisplayMetrics().density);
        int safeWidth = Math.max(Math.round(280 * getResources().getDisplayMetrics().density),
                getResources().getDisplayMetrics().widthPixels - Math.round(40 * getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams panelLp = (FrameLayout.LayoutParams) settingsPanel.getLayoutParams();
        panelLp.width = Math.min(maxWidth, safeWidth);
        settingsPanel.setLayoutParams(panelLp);
        settingsScrim = findViewById(R.id.settingsScrim);
        panelClose = findViewById(R.id.panelClose);
        panelClose.setOnClickListener(v -> finish());
        settingsScrim.setOnClickListener(v -> finish());
        findViewById(R.id.settingsPanel).setOnClickListener(v -> { });
        tabs = new View[]{findViewById(R.id.tabLayout), findViewById(R.id.tabAppearance),
                findViewById(R.id.tabMotion), findViewById(R.id.tabApps)};
        tabContents = new View[]{findViewById(R.id.layoutTabContent), findViewById(R.id.appearanceTabContent),
                findViewById(R.id.motionTabContent), findViewById(R.id.appsTabContent)};
        panelClose.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                tabs[tabIndex].requestFocus();
                return true;
            }
            return keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
        for (int i = 0; i < tabs.length; i++) {
            final int selected = i;
            tabs[i].setOnClickListener(v -> showTab(selected));
            tabs[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) return false;
                int logicalDelta = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? -1
                        : keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                if (rtl) logicalDelta = -logicalDelta;
                if (logicalDelta != 0) {
                    if (selected + logicalDelta >= 0 && selected + logicalDelta < tabs.length) {
                        tabs[selected + logicalDelta].requestFocus();
                    }
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    firstFocusable(tabContents[selected]);
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    panelClose.requestFocus();
                    return true;
                }
                return false;
            });
        }
        for (View tab : tabs) tab.setMinimumWidth(Math.round(92 * getResources().getDisplayMetrics().density));
        if (rtl) {
            LinearLayout tabRow = findViewById(R.id.settingsTabs);
            tabRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) settingsPanel.getLayoutParams();
            panelParams.gravity = Gravity.START;
            findViewById(R.id.settingsPanel).setLayoutParams(panelParams);
        }
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            View.OnClickListener choose = v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            };
            columnChoices[i].setOnClickListener(choose);
            columnChoices[i].setOnKeyListener(choiceKeys(choose, columnChoices, i));
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            View.OnClickListener choose = v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            };
            rowChoices[i].setOnClickListener(choose);
            rowChoices[i].setOnKeyListener(choiceKeys(choose, rowChoices, i));
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            View.OnClickListener choose = v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            };
            dockChoices[i].setOnClickListener(choose);
            dockChoices[i].setOnKeyListener(choiceKeys(choose, dockChoices, i));
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperChoice.setOnKeyListener((v, keyCode, event) -> activateOnCenter(wallpaperChoice, keyCode, event));
        animationsChoice = findViewById(R.id.animationsChoice);
        View.OnClickListener toggleAnimations = v -> {
            animations = animationsChoice.isChecked();
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        };
        animationsChoice.setOnClickListener(toggleAnimations);
        animationsChoice.setOnKeyListener((v, keyCode, event) -> activateOnCenter(animationsChoice, keyCode, event));
        advancedChoice = findViewById(R.id.advancedChoice);
        View.OnClickListener toggleAdvanced = v -> {
            if (!advancedChoice.isChecked()) {
                setAdvanced(false);
            } else {
                advancedChoice.setChecked(false);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        };
        advancedChoice.setOnClickListener(toggleAdvanced);
        advancedChoice.setOnKeyListener((v, keyCode, event) -> activateOnCenter(advancedChoice, keyCode, event));
        followThemeChoice = findViewById(R.id.followThemeChoice);
        View.OnClickListener resetThemeChoices = v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        };
        followThemeChoice.setOnClickListener(resetThemeChoices);
        followThemeChoice.setOnKeyListener((v, keyCode, event) -> activateOnCenter(followThemeChoice, keyCode, event));
        View.OnClickListener openReorder = v -> startReorder(false);
        findViewById(R.id.reorderPanelChoice).setOnClickListener(openReorder);
        findViewById(R.id.reorderPanelChoice).setOnKeyListener((v, keyCode, event) -> activateOnCenter(v, keyCode, event));
        View.OnClickListener editDock = v -> startReorder(true);
        findViewById(R.id.dockEditPanelChoice).setOnClickListener(editDock);
        findViewById(R.id.dockEditPanelChoice).setOnKeyListener((v, keyCode, event) -> activateOnCenter(v, keyCode, event));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        showTab(0);
        refresh();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_APPS_TAB, false)) {
            showTab(3);
            findViewById(R.id.reorderPanelChoice).requestFocus();
        } else {
            tabs[0].requestFocus();
        }
    }

    private void showTab(int selected) {
        tabIndex = selected;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == selected);
            tabContents[i].setVisibility(i == selected ? View.VISIBLE : View.GONE);
        }
        TextView title = findViewById(R.id.panelTitle);
        int[] titles = {R.string.settings_tab_layout, R.string.settings_tab_appearance,
                R.string.settings_tab_motion, R.string.settings_tab_apps};
        title.setText(titles[selected]);
    }

    private boolean firstFocusable(View parent) {
        if (!(parent instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) parent;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child.isFocusable()) {
                if (child.requestFocus()) return true;
            }
            if (firstFocusable(child)) return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private View.OnKeyListener choiceKeys(View.OnClickListener action, TextView[] choices, int index) {
        return (view, keyCode, event) -> {
            boolean center = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER;
            if (center) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) action.onClick(view);
                return true;
            }
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            int direction = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT
                    : keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
            if (direction != 0) {
                View next = view.focusSearch(direction);
                for (TextView choice : choices) {
                    if (next == choice) {
                        choice.requestFocus();
                        return true;
                    }
                }
                return true;
            }
            return false;
        };
    }

    private boolean activateOnCenter(View view, int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) view.performClick();
            return true;
        }
        return false;
    }

    private void startReorder(boolean editDock) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true)
                .putExtra(EXTRA_DOCK_EDIT, editDock)
                .putExtra(EXTRA_RETURN_TO_PANEL, true));
        finish();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(R.string.animations_label);
        animationsChoice.setChecked(animations);
        advancedChoice.setText(R.string.advanced_effects);
        advancedChoice.setChecked(advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
