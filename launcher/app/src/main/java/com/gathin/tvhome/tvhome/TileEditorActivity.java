package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rearranges the tiles with the remote. Opens from the pencil pill on the home screen.
 *
 * The dock row is shown greyed out at the top and never moves: it belongs to the launcher
 * configuration, and the editor must not break it. Everything after it is one editable list:
 * OK picks a tile up, the D-pad moves it through the list step by step, OK again puts it down,
 * Back puts it down without moving (a further Back undoes the last step), and with nothing left
 * to undo Back leaves. Every step an arrow made is reversed one by one, so nothing is lost.
 *
 * The order only takes effect once Save is pressed: it writes the tile keys to
 * {@link TileOrder} (layout.json); Exit - and Back without history - leave the arrangement as it was.
 */
public class TileEditorActivity extends Activity {

    /** Everything needed to draw one tile row in the editor list. */
    private static final class Item {
        final String label;
        final Drawable icon;

        Item(String label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private static final int[] TOP_KEYS = {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    };

    private final Map<String, Item> items = new HashMap<>();
    /** Fixed dock keys, in dock order; shown greyed out and never editable. */
    private final List<String> dock = new ArrayList<>();
    /** The editable grid tiles in their live order. */
    private final List<String> current = new ArrayList<>();
    /** One entry per arrow step the user made: {from, to}; Back reverses the newest first. */
    private final Deque<int[]> undo = new ArrayDeque<>();
    /** The grabbed tile's position, or -1 when no tile is carried. */
    private int grabbed = -1;
    /** Set in onPause: remote keys after that belong to the leaving screen, not to rows held here. */
    private boolean closing;

    private LinearLayout list;
    private float density;
    private int iconPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        iconPx = Math.round(26 * density);
        if (!loadData()) {
            finish();
            return;
        }
        buildUi();
    }

