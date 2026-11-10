package com.example.progressoverlay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Only observes window switches; it never reads view content. */
public class ForegroundAppService extends AccessibilityService {
    private static ForegroundAppService active;
    private String lastPackage;

    @Override protected void onServiceConnected() {
        active = this;
        OverlayController.accessibilityServiceConnected(this);
    }

    static void resetCachedPackage() {
        if (active != null) active.lastPackage = null;
    }

    static boolean isConnected() { return active != null; }
    static ForegroundAppService connectedService() { return active; }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayController.isEnabled(this)) { lastPackage = null; return; }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(lastPackage) || pkg.equals(getPackageName())
                || pkg.equals("com.android.systemui")) return;
        lastPackage = pkg;
        IconPalette colors = IconPalette.forPackage(this, pkg);
        if (colors != null) OverlayController.setForegroundColors(this, colors);
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (active == this) active = null;
        OverlayController.setForegroundColors(this, null);
        super.onDestroy();
    }
}
