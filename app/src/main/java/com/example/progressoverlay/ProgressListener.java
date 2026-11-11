package com.example.progressoverlay;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

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
        float ratio = -1f;
        IconPalette colors = null;
        long latest = Long.MIN_VALUE;
        if (all != null) for (StatusBarNotification item : all) {
            if (item.getPackageName().equals(getPackageName())) continue;
            Bundle e = item.getNotification().extras;
            if (e == null || !e.containsKey(Notification.EXTRA_PROGRESS)
                    || e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) continue;
            int max = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            if (max <= 0) continue;
            if (item.getPostTime() >= latest) {
                latest = item.getPostTime();
                ratio = Math.max(0f, Math.min(1f, (float)e.getInt(Notification.EXTRA_PROGRESS, 0) / max));
                colors = IconPalette.forNotification(this, item);
            }
        }
        OverlayController.setLive(this, ratio, colors);
    }
}
