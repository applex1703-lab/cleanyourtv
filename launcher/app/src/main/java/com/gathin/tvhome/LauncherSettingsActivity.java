package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Minimal remote-friendly panel for the launcher's four requested controls. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_DOCK_EDIT = "com.gathin.tvhome.extra.DOCK_EDIT_FROM_SETTINGS";
    static final String EXTRA_RETURN_TO_PANEL = "com.gathin.tvhome.extra.RETURN_TO_PANEL";
    static final String EXTRA_OPEN_APPS_TAB = "com.gathin.tvhome.extra.OPEN_APPS_TAB";

    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private SharedPreferences choices;
    private TextView wallpaperChoice;
    private View[] tabs;
    private View[] tabContents;
    private View panelClose;
    private int columns;
    private int dockOpacity;
    private int tabIndex;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        View panel = findViewById(R.id.settingsPanel);
        float density = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) panel.getLayoutParams();
        panelParams.width = Math.min(Math.round(540 * density),
                Math.max(Math.round(280 * density), getResources().getDisplayMetrics().widthPixels - Math.round(40 * density)));
        panel.setLayoutParams(panelParams);

        panelClose = findViewById(R.id.panelClose);
        panelClose.setOnClickListener(v -> finish());
        findViewById(R.id.settingsScrim).setOnClickListener(v -> finish());
        panel.setOnClickListener(v -> { });

        tabs = new View[]{findViewById(R.id.tabLayout)};
        tabContents = new View[]{findViewById(R.id.layoutTabContent)};
        boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> showTab(index));
            tabs[i].setOnKeyListener((v, key, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                int direction = key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? -1
                        : key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                if (rtl) direction = -direction;
                if (direction != 0) {
                    int next = index + direction;
                    if (next >= 0 && next < tabs.length) tabs[next].requestFocus();
                    return true;
                }
                if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) return firstFocusable(tabContents[index]);
                if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    panelClose.requestFocus();
                    return true;
                }
                return false;
            });
            tabs[i].setMinimumWidth(Math.round(92 * density));
        }
        panelClose.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                tabs[tabIndex].requestFocus();
                return true;
            }
            return key == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        if (rtl) {
            ((LinearLayout) findViewById(R.id.settingsTabs)).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            panelParams.gravity = Gravity.START;
            panel.setLayoutParams(panelParams);
        }

        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        dockOpacity = config.dockOpacity;

        int[] columnIds = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < columnIds.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(columnIds[i]);
            View.OnClickListener choose = v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            };
            columnChoices[i].setOnClickListener(choose);
            columnChoices[i].setOnKeyListener(choiceKeys(choose, columnChoices));
        }

        int[] opacityIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < opacityIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(opacityIds[i]);
            View.OnClickListener choose = v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            };
            dockChoices[i].setOnClickListener(choose);
            dockChoices[i].setOnKeyListener(choiceKeys(choose, dockChoices));
        }
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(dockChoices);
        }

        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperChoice.setOnKeyListener(this::activateOnCenter);
        View reorder = findViewById(R.id.reorderPanelChoice);
        reorder.setOnClickListener(v -> startReorder(false));
        reorder.setOnKeyListener(this::activateOnCenter);

        showTab(0);
        refresh();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_APPS_TAB, false)) {
            showTab(0);
            reorder.requestFocus();
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
        ((TextView) findViewById(R.id.panelTitle)).setText(R.string.settings_tab_layout);
    }

    private boolean firstFocusable(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child.isFocusable() && child.requestFocus()) return true;
            if (firstFocusable(child)) return true;
        }
        return false;
    }

    private View.OnKeyListener choiceKeys(View.OnClickListener action, TextView[] row) {
        return (view, key, event) -> {
            if (key == android.view.KeyEvent.KEYCODE_DPAD_CENTER || key == android.view.KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) action.onClick(view);
                return true;
            }
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key != android.view.KeyEvent.KEYCODE_DPAD_LEFT && key != android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            View next = view.focusSearch(key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
            for (TextView choice : row) if (next == choice) { choice.requestFocus(); return true; }
            return true;
        };
    }

    private boolean activateOnCenter(View view, int key, android.view.KeyEvent event) {
        if (key != android.view.KeyEvent.KEYCODE_DPAD_CENTER && key != android.view.KeyEvent.KEYCODE_ENTER) return false;
        if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) view.performClick();
        return true;
    }

    private void reverseRow(TextView[] row) {
        LinearLayout parent = (LinearLayout) row[0].getParent();
        parent.removeAllViews();
        for (int i = row.length - 1; i >= 0; i--) parent.addView(row[i]);
    }

    private void startReorder(boolean editDock) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true)
                .putExtra(EXTRA_DOCK_EDIT, editDock).putExtra(EXTRA_RETURN_TO_PANEL, true));
        finish();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size() : indexOf(images, current);
        new AlertDialog.Builder(this).setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                            which == images.size() ? "none" : images.get(which).getName()).apply();
                    changed();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static int indexOf(List<File> files, String name) {
        if (name == null) return -1;
        for (int i = 0; i < files.size(); i++) if (files.get(i).getName().equals(name)) return i;
        return -1;
    }

    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) columnChoices[i].setSelected(columns == i + 4);
        for (int i = 0; i < dockChoices.length; i++) dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        String choice = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state, "none".equals(choice)
                ? getString(R.string.wallpaper_none) : choice != null ? choice : getString(R.string.wallpaper_theme_default)));
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
