package com.example.progressoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;
import java.util.List;

public class MainActivity extends Activity {
    private TextView overlayPermission, listenerPermission, accessibilityPermission;
    private TextView settingsTab, myTab;
    private ScrollView settingsPage, myPage, homePage;
    private LinearLayout homeContent, nav;
    private String editingId;
    private boolean creating;
    private Spinner style, side;
    private LinearLayout sideRow, ringRow, screenRow, normalSizeRow;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        InstanceStore.ensure(this);
        editingId = getIntent().getStringExtra("edit_id");
        creating = getIntent().getBooleanExtra("is_new", false);
        if (editingId != null && !InstanceStore.ids(this).contains(editingId)) editingId = null;
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xfff4f7fb);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        settingsPage = scroll;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16); root.setPadding(pad, dp(18), pad, dp(28)); scroll.addView(root);
        button(root, "← 返回实例列表", this::finish);
        label(root, creating ? "新建进度实例" : "编辑进度实例", 28, 0xff182438);
        LinearLayout identityCard = section(root, "实例");
        EditText instanceName = new EditText(this);
        instanceName.setSingleLine(true);
        instanceName.setText(instancePrefs().getString(InstanceStore.NAME, "进度条"));
        instanceName.setHint("实例名称");
        identityCard.addView(instanceName, new LinearLayout.LayoutParams(-1, dp(52)));
        instanceName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                instancePrefs().edit().putString(InstanceStore.NAME, s.toString()).apply();
            }
            public void afterTextChanged(Editable s) { }
        });
        Switch instanceActive = new Switch(this);
        instanceActive.setText("启用这个实例");
        instanceActive.setTextSize(16);
        tintSwitch(instanceActive);
        instanceActive.setChecked(instancePrefs().getBoolean(InstanceStore.ACTIVE, true));
        identityCard.addView(instanceActive);
        instanceActive.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(InstanceStore.ACTIVE, checked).apply();
            OverlayController.refresh(this);
        });
        label(identityCard, "进度来源：自动选择最新的进度通知。后续可为每个实例单独指定来源。", 13, 0xff667184);
        LinearLayout styleCard = section(root, "显示样式");
        style = spinner(styleCard, "样式", new String[]{"屏幕边缘进度条", "摄像头挖孔圆环", "环绕屏幕进度条"},
                instancePrefs().getInt(OverlayController.STYLE, 0), OverlayController.STYLE);
        sideRow = section(root, "边栏条设置");
        side = spinner(sideRow, "位置", new String[]{"上边", "下边", "左边", "右边"},
                instancePrefs().getInt(OverlayController.SIDE, 0), OverlayController.SIDE);
        fineSlider(sideRow, "X 偏移", OverlayController.EDGE_X, -500, 500, 0, false);
        fineSlider(sideRow, "Y 偏移", OverlayController.EDGE_Y, -500, 500, 0, false);
        fineSlider(sideRow, "长度", OverlayController.EDGE_LENGTH, 0, 5000, 0, true);
        Spinner edgeDirection = spinner(sideRow, "进度方向", new String[]{"正向（横向向右、纵向向上）", "反向"},
                instancePrefs().getInt(OverlayController.EDGE_DIRECTION, 0), OverlayController.EDGE_DIRECTION);
        edgeDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.EDGE_DIRECTION, edgeDirection)));
        ringRow = section(root, "挖孔圆环设置");
        fineSlider(ringRow, "竖屏 X 偏移", OverlayController.RING_X, -500, 500, 0, false);
        fineSlider(ringRow, "竖屏 Y 偏移", OverlayController.RING_Y, -500, 500, 0, false);
        fineSlider(ringRow, "横屏 X 偏移", OverlayController.RING_LANDSCAPE_X, -500, 500, 0, false);
        fineSlider(ringRow, "横屏 Y 偏移", OverlayController.RING_LANDSCAPE_Y, -500, 500, 0, false);
        fineSlider(ringRow, "半径", OverlayController.RING_RADIUS, 0, 500, 0, true);
        fineSlider(ringRow, "起始角", OverlayController.RING_ANGLE, -900, 900, -90, false, "°");
        Spinner ringDirection = spinner(ringRow, "进度方向", new String[]{"顺时针", "逆时针"},
                instancePrefs().getInt(OverlayController.RING_DIRECTION, 0), OverlayController.RING_DIRECTION);
        ringDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.RING_DIRECTION, ringDirection)));
        label(ringRow, "竖屏和横屏偏移分别以当前方向的挖孔中心为基准；半径设为自动时按挖孔大小适配。角度 0° 在右、90° 在下、-90° 在上。圆环不会超出屏幕边界。", 14, 0xff667184);
        screenRow = section(root, "环绕屏幕设置");
        fineSlider(screenRow, "宽度", OverlayController.SCREEN_WIDTH, 0, screenMaxFifths(), 0, true);
        fineSlider(screenRow, "高度", OverlayController.SCREEN_HEIGHT, 0, screenMaxFifths(), 0, true);
        fineSlider(screenRow, "X 偏移", OverlayController.SCREEN_X, -500, 500, 0, false);
        fineSlider(screenRow, "Y 偏移", OverlayController.SCREEN_Y, -500, 500, 0, false);
        fineSlider(screenRow, "圆角", OverlayController.SCREEN_RADIUS, 0, 400, 18, false);
        fineSlider(screenRow, "粗细", OverlayController.SCREEN_SIZE, 0, 100, 6, false);
        fineSlider(screenRow, "起始角", OverlayController.SCREEN_ANGLE, -900, 900, -90, false, "°");
        Spinner screenDirection = spinner(screenRow, "进度方向", new String[]{"顺时针", "逆时针"},
                instancePrefs().getInt(OverlayController.SCREEN_DIRECTION, 0), OverlayController.SCREEN_DIRECTION);
        screenDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.SCREEN_DIRECTION, screenDirection)));
        label(screenRow, "宽高最大可调到屏幕长边的 150%；超过屏幕的部分会被裁切。横屏会交换自定义宽高。自动模式沿系统栏之间的可见区域绕行；自定义尺寸以整屏为基准居中。", 14, 0xff667184);
        normalSizeRow = section(root, "线条设置");
        fineSlider(normalSizeRow, "粗细", OverlayController.SIZE, 0, 100, 6, false);
        style.setOnItemSelectedListener(selection(() -> {
            int selected = style.getSelectedItemPosition();
            instancePrefs().edit().putInt(OverlayController.STYLE, selected).apply();
            sideRow.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
            ringRow.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
            screenRow.setVisibility(selected == 2 ? View.VISIBLE : View.GONE);
            normalSizeRow.setVisibility(selected == 2 ? View.GONE : View.VISIBLE);
            OverlayController.refresh(this);
        }));
        side.setOnItemSelectedListener(selection(() -> {
            instancePrefs().edit().putInt(OverlayController.SIDE, side.getSelectedItemPosition()).apply();
            OverlayController.refresh(this);
        }));
        LinearLayout colorCard = section(root, "颜色");
        Switch autoColor = new Switch(this);
        autoColor.setText("跟随通知来源应用图标（主色 + 辅助色）");
        autoColor.setTextSize(16);
        tintSwitch(autoColor);
        Switch foregroundColor = new Switch(this);
        foregroundColor.setText("跟随前台应用图标（需开启无障碍服务）");
        foregroundColor.setTextSize(16);
        tintSwitch(foregroundColor);
        boolean foregroundOn = instancePrefs().getBoolean(OverlayController.FOREGROUND_COLOR, false);
        autoColor.setChecked(!foregroundOn && instancePrefs().getBoolean(OverlayController.AUTO_COLOR, false));
        foregroundColor.setChecked(foregroundOn);
        switchSetting(colorCard, autoColor, OverlayController.AUTO_COLOR, false);
        switchSetting(colorCard, foregroundColor, OverlayController.FOREGROUND_COLOR, false);
        LinearLayout colorModeRow = new LinearLayout(this);
        colorModeRow.setOrientation(LinearLayout.VERTICAL);
        colorCard.addView(colorModeRow);
        Spinner colorMode = spinner(colorModeRow, "图标颜色呈现方式", new String[]{"主色 + 辅助色", "显示占比超过 10% 的颜色", "按图标颜色位置渐变"},
                instancePrefs().getInt(OverlayController.COLOR_MODE, 0), OverlayController.COLOR_MODE);
        colorModeRow.setVisibility(autoColor.isChecked() || foregroundColor.isChecked() ? View.VISIBLE : View.GONE);
        colorMode.setOnItemSelectedListener(selection(() -> {
            instancePrefs().edit().putInt(OverlayController.COLOR_MODE, colorMode.getSelectedItemPosition()).apply();
            OverlayController.refresh(this);
        }));
        autoColor.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(OverlayController.AUTO_COLOR, checked).apply();
            if (checked) foregroundColor.setChecked(false);
            colorModeRow.setVisibility(autoColor.isChecked() || foregroundColor.isChecked() ? View.VISIBLE : View.GONE);
            OverlayController.refresh(this);
        });
        foregroundColor.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(OverlayController.FOREGROUND_COLOR, checked).apply();
            if (checked) autoColor.setChecked(false);
            colorModeRow.setVisibility(autoColor.isChecked() || foregroundColor.isChecked() ? View.VISIBLE : View.GONE);
            OverlayController.refresh(this);
        });
        int savedColor = instancePrefs().getInt(OverlayController.COLOR, 0xff2fabff);
        int savedOpacity = instancePrefs().getInt(OverlayController.OPACITY, Color.alpha(savedColor));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setColor(savedColor);
        LinearLayout manualColor = option(colorCard);
        LinearLayout colorHeader = optionHeader(manualColor, "手动颜色");
        manualColor.addView(wheel, new LinearLayout.LayoutParams(-1, dp(220)));
        LinearLayout opacityItem = option(colorCard);
        LinearLayout opacityHeader = optionHeader(opacityItem, "不透明度");
        SeekBar opacityBar = new SeekBar(this); opacityBar.setMax(255); opacityBar.setProgress(savedOpacity);
        tintSlider(opacityBar);
        opacityItem.addView(opacityBar, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout opacityRow = stepperRow(opacityItem);
        Button opacityMinus = stepButton(opacityRow, "−", "降低颜色不透明度");
        TextView opacityLabel = stepValue(opacityRow);
        Button opacityPlus = stepButton(opacityRow, "+", "提高颜色不透明度");
        resetButton(opacityHeader, "不透明度", () -> {
            opacityBar.setProgress(255);
            instancePrefs().edit().remove(OverlayController.OPACITY).apply();
            OverlayController.refresh(this);
        });
        EditText hex = new EditText(this);
        hex.setSingleLine(true); hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        hex.setHint("#AARRGGBB"); manualColor.addView(hex);
        TextView swatch = label(manualColor, "颜色预览", 14, 0xff182438);
        swatch.setPadding(dp(12), dp(10), dp(12), dp(10));
        boolean[] syncingColor = {false};
        java.util.function.IntConsumer applyManualColor = argb -> {
            syncingColor[0] = true;
            wheel.setColor(argb);
            opacityBar.setProgress(Color.alpha(argb));
            opacityLabel.setText(opacityText(Color.alpha(argb)));
            hex.setText(String.format(Locale.US, "#%08X", argb));
            hex.setSelection(hex.length());
            swatch.setBackgroundColor(argb);
            syncingColor[0] = false;
            autoColor.setChecked(false);
            foregroundColor.setChecked(false);
            instancePrefs().edit().putInt(OverlayController.COLOR, argb)
                    .putInt(OverlayController.OPACITY, Color.alpha(argb)).apply();
            OverlayController.refresh(this);
        };
        resetButton(colorHeader, "颜色", () -> applyManualColor.accept(
                (opacityBar.getProgress() << 24) | 0x002fabff));
        int initialArgb = (savedOpacity << 24) | (savedColor & 0x00ffffff);
        hex.setText(String.format(Locale.US, "#%08X", initialArgb));
        opacityLabel.setText(opacityText(savedOpacity));
        swatch.setBackgroundColor(initialArgb);
        wheel.setListener(rgb -> applyManualColor.accept((opacityBar.getProgress() << 24) | (rgb & 0x00ffffff)));
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int alpha, boolean fromUser) {
                if (syncingColor[0]) return;
                int argb = (alpha << 24) | (instancePrefs()
                        .getInt(OverlayController.COLOR, savedColor) & 0x00ffffff);
                syncingColor[0] = true;
                opacityLabel.setText(opacityText(alpha));
                hex.setText(String.format(Locale.US, "#%08X", argb));
                hex.setSelection(hex.length());
                swatch.setBackgroundColor(argb);
                syncingColor[0] = false;
                instancePrefs().edit().putInt(OverlayController.COLOR, argb)
                        .putInt(OverlayController.OPACITY, alpha).apply();
                OverlayController.refresh(MainActivity.this);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        opacityMinus.setOnClickListener(v -> opacityBar.setProgress(Math.max(0, opacityBar.getProgress()-1)));
        opacityPlus.setOnClickListener(v -> opacityBar.setProgress(Math.min(255, opacityBar.getProgress()+1)));
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable s) {
                if (syncingColor[0]) return;
                String value = s.toString().trim();
                if (value.matches("#?[0-9a-fA-F]{8}")) {
                    try { applyManualColor.accept((int)Long.parseLong(value.replace("#", ""), 16)); }
                    catch (NumberFormatException ignored) { }
                }
            }
        });
        LinearLayout glowCard = section(root, "发光效果");
        Switch glowEnabled = new Switch(this);
        glowEnabled.setText("启用霓虹光晕");
        tintSwitch(glowEnabled);
        glowEnabled.setChecked(instancePrefs().getBoolean(OverlayController.GLOW_ENABLED, false));
        switchSetting(glowCard, glowEnabled, OverlayController.GLOW_ENABLED, false);
        LinearLayout glowDetails = new LinearLayout(this);
        glowDetails.setOrientation(LinearLayout.VERTICAL);
        glowCard.addView(glowDetails);
        glowDetails.setVisibility(glowEnabled.isChecked() ? View.VISIBLE : View.GONE);
        glowEnabled.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(OverlayController.GLOW_ENABLED, checked).apply();
            glowDetails.setVisibility(checked ? View.VISIBLE : View.GONE);
            OverlayController.refresh(this);
        });
        Switch glowFollow = new Switch(this);
        glowFollow.setText("跟随进度线条颜色");
        tintSwitch(glowFollow);
        glowFollow.setChecked(instancePrefs().getBoolean(OverlayController.GLOW_FOLLOW, true));
        switchSetting(glowDetails, glowFollow, OverlayController.GLOW_FOLLOW, true);
        LinearLayout glowColorItem = option(glowDetails);
        LinearLayout glowColorHeader = optionHeader(glowColorItem, "指定光晕颜色");
        glowColorItem.setVisibility(glowFollow.isChecked() ? View.GONE : View.VISIBLE);
        glowFollow.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(OverlayController.GLOW_FOLLOW, checked).apply();
            glowColorItem.setVisibility(checked ? View.GONE : View.VISIBLE);
            OverlayController.refresh(this);
        });
        int savedGlowColor = instancePrefs().getInt(OverlayController.GLOW_COLOR, 0xff37bfff);
        ColorWheelView glowWheel = new ColorWheelView(this);
        glowWheel.setColor(savedGlowColor);
        glowColorItem.addView(glowWheel, new LinearLayout.LayoutParams(-1, dp(220)));
        EditText glowHex = new EditText(this);
        glowHex.setSingleLine(true);
        glowHex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        glowHex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        glowHex.setHint("#RRGGBB");
        glowColorItem.addView(glowHex);
        TextView glowSwatch = label(glowColorItem, "颜色预览", 14, 0xff182438);
        glowSwatch.setPadding(dp(12), dp(10), dp(12), dp(10));
        glowSwatch.setBackgroundColor(savedGlowColor);
        glowHex.setText(String.format(Locale.US, "#%06X", savedGlowColor & 0x00ffffff));
        boolean[] syncingGlow = {false};
        java.util.function.IntConsumer setGlowColor = rgb -> {
            int opaque = 0xff000000 | (rgb & 0x00ffffff);
            syncingGlow[0] = true;
            glowWheel.setColor(opaque);
            glowHex.setText(String.format(Locale.US, "#%06X", opaque & 0x00ffffff));
            glowHex.setSelection(glowHex.length());
            glowSwatch.setBackgroundColor(opaque);
            syncingGlow[0] = false;
            instancePrefs().edit().putInt(OverlayController.GLOW_COLOR, opaque).apply();
            OverlayController.refresh(this);
        };
        glowWheel.setListener(setGlowColor::accept);
        glowHex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable s) {
                if (syncingGlow[0]) return;
                String value = s.toString().trim();
                if (value.matches("#?[0-9a-fA-F]{6}")) {
                    try { setGlowColor.accept((int)Long.parseLong(value.replace("#", ""), 16)); }
                    catch (NumberFormatException ignored) { }
                }
            }
        });
        resetButton(glowColorHeader, "光晕颜色", () -> {
            setGlowColor.accept(0xff37bfff);
            instancePrefs().edit().remove(OverlayController.GLOW_COLOR).apply();
        });
        fineSlider(glowDetails, "扩散大小", OverlayController.GLOW_RADIUS, 0, 400, 12, false);
        fineSlider(glowDetails, "不透明度", OverlayController.GLOW_OPACITY, 0, 500, 55, false, "%");
        label(glowCard, "光晕沿已完成的进度线扩散；透明度会同时受线条透明度影响。", 13, 0xff667184);
        LinearLayout previewCard = section(root, "预览");
        Switch preview = new Switch(this); preview.setText("强制预览 100%"); preview.setTextSize(16);
        tintSwitch(preview);
        preview.setChecked(instancePrefs().getBoolean(OverlayController.PREVIEW, false));
        switchSetting(previewCard, preview, OverlayController.PREVIEW, false);
        preview.setOnCheckedChangeListener((button, checked) -> {
            instancePrefs().edit().putBoolean(OverlayController.PREVIEW, checked).apply();
            OverlayController.refresh(this);
        });
        label(previewCard, "预览会覆盖真实通知进度。图标颜色不可用时使用示例色。", 13, 0xff667184);
        homePage = new ScrollView(this);
        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(16), dp(18), dp(16), dp(28));
        homePage.addView(homeContent);
        shell.addView(homePage, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(settingsPage, new LinearLayout.LayoutParams(-1, 0, 1));
        myPage = createMyPage();
        myPage.setVisibility(View.GONE);
        shell.addView(myPage, new LinearLayout.LayoutParams(-1, 0, 1));
        View divider = new View(this);
        divider.setBackgroundColor(0xffe4eaf1);
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setElevation(dp(4));
        settingsTab = tab(nav, "首页", () -> showPage(0));
        myTab = tab(nav, "我的", () -> showPage(2));
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(shell);
        refreshHome();
        showPage(editingId == null ? 0 : 1);
        getWindow().getDecorView().post(() -> {
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
    }
    @Override protected void onResume() {
        super.onResume();
        refreshPermissions();
        if (homeContent != null) refreshHome();
        getWindow().getDecorView().postDelayed(this::refreshPermissions, 500);
        OverlayController.refresh(this);
    }
    private ScrollView createMyPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, dp(18), pad, dp(28));
        scroll.addView(content);
        label(content, "我的", 28, 0xff182438);
        label(content, "权限状态会在返回应用时自动更新。", 15, 0xff667184);
        LinearLayout overlayCard = section(content, "悬浮窗");
        label(overlayCard, "用于在其他应用上方显示进度条。", 14, 0xff667184);
        overlayPermission = label(overlayCard, "检测中", 16, 0xff182438);
        button(overlayCard, "打开悬浮窗设置", () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        LinearLayout listenerCard = section(content, "通知使用权");
        label(listenerCard, "用于读取通知里的标准进度值。", 14, 0xff667184);
        listenerPermission = label(listenerCard, "检测中", 16, 0xff182438);
        button(listenerCard, "打开通知使用权设置", () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LinearLayout accessibilityCard = section(content, "前台应用配色服务");
        label(accessibilityCard, "仅在使用前台应用图标配色时需要。", 14, 0xff667184);
        accessibilityPermission = label(accessibilityCard, "检测中", 16, 0xff182438);
        button(accessibilityCard, "打开无障碍设置", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(content, "重新检查权限", this::refreshPermissions);
        return scroll;
    }
    private TextView tab(LinearLayout nav, String title, Runnable action) {
        TextView tab = new TextView(this);
        tab.setText(title);
        tab.setTextSize(16);
        tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = dp(4); lp.rightMargin = dp(4);
        nav.addView(tab, lp);
        return tab;
    }
    private SharedPreferences instancePrefs() {
        List<String> ids = InstanceStore.ids(this);
        String id = editingId == null ? (ids.isEmpty() ? "defaults" : ids.get(0)) : editingId;
        return InstanceStore.prefs(this, id);
    }
    private void refreshHome() {
        homeContent.removeAllViews();
        label(homeContent, "进度边栏", 28, 0xff182438);
        label(homeContent, "管理同时显示的进度环和进度线。", 15, 0xff667184);
        LinearLayout power = section(homeContent, "运行状态");
        Switch enabled = new Switch(this);
        enabled.setText("启用进度边栏");
        enabled.setTextSize(17);
        tintSwitch(enabled);
        enabled.setChecked(OverlayController.isEnabled(this));
        power.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            OverlayController.prefs(this).edit().putBoolean(OverlayController.ENABLED, checked).apply();
            if (checked) {
                ForegroundAppService.resetCachedPackage();
                ProgressListener.rescanActive();
            } else {
                OverlayController.setLive(this, -1f, null);
                OverlayController.setForegroundColors(this, null);
            }
            OverlayController.refresh(this);
        });
        label(power, "关闭后隐藏全部实例，并暂停通知扫描和前台应用取色。", 13, 0xff667184);
        button(homeContent, "＋ 新建进度实例", () -> openEditor(InstanceStore.add(this), true));
        List<String> ids = InstanceStore.ids(this);
        if (ids.isEmpty()) {
            label(homeContent, "还没有实例。点击上方按钮创建第一个进度环或进度线。", 15, 0xff667184);
            return;
        }
        String[] styles = {"屏幕边缘进度条", "摄像头挖孔圆环", "环绕屏幕进度条"};
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            SharedPreferences settings = InstanceStore.prefs(this, id);
            String name = settings.getString(InstanceStore.NAME, "进度实例");
            LinearLayout card = section(homeContent, name.isEmpty() ? "未命名实例" : name);
            int styleIndex = Math.max(0, Math.min(styles.length - 1,
                    settings.getInt(OverlayController.STYLE, 0)));
            label(card, styles[styleIndex] + " · 自动选择最新进度通知", 14, 0xff667184);
            Switch active = new Switch(this);
            active.setText("启用这个实例");
            active.setTextSize(15);
            tintSwitch(active);
            active.setChecked(settings.getBoolean(InstanceStore.ACTIVE, true));
            card.addView(active);
            active.setOnCheckedChangeListener((button, checked) -> {
                settings.edit().putBoolean(InstanceStore.ACTIVE, checked).apply();
                OverlayController.refresh(this);
            });
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(actions);
            compactButton(actions, "编辑", () -> openEditor(id, false));
            compactButton(actions, "复制", () -> {
                InstanceStore.duplicate(this, id);
                refreshHome();
            });
            compactButton(actions, "删除", () -> new AlertDialog.Builder(this)
                    .setTitle("删除“" + name + "”？")
                    .setMessage("这个实例的设置会被删除。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        InstanceStore.delete(this, id);
                        refreshHome();
                    }).show());
            LinearLayout order = new LinearLayout(this);
            order.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(order);
            if (index > 0) compactButton(order, "上移", () -> {
                InstanceStore.move(this, id, -1);
                refreshHome();
            });
            if (index < ids.size() - 1) compactButton(order, "下移", () -> {
                InstanceStore.move(this, id, 1);
                refreshHome();
            });
        }
    }
    private void compactButton(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(0xff1767b4);
        button.setTextSize(14);
        button.setBackground(rounded(0xffe7f0fa, 10));
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
        lp.setMargins(dp(3), dp(6), dp(3), 0);
        row.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }
    private void openEditor(String id, boolean isNew) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra("edit_id", id).putExtra("is_new", isNew));
    }
    private void showPage(int page) {
        homePage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        myPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        nav.setVisibility(page == 1 ? View.GONE : View.VISIBLE);
        settingsTab.setTextColor(page == 0 ? 0xff1676c8 : 0xff667184);
        myTab.setTextColor(page == 2 ? 0xff1676c8 : 0xff667184);
        settingsTab.setBackground(rounded(page == 0 ? 0xffe9f4ff : Color.WHITE, 12));
        myTab.setBackground(rounded(page == 2 ? 0xffe9f4ff : Color.WHITE, 12));
        if (page == 0) refreshHome();
        if (page == 2) refreshPermissions();
    }
    private void refreshPermissions() {
        if (overlayPermission == null) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean listener = nm.isNotificationListenerAccessGranted(new ComponentName(this, ProgressListener.class));
        ComponentName component = new ComponentName(this, ForegroundAppService.class);
        boolean foregroundService = false;
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices != null) {
            for (String id : enabledServices.split(":")) {
                if (component.equals(ComponentName.unflattenFromString(id))) {
                    foregroundService = true;
                    break;
                }
            }
        }
        permissionText(overlayPermission, "悬浮窗", Settings.canDrawOverlays(this));
        permissionText(listenerPermission, "通知使用权", listener);
        if (foregroundService && !ForegroundAppService.isConnected()) {
            accessibilityPermission.setText("前台应用配色服务：已开启，但服务未连接");
            accessibilityPermission.setTextColor(0xffb05a24);
        } else {
            permissionText(accessibilityPermission, "前台应用配色服务", foregroundService);
        }
    }
    private void permissionText(TextView view, String title, boolean granted) {
        view.setText(title + "：" + (granted ? "已开启 ✓" : "未开启"));
        view.setTextColor(granted ? 0xff16804a : 0xffb05a24);
    }
    private void fineSlider(LinearLayout parent, String title, String key,
                            int minFifths, int maxFifths, int defaultDp, boolean autoZero) {
        fineSlider(parent, title, key, minFifths, maxFifths, defaultDp, autoZero, "dp");
    }
    private void fineSlider(LinearLayout parent, String title, String key,
                            int minFifths, int maxFifths, int defaultDp, boolean autoZero, String unit) {
        LinearLayout item = option(parent);
        LinearLayout header = optionHeader(item, title);
        SeekBar bar = new SeekBar(this);
        tintSlider(bar);
        bar.setMax(maxFifths - minFifths);
        int saved = OverlayController.fifths(instancePrefs(), key, defaultDp);
        bar.setProgress(Math.max(0, Math.min(maxFifths - minFifths, saved - minFifths)));
        boolean[] changedByUser = {false};
        item.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout steps = stepperRow(item);
        Button minus = stepButton(steps, "−", title + "减 0.2 " + unit);
        TextView valueLabel = stepValue(steps);
        Button plus = stepButton(steps, "+", title + "加 0.2 " + unit);
        String initialText = sliderText(saved, autoZero, unit);
        if (saved > maxFifths && (OverlayController.SCREEN_WIDTH.equals(key)
                || OverlayController.SCREEN_HEIGHT.equals(key)))
            initialText += String.format(Locale.CHINA, "（当前滑杆上限 %.1f dp）", maxFifths / 5f);
        valueLabel.setText(initialText);
        valueLabel.setContentDescription(title + "：" + initialText);
        resetButton(header, title, () -> {
            changedByUser[0] = true;
            bar.setProgress(defaultDp * 5 - minFifths);
            instancePrefs().edit().remove(key).remove(key + "_fifths").apply();
            valueLabel.setText(sliderText(defaultDp * 5, autoZero, unit));
            valueLabel.setContentDescription(title + "：" + sliderText(defaultDp * 5, autoZero, unit));
            OverlayController.refresh(this);
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser && !changedByUser[0]) return;
                int value = progress + minFifths;
                valueLabel.setText(sliderText(value, autoZero, unit));
                valueLabel.setContentDescription(title + "：" + sliderText(value, autoZero, unit));
                instancePrefs().edit().putInt(key + "_fifths", value).apply();
                OverlayController.refresh(MainActivity.this);
            }
            public void onStartTrackingTouch(SeekBar seekBar) { changedByUser[0] = true; }
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        minus.setOnClickListener(v -> {
            changedByUser[0] = true;
            bar.setProgress(Math.max(0, bar.getProgress() - 1));
        });
        plus.setOnClickListener(v -> {
            changedByUser[0] = true;
            bar.setProgress(Math.min(bar.getMax(), bar.getProgress() + 1));
        });
    }
    private String sliderText(int fifths, boolean autoZero, String unit) {
        return autoZero && fifths == 0 ? "自动" : String.format(Locale.CHINA, "%.1f %s", fifths / 5f, unit);
    }
    private int screenMaxFifths() {
        WindowManager wm = getSystemService(WindowManager.class);
        int pixels;
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            pixels = Math.max(bounds.width(), bounds.height());
        } else {
            Point size = new Point();
            wm.getDefaultDisplay().getRealSize(size);
            pixels = Math.max(size.x, size.y);
        }
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, (int)Math.ceil(pixels / density * 5f * 1.5f));
    }
    private String opacityText(int alpha) {
        return String.format(Locale.CHINA, "不透明度：%.1f%%（%02X）", alpha * 100f / 255f, alpha);
    }
    private void saveChoice(String key, Spinner spinner) {
        instancePrefs().edit().putInt(key, spinner.getSelectedItemPosition()).apply();
        OverlayController.refresh(this);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }
    private void tintSlider(SeekBar bar) {
        ColorStateList accent = ColorStateList.valueOf(0xff1676c8);
        bar.setProgressTintList(accent);
        bar.setThumbTintList(accent);
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xffdce5ee));
    }
    private void tintSwitch(Switch control) {
        control.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xff1676c8, 0xfff8fbff}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xff87bceb, 0xffcbd5df}));
    }
    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Color.WHITE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        parent.addView(card, lp);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(18);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(0xff182438);
        heading.setPadding(dp(2), dp(2), dp(2), dp(10));
        card.addView(heading);
        return card;
    }
    private LinearLayout option(LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(rounded(0xfff6f8fc, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(item, lp);
        return item;
    }
    private LinearLayout optionHeader(LinearLayout item, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(row, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(0xff25354a);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }
    private LinearLayout stepperRow(LinearLayout item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
        return row;
    }
    private Button stepButton(LinearLayout row, String text, String description) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(0xff1767b4);
        b.setContentDescription(description);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(rounded(0xffe7f0fa, 10));
        b.setStateListAnimator(null);
        row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }
    private TextView stepValue(LinearLayout row) {
        TextView v = new TextView(this);
        v.setTextSize(14);
        v.setTextColor(0xff293b52);
        v.setGravity(Gravity.CENTER);
        row.addView(v, new LinearLayout.LayoutParams(0, dp(44), 1));
        return v;
    }
    private TextView label(LinearLayout parent, String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(10), 0, dp(10)); parent.addView(v); return v;
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(text);
        b.setTextSize(14); b.setTextColor(0xff1767b4);
        b.setBackground(rounded(0xffe7f0fa, 10));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(6);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }
    private void switchSetting(LinearLayout parent, Switch control, String key, boolean defaultValue) {
        LinearLayout item = option(parent);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        item.addView(row, new LinearLayout.LayoutParams(-1, -2));
        String title = control.getText().toString();
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTextColor(0xff25354a);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        control.setText("");
        row.addView(control);
        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setGravity(Gravity.END);
        item.addView(resetRow);
        resetButton(resetRow, title, () -> {
            control.setChecked(defaultValue);
            instancePrefs().edit().remove(key).apply();
            OverlayController.refresh(this);
        });
    }
    private Button resetButton(LinearLayout parent, String title, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText("重置默认");
        b.setTextSize(13);
        b.setTextColor(0xff1767b4);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(rounded(0xffe7f0fa, 9));
        b.setStateListAnimator(null);
        b.setContentDescription(title + "重置为默认");
        parent.addView(b, new LinearLayout.LayoutParams(dp(82), dp(36)));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private Spinner spinner(LinearLayout parent, String title, String[] entries, int selected, String key) {
        LinearLayout item = option(parent);
        LinearLayout header = optionHeader(item, title);
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        s.setAdapter(a); s.setSelection(selected);
        s.setPadding(dp(8), 0, dp(8), 0);
        s.setBackground(rounded(Color.WHITE, 10));
        item.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
        resetButton(header, title, () -> {
            s.setSelection(0);
            instancePrefs().edit().remove(key).apply();
            OverlayController.refresh(this);
        });
        return s;
    }
    private android.widget.AdapterView.OnItemSelectedListener selection(Runnable action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) { action.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
    }
}
