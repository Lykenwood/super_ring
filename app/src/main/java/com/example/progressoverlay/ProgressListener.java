package com.example.progressoverlay;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.Map;

public class ProgressListener extends NotificationListenerService {
    private static ProgressListener active;

    @Override public void onListenerConnected() { active = this; scan(); }
    @Override public void onListenerDisconnected() {
        if (active == this) active = null;
        OverlayController.clearLive(this);
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { scan(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { scan(); }

    static void rescanActive() {
        if (active != null) active.scan();
    }

    private void scan() {
        if (!OverlayController.isEnabled(this)) return;
        StatusBarNotification[] all = getActiveNotifications();
        OverlayController.LiveData selected = null;
        Map<String, OverlayController.LiveData> byApp = new HashMap<>();
        long latest = Long.MIN_VALUE;
        if (all != null) for (StatusBarNotification item : all) {
            if (item.getPackageName().equals(getPackageName())) continue;
            Bundle e = item.getNotification().extras;
            if (e == null || !e.containsKey(Notification.EXTRA_PROGRESS)
                    || e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) continue;
            int max = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            if (max <= 0) continue;
            float value = Math.max(0f, Math.min(1f, (float)e.getInt(Notification.EXTRA_PROGRESS, 0) / max));
            OverlayController.LiveData previous = byApp.get(item.getPackageName());
            if (previous == null || item.getPostTime() >= previous.postTime)
                byApp.put(item.getPackageName(), new OverlayController.LiveData(value,
                        IconPalette.forNotification(this, item), item.getPostTime()));
            if (item.getPostTime() >= latest) {
                latest = item.getPostTime();
                selected = byApp.get(item.getPackageName());
            }
        }
        OverlayController.setLives(this, selected, byApp);
    }
}
