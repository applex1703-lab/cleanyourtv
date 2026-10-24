package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings entry for existing Home shortcut removals; never edits packages, themes or tile order. */
final class HiddenAppsDialog {
    private HiddenAppsDialog() { }

    static boolean eligible(boolean tvApp, boolean added, boolean removed, boolean themeHidden, boolean self) {
        return !self && !themeHidden && (tvApp || added || removed);
    }

    static void show(Activity activity, Runnable changed) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.hide_apps)
                .setMessage(R.string.hide_apps_loading).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnDismissListener(ignored -> worker.shutdown());
        dialog.show();
        worker.execute(() -> {
            try {
                Config config = Config.load(activity);
                Set<String> removed = LibraryApps.loadRemoved(activity);
                Set<String> added = new HashSet<>(LibraryApps.load(activity));
                added.addAll(config.libraryApps);
                Set<String> themeHidden = new HashSet<>(config.hiddenApps);
                Map<String, ResolveInfo> unique = new LinkedHashMap<>();
                PackageManager pm = activity.getPackageManager();
                for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                    for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                        if (info.activityInfo == null || !info.activityInfo.exported) continue;
                        String pkg = info.activityInfo.packageName;
                        if (eligible(Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category), added.contains(pkg),
                                removed.contains(pkg), themeHidden.contains(pkg), pkg.equals(activity.getPackageName())))
                            unique.putIfAbsent(pkg, info);
                    }
                }
                Map<String, String> labels = new HashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                for (Map.Entry<String, ResolveInfo> entry : unique.entrySet()) {
                    String label;
                    try { label = Cards.cleanLabel(String.valueOf(entry.getValue().loadLabel(pm)), entry.getKey()); }
                    catch (RuntimeException e) { label = entry.getKey(); }
                    labels.put(entry.getKey(), label);
                    counts.put(label, counts.getOrDefault(label, 0) + 1);
                }
                List<String> packages = new ArrayList<>(unique.keySet());
                Collator collator = Collator.getInstance();
                packages.sort((a, b) -> {
                    int result = collator.compare(labels.get(a), labels.get(b));
                    return result != 0 ? result : a.compareTo(b);
                });
                String[] names = new String[packages.size()];
                boolean[] checked = new boolean[packages.size()];
                for (int i = 0; i < packages.size(); i++) {
                    String pkg = packages.get(i), label = labels.get(pkg);
                    names[i] = counts.get(label) > 1 ? label + "\n" + pkg : label;
                    checked[i] = removed.contains(pkg);
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                    dialog.dismiss();
                    showList(activity, changed, packages, names, checked);
                });
            } catch (RuntimeException e) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                    dialog.dismiss();
                    Toast.makeText(activity, R.string.hide_apps_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void showList(Activity activity, Runnable changed, List<String> packages,
                                 String[] names, boolean[] checked) {
        if (packages.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(R.string.hide_apps).setMessage(R.string.hide_apps_empty)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        ExecutorService writer = Executors.newSingleThreadExecutor();
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.hide_apps_hint)
                .setMultiChoiceItems(names, checked, (whichDialog, index, hidden) -> {
                    AlertDialog listDialog = (AlertDialog) whichDialog;
                    listDialog.getListView().setEnabled(false);
                    writer.execute(() -> {
                        boolean saved = LibraryApps.save(activity, packages.get(index), !hidden);
                        activity.runOnUiThread(() -> {
                            if (saved) changed.run();
                            if (activity.isFinishing() || activity.isDestroyed() || !listDialog.isShowing()) return;
                            if (!saved) {
                                checked[index] = !hidden;
                                listDialog.getListView().setItemChecked(index, !hidden);
                                Toast.makeText(activity, R.string.hide_apps_failed, Toast.LENGTH_LONG).show();
                            }
                            listDialog.getListView().setEnabled(true);
                        });
                    });
                }).setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnDismissListener(ignored -> writer.shutdown());
        dialog.show();
    }
}
