package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The tile order saved by the editor, as a JSON file next to config.json
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/layout.json}). The dock keeps following the
 * launcher configuration; this file only holds the arrangement of the tiles after it, written as
 * component keys ({@code "pkg/.Class"}) in the order they sit in the grid.
 *
 * A missing or broken file means "no saved order": the built-in arrangement is used, so wiping the
 * file is the way back. Keys of apps no longer installed are skipped, and new apps join at the end.
 */
final class TileOrder {
    private static final String TAG = "TvHome.TileOrder";
    private static final String FILE_NAME = "layout.json";

    private TileOrder() {
    }

    private static File file(Context ctx) {
        File dir = Config.dir(ctx);
        return dir != null ? new File(dir, FILE_NAME) : null;
    }

    /** The saved order, or an empty list; any problem means empty, never a crash on the home screen. */
    static List<String> load(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            File f = file(ctx);
            if (f == null || !f.isFile() || f.length() == 0 || f.length() > 64 * 1024) return out;
            byte[] buf = new byte[(int) f.length()];
            try (InputStream in = new FileInputStream(f)) {
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            }
            String text = new String(buf, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            JSONArray a = new JSONObject(text).optJSONArray("order");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                Object item = a.opt(i);
                if (item instanceof String && !((String) item).trim().isEmpty()) {
                    out.add(((String) item).trim());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + FILE_NAME + ": " + e.getMessage());
            out.clear();
        }
        return out;
    }

    /** Writes the order atomically (tmp file, then rename). Returns false when it could not be saved. */
    static boolean save(Context ctx, List<String> order) {
        try {
            File f = file(ctx);
            if (f == null) return false;
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), FILE_NAME + ".tmp");
            JSONArray a = new JSONArray();
            for (String key : order) a.put(key);
            JSONObject json = new JSONObject();
            json.put("order", a);
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
            if (f.exists()) f.delete();
            return tmp.renameTo(f);
        } catch (Exception e) {
            Log.w(TAG, "save failed: " + e.getMessage());
            return false;
        }
    }
}
