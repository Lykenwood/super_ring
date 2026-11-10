package com.example.progressoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Hue and saturation wheel with an adjacent brightness strip. */
final class ColorWheelView extends View {
    interface Listener { void onColorChanged(int rgb); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = {200f, .8f, 1f};
    private Bitmap wheel;
    private Listener listener;
    private float cx, cy, radius;
    private RectF brightness;

    ColorWheelView(Context context) { super(context); setMinimumHeight(dp(240)); }
    void setListener(Listener listener) { this.listener = listener; }
    void setColor(int color) { Color.colorToHSV(color, hsv); invalidate(); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        float gap = dp(18), strip = dp(28);
        radius = Math.max(1, Math.min((w - strip - gap) / 2f - dp(6), h / 2f - dp(8)));
        cx = radius + dp(8); cy = h / 2f;
        brightness = new RectF(cx + radius + gap, cy - radius, cx + radius + gap + strip, cy + radius);
        int size = 256;
        wheel = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];
        float center = (size - 1) / 2f;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float dx = x - center, dy = y - center;
            float sat = (float)Math.hypot(dx, dy) / center;
            if (sat <= 1f) {
                float hue = (float)((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
                pixels[y * size + x] = Color.HSVToColor(new float[]{hue, sat, 1f});
            }
        }
        wheel.setPixels(pixels, 0, size, 0, 0, size, size);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheel == null) return;
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        canvas.drawBitmap(wheel, null, new RectF(cx-radius, cy-radius, cx+radius, cy+radius), paint);
        int full = Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f});
        paint.setShader(new LinearGradient(0, brightness.top, 0, brightness.bottom,
                full, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(brightness, dp(8), dp(8), paint);
        paint.setShader(null);
        float angle = (float)Math.toRadians(hsv[0]);
        float markerX = cx + (float)Math.cos(angle) * hsv[1] * radius;
        float markerY = cy + (float)Math.sin(angle) * hsv[1] * radius;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(Color.WHITE);
        canvas.drawCircle(markerX, markerY, dp(8), paint);
        paint.setColor(Color.DKGRAY); paint.setStrokeWidth(dp(1));
        canvas.drawCircle(markerX, markerY, dp(9), paint);
        float brightnessY = brightness.bottom - hsv[2] * brightness.height();
        paint.setColor(Color.WHITE); paint.setStrokeWidth(dp(3));
        canvas.drawRoundRect(new RectF(brightness.left-dp(3), brightnessY-dp(3),
                brightness.right+dp(3), brightnessY+dp(3)), dp(3), dp(3), paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN && event.getActionMasked() != MotionEvent.ACTION_MOVE)
            return event.getActionMasked() == MotionEvent.ACTION_UP;
        float x=event.getX(), y=event.getY();
        if (x >= brightness.left-dp(8)) {
            hsv[2] = Math.max(0, Math.min(1, (brightness.bottom-y)/brightness.height()));
        } else {
            float dx=x-cx, dy=y-cy;
            hsv[0] = (float)((Math.toDegrees(Math.atan2(dy, dx))+360)%360);
            hsv[1] = Math.max(0, Math.min(1, (float)Math.hypot(dx,dy)/radius));
        }
        invalidate();
        if (listener != null) listener.onColorChanged(Color.HSVToColor(hsv));
        return true;
    }
}
