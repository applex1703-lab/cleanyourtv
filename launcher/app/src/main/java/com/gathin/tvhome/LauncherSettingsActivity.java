package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private TextView backgroundScope;
    private static final int PICK_IMAGE = 42;
    private boolean importing;
    private int columns;
    private int dockOpacity;

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

        findViewById(R.id.settingsScrim).setOnClickListener(v -> finish());
        panel.setOnClickListener(v -> { });
        boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
        if (rtl) {
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
        backgroundScope = findViewById(R.id.backgroundScope);
        backgroundScope.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.background_scope)
                .setSingleChoiceItems(new String[]{getString(R.string.background_dock), getString(R.string.background_always)},
                        choices.getBoolean(BackgroundChoice.ALWAYS, false) ? 1 : 0, (dialog, which) -> {
                            choices.edit().putBoolean(BackgroundChoice.ALWAYS, which == 1).apply();
                            changed();
                            refresh();
                            dialog.dismiss();
                        }).show());
        backgroundScope.setOnKeyListener(this::activateOnCenter);
        View reorder = findViewById(R.id.reorderPanelChoice);
        reorder.setOnClickListener(v -> startReorder(false));
        reorder.setOnKeyListener(this::activateOnCenter);
        View hideApps = findViewById(R.id.hideAppsChoice);
        hideApps.setOnClickListener(v -> HiddenAppsDialog.show(this, this::changed));
        hideApps.setOnKeyListener(this::activateOnCenter);

        refresh();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_APPS_TAB, false)) {
            reorder.requestFocus();
        } else {
            columnChoices[0].requestFocus();
        }
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
        if (importing) return;
        new AlertDialog.Builder(this).setTitle(R.string.background_title)
                .setItems(new String[]{getString(R.string.background_image), getString(R.string.background_colors)}, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        try {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK_IMAGE);
                        } catch (ActivityNotFoundException | SecurityException e) {
                            Toast.makeText(this, R.string.background_no_picker, Toast.LENGTH_LONG).show();
                        }
                    } else showColors();
                }).show();
    }

    private String[] colorLabels() {
        return new String[]{getString(R.string.background_auto), getString(R.string.gradient_aurora),
                getString(R.string.gradient_neon), getString(R.string.gradient_sunset),
                getString(R.string.gradient_ocean), getString(R.string.gradient_lava), getString(R.string.gradient_gold)};
    }

    private void showColors() {
        String[] names = colorLabels();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this,
                android.R.layout.select_dialog_singlechoice, names) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                int palette = BackgroundChoice.PICKER_PALETTES[position];
                float dp = getResources().getDisplayMetrics().density;
                int width = Math.round(64 * dp), height = Math.round(36 * dp);
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                canvas.drawColor(android.graphics.Color.BLACK);
                int color = palette == 0 ? 0xFF90A4AE : BackgroundChoice.COLORS[palette];
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setShader(new android.graphics.RadialGradient(width * 0.32f, height * 0.42f, width * 0.62f,
                        new int[]{(color & 0xFFFFFF) | 0x5C000000, (color & 0xFFFFFF) | 0x26000000, color & 0xFFFFFF},
                        new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, width, height, paint);
                android.graphics.drawable.BitmapDrawable preview = new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                preview.setBounds(0, 0, width, height);
                row.setCompoundDrawablesRelative(preview, null, null, null);
                row.setCompoundDrawablePadding(Math.round(14 * dp));
                return row;
            }
        };
        new AlertDialog.Builder(this).setTitle(R.string.background_colors)
                .setSingleChoiceItems(adapter, BackgroundChoice.pickerIndex(choices.getInt(BackgroundChoice.PALETTE, 0)),
                        (dialog, which) -> {
                            choices.edit().putString(BackgroundChoice.MODE, "colors")
                                    .putInt(BackgroundChoice.PALETTE, BackgroundChoice.PICKER_PALETTES[which]).apply();
                            changed();
                            refresh();
                            dialog.dismiss();
                        }).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        importing = true;
        wallpaperChoice.setEnabled(false);
        Toast.makeText(this, R.string.background_loading, Toast.LENGTH_SHORT).show();
        // Copy under the temporary URI grant; no persisted grant or broad storage permission is needed.
        new Thread(() -> {
            File temporary = new File(getFilesDir(), BackgroundChoice.IMAGE + ".tmp");
            boolean success = false;
            try {
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(temporary)) {
                    if (in == null) throw new java.io.IOException("No image stream");
                    byte[] buffer = new byte[16384];
                    int count, total = 0;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (total > 20 * 1024 * 1024) throw new java.io.IOException("Image too large");
                        out.write(buffer, 0, count);
                    }
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temporary.getPath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 16384 || bounds.outHeight > 16384)
                    throw new java.io.IOException("Unsupported image");
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = 1;
                while (bounds.outWidth / decode.inSampleSize > 2048 || bounds.outHeight / decode.inSampleSize > 2048)
                    decode.inSampleSize *= 2;
                decode.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeFile(temporary.getPath(), decode);
                if (bitmap == null) throw new java.io.IOException("Unreadable image");
                File normalized = new File(getFilesDir(), BackgroundChoice.IMAGE + ".ready");
                try (FileOutputStream out = new FileOutputStream(normalized)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
                        throw new java.io.IOException("Cannot encode image");
                } finally {
                    bitmap.recycle();
                }
                if (!normalized.renameTo(new File(getFilesDir(), BackgroundChoice.IMAGE)))
                    throw new java.io.IOException("Cannot save image");
                temporary.delete();
                choices.edit().putString(BackgroundChoice.MODE, "image").apply();
                choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                        choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
                success = true;
            } catch (Exception e) {
                temporary.delete();
                new File(getFilesDir(), BackgroundChoice.IMAGE + ".ready").delete();
            }
            boolean saved = success;
            runOnUiThread(() -> {
                importing = false;
                if (isFinishing() || isDestroyed()) return;
                wallpaperChoice.setEnabled(true);
                if (saved) setResult(RESULT_OK);
                else Toast.makeText(this, R.string.background_failed, Toast.LENGTH_LONG).show();
                refresh();
            });
        }, "WallpaperImport").start();
    }

    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) columnChoices[i].setSelected(columns == i + 4);
        for (int i = 0; i < dockChoices.length; i++) dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        String label = "image".equals(choices.getString(BackgroundChoice.MODE, "colors"))
                ? getString(R.string.background_image) : colorLabels()[BackgroundChoice.pickerIndex(choices.getInt(BackgroundChoice.PALETTE, 0))];
        wallpaperChoice.setText(getString(R.string.background_current, label));
        backgroundScope.setText(choices.getBoolean(BackgroundChoice.ALWAYS, false)
                ? R.string.background_always : R.string.background_dock);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
