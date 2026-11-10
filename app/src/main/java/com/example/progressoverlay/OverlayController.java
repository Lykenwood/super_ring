package com.example.progressoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.BlurMaskFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
    static final String OPACITY = "opacity";
    static final String FOREGROUND_COLOR = "foreground_color";
    static final String COLOR_MODE = "color_mode";
    static final String GLOW_ENABLED = "glow_enabled", GLOW_FOLLOW = "glow_follow";
    static final String GLOW_COLOR = "glow_color", GLOW_RADIUS = "glow_radius", GLOW_OPACITY = "glow_opacity";
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

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean isEnabled(Context c) { return prefs(c).getBoolean(ENABLED, true); }
    static void setLive(Context c, float ratio, IconPalette colors) {
        live = ratio;
        liveColors = colors;
        refresh(c);
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
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
        private static final long COLOR_TRANSITION_MS = 200;
        private static final AccelerateDecelerateInterpolator COLOR_EASING = new AccelerateDecelerateInterpolator();
        float ratio;
        private SharedPreferences settings;
        private RenderState state;
        private final Map<String, RenderState> renderStates = new LinkedHashMap<>();
        float lineThickness;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final class RenderState {
            int[] displayedColors, startingColors, targetColors;
            int displayedGlowColor, startingGlowColor, targetGlowColor;
            long colorTransitionStart;
            boolean colorTransitionActive;
        }
        ProgressView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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
                ratio = preview ? 1f : live;
                drawInstance(canvas);
            }
        }
        private void drawInstance(Canvas canvas) {
            SharedPreferences p = settings;
            int displayStyle = p.getInt(STYLE, 0);
            float thickness = dp(fifths(p, displayStyle == 2 ? SCREEN_SIZE : SIZE, 6) / 5f);
            lineThickness = thickness;
            float value = Math.max(0f, Math.min(1f, ratio));
            int savedColor = p.getInt(COLOR, Color.rgb(47, 171, 255));
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
                    opacity, p.getInt(GLOW_COLOR, 0xff37bfff));
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
            }
        }
        private float clamp(float n, float min, float max) { return Math.max(min, Math.min(max, n)); }
        private int withAlpha(int color, int alpha) {
            return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
        }
        private int[] transitionColors(IconPalette palette, int mode, int style, int side,
                                       int color, int opacity, int glowColor) {
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
            } else if (!Arrays.equals(next, state.targetColors) || glowColor != state.targetGlowColor) {
                advanceColors(now);
                state.startingColors = state.displayedColors.clone();
                state.startingGlowColor = state.displayedGlowColor;
                state.targetColors = next;
                state.targetGlowColor = glowColor;
                state.colorTransitionStart = now;
                state.colorTransitionActive = ValueAnimator.areAnimatorsEnabled();
                if (!state.colorTransitionActive) {
                    state.displayedColors = next.clone();
                    state.displayedGlowColor = glowColor;
                }
            }
            advanceColors(now);
            if (state.colorTransitionActive) postInvalidateOnAnimation();
            return state.displayedColors;
        }
        private void advanceColors(long now) {
            if (!state.colorTransitionActive) return;
            float elapsed = Math.min(1f, (now - state.colorTransitionStart) / (float)COLOR_TRANSITION_MS);
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
                    float outerSpread = radius * .38f;
                    glowPaint.setAlpha(Math.round(glowAlpha * .65f));
                    glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, radius * .7f), BlurMaskFilter.Blur.NORMAL));
                    if (paint.getStyle() == Paint.Style.STROKE) glowPaint.setStrokeWidth(stroke + outerSpread * 2f);
                    shape.draw(canvas, glowPaint, outerSpread);
                    float innerSpread = radius * .14f;
                    glowPaint.setAlpha(Math.round(glowAlpha * .75f));
                    glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, radius * .28f), BlurMaskFilter.Blur.NORMAL));
                    if (paint.getStyle() == Paint.Style.STROKE) glowPaint.setStrokeWidth(stroke + innerSpread * 2f);
                    shape.draw(canvas, glowPaint, innerSpread);
                }
            }
            if (lineThickness > 0f) shape.draw(canvas, paint, 0f);
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
            Path route = new Path(); route.addRoundRect(bounds, corner, corner,
                    direction == 0 ? Path.Direction.CW : Path.Direction.CCW);
            PathMeasure measure = new PathMeasure(route, true);
            float routeLength = measure.getLength();
            if (routeLength <= 0) return;
            paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(withAlpha(colors[0], Math.round(Color.alpha(colors[0]) * .27f)));
            if (thickness > 0f) canvas.drawPath(route, paint);
            paint.setColor(colors[0]);
            float startAngle = fifths(p, SCREEN_ANGLE, -90) / 5f;
            if (varies(colors)) {
                int[] closed = Arrays.copyOf(colors, colors.length + 1); closed[colors.length] = colors[0];
                SweepGradient shader = new SweepGradient(bounds.centerX(), bounds.centerY(), closed, null);
                Matrix matrix = new Matrix(); matrix.setRotate(startAngle, bounds.centerX(), bounds.centerY()); shader.setLocalMatrix(matrix);
                paint.setShader(shader);
            }
            float start = nearestAngleDistance(measure, bounds.centerX(), bounds.centerY(), startAngle);
            float end = start + routeLength * value;
            Path filled = new Path();
            if (value >= 1f) drawWithGlow(canvas, p, opacity, (target, brush, spread) -> target.drawPath(route, brush));
            else if (value > 0f) {
                if (end <= routeLength) measure.getSegment(start, end, filled, true);
                else {
                    measure.getSegment(start, routeLength, filled, true);
                    measure.getSegment(0, end - routeLength, filled, true);
                }
                drawWithGlow(canvas, p, opacity, (target, brush, spread) -> target.drawPath(filled, brush));
            }
        }
        private float nearestAngleDistance(PathMeasure measure, float cx, float cy, float degrees) {
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
