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
import android.view.WindowInsets;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;

public class MainActivity extends Activity {
    private static final String DARK_MODE = "dark_mode";
    private static final String ADAPTIVE_SOURCE = "adaptive_source";
    private boolean darkMode;
    private int currentPage;
    private int currentEditorTab;
    private TextView overlayPermission, listenerPermission, accessibilityPermission;
    private TextView settingsTab, myTab;
    private ScrollView settingsPage, myPage, homePage;
    private LinearLayout editorContainer;
    private final LinearLayout[] editorSections = new LinearLayout[3];
    private final TextView[] editorTabs = new TextView[3];
    private LinearLayout homeContent, nav;
    private String editingId;
    private boolean creating;
    private Spinner style, side;
    private LinearLayout sideRow, ringRow, screenRow, normalSizeRow;
    private LinearLayout sideDirectionRow, ringDirectionRow, screenDirectionRow;

    @Override public void onCreate(Bundle state) {
        darkMode = OverlayController.prefs(this).getBoolean(DARK_MODE, false);
        setTheme(darkMode ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        InstanceStore.ensure(this);
        editingId = getIntent().getStringExtra("edit_id");
        creating = getIntent().getBooleanExtra("is_new", false);
        if (editingId != null && !InstanceStore.ids(this).contains(editingId)) editingId = null;
        getWindow().setStatusBarColor(darkMode ? 0xff10151f : Color.WHITE);
        getWindow().setNavigationBarColor(darkMode ? 0xff10151f : Color.WHITE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(uiColor(0xfff4f7fb));
        editorContainer = new LinearLayout(this);
        editorContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout editorHeader = new LinearLayout(this);
        editorHeader.setOrientation(LinearLayout.VERTICAL);
        editorHeader.setPadding(dp(16), dp(8), dp(16), dp(8));
        editorContainer.addView(editorHeader);
        button(editorHeader, "← 返回实例列表", this::finish);
        label(editorHeader, creating ? "新建进度实例" : "编辑进度实例", 25, 0xff182438);
        LinearLayout editorTabBar = new LinearLayout(this);
        editorTabBar.setOrientation(LinearLayout.HORIZONTAL);
        editorTabBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        editorContainer.addView(editorTabBar, new LinearLayout.LayoutParams(-1, dp(56)));
        editorTabs[0] = tab(editorTabBar, "类型", () -> showEditorTab(0));
        editorTabs[1] = tab(editorTabBar, "位置与大小", () -> showEditorTab(1));
        editorTabs[2] = tab(editorTabBar, "颜色", () -> showEditorTab(2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        settingsPage = scroll;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16); root.setPadding(pad, dp(10), pad, dp(28)); scroll.addView(root);
        editorContainer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < editorSections.length; i++) {
            editorSections[i] = new LinearLayout(this);
            editorSections[i].setOrientation(LinearLayout.VERTICAL);
            root.addView(editorSections[i]);
        }
        LinearLayout typePage = editorSections[0];
        LinearLayout geometryPage = editorSections[1];
        LinearLayout colorPage = editorSections[2];
        LinearLayout identityCard = section(typePage, "实例");
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
        LinearLayout styleCard = section(typePage, "显示样式");
        style = spinner(styleCard, "样式", new String[]{"屏幕边缘进度条", "摄像头挖孔圆环", "环绕屏幕进度条"},
                instancePrefs().getInt(OverlayController.STYLE, 0), OverlayController.STYLE);
        sideDirectionRow = section(typePage, "边栏进度方向");
        Spinner edgeDirection = spinner(sideDirectionRow, "进度方向", new String[]{"正向（横向向右、纵向向上）", "反向"},
                instancePrefs().getInt(OverlayController.EDGE_DIRECTION, 0), OverlayController.EDGE_DIRECTION);
        edgeDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.EDGE_DIRECTION, edgeDirection)));
        ringDirectionRow = section(typePage, "挖孔圆环进度方向");
        Spinner ringDirection = spinner(ringDirectionRow, "进度方向", new String[]{"顺时针", "逆时针"},
                instancePrefs().getInt(OverlayController.RING_DIRECTION, 0), OverlayController.RING_DIRECTION);
        ringDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.RING_DIRECTION, ringDirection)));
        screenDirectionRow = section(typePage, "环绕屏幕进度方向");
        Spinner screenDirection = spinner(screenDirectionRow, "进度方向", new String[]{"顺时针", "逆时针"},
                instancePrefs().getInt(OverlayController.SCREEN_DIRECTION, 0), OverlayController.SCREEN_DIRECTION);
        screenDirection.setOnItemSelectedListener(selection(() -> saveChoice(OverlayController.SCREEN_DIRECTION, screenDirection)));
        sideRow = section(geometryPage, "边栏条位置与大小");
        side = spinner(sideRow, "位置", new String[]{"上边", "下边", "左边", "右边"},
                instancePrefs().getInt(OverlayController.SIDE, 0), OverlayController.SIDE);
        fineSlider(sideRow, "X 偏移", OverlayController.EDGE_X, -500, 500, 0, false);
        fineSlider(sideRow, "Y 偏移", OverlayController.EDGE_Y, -500, 500, 0, false);
        fineSlider(sideRow, "长度", OverlayController.EDGE_LENGTH, 0, 5000, 0, true);
        ringRow = section(geometryPage, "挖孔圆环位置与大小");
        fineSlider(ringRow, "竖屏 X 偏移", OverlayController.RING_X, -500, 500, 0, false);
        fineSlider(ringRow, "竖屏 Y 偏移", OverlayController.RING_Y, -500, 500, 0, false);
        fineSlider(ringRow, "横屏 X 偏移", OverlayController.RING_LANDSCAPE_X, -500, 500, 0, false);
        fineSlider(ringRow, "横屏 Y 偏移", OverlayController.RING_LANDSCAPE_Y, -500, 500, 0, false);
        fineSlider(ringRow, "半径", OverlayController.RING_RADIUS, 0, 500, 0, true);
        fineSlider(ringRow, "起始角", OverlayController.RING_ANGLE, -900, 900, -90, false, "°");
        label(ringRow, "竖屏和横屏偏移分别以当前方向的挖孔中心为基准；半径设为自动时按挖孔大小适配。角度 0° 在右、90° 在下、-90° 在上。圆环不会超出屏幕边界。", 14, 0xff667184);
        screenRow = section(geometryPage, "环绕屏幕位置与大小");
        fineSlider(screenRow, "宽度", OverlayController.SCREEN_WIDTH, 0, screenMaxFifths(), 0, true);
        fineSlider(screenRow, "高度", OverlayController.SCREEN_HEIGHT, 0, screenMaxFifths(), 0, true);
        fineSlider(screenRow, "X 偏移", OverlayController.SCREEN_X, -500, 500, 0, false);
        fineSlider(screenRow, "Y 偏移", OverlayController.SCREEN_Y, -500, 500, 0, false);
        fineSlider(screenRow, "圆角", OverlayController.SCREEN_RADIUS, 0, 400, 18, false);
        fineSlider(screenRow, "粗细", OverlayController.SCREEN_SIZE, 0, 100, 6, false);
        fineSlider(screenRow, "起始角", OverlayController.SCREEN_ANGLE, -900, 900, -90, false, "°");
        label(screenRow, "宽高最大可调到屏幕长边的 150%；超过屏幕的部分会被裁切。横屏会交换自定义宽高。自动模式沿系统栏之间的可见区域绕行；自定义尺寸以整屏为基准居中。", 14, 0xff667184);
        normalSizeRow = section(geometryPage, "线条大小");
        fineSlider(normalSizeRow, "粗细", OverlayController.SIZE, 0, 100, 6, false);
        style.setOnItemSelectedListener(selection(() -> {
            int selected = style.getSelectedItemPosition();
            instancePrefs().edit().putInt(OverlayController.STYLE, selected).apply();
            sideRow.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
            ringRow.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
            screenRow.setVisibility(selected == 2 ? View.VISIBLE : View.GONE);
            normalSizeRow.setVisibility(selected == 2 ? View.GONE : View.VISIBLE);
            sideDirectionRow.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
            ringDirectionRow.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
            screenDirectionRow.setVisibility(selected == 2 ? View.VISIBLE : View.GONE);
            OverlayController.refresh(this);
        }));
        side.setOnItemSelectedListener(selection(() -> {
            instancePrefs().edit().putInt(OverlayController.SIDE, side.getSelectedItemPosition()).apply();
            OverlayController.refresh(this);
        }));
        LinearLayout colorChoiceCard = section(colorPage, "配色方式");
        boolean foregroundOn = instancePrefs().getBoolean(OverlayController.FOREGROUND_COLOR, false);
        boolean adaptiveOn = foregroundOn || instancePrefs().getBoolean(OverlayController.AUTO_COLOR, false);
        Spinner colorType = spinner(colorChoiceCard, "颜色", new String[]{"指定颜色", "适应颜色"},
                adaptiveOn ? 1 : 0, "color_type");
        LinearLayout manualCard = section(colorPage, "指定颜色");
        LinearLayout adaptiveCard = section(colorPage, "适应颜色");
        int savedSource = foregroundOn ? 1 : instancePrefs().getInt(ADAPTIVE_SOURCE, 0);
        Spinner colorSource = spinner(adaptiveCard, "来源", new String[]{"通知图标", "当前应用"},
                savedSource, ADAPTIVE_SOURCE);
        int savedMode = instancePrefs().getInt(OverlayController.COLOR_MODE, 0);
        Spinner colorMode = spinner(adaptiveCard, "取色方式",
                new String[]{"取主色", "主色 + 辅色", "超过 10% 的颜色", "对应位置颜色"},
                savedMode == 3 ? 0 : Math.max(1, Math.min(3, savedMode + 1)),
                OverlayController.COLOR_MODE, 1);
        label(adaptiveCard, "选择当前应用时需要开启“我的”页中的前台应用配色服务。", 13, 0xff667184);
        Runnable updateColorSource = () -> {
            boolean adaptive = colorType.getSelectedItemPosition() == 1;
            boolean foreground = adaptive && colorSource.getSelectedItemPosition() == 1;
            manualCard.setVisibility(adaptive ? View.GONE : View.VISIBLE);
            adaptiveCard.setVisibility(adaptive ? View.VISIBLE : View.GONE);
            instancePrefs().edit().putBoolean(OverlayController.AUTO_COLOR, adaptive && !foreground)
                    .putBoolean(OverlayController.FOREGROUND_COLOR, foreground)
                    .putInt(ADAPTIVE_SOURCE, colorSource.getSelectedItemPosition()).apply();
            OverlayController.refresh(this);
        };
        colorType.setOnItemSelectedListener(selection(updateColorSource));
        colorSource.setOnItemSelectedListener(selection(updateColorSource));
        colorMode.setOnItemSelectedListener(selection(() -> {
            int selected = colorMode.getSelectedItemPosition();
            instancePrefs().edit().putInt(OverlayController.COLOR_MODE, selected == 0 ? 3 : selected - 1).apply();
            OverlayController.refresh(this);
        }));
        manualCard.setVisibility(adaptiveOn ? View.GONE : View.VISIBLE);
        adaptiveCard.setVisibility(adaptiveOn ? View.VISIBLE : View.GONE);
        LinearLayout commonColorCard = section(colorPage, "显示调整");
        int savedColor = instancePrefs().getInt(OverlayController.COLOR, OverlayController.DEFAULT_COLOR);
        int savedOpacity = instancePrefs().getInt(OverlayController.OPACITY, Color.alpha(savedColor));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setColor(savedColor);
        LinearLayout manualColor = option(manualCard);
        LinearLayout colorHeader = optionHeader(manualColor, "指定颜色");
        manualColor.addView(wheel, new LinearLayout.LayoutParams(-1, dp(220)));
        LinearLayout opacityItem = option(commonColorCard);
        LinearLayout opacityHeader = optionHeader(opacityItem, "不透明度");
        SeekBar opacityBar = new SeekBar(this); opacityBar.setMax(255); opacityBar.setProgress(savedOpacity);
        tintSlider(opacityBar);
        opacityItem.addView(opacityBar, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout opacityRow = stepperRow(opacityItem);
        Button opacityMinus = stepButton(opacityRow, "−", "降低颜色不透明度");
        TextView opacityLabel = stepValue(opacityRow);
        Button opacityPlus = stepButton(opacityRow, "+", "提高颜色不透明度");
        makeValueEditable(opacityLabel);
        opacityLabel.setOnClickListener(v -> showNumberEditor("颜色不透明度",
                String.format(Locale.US, "%.1f", opacityBar.getProgress() * 100f / 255f),
                "输入 0–100%，会换算为 8 位透明度。", raw -> {
                    try {
                        BigDecimal percent = new BigDecimal(raw.trim().replace(',', '.'));
                        if (percent.compareTo(BigDecimal.ZERO) < 0
                                || percent.compareTo(BigDecimal.valueOf(100)) > 0)
                            return "请输入 0–100 之间的数值";
                        int alpha = percent.multiply(BigDecimal.valueOf(255))
                                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).intValueExact();
                        opacityBar.setProgress(alpha);
                        return null;
                    } catch (NumberFormatException | ArithmeticException e) {
                        return "请输入有效数字";
                    }
                }));
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
            setValueLabel(opacityLabel, "颜色不透明度", opacityText(Color.alpha(argb)));
            hex.setText(String.format(Locale.US, "#%08X", argb));
            hex.setSelection(hex.length());
            swatch.setBackgroundColor(argb);
            syncingColor[0] = false;
            colorType.setSelection(0);
            instancePrefs().edit().putInt(OverlayController.COLOR, argb)
                    .putInt(OverlayController.OPACITY, Color.alpha(argb)).apply();
            OverlayController.refresh(this);
        };
        resetButton(colorHeader, "颜色", () -> applyManualColor.accept(
                (opacityBar.getProgress() << 24) | (OverlayController.DEFAULT_COLOR & 0x00ffffff)));
        int initialArgb = (savedOpacity << 24) | (savedColor & 0x00ffffff);
        hex.setText(String.format(Locale.US, "#%08X", initialArgb));
        setValueLabel(opacityLabel, "颜色不透明度", opacityText(savedOpacity));
        swatch.setBackgroundColor(initialArgb);
        wheel.setListener(rgb -> applyManualColor.accept((opacityBar.getProgress() << 24) | (rgb & 0x00ffffff)));
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int alpha, boolean fromUser) {
                if (syncingColor[0]) return;
                int argb = (alpha << 24) | (instancePrefs()
                        .getInt(OverlayController.COLOR, savedColor) & 0x00ffffff);
                syncingColor[0] = true;
                setValueLabel(opacityLabel, "颜色不透明度", opacityText(alpha));
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
        durationSlider(commonColorCard);
        LinearLayout glowCard = section(colorPage, "发光效果");
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
        LinearLayout previewCard = section(typePage, "强制显示");
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
        shell.addView(editorContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        myPage = createMyPage();
        myPage.setVisibility(View.GONE);
        shell.addView(myPage, new LinearLayout.LayoutParams(-1, 0, 1));
        View divider = new View(this);
        divider.setBackgroundColor(uiColor(0xffe4eaf1));
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(uiColor(Color.WHITE));
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setElevation(dp(4));
        settingsTab = tab(nav, "首页", () -> showPage(0));
        myTab = tab(nav, "我的", () -> showPage(2));
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(shell);
        editorHeader.post(() -> {
            WindowInsets insets = editorHeader.getRootWindowInsets();
            if (insets == null) return;
            int top = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.statusBars()).top
                    : insets.getSystemWindowInsetTop();
            editorHeader.setPadding(dp(16), dp(8) + top, dp(16), dp(8));
        });
        showEditorTab(state == null ? 0 : state.getInt("editor_tab", 0));
        refreshHome();
        showPage(state == null ? getIntent().getIntExtra("open_page", editingId == null ? 0 : 1)
                : state.getInt("current_page", editingId == null ? 0 : 1));
        getWindow().getDecorView().post(() -> {
            View decor = getWindow().getDecorView();
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller == null) return;
                int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkMode ? 0 : flags, flags);
            } else {
                int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility((decor.getSystemUiVisibility() & ~flags)
                        | (darkMode ? 0 : flags));
            }
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("current_page", currentPage);
        state.putInt("editor_tab", currentEditorTab);
        super.onSaveInstanceState(state);
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
        LinearLayout appearanceCard = section(content, "外观");
        Switch darkSwitch = new Switch(this);
        darkSwitch.setText("深色模式");
        darkSwitch.setTextSize(16);
        tintSwitch(darkSwitch);
        darkSwitch.setChecked(darkMode);
        appearanceCard.addView(darkSwitch);
        darkSwitch.setOnCheckedChangeListener((button, checked) -> setDarkMode(checked));
        LinearLayout appearanceActions = new LinearLayout(this);
        appearanceActions.setGravity(Gravity.END);
        appearanceCard.addView(appearanceActions);
        resetButton(appearanceActions, "深色模式", () -> setDarkMode(false));
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
        button.setTextColor(uiColor(0xff1767b4));
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
        currentPage = page;
        homePage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        editorContainer.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        myPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        nav.setVisibility(page == 1 ? View.GONE : View.VISIBLE);
        settingsTab.setTextColor(uiColor(page == 0 ? 0xff1676c8 : 0xff667184));
        myTab.setTextColor(uiColor(page == 2 ? 0xff1676c8 : 0xff667184));
        settingsTab.setBackground(rounded(page == 0 ? 0xffe9f4ff : Color.WHITE, 12));
        myTab.setBackground(rounded(page == 2 ? 0xffe9f4ff : Color.WHITE, 12));
        if (page == 0) refreshHome();
        if (page == 2) refreshPermissions();
    }
    private void showEditorTab(int index) {
        currentEditorTab = Math.max(0, Math.min(editorSections.length - 1, index));
        for (int i = 0; i < editorSections.length; i++) {
            boolean selected = i == currentEditorTab;
            editorSections[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            editorTabs[i].setTextColor(uiColor(selected ? 0xff1676c8 : 0xff667184));
            editorTabs[i].setBackground(rounded(selected ? 0xffe9f4ff : Color.WHITE, 12));
        }
        if (settingsPage != null) settingsPage.scrollTo(0, 0);
    }
    private void setDarkMode(boolean enabled) {
        if (darkMode == enabled) return;
        OverlayController.prefs(this).edit().putBoolean(DARK_MODE, enabled).apply();
        currentPage = 2;
        getIntent().putExtra("open_page", 2);
        recreate();
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
            accessibilityPermission.setTextColor(uiColor(0xffb05a24));
        } else {
            permissionText(accessibilityPermission, "前台应用配色服务", foregroundService);
        }
    }
    private void permissionText(TextView view, String title, boolean granted) {
        view.setText(title + "：" + (granted ? "已开启 ✓" : "未开启"));
        view.setTextColor(uiColor(granted ? 0xff16804a : 0xffb05a24));
    }
    private void fineSlider(LinearLayout parent, String title, String key,
                            int minFifths, int maxFifths, int defaultDp, boolean autoZero) {
        fineSlider(parent, title, key, minFifths, maxFifths, defaultDp, autoZero, "dp");
    }
    private void durationSlider(LinearLayout parent) {
        String title = "颜色过渡时长";
        String key = OverlayController.COLOR_TRANSITION_MS;
        int defaultMs = OverlayController.DEFAULT_COLOR_TRANSITION_MS;
        LinearLayout item = option(parent);
        LinearLayout header = optionHeader(item, title);
        SeekBar bar = new SeekBar(this);
        tintSlider(bar);
        bar.setMax(5000);
        bar.setProgress(Math.max(0, Math.min(5000, instancePrefs().getInt(key, defaultMs))));
        item.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout steps = stepperRow(item);
        Button minus = stepButton(steps, "−", title + "减 1 ms");
        TextView valueLabel = stepValue(steps);
        Button plus = stepButton(steps, "+", title + "加 1 ms");
        makeValueEditable(valueLabel);
        setValueLabel(valueLabel, title, bar.getProgress() + " ms");
        boolean[] changedByUser = {false};
        java.util.function.IntConsumer save = value -> {
            instancePrefs().edit().putInt(key, value).apply();
            setValueLabel(valueLabel, title, value + " ms");
            OverlayController.refresh(this);
        };
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser || changedByUser[0]) save.accept(progress);
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
            bar.setProgress(Math.min(5000, bar.getProgress() + 1));
        });
        valueLabel.setOnClickListener(v -> showNumberEditor(title,
                String.valueOf(instancePrefs().getInt(key, defaultMs)),
                "输入 0–5000 之间的整数毫秒；0 表示立即切换。", raw -> {
                    try {
                        int value = Integer.parseInt(raw.trim());
                        if (value < 0 || value > 5000) return "请输入 0–5000 之间的整数";
                        changedByUser[0] = true;
                        bar.setProgress(value);
                        save.accept(value);
                        return null;
                    } catch (NumberFormatException e) {
                        return "请输入整数毫秒";
                    }
                }));
        resetButton(header, title, () -> {
            changedByUser[0] = true;
            bar.setProgress(defaultMs);
            instancePrefs().edit().remove(key).apply();
            setValueLabel(valueLabel, title, defaultMs + " ms");
            OverlayController.refresh(this);
        });
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
        makeValueEditable(valueLabel);
        String initialText = sliderText(saved, autoZero, unit);
        if (saved > maxFifths && (OverlayController.SCREEN_WIDTH.equals(key)
                || OverlayController.SCREEN_HEIGHT.equals(key)))
            initialText += String.format(Locale.CHINA, "（当前滑杆上限 %.1f dp）", maxFifths / 5f);
        setValueLabel(valueLabel, title, initialText);
        valueLabel.setOnClickListener(v -> showNumberEditor(title,
                String.format(Locale.US, "%.1f", OverlayController.fifths(instancePrefs(), key, defaultDp) / 5f),
                String.format(Locale.CHINA, "范围 %.1f–%.1f %s，步进 0.2。%s",
                        minFifths / 5f, maxFifths / 5f, unit,
                        autoZero ? "输入 0 表示自动。" : ""), raw -> {
                    try {
                        BigDecimal scaled = new BigDecimal(raw.trim().replace(',', '.'))
                                .multiply(BigDecimal.valueOf(5));
                        int value = scaled.intValueExact();
                        if (value < minFifths || value > maxFifths)
                            return String.format(Locale.CHINA, "请输入 %.1f–%.1f 之间的数值",
                                    minFifths / 5f, maxFifths / 5f);
                        changedByUser[0] = true;
                        bar.setProgress(value - minFifths);
                        // setProgress does not notify when the thumb is already there.
                        instancePrefs().edit().putInt(key + "_fifths", value).apply();
                        setValueLabel(valueLabel, title, sliderText(value, autoZero, unit));
                        OverlayController.refresh(this);
                        return null;
                    } catch (NumberFormatException e) {
                        return "请输入有效数字";
                    } catch (ArithmeticException e) {
                        return "请输入 0.2 的倍数";
                    }
                }));
        resetButton(header, title, () -> {
            changedByUser[0] = true;
            bar.setProgress(defaultDp * 5 - minFifths);
            instancePrefs().edit().remove(key).remove(key + "_fifths").apply();
            setValueLabel(valueLabel, title, sliderText(defaultDp * 5, autoZero, unit));
            OverlayController.refresh(this);
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser && !changedByUser[0]) return;
                int value = progress + minFifths;
                setValueLabel(valueLabel, title, sliderText(value, autoZero, unit));
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
    private void makeValueEditable(TextView value) {
        value.setTextColor(uiColor(0xff1767b4));
        value.setBackground(rounded(0xffe7f0fa, 10));
        value.setClickable(true);
        value.setFocusable(true);
        value.setPadding(dp(4), 0, dp(4), 0);
    }
    private void setValueLabel(TextView value, String title, String text) {
        value.setText(text);
        value.setContentDescription(title + "：" + text + "，点击输入");
    }
    private void showNumberEditor(String title, String initial, String hint,
                                  Function<String, String> save) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(24), 0, dp(24), 0);
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(56)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("输入" + title)
                .setMessage(hint)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String error = save.apply(input.getText().toString());
                if (error == null) dialog.dismiss();
                else input.setError(error);
            });
            input.requestFocus();
            input.selectAll();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
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
    private int uiColor(int light) {
        if (!darkMode) return light;
        switch (light) {
            case Color.WHITE: return 0xff1a2230;
            case 0xfff4f7fb: return 0xff10151f;
            case 0xffe4eaf1: return 0xff344255;
            case 0xfff6f8fc: return 0xff222d3d;
            case 0xffe7f0fa: return 0xff263b51;
            case 0xffe9f4ff: return 0xff2c4660;
            case 0xff182438: return 0xffedf4ff;
            case 0xff25354a:
            case 0xff293b52: return 0xffdce8f5;
            case 0xff667184: return 0xffaabbd0;
            case 0xff1767b4:
            case 0xff1676c8: return 0xff8bc8ff;
            case 0xffdce5ee: return 0xff3b4a5d;
            case 0xfff8fbff: return 0xffedf4ff;
            case 0xff87bceb: return 0xff5c96c5;
            case 0xffcbd5df: return 0xff607083;
            case 0xff16804a: return 0xff79dda8;
            case 0xffb05a24: return 0xffffbd7a;
            default: return light;
        }
    }
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(uiColor(color));
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }
    private void tintSlider(SeekBar bar) {
        ColorStateList accent = ColorStateList.valueOf(uiColor(0xff1676c8));
        bar.setProgressTintList(accent);
        bar.setThumbTintList(accent);
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(uiColor(0xffdce5ee)));
    }
    private void tintSwitch(Switch control) {
        control.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{uiColor(0xff1676c8), uiColor(0xfff8fbff)}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{uiColor(0xff87bceb), uiColor(0xffcbd5df)}));
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
        heading.setTextColor(uiColor(0xff182438));
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
        name.setTextColor(uiColor(0xff25354a));
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
        b.setTextColor(uiColor(0xff1767b4));
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
        v.setTextColor(uiColor(0xff293b52));
        v.setGravity(Gravity.CENTER);
        row.addView(v, new LinearLayout.LayoutParams(0, dp(44), 1));
        return v;
    }
    private TextView label(LinearLayout parent, String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(uiColor(color));
        v.setPadding(0, dp(10), 0, dp(10)); parent.addView(v); return v;
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(text);
        b.setTextSize(14); b.setTextColor(uiColor(0xff1767b4));
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
        name.setTextColor(uiColor(0xff25354a));
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
        b.setTextColor(uiColor(0xff1767b4));
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
        return spinner(parent, title, entries, selected, key, 0);
    }
    private Spinner spinner(LinearLayout parent, String title, String[] entries,
                            int selected, String key, int defaultIndex) {
        LinearLayout item = option(parent);
        LinearLayout header = optionHeader(item, title);
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        s.setAdapter(a); s.setSelection(selected);
        s.setPadding(dp(8), 0, dp(8), 0);
        s.setBackground(rounded(Color.WHITE, 10));
        item.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
        resetButton(header, title, () -> {
            s.setSelection(defaultIndex);
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
