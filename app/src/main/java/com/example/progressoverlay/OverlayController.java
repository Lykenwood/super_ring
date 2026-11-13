package com.example.progressoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.BlurMaskFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OverlayController {
    static final String PREFS = "display";
    static final String ENABLED = "enabled";
    static final String PREVIEW = "preview", STYLE = "style", SIDE = "side", SIZE = "size", COLOR = "color", AUTO_COLOR = "auto_color";
    static final int DEFAULT_COLOR = 0xffeeeeee;
    static final String OPACITY = "opacity";
    static final String FOREGROUND_COLOR = "foreground_color";
    static final String COLOR_MODE = "color_mode";
    static final String GLOW_ENABLED = "glow_enabled", GLOW_FOLLOW = "glow_follow";
    static final String GLOW_COLOR = "glow_color", GLOW_RADIUS = "glow_radius", GLOW_OPACITY = "glow_opacity";
    static final String COLOR_TRANSITION_MS = "color_transition_ms";
    static final int DEFAULT_COLOR_TRANSITION_MS = 200;
    private static final int COMPLETE_TRANSITION_MS = 250;
    private static final int FINISH_FADE_MS = 3000;
    static final String RING_X = "ring_x", RING_Y = "ring_y", RING_RADIUS = "ring_radius";
    static final String RING_LANDSCAPE_X = "ring_landscape_x", RING_LANDSCAPE_Y = "ring_landscape_y";
    static final String RING_ANGLE = "ring_angle", RING_DIRECTION = "ring_direction";
    static final String EDGE_X = "edge_x", EDGE_Y = "edge_y", EDGE_LENGTH = "edge_length", EDGE_DIRECTION = "edge_direction";
    static final String SCREEN_X = "screen_x", SCREEN_Y = "screen_y", SCREEN_RADIUS = "screen_radius";
    static final String SCREEN_WIDTH = "screen_width", SCREEN_HEIGHT = "screen_height";
    static final String SCREEN_SIZE = "screen_size", SCREEN_ANGLE = "screen_angle", SCREEN_DIRECTION = "screen_direction";
    static int fifths(SharedPreferences p, String key, int fallbackDp) {
        return p.getInt(key + "_fifths", p.getInt(key, fallbackDp) * 5);
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager manager;
    private static Context windowContext;
    private static int windowType;
    private static boolean accessibilityOverlayFailed;
    private static ProgressView view;
    private static float live = -1f;
    private static IconPalette liveColors;
    private static IconPalette foregroundColors;
    private static boolean finishing, completionLatched;
    private static long finishStartedAt;
    private static int finishGeneration;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean isEnabled(Context c) { return prefs(c).getBoolean(ENABLED, true); }
    static void setLive(Context c, float ratio, IconPalette colors) {
        Context app = c.getApplicationContext();
        MAIN.post(() -> {
            if (ratio < 0f) {
                if (live >= 0f && !completionLatched) startFinish(app);
            } else if (ratio >= 1f) {
                if (!completionLatched) {
                    liveColors = colors;
                    startFinish(app);
                }
            } else {
                finishGeneration++;
                finishing = false;
                completionLatched = false;
                live = ratio;
                liveColors = colors;
            }
            update(app);
        });
    }
    private static void startFinish(Context c) {
        finishing = true;
        completionLatched = true;
        finishStartedAt = SystemClock.uptimeMillis();
        live = 1f;
        int generation = ++finishGeneration;
        MAIN.postDelayed(() -> {
            if (!finishing || generation != finishGeneration) return;
            finishing = false;
            live = -1f;
            liveColors = null;
            update(c);
        }, COMPLETE_TRANSITION_MS + FINISH_FADE_MS);
    }
    static void clearLive(Context c) {
        Context app = c.getApplicationContext();
        MAIN.post(() -> {
            finishGeneration++;
            finishing = false;
            completionLatched = false;
            live = -1f;
            liveColors = null;
            update(app);
        });
    }
    static void refresh(Context c) {
        Context app = c.getApplicationContext();
        MAIN.post(() -> update(app));
    }
    static void accessibilityServiceConnected(Context c) {
        accessibilityOverlayFailed = false;
        refresh(c);
    }
    static void setForegroundColors(Context c, IconPalette colors) {
        foregroundColors = colors;
        refresh(c);
    }
    private static void update(Context c) {
        if (!isEnabled(c)) { remove(); return; }
        if (!Settings.canDrawOverlays(c)) {
            remove(); return;
        }
        boolean hasVisible = false;
        for (String id : InstanceStore.ids(c)) {
            SharedPreferences settings = InstanceStore.prefs(c, id);
            if (settings.getBoolean(InstanceStore.ACTIVE, true)
                    && (settings.getBoolean(PREVIEW, false) || live >= 0)) {
                hasVisible = true;
                break;
            }
        }
        if (!hasVisible) { remove(); return; }
        int desiredType = ForegroundAppService.isConnected() && !accessibilityOverlayFailed
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        if (view != null && windowType != desiredType) remove();
        if (!addWindow(c, desiredType) && desiredType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            accessibilityOverlayFailed = true;
            addWindow(c, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        if (view != null) view.invalidate();
    }
    private static boolean addWindow(Context c, int type) {
        if (windowContext == null) {
            windowContext = type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    ? ForegroundAppService.connectedService() : c;
            if (windowContext == null) return false;
            if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && Build.VERSION.SDK_INT >= 30) {
                DisplayManager displays = (DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE);
                Display display = displays.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    Context displayContext = c.createDisplayContext(display);
                    windowContext = displayContext.createWindowContext(
                            type, null);
                }
            }
            manager = (WindowManager)windowContext.getSystemService(Context.WINDOW_SERVICE);
        }
        if (view == null) {
            view = new ProgressView(windowContext);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    -1, -1, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | (Build.VERSION.SDK_INT >= 31
                                    ? WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED : 0),
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            // Android 12+ can discard touches passing through an opaque app overlay.
            // Accessibility overlays are trusted while the service is connected.
            lp.alpha = type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    && Build.VERSION.SDK_INT >= 31 ? safeTouchOpacity(c) : 1f;
            if (Build.VERSION.SDK_INT >= 30) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                lp.setFitInsetsTypes(0);
            } else if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try { manager.addView(view, lp); windowType = type; }
            catch (RuntimeException ignored) { remove(); return false; }
        }
        return true;
    }
    private static float safeTouchOpacity(Context c) {
        InputManager input = (InputManager)c.getSystemService(Context.INPUT_SERVICE);
        float maximum = input == null ? .8f : input.getMaximumObscuringOpacityForTouch();
        return Math.max(0f, Math.min(.79f, maximum - .01f));
    }
    private static void remove() {
        if (view != null && manager != null) {
            try { manager.removeView(view); } catch (RuntimeException ignored) { }
        }
        view = null; manager = null; windowContext = null; windowType = 0;
    }

    private static final class ProgressView extends View {
        private static final int COLOR_STOPS = 16;
        private static final int PROGRESS_TRANSITION_MS = 1000;
        private static final AccelerateDecelerateInterpolator COLOR_EASING = new AccelerateDecelerateInterpolator();
        float ratio;
        private SharedPreferences settings;
        private RenderState state;
        private final Map<String, RenderState> renderStates = new LinkedHashMap<>();
        float lineThickness;
        float instanceFade = 1f;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix haloMatrix = new Matrix();
        private static final class RenderState {
            float displayedProgress, startingProgress, targetProgress;
            long progressTransitionStart;
            int progressTransitionDurationMs;
            boolean progressInitialized, progressTransitionActive;
            ScreenGeometry screenGeometry;
            float glowFilterRadius = -1f;
            BlurMaskFilter outerGlowFilter, innerGlowFilter;
            RenderNode outerGlowNode, innerGlowNode;
            float hardwareGlowRadius = -1f;
            final Map<Integer, RadialGradient> sparkleHalos = new LinkedHashMap<>();
            int[] displayedColors, startingColors, targetColors;
            int displayedGlowColor, startingGlowColor, targetGlowColor;
            long colorTransitionStart;
            int colorTransitionDurationMs;
            boolean colorTransitionActive;
        }
        private static final class ScreenGeometry {
            final RectF bounds;
            final float corner, startAngle, length, start;
            final int direction;
            final Path route;
            final PathMeasure measure;

            ScreenGeometry(RectF bounds, float corner, int direction, float startAngle) {
                this.bounds = new RectF(bounds);
                this.corner = corner;
                this.direction = direction;
                this.startAngle = startAngle;
                route = new Path();
                route.addRoundRect(bounds, corner, corner,
                        direction == 0 ? Path.Direction.CW : Path.Direction.CCW);
                measure = new PathMeasure(route, true);
                length = measure.getLength();
                start = length > 0f ? nearestAngleDistance(measure,
                        bounds.centerX(), bounds.centerY(), startAngle) : 0f;
            }

            boolean matches(RectF nextBounds, float nextCorner, int nextDirection, float nextAngle) {
                return bounds.equals(nextBounds) && corner == nextCorner
                        && direction == nextDirection && startAngle == nextAngle;
            }
        }
        ProgressView(Context c) {
            super(c);
            if (Build.VERSION.SDK_INT < 31) setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        float dp(float n) { return n * getResources().getDisplayMetrics().density; }
        @Override protected void onDraw(Canvas canvas) {
            List<String> ids = InstanceStore.ids(getContext());
            renderStates.keySet().retainAll(ids);
            for (String id : ids) {
                SharedPreferences p = InstanceStore.prefs(getContext(), id);
                if (!p.getBoolean(InstanceStore.ACTIVE, true)) continue;
                boolean preview = p.getBoolean(PREVIEW, false);
                if (!preview && live < 0) continue;
                settings = p;
                state = renderStates.computeIfAbsent(id, key -> new RenderState());
                if (preview) {
                    state.progressInitialized = false;
                    ratio = 1f;
                } else ratio = transitionProgress(Math.max(0f, Math.min(1f, live)));
                int fadeAlpha = 255;
                if (!preview && finishing) {
                    long fadeElapsed = SystemClock.uptimeMillis() - finishStartedAt - COMPLETE_TRANSITION_MS;
                    if (fadeElapsed > 0) fadeAlpha = Math.max(0, 255 - Math.round(255f * fadeElapsed / FINISH_FADE_MS));
                    if (fadeAlpha > 0) postInvalidateOnAnimation();
                }
                if (fadeAlpha > 0) {
                    instanceFade = fadeAlpha / 255f;
                    paint.setColorFilter(fadeAlpha == 255 ? null : new ColorMatrixColorFilter(new float[]{
                            1f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, fadeAlpha / 255f, 0f
                    }));
                    drawInstance(canvas);
                }
            }
        }
        private float transitionProgress(float target) {
            long now = SystemClock.uptimeMillis();
            if (!state.progressInitialized) {
                state.displayedProgress = target;
                state.targetProgress = target;
                state.progressInitialized = true;
            } else if (Float.compare(target, state.targetProgress) != 0) {
                advanceProgress(now);
                if (target < state.displayedProgress || !ValueAnimator.areAnimatorsEnabled()) {
                    state.displayedProgress = target;
                    state.progressTransitionActive = false;
                } else {
                    state.startingProgress = state.displayedProgress;
                    state.progressTransitionStart = finishing && target >= 1f ? finishStartedAt : now;
                    state.progressTransitionDurationMs = target >= 1f
                            ? COMPLETE_TRANSITION_MS : PROGRESS_TRANSITION_MS;
                    state.progressTransitionActive = true;
                }
                state.targetProgress = target;
            }
            advanceProgress(now);
            if (state.progressTransitionActive) postInvalidateOnAnimation();
            return state.displayedProgress;
        }
        private void advanceProgress(long now) {
            if (!state.progressTransitionActive) return;
            float elapsed = Math.min(1f, (now - state.progressTransitionStart)
                    / (float)state.progressTransitionDurationMs);
            state.displayedProgress = state.startingProgress
                    + (state.targetProgress - state.startingProgress) * elapsed;
            if (elapsed >= 1f) state.progressTransitionActive = false;
        }
        private void drawInstance(Canvas canvas) {
            SharedPreferences p = settings;
            int displayStyle = p.getInt(STYLE, 0);
            float thickness = dp(fifths(p, displayStyle == 2 ? SCREEN_SIZE : SIZE, 6) / 5f);
            lineThickness = thickness;
            float value = Math.max(0f, Math.min(1f, ratio));
            int savedColor = p.getInt(COLOR, DEFAULT_COLOR);
            int opacity = p.getInt(OPACITY, Color.alpha(savedColor));
            int color = withAlpha(savedColor, opacity);
            IconPalette palette = null;
            if (p.getBoolean(FOREGROUND_COLOR, false)) palette = foregroundColors;
            else if (p.getBoolean(AUTO_COLOR, false)) palette = liveColors;
            if ((p.getBoolean(FOREGROUND_COLOR, false) || p.getBoolean(AUTO_COLOR, false)) && palette == null)
                palette = IconPalette.sample();
            int mode = p.getInt(COLOR_MODE, 0);
            int side = p.getInt(SIDE, 0);
            int[] colors = transitionColors(palette, mode, displayStyle, side, color,
                    opacity, p.getInt(GLOW_COLOR, 0xff37bfff),
                    Math.max(0, Math.min(5000, p.getInt(COLOR_TRANSITION_MS, DEFAULT_COLOR_TRANSITION_MS))));
            paint.setColor(colors[0]); paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            if (displayStyle == 2) {
                drawScreen(canvas, p, thickness, value, colors, opacity);
            } else if (displayStyle == 1) {
                Rect cutout = null;
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
                    WindowInsets insets = wm.getCurrentWindowMetrics().getWindowInsets();
                    DisplayCutout displayCutout = insets.getDisplayCutout();
                    if (displayCutout != null && !displayCutout.getBoundingRects().isEmpty())
                        cutout = displayCutout.getBoundingRects().get(0);
                }
                float cx = cutout == null ? getWidth() / 2f : cutout.exactCenterX();
                float cy = cutout == null ? dp(24) : cutout.exactCenterY();
                float automaticRadius = cutout == null ? dp(19) : Math.max(cutout.width(), cutout.height()) / 2f + thickness;
                int chosenRadius = fifths(p, RING_RADIUS, 0);
                float radius = chosenRadius == 0 ? automaticRadius : dp(chosenRadius / 5f);
                int[] origin = new int[2];
                getLocationOnScreen(origin);
                boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                String xKey = landscape ? RING_LANDSCAPE_X : RING_X;
                String yKey = landscape ? RING_LANDSCAPE_Y : RING_Y;
                cx = cx - origin[0] + dp(fifths(p, xKey, 0) / 5f);
                cy = cy - origin[1] + dp(fifths(p, yKey, 0) / 5f);
                // Keep the complete ring inside the overlay window on devices
                // that still inset application overlays below the status bar.
                float padding = thickness / 2f + dp(3);
                cx = Math.max(radius + padding, Math.min(getWidth() - radius - padding, cx));
                cy = Math.max(radius + padding, Math.min(getHeight() - radius - padding, cy));
                RectF arc = new RectF(cx-radius, cy-radius, cx+radius, cy+radius);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);
                paint.setColor(withAlpha(colors[0], Math.round(Color.alpha(colors[0]) * .27f)));
                if (thickness > 0f) canvas.drawArc(arc, 0, 360, false, paint);
                paint.setColor(colors[0]); paint.setStrokeCap(Paint.Cap.ROUND);
                float startAngle = fifths(p, RING_ANGLE, -90) / 5f;
                int direction = p.getInt(RING_DIRECTION, 0) == 0 ? 1 : -1;
                if (varies(colors)) {
                    int[] closed = Arrays.copyOf(colors, colors.length + 1);
                    closed[colors.length] = colors[0];
                    SweepGradient shader = new SweepGradient(cx, cy, closed, null);
                    Matrix matrix = new Matrix(); matrix.setRotate(startAngle, cx, cy); shader.setLocalMatrix(matrix);
                    paint.setShader(shader);
                }
                if (value > 0f) drawWithGlow(canvas, p, opacity,
                        (target, brush, spread) -> target.drawArc(arc, startAngle, direction * 360 * value, false, brush));
                drawRingSparkles(canvas, cx, cy, radius, colors);
            } else {
                float w = getWidth(), h = getHeight();
                WindowInsets rootInsets = getRootWindowInsets();
                float statusBottom = 0;
                float navigationTop = h;
                if (rootInsets != null) {
                    int screenInset = Build.VERSION.SDK_INT >= 30
                            ? rootInsets.getInsets(WindowInsets.Type.statusBars()).top
                            : rootInsets.getSystemWindowInsetTop();
                    int navInset = Build.VERSION.SDK_INT >= 30
                            ? rootInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
                            : rootInsets.getSystemWindowInsetBottom();
                    int[] origin = new int[2]; getLocationOnScreen(origin);
                    statusBottom = Math.max(0, screenInset - origin[1]);
                    navigationTop = Math.max(statusBottom, h - navInset);
                }
                boolean horizontal = side < 2;
                float available = horizontal ? w : Math.max(0, navigationTop - statusBottom);
                int lengthFifths = fifths(p, EDGE_LENGTH, 0);
                float length = lengthFifths == 0 ? available : Math.min(available, dp(lengthFifths / 5f));
                float ox = dp(fifths(p, EDGE_X, 0) / 5f), oy = dp(fifths(p, EDGE_Y, 0) / 5f);
                float from, center;
                if (horizontal) {
                    from = clamp((w - length) / 2f + ox, 0, w - length);
                    center = clamp((side == 0 ? statusBottom + thickness / 2f : navigationTop - thickness / 2f) + oy,
                            thickness / 2f, h - thickness / 2f);
                } else {
                    from = clamp(statusBottom + (available - length) / 2f + oy, statusBottom, navigationTop - length);
                    center = clamp((side == 2 ? thickness / 2f : w - thickness / 2f) + ox,
                            thickness / 2f, w - thickness / 2f);
                }
                paint.setColor(colors[0]);
                if (varies(colors) && length > 0) {
                    paint.setShader(horizontal
                            ? new LinearGradient(from, center, from + length, center, colors, null, Shader.TileMode.CLAMP)
                            : new LinearGradient(center, from, center, from + length, colors, null, Shader.TileMode.CLAMP));
                }
                boolean reverse = p.getInt(EDGE_DIRECTION, 0) == 1;
                float filled = length * value;
                if (filled > 0f) {
                    if (horizontal) {
                        float left = reverse ? from + length - filled : from;
                        drawWithGlow(canvas, p, opacity, (target, brush, spread) -> target.drawRect(
                                left - spread, center - thickness / 2f - spread,
                                left + filled + spread, center + thickness / 2f + spread, brush));
                    } else {
                        float top = reverse ? from : from + length - filled;
                        drawWithGlow(canvas, p, opacity, (target, brush, spread) -> target.drawRect(
                                center - thickness / 2f - spread, top - spread,
                                center + thickness / 2f + spread, top + filled + spread, brush));
                    }
                }
                drawEdgeSparkles(canvas, horizontal, side, from, center, length, colors);
            }
        }
        private long sparkleAge() {
            if (!finishing || ratio < 1f || !ValueAnimator.areAnimatorsEnabled()) return -1;
            long age = SystemClock.uptimeMillis() - finishStartedAt - COMPLETE_TRANSITION_MS;
            return age >= 0 && age < 1300 ? age : -1;
        }
        private void drawRingSparkles(Canvas canvas, float cx, float cy, float radius, int[] colors) {
            long age = sparkleAge();
            if (age < 0) return;
            for (int i = 0; i < 8; i++) {
                double angle = 2 * Math.PI * (i + .35) / 8;
                float vx = (float)Math.cos(angle), vy = (float)Math.sin(angle);
                drawSparkle(canvas, cx + vx * radius, cy + vy * radius,
                        vx, vy, colors[i * colors.length / 8], age, i);
            }
        }
        private void drawEdgeSparkles(Canvas canvas, boolean horizontal, int side,
                                      float from, float center, float length, int[] colors) {
            long age = sparkleAge();
            if (age < 0 || length <= 0f) return;
            for (int i = 0; i < 10; i++) {
                float position = from + length * (i + .5f) / 10f;
                float x = horizontal ? position : center;
                float y = horizontal ? center : position;
                float vx = horizontal ? 0f : side == 2 ? 1f : -1f;
                float vy = horizontal ? side == 0 ? 1f : -1f : 0f;
                drawSparkle(canvas, x, y, vx, vy,
                        colors[i * colors.length / 10], age, i);
            }
        }
        private void drawScreenSparkles(Canvas canvas, ScreenGeometry geometry, int[] colors) {
            long age = sparkleAge();
            if (age < 0) return;
            float[] point = new float[2];
            for (int i = 0; i < 12; i++) {
                geometry.measure.getPosTan(geometry.length * (i + .5f) / 12f, point, null);
                float vx = geometry.bounds.centerX() - point[0];
                float vy = geometry.bounds.centerY() - point[1];
                float distance = (float)Math.hypot(vx, vy);
                if (distance <= 0f) continue;
                drawSparkle(canvas, point[0], point[1], vx / distance, vy / distance,
                        colors[i * colors.length / 12], age, i);
            }
        }
        private void drawSparkle(Canvas canvas, float x, float y, float vx, float vy,
                                 int sourceColor, long age, int index) {
            float t = (age - (index % 4) * 65f) / 1050f;
            if (t < 0f || t >= 1f) return;
            float drift = dp(12f + index % 5 * 3.5f) * t;
            float sideways = dp((index % 3 - 1) * 5f) * t;
            x += vx * drift - vy * sideways;
            y += vy * drift + vx * sideways;
            float pulse = .8f + .2f * (float)Math.sin(t * Math.PI * 5f + index);
            float opacity = Math.min(1f, t * 8f) * (float)Math.sqrt(1f - t) * pulse * instanceFade
                    * Color.alpha(sourceColor) / 255f;
            if (opacity <= 0f) return;
            int tint = sourceColor | 0xff000000;
            int bright = blend(tint, Color.WHITE, .25f);
            float size = dp(index % 5 == 0 ? 2.8f : 1.5f + index % 3 * .35f);
            sparklePaint.setShader(null);
            sparklePaint.setStyle(Paint.Style.FILL);
            if (index % 3 == 0) {
                RadialGradient halo = state.sparkleHalos.get(tint);
                if (halo == null) {
                    if (state.sparkleHalos.size() >= 32) state.sparkleHalos.clear();
                    halo = new RadialGradient(0f, 0f, 1f,
                            new int[]{withAlpha(tint, 180), withAlpha(tint, 70), withAlpha(tint, 0)},
                            new float[]{0f, .3f, 1f}, Shader.TileMode.CLAMP);
                    state.sparkleHalos.put(tint, halo);
                }
                float haloRadius = size * 4.2f;
                haloMatrix.setScale(haloRadius, haloRadius);
                haloMatrix.postTranslate(x, y);
                halo.setLocalMatrix(haloMatrix);
                sparklePaint.setShader(halo);
                sparklePaint.setAlpha(Math.round(255f * opacity));
                canvas.drawCircle(x, y, haloRadius, sparklePaint);
                sparklePaint.setShader(null);
            }
            sparklePaint.setColor(withAlpha(bright, Math.round(255f * opacity)));
            canvas.drawCircle(x, y, size, sparklePaint);
            sparklePaint.setStyle(Paint.Style.STROKE);
            sparklePaint.setStrokeCap(Paint.Cap.ROUND);
            sparklePaint.setStrokeWidth(dp(.8f));
            float ray = size * 3.5f * (1f - t * .4f);
            canvas.drawLine(x - ray, y, x + ray, y, sparklePaint);
            canvas.drawLine(x, y - ray, x, y + ray, sparklePaint);
        }
        private float clamp(float n, float min, float max) { return Math.max(min, Math.min(max, n)); }
        private int withAlpha(int color, int alpha) {
            return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
        }
        private int[] transitionColors(IconPalette palette, int mode, int style, int side,
                                       int color, int opacity, int glowColor, int durationMs) {
            int[] source = palette == null ? new int[]{color}
                    : palette.stops(mode, style != 0, style == 0 && side >= 2);
            int[] next = new int[COLOR_STOPS];
            for (int i = 0; i < COLOR_STOPS; i++) {
                float index = source.length == 1 ? 0f : (source.length - 1f) * i / (COLOR_STOPS - 1f);
                int from = (int)index;
                int to = Math.min(source.length - 1, from + 1);
                next[i] = withAlpha(blend(source[from], source[to], index - from), opacity);
            }
            long now = SystemClock.uptimeMillis();
            if (state.displayedColors == null) {
                state.displayedColors = next.clone();
                state.targetColors = next;
                state.displayedGlowColor = glowColor;
                state.targetGlowColor = glowColor;
                state.colorTransitionDurationMs = durationMs;
            } else if (!Arrays.equals(next, state.targetColors) || glowColor != state.targetGlowColor
                    || (state.colorTransitionActive && durationMs != state.colorTransitionDurationMs)) {
                advanceColors(now);
                state.startingColors = state.displayedColors.clone();
                state.startingGlowColor = state.displayedGlowColor;
                state.targetColors = next;
                state.targetGlowColor = glowColor;
                state.colorTransitionStart = now;
                state.colorTransitionDurationMs = durationMs;
                state.colorTransitionActive = durationMs > 0 && ValueAnimator.areAnimatorsEnabled();
                if (!state.colorTransitionActive) {
                    state.displayedColors = next.clone();
                    state.displayedGlowColor = glowColor;
                }
            } else state.colorTransitionDurationMs = durationMs;
            advanceColors(now);
            if (state.colorTransitionActive) postInvalidateOnAnimation();
            return state.displayedColors;
        }
        private void advanceColors(long now) {
            if (!state.colorTransitionActive) return;
            float elapsed = Math.min(1f, (now - state.colorTransitionStart)
                    / (float)state.colorTransitionDurationMs);
            float fraction = COLOR_EASING.getInterpolation(elapsed);
            for (int i = 0; i < COLOR_STOPS; i++)
                state.displayedColors[i] = blend(state.startingColors[i], state.targetColors[i], fraction);
            state.displayedGlowColor = blend(state.startingGlowColor, state.targetGlowColor, fraction);
            if (elapsed >= 1f) state.colorTransitionActive = false;
        }
        private int blend(int from, int to, float amount) {
            int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount);
            int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
            int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
            int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
            return Color.argb(a, r, g, b);
        }
        private boolean varies(int[] colors) {
            for (int i = 1; i < colors.length; i++) if (colors[i] != colors[0]) return true;
            return false;
        }
        private interface ShapeDrawer { void draw(Canvas canvas, Paint brush, float spread); }
        private void drawWithGlow(Canvas canvas, SharedPreferences p, int lineOpacity, ShapeDrawer shape) {
            if (p.getBoolean(GLOW_ENABLED, false)) {
                float radius = dp(fifths(p, GLOW_RADIUS, 12) / 5f);
                float percent = Math.max(0f, Math.min(1f, fifths(p, GLOW_OPACITY, 55) / 500f));
                if (radius > 0f && percent > 0f) {
                    glowPaint.set(paint);
                    int sourceAlpha = paint.getShader() == null ? Color.alpha(paint.getColor()) : 255;
                    if (p.getBoolean(GLOW_FOLLOW, true)) {
                        glowPaint.setAlpha(Math.round(sourceAlpha * percent));
                    } else {
                        glowPaint.setShader(null);
                        glowPaint.setColor(withAlpha(state.displayedGlowColor,
                                Math.round(lineOpacity * percent)));
                    }
                    int glowAlpha = glowPaint.getAlpha();
                    float stroke = paint.getStrokeWidth();
                    boolean hardwareGlow = Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated();
                    if (hardwareGlow) prepareHardwareGlow(radius);
                    else prepareSoftwareGlow(radius);
                    float outerSpread = radius * .38f;
                    glowPaint.setAlpha(Math.round(glowAlpha * .65f));
                    glowPaint.setMaskFilter(hardwareGlow ? null : state.outerGlowFilter);
                    if (paint.getStyle() == Paint.Style.STROKE) glowPaint.setStrokeWidth(stroke + outerSpread * 2f);
                    if (hardwareGlow) drawHardwareGlow(canvas, state.outerGlowNode, shape, outerSpread);
                    else shape.draw(canvas, glowPaint, outerSpread);
                    float innerSpread = radius * .14f;
                    glowPaint.setAlpha(Math.round(glowAlpha * .75f));
                    glowPaint.setMaskFilter(hardwareGlow ? null : state.innerGlowFilter);
                    if (paint.getStyle() == Paint.Style.STROKE) glowPaint.setStrokeWidth(stroke + innerSpread * 2f);
                    if (hardwareGlow) drawHardwareGlow(canvas, state.innerGlowNode, shape, innerSpread);
                    else shape.draw(canvas, glowPaint, innerSpread);
                }
            }
            if (lineThickness > 0f) shape.draw(canvas, paint, 0f);
        }
        private void prepareSoftwareGlow(float radius) {
            if (state.glowFilterRadius == radius) return;
            state.glowFilterRadius = radius;
            state.outerGlowFilter = new BlurMaskFilter(Math.max(1f, radius * .7f), BlurMaskFilter.Blur.NORMAL);
            state.innerGlowFilter = new BlurMaskFilter(Math.max(1f, radius * .28f), BlurMaskFilter.Blur.NORMAL);
        }
        private void prepareHardwareGlow(float radius) {
            if (state.outerGlowNode == null) {
                state.outerGlowNode = new RenderNode("Super Ring outer glow");
                state.innerGlowNode = new RenderNode("Super Ring inner glow");
            }
            state.outerGlowNode.setPosition(0, 0, getWidth(), getHeight());
            state.innerGlowNode.setPosition(0, 0, getWidth(), getHeight());
            if (state.hardwareGlowRadius == radius) return;
            state.hardwareGlowRadius = radius;
            state.outerGlowNode.setRenderEffect(RenderEffect.createBlurEffect(
                    Math.max(1f, radius * .7f), Math.max(1f, radius * .7f), Shader.TileMode.DECAL));
            state.innerGlowNode.setRenderEffect(RenderEffect.createBlurEffect(
                    Math.max(1f, radius * .28f), Math.max(1f, radius * .28f), Shader.TileMode.DECAL));
        }
        private void drawHardwareGlow(Canvas canvas, RenderNode node, ShapeDrawer shape, float spread) {
            RecordingCanvas recording = node.beginRecording();
            try { shape.draw(recording, glowPaint, spread); }
            finally { node.endRecording(); }
            canvas.drawRenderNode(node);
        }

        private void drawScreen(Canvas canvas, SharedPreferences p, float thickness, float value,
                                int[] colors, int opacity) {
            WindowInsets insets = getRootWindowInsets();
            int[] origin = new int[2]; getLocationOnScreen(origin);
            float topInset = 0, bottomInset = 0;
            if (insets != null) {
                topInset = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.statusBars()).top : insets.getSystemWindowInsetTop();
                bottomInset = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : insets.getSystemWindowInsetBottom();
            }
            float pad = dp(5) + thickness / 2f;
            float dx = dp(fifths(p, SCREEN_X, 0) / 5f), dy = dp(fifths(p, SCREEN_Y, 0) / 5f);
            float availableLeft = pad;
            float availableTop = Math.max(pad, topInset - origin[1] + pad);
            float availableRight = getWidth() - pad;
            float availableBottom = getHeight() - bottomInset - pad;
            float availableWidth = availableRight - availableLeft;
            float availableHeight = availableBottom - availableTop;
            if (availableWidth <= 0 || availableHeight <= 0) return;
            int savedWidth = fifths(p, SCREEN_WIDTH, 0), savedHeight = fifths(p, SCREEN_HEIGHT, 0);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                int portraitWidth = savedWidth;
                savedWidth = savedHeight;
                savedHeight = portraitWidth;
            }
            // A manual size uses the whole overlay canvas. The safe region above
            // applies only to Auto, so system bars and split screen insets do not
            // silently cap a manually chosen width or height.
            float width = savedWidth == 0 ? availableWidth : dp(savedWidth / 5f);
            float height = savedHeight == 0 ? availableHeight : dp(savedHeight / 5f);
            float centerX = savedWidth == 0 ? (availableLeft + availableRight) / 2f : getWidth() / 2f;
            float centerY = savedHeight == 0 ? (availableTop + availableBottom) / 2f : getHeight() / 2f;
            float left = centerX - width / 2f + dx;
            float top = centerY - height / 2f + dy;
            RectF bounds = new RectF(left, top, left + width, top + height);
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            float corner = Math.min(dp(fifths(p, SCREEN_RADIUS, 18) / 5f), Math.min(bounds.width(), bounds.height()) / 2f);
            int direction = p.getInt(SCREEN_DIRECTION, 0);
            float startAngle = fifths(p, SCREEN_ANGLE, -90) / 5f;
            ScreenGeometry geometry = state.screenGeometry;
            if (geometry == null || !geometry.matches(bounds, corner, direction, startAngle)) {
                geometry = new ScreenGeometry(bounds, corner, direction, startAngle);
                state.screenGeometry = geometry;
            }
            Path route = geometry.route;
            PathMeasure measure = geometry.measure;
            float routeLength = geometry.length;
            if (routeLength <= 0) return;
            paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(withAlpha(colors[0], Math.round(Color.alpha(colors[0]) * .27f)));
            if (thickness > 0f) canvas.drawPath(route, paint);
            paint.setColor(colors[0]);
            if (varies(colors)) {
                int[] closed = Arrays.copyOf(colors, colors.length + 1); closed[colors.length] = colors[0];
                SweepGradient shader = new SweepGradient(bounds.centerX(), bounds.centerY(), closed, null);
                Matrix matrix = new Matrix(); matrix.setRotate(startAngle, bounds.centerX(), bounds.centerY()); shader.setLocalMatrix(matrix);
                paint.setShader(shader);
            }
            float start = geometry.start;
            float end = start + routeLength * value;
            if (value >= 1f) drawWithGlow(canvas, p, opacity, (target, brush, spread) -> target.drawPath(route, brush));
            else if (value > 0f) {
                Path first = new Path();
                if (end <= routeLength) {
                    measure.getSegment(start, end, first, true);
                    drawWithGlow(canvas, p, opacity,
                            (target, brush, spread) -> target.drawPath(first, brush));
                } else {
                    Path wrapped = new Path();
                    measure.getSegment(start, routeLength, first, true);
                    measure.getSegment(0, end - routeLength, wrapped, true);
                    // Keep the two pieces separate when progress crosses the
                    // path's original starting point.
                    drawWithGlow(canvas, p, opacity, (target, brush, spread) -> {
                        target.drawPath(first, brush);
                        target.drawPath(wrapped, brush);
                    });
                }
            }
            drawScreenSparkles(canvas, geometry, colors);
        }
        private static float nearestAngleDistance(PathMeasure measure, float cx, float cy, float degrees) {
            float length = measure.getLength(), nearest = 0, best = Float.MAX_VALUE;
            float[] point = new float[2];
            for (int i = 0; i < 720; i++) {
                float distance = length * i / 720f;
                measure.getPosTan(distance, point, null);
                float angle = (float)Math.toDegrees(Math.atan2(point[1] - cy, point[0] - cx));
                float delta = Math.abs((angle - degrees + 540f) % 360f - 180f);
                if (delta < best) { best = delta; nearest = distance; }
            }
            return nearest;
        }
    }
}