    /** Reads the apps and the saved order; false when there is nothing the editor could arrange. */
    private boolean loadData() {
        Config cfg = Config.load(this);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<String> gridKeys = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || cfg.hiddenApps.contains(pkg)) continue;
            String key = new ComponentName(pkg, ri.activityInfo.name).flattenToShortString();
            Drawable icon;
            String label;
            try {
                icon = ri.loadIcon(pm);
                label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
            } catch (Exception e) {
                continue;   // a broken icon must not take the editor down
            }
            items.put(key, new Item(label, icon));
            if (cfg.dockApps.contains(pkg)) dock.add(key);
            else gridKeys.add(key);
        }
        // Dock order follows the configuration's favourites order, like the home screen draws it.
        dock.sort((a, b) -> Integer.compare(
                cfg.dockApps.indexOf(pkgOf(a)), cfg.dockApps.indexOf(pkgOf(b))));
        // The saved arrangement first (skipping keys since gone), the rest after in list order.
        Set<String> have = new HashSet<>(gridKeys);
        for (String key : TileOrder.load(this)) {
            if (have.remove(key)) current.add(key);
        }
        current.addAll(gridKeys);
        return !current.isEmpty() || !dock.isEmpty();
    }

    /** The package half of a component key ("pkg/.Class"): the dock sorts by it, like AppAdapter does. */
    private static String pkgOf(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    // ---- UI --------------------------------------------------------------------------------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = Math.round(56 * density), top = Math.round(26 * density);
        root.setPadding(side, top, side, Math.round(20 * density));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView save = pill(getString(R.string.editor_save), v -> {
            if (TileOrder.save(this, current)) {
                Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
        TextView exit = pill(getString(R.string.editor_exit), v -> finish());
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.editor_hint)
                + "\n\u2022 " + getString(R.string.editor_dock_hint));
        hint.setTextColor(0x8CFFFFFF);
        hint.setTextSize(11f);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        hintLp.leftMargin = Math.round(24 * density);
        bar.addView(save);
        bar.addView(exit);
        bar.addView(hint, hintLp);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
        save.requestFocus();   // the bar leads: Down from it walks into the first editable row
    }

    /** A frosted pill button (same look as the home screen's top row). */
    private TextView pill(String text, View.OnClickListener click) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15f);
        v.setTextColor(getResources().getColorStateList(R.drawable.pill_text));
        v.setGravity(Gravity.CENTER);
        v.setPadding(Math.round(16 * density), 0, Math.round(20 * density), 0);
        v.setHeight(Math.round(42 * density));
        v.setBackground(getResources().getDrawable(R.drawable.pill_bg));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Math.round(10 * density);
        lp.topMargin = 0;
        v.setLayoutParams(lp);
        return v;
    }

    /** Rebuilds every row: dock rows, the separator note, then the editable tiles in live order. */
    private void render() {
        list.removeAllViews();
        for (String key : dock) list.addView(row(key, true), rowParams());
        list.addView(separator());
        TextView note = new TextView(this);
        note.setText(getString(R.string.editor_dock_hint));
        note.setTextColor(0x66FFFFFF);
        note.setTextSize(10f);
        int m = Math.round(10 * density);
        note.setPadding(0, m, 0, m);
        note.setFocusable(false);
        list.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < current.size(); i++) {
            String key = current.get(i);
            View rw = row(key, false);
            rw.setLayoutParams(rowParams());
            rw.setOnKeyListener((v, keyCode, event) ->
                    event.getAction() == KeyEvent.ACTION_DOWN && !closing && onKey(key, keyCode));
            list.addView(rw);
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(4 * density);
        return lp;
    }

    /** One row: icon and name; inDock greyed out (never focusable), grabbed one an orange sheet. */
    private LinearLayout row(String key, boolean inDock) {
        Item it = items.get(key);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(8 * density), Math.round(6 * density),
                Math.round(8 * density), Math.round(6 * density));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(it != null ? it.icon : null);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
        row.addView(icon);
        TextView label = new TextView(this);
        label.setTextSize(15f);
        label.setTextColor(0xFFFFFFFF);
        label.setPadding(Math.round(12 * density), 0, 0, 0);
        String text = it != null ? it.label : key;
        label.setText(grabbed != -1 && current.get(grabbed).equals(key) ? "\u25B6 " + text : text);
        row.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (inDock) {
            row.setAlpha(0.35f);
            row.setFocusable(false);
        } else {
            row.setFocusable(true);
            row.setOnFocusChangeListener((v, focused) ->
                    v.setBackgroundColor(focused ? 0x33FFFFFF : 0x00000000));
            if (grabbed != -1 && current.get(grabbed).equals(key)) row.setBackgroundColor(0x4DFF9800);
        }
        return row;
    }

    /** Focuses the editable row at a position after the fresh views are laid out. */
    private void focusRow(int position) {
        list.post(() -> {
            // First two children after the dock block are the separator and the note.
            View v = list.getChildCount() > dock.size() + 2 + position
                    ? list.getChildAt(dock.size() + 2 + position) : null;
            if (v != null) v.requestFocus();
        });
    }

    private View separator() {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        line.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density)));
        lp.topMargin = Math.round(10 * density);
        lp.bottomMargin = Math.round(2 * density);
        return line;
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** Remote keys on an editable row. Returns true when the key is consumed. */
    private boolean onKey(String key, int keyCode) {
        boolean isTop = false;
        for (int code : TOP_KEYS) if (code == keyCode) isTop = true;
        if (!isTop) return false;   // Menu etc.: behave normally
        int index = current.indexOf(key);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                grabbed = grabbed == index ? -1 : index;   // pick up / put down
                render();
                focusRow(grabbed != -1 ? grabbed : index);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (grabbed != -1) {   // first Back: put the carried tile down without moving it
                    int was = grabbed;
                    grabbed = -1;
                    render();
                    focusRow(was);
                } else if (!undo.isEmpty()) {   // then each Back undoes exactly one arrow step
                    int[] step = undo.pop();
                    String moved = current.remove(step[1]);
                    current.add(step[0], moved);
                    render();
                    focusRow(step[0]);
                } else {
                    finish();   // nothing carried and nothing to undo: leave without saving
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return moveTile(index, -1);
            default:
                return moveTile(index, 1);
        }
    }

    /** A carried tile moves one step per press (with focus following); free focus moves normally. */
    private boolean moveTile(int index, int step) {
        if (grabbed == -1) return false;
        int to = grabbed + step;
        if (to < 0 || to >= current.size()) return true;   // at an edge: keep hold, stay put
        java.util.Collections.swap(current, grabbed, to);
        undo.push(new int[]{grabbed, to});
        grabbed = to;
        render();
        focusRow(to);
        return true;
    }

    @Override
    public void onBackPressed() {
        // Same rule as on a row: drop what is carried, undo one step, and only then leave.
        String key = grabbed != -1 ? current.get(grabbed) : current.size() > 0 ? current.get(0) : null;
        if (key != null) onKey(key, KeyEvent.KEYCODE_BACK);
        else finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closing = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        closing = false;
    }
}
