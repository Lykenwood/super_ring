package com.example.progressoverlay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ordered overlay instances. Each instance owns its display settings and future source binding. */
final class InstanceStore {
    static final String NAME = "instance_name";
    static final String ACTIVE = "instance_active";
    static final String SOURCE_TYPE = "source_type";
    static final String SOURCE_VALUE = "source_value";
    static final String SOURCE_AUTO_LATEST = "auto_latest";
    private static final String IDS = "instance_ids";
    private static final String MIGRATED = "instances_migrated";

    private InstanceStore() { }

    static synchronized void ensure(Context context) {
        SharedPreferences global = OverlayController.prefs(context);
        if (global.getBoolean(MIGRATED, false)) return;
        String id = "original";
        SharedPreferences target = prefs(context, id);
        copy(global, target);
        target.edit().putString(NAME, "原有进度条")
                .putBoolean(ACTIVE, true)
                .putString(SOURCE_TYPE, SOURCE_AUTO_LATEST).apply();
        global.edit().putString(IDS, id).putBoolean(MIGRATED, true).commit();
    }

    static List<String> ids(Context context) {
        ensure(context);
        String raw = OverlayController.prefs(context).getString(IDS, "original");
        ArrayList<String> result = new ArrayList<>();
        if (raw != null) for (String id : raw.split(",")) if (!id.isEmpty()) result.add(id);
        return result;
    }

    static SharedPreferences prefs(Context context, String id) {
        return context.getSharedPreferences("instance_" + id, Context.MODE_PRIVATE);
    }

    static String add(Context context) {
        List<String> ids = ids(context);
        String id = UUID.randomUUID().toString();
        prefs(context, id).edit().putString(NAME, "进度条 " + (ids.size() + 1))
                .putBoolean(ACTIVE, true)
                .putString(SOURCE_TYPE, SOURCE_AUTO_LATEST).apply();
        ids.add(id);
        saveIds(context, ids);
        return id;
    }

    static String duplicate(Context context, String id) {
        List<String> ids = ids(context);
        String copyId = UUID.randomUUID().toString();
        SharedPreferences target = prefs(context, copyId);
        copy(prefs(context, id), target);
        String name = prefs(context, id).getString(NAME, "进度条");
        target.edit().putString(NAME, name + " 副本").putBoolean(ACTIVE, true).apply();
        ids.add(ids.indexOf(id) + 1, copyId);
        saveIds(context, ids);
        return copyId;
    }

    static void delete(Context context, String id) {
        List<String> ids = ids(context);
        if (!ids.remove(id)) return;
        saveIds(context, ids);
        prefs(context, id).edit().clear().apply();
    }

    static void move(Context context, String id, int delta) {
        List<String> ids = ids(context);
        int oldIndex = ids.indexOf(id), newIndex = oldIndex + delta;
        if (oldIndex < 0 || newIndex < 0 || newIndex >= ids.size()) return;
        ids.set(oldIndex, ids.get(newIndex));
        ids.set(newIndex, id);
        saveIds(context, ids);
    }

    private static void saveIds(Context context, List<String> ids) {
        OverlayController.prefs(context).edit().putString(IDS, String.join(",", ids)).apply();
        OverlayController.refresh(context);
    }

    private static void copy(SharedPreferences from, SharedPreferences to) {
        SharedPreferences.Editor edit = to.edit();
        for (Map.Entry<String, ?> item : from.getAll().entrySet()) {
            String key = item.getKey();
            if (OverlayController.ENABLED.equals(key) || IDS.equals(key) || MIGRATED.equals(key)) continue;
            Object value = item.getValue();
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof String) edit.putString(key, (String) value);
        }
        edit.apply();
    }
}
