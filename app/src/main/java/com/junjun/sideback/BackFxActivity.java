package com.junjun.sideback;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.junjun.sideback.PageAnim;
import com.junjun.sideback.PredictiveBack;
import com.junjun.sideback.R;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 侧滑返回样式设置页（COUI）：实时预览 + 图标 / 颜色 / 背景 / 渐变 / 不透明度。
 * 任何改动都会立刻写入 Settings.Global，SystemUI 内的 hook 下一次手势即生效。
 */
public class BackFxActivity extends Activity {

    private static final int REQ_PICK = 0x5b01;

    private static final String[] ICON_LABELS = {"箭头", "圆点", "爱心", "星星", "闪电", "猫爪", "图片", "透明"};
    private static final String[] ICON_VALUES = {
            BackFxConfig.ICON_ARROW, BackFxConfig.ICON_DOT, BackFxConfig.ICON_HEART,
            BackFxConfig.ICON_STAR, BackFxConfig.ICON_BOLT, BackFxConfig.ICON_PAW,
            BackFxConfig.ICON_IMAGE, BackFxConfig.ICON_NONE};
    private static final int[] ICON_COLORS = {
            0xFFFFFFFF, 0xFF1B1B1F, 0xFF1E7BFF, 0xFFFF4D6D,
            0xFFFFB300, 0xFFA855F7, 0xFF22C55E, 0xFFFF7EB6};
    private static final int[] BG_COLORS = {
            BackFxConfig.SYSTEM_BG_COLOR, 0xFF000000, 0xFFFFFFFF, 0xFFFFD7E6,
            0xFFFF7EB6, 0xFF1E7BFF, 0xFF7C4DFF, 0xFF22C55E};
    private static final String[] BG_LABELS = {"系统", "纯色", "渐变", "玻璃", "无"};
    private static final String[] BG_VALUES = {
            BackFxConfig.BG_SYSTEM, BackFxConfig.BG_SOLID,
            BackFxConfig.BG_GRADIENT, BackFxConfig.BG_GLASS, BackFxConfig.BG_NONE};
    private static final String[] GRAD_LABELS = {"线性", "径向", "扫描"};
    private static final String[] GRAD_VALUES = {
            BackFxConfig.GRAD_LINEAR, BackFxConfig.GRAD_RADIAL, BackFxConfig.GRAD_SWEEP};
    private static final String[] EFFECT_LABELS = {"无", "拖尾", "粒子", "残影", "弹性", "波纹"};
    private static final String[] EFFECT_VALUES = {
            BackFxConfig.EFFECT_NONE, BackFxConfig.EFFECT_TRAIL, BackFxConfig.EFFECT_PARTICLES,
            BackFxConfig.EFFECT_GHOST, BackFxConfig.EFFECT_STRETCH, BackFxConfig.EFFECT_RIPPLE};
    private static final int[] ALPHAS = {77, 128, 191, 255};

    private BackFxConfig cfg;
    private BackFxPreview preview;
    private float density;
    private boolean permissionWarned;

    private final List<TextView> iconChips = new ArrayList<>();
    private final List<TextView> bgChips = new ArrayList<>();
    private final List<TextView> radiusChips = new ArrayList<>();
    private final List<TextView> gradChips = new ArrayList<>();
    private final List<TextView> anglePresetChips = new ArrayList<>();
    private final List<TextView> effectChips = new ArrayList<>();
    private final List<View> iconColorDots = new ArrayList<>();
    private final List<View> bgColorDots = new ArrayList<>();
    private final List<View> bgColorDots2 = new ArrayList<>();

    private View iconCustomDot;
    private View bgCustomDot;
    private View bg2CustomDot;
    private LinearLayout iconColorBlock;
    private LinearLayout gradientParams;
    private LinearLayout angleRow;
    private LinearLayout midPosRow;
    private LinearLayout midColorRow;
    private LinearLayout imageMirrorRow;
    private View midColorDot;

    private ImageView thumb;
    private TextView imageHint;
    private TextView angleValue;
    private TextView midPosValue;
    private TextView iconSizeValue;
    private TextView iconOffsetValue;
    private EditText alphaEdit;
    private SeekBar alphaSeek;
    private SeekBar angleSeek;
    private SeekBar midPosSeek;
    private SeekBar iconSizeSeek;
    private SeekBar iconOffsetSeek;
    private SeekBar glassFrostSeek;
    private TextView glassFrostValue;
    private LinearLayout glassRow;
    private LinearLayout previewCard;
    private LinearLayout fixedHolder;
    private LinearLayout scrollContent;
    private com.junjun.sideback.MiuixSwitch fixedSwitch;
    private boolean updatingFields;

    private com.junjun.sideback.MiuixSwitch enableSwitch;
    private com.junjun.sideback.MiuixSwitch borderSwitch;
    private com.junjun.sideback.MiuixSwitch midSwitch;
    private com.junjun.sideback.MiuixSwitch mirrorSwitch;
    private com.junjun.sideback.MiuixSwitch elasticSwitch;
    private com.junjun.sideback.MiuixSwitch glowSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        cfg = BackFxConfig.load(this);
        setupWindow();
        setContentView(buildUi());
        applyLightSystemBars();
        PageAnim.slideIn(this, findViewById(android.R.id.content));
        PredictiveBack.install(this, findViewById(android.R.id.content));
        refreshAll();
        cfg.publish(this);
    }

    private void setupWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applyLightSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
                if (controller != null) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ ui

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_page);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(10), dp(18), dp(14));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildHeader());

        fixedHolder = new LinearLayout(this);
        fixedHolder.setOrientation(LinearLayout.VERTICAL);
        fixedHolder.setVisibility(View.GONE);
        column.addView(fixedHolder);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(12);
        column.addView(scroll, scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(8));
        scroll.addView(content);
        scrollContent = content;

        previewCard = buildPreviewCard();
        if (cfg.previewFixed) {
            fixedHolder.setVisibility(View.VISIBLE);
            fixedHolder.addView(previewCard, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            content.addView(previewCard);
        }
        content.addView(buildEnableCard());
        content.addView(buildIconCard());
        content.addView(buildBackgroundCard());
        content.addView(buildFooter());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                column.setPadding(dp(18), bars.top + dp(10), dp(18), bars.bottom + dp(14));
                return insets;
            });
        }
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("侧滑返回");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(getColor(R.color.text_primary));
        header.addView(title);

        TextView caption = new TextView(this);
        caption.setText("SystemUI · 手势图标与背景");
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        caption.setTextColor(getColor(R.color.text_secondary));
        caption.setPadding(0, dp(2), 0, 0);
        header.addView(caption);
        return header;
    }

    private LinearLayout buildPreviewCard() {
        LinearLayout card = card();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText("预览");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(getColor(R.color.text_secondary));
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(label);

        TextView pinLabel = new TextView(this);
        pinLabel.setText("固定");
        pinLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pinLabel.setTextColor(getColor(R.color.text_secondary));
        pinLabel.setPadding(0, 0, dp(8), 0);
        headerRow.addView(pinLabel);

        fixedSwitch = new com.junjun.sideback.MiuixSwitch(this);
        fixedSwitch.setChecked(cfg.previewFixed);
        fixedSwitch.setOnCheckedChangeListener(value -> {
            cfg.previewFixed = value;
            persist();
            applyPreviewPlacement();
        });
        headerRow.addView(fixedSwitch);
        card.addView(headerRow);

        FrameLayout holder = new FrameLayout(this);
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
        holderParams.topMargin = dp(10);
        holder.setLayoutParams(holderParams);
        holder.setBackground(roundRect(0xFFF2F3F6, dp(18)));
        holder.setClipToOutline(true);
        card.addView(holder);

        LinearLayout fake = new LinearLayout(this);
        fake.setOrientation(LinearLayout.VERTICAL);
        fake.setPadding(dp(56), dp(28), dp(16), 0);
        holder.addView(fake, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addFakeLine(fake, 0.42f, 18);
        addFakeLine(fake, 0.78f, 12);
        addFakeLine(fake, 0.62f, 12);
        addFakeLine(fake, 0.88f, 12);
        addFakeLine(fake, 0.30f, 18);
        addFakeLine(fake, 0.70f, 12);
        addFakeLine(fake, 0.55f, 12);

        preview = new BackFxPreview(this);
        holder.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preview.setOnClickListener(v -> preview.pulse());
        return card;
    }

    private void addFakeLine(LinearLayout parent, float widthRatio, int heightDp) {
        View line = new View(this);
        line.setBackground(roundRect(0xFFE4E6EB, dp(6)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp));
        p.weight = widthRatio;
        p.topMargin = dp(10);
        parent.addView(line, p);
        View spacer = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1);
        sp.weight = 1f - widthRatio;
        parent.addView(spacer, sp);
    }

    private View buildEnableCard() {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("启用自定义样式");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(getColor(R.color.text_primary));
        texts.addView(title);

        TextView desc = new TextView(this);
        desc.setText("贴边滑动时显示；关闭后恢复系统原样");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, dp(3), 0, 0);
        texts.addView(desc);
        row.addView(texts);

        enableSwitch = new com.junjun.sideback.MiuixSwitch(this);
        enableSwitch.setOnCheckedChangeListener(value -> {
            cfg.enabled = value;
            persist();
            refreshAll();
        });
        row.addView(enableSwitch);
        card.addView(row);
        return card;
    }

    private View buildIconCard() {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);

        card.addView(sectionTitle("图标"));

        // 7 个图标分成两行，避免横向滚动把最后一个裁掉。
        LinearLayout chipRows = new LinearLayout(this);
        chipRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout chipRow1 = new LinearLayout(this);
        chipRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout chipRow2 = new LinearLayout(this);
        chipRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Params.topMargin = dp(8);
        chipRow2.setLayoutParams(row2Params);
        chipRows.addView(chipRow1);
        chipRows.addView(chipRow2);
        chipRows.setPadding(0, dp(10), 0, 0);
        card.addView(chipRows);

        for (int i = 0; i < ICON_LABELS.length; i++) {
            final int index = i;
            TextView chip = makeChip(ICON_LABELS[i]);
            chip.setOnClickListener(v -> {
                cfg.icon = ICON_VALUES[index];
                persist();
                refreshAll();
            });
            iconChips.add(chip);
            (i < 4 ? chipRow1 : chipRow2).addView(chip);
        }

        iconColorBlock = new LinearLayout(this);
        iconColorBlock.setOrientation(LinearLayout.VERTICAL);
        card.addView(iconColorBlock);
        addDivider(iconColorBlock);
        iconColorBlock.addView(rowLabel("图标颜色"));
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setPadding(0, dp(10), 0, dp(2));
        for (int color : ICON_COLORS) {
            View dot = makeColorDot(color);
            dot.setOnClickListener(v -> pickColor(cfg.iconColor, picked -> {
                cfg.iconColor = picked;
                persist();
                refreshAll();
            }));
            iconColorDots.add(dot);
            colors.addView(dot);
        }
        iconCustomDot = makeCustomDot();
        iconCustomDot.setOnClickListener(v -> pickColor(cfg.iconColor, picked -> {
            cfg.iconColor = picked;
            persist();
            refreshAll();
        }));
        colors.addView(iconCustomDot);
        iconColorBlock.addView(colors);

        addDivider(card);
        LinearLayout sizeHeader = new LinearLayout(this);
        sizeHeader.setOrientation(LinearLayout.HORIZONTAL);
        sizeHeader.setGravity(Gravity.CENTER_VERTICAL);
        sizeHeader.setPadding(0, dp(12), 0, 0);
        TextView sizeLabel = rowLabel("图标大小");
        sizeLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sizeHeader.addView(sizeLabel);
        iconSizeValue = new TextView(this);
        iconSizeValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        iconSizeValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        iconSizeValue.setTextColor(getColor(R.color.text_primary));
        sizeHeader.addView(iconSizeValue);
        card.addView(sizeHeader);

        iconSizeSeek = makeSeek(0, 100, BackFxConfig.percentForIconScale(cfg.iconScale));
        iconSizeSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cfg.iconScale = BackFxConfig.iconScaleForPercent(progress);
                updateIconSizeText(progress);
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        card.addView(iconSizeSeek);

        LinearLayout offsetHeader = new LinearLayout(this);
        offsetHeader.setOrientation(LinearLayout.HORIZONTAL);
        offsetHeader.setGravity(Gravity.CENTER_VERTICAL);
        offsetHeader.setPadding(0, dp(12), 0, 0);
        TextView offsetLabel = rowLabel("图标偏移");
        offsetLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        offsetHeader.addView(offsetLabel);
        iconOffsetValue = new TextView(this);
        iconOffsetValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        iconOffsetValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        iconOffsetValue.setTextColor(getColor(R.color.text_primary));
        offsetHeader.addView(iconOffsetValue);
        card.addView(offsetHeader);

        iconOffsetSeek = makeSeek(0, 100, offsetPercent());
        iconOffsetSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cfg.iconOffsetDp = maxIconOffsetDp() * progress / 100f;
                updateIconOffsetText();
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        card.addView(iconOffsetSeek);

        addDivider(card);
        card.addView(rowLabel("图标动效"));
        LinearLayout effectRows = new LinearLayout(this);
        effectRows.setOrientation(LinearLayout.VERTICAL);
        effectRows.setPadding(0, dp(10), 0, 0);
        LinearLayout effectRow1 = new LinearLayout(this);
        effectRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout effectRow2 = new LinearLayout(this);
        effectRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams effectRow2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        effectRow2Params.topMargin = dp(8);
        effectRow2.setLayoutParams(effectRow2Params);
        effectRows.addView(effectRow1);
        effectRows.addView(effectRow2);
        card.addView(effectRows);

        for (int i = 0; i < EFFECT_LABELS.length; i++) {
            final int index = i;
            TextView chip = makeChip(EFFECT_LABELS[i]);
            chip.setOnClickListener(v -> {
                cfg.iconEffect = EFFECT_VALUES[index];
                persist();
                refreshAll();
            });
            effectChips.add(chip);
            (i < 3 ? effectRow1 : effectRow2).addView(chip);
        }

        addDivider(card);
        elasticSwitch = new com.junjun.sideback.MiuixSwitch(this);
        elasticSwitch.setOnCheckedChangeListener(value -> {
            cfg.elastic = value;
            persist();
            refreshAll();
        });
        card.addView(buildSwitchRow("跟手弹性",
                "图标弹簧跟随手指，快速滑动时轻微拉伸", elasticSwitch));

        glowSwitch = new com.junjun.sideback.MiuixSwitch(this);
        glowSwitch.setOnCheckedChangeListener(value -> {
            cfg.glow = value;
            persist();
            refreshAll();
        });
        card.addView(buildSwitchRow("图标光晕",
                "图标外圈一层呼吸光晕，暗色背景下更明显", glowSwitch));

        addDivider(card);
        LinearLayout imageRow = new LinearLayout(this);
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        imageRow.setGravity(Gravity.CENTER_VERTICAL);
        imageRow.setPadding(0, dp(12), 0, dp(2));

        LinearLayout imageTexts = new LinearLayout(this);
        imageTexts.setOrientation(LinearLayout.VERTICAL);
        imageTexts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView imageTitle = new TextView(this);
        imageTitle.setText("自定义图片");
        imageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        imageTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        imageTitle.setTextColor(getColor(R.color.text_primary));
        imageTexts.addView(imageTitle);
        imageHint = new TextView(this);
        imageHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        imageHint.setTextColor(getColor(R.color.text_secondary));
        imageHint.setPadding(0, dp(3), 0, 0);
        imageTexts.addView(imageHint);

        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        thumbParams.rightMargin = dp(10);
        thumb.setLayoutParams(thumbParams);

        TextView pick = makeChip("选择");
        pick.setOnClickListener(v -> pickImage());
        TextView clear = makeChip("清除");
        clear.setOnClickListener(v -> {
            cfg.image = "";
            persist();
            refreshAll();
        });

        imageRow.addView(imageTexts);
        imageRow.addView(thumb);
        imageRow.addView(pick);
        imageRow.addView(clear);
        card.addView(imageRow);

        imageMirrorRow = new LinearLayout(this);
        imageMirrorRow.setOrientation(LinearLayout.HORIZONTAL);
        imageMirrorRow.setGravity(Gravity.CENTER_VERTICAL);
        imageMirrorRow.setPadding(0, dp(14), 0, dp(2));
        LinearLayout mirrorTexts = new LinearLayout(this);
        mirrorTexts.setOrientation(LinearLayout.VERTICAL);
        mirrorTexts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mirrorTitle = new TextView(this);
        mirrorTitle.setText("右侧镜像翻转");
        mirrorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mirrorTitle.setTextColor(getColor(R.color.text_primary));
        mirrorTexts.addView(mirrorTitle);
        TextView mirrorDesc = new TextView(this);
        mirrorDesc.setText("让右侧图标与左侧图标互为镜像");
        mirrorDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mirrorDesc.setTextColor(getColor(R.color.text_secondary));
        mirrorDesc.setPadding(0, dp(3), 0, 0);
        mirrorTexts.addView(mirrorDesc);
        imageMirrorRow.addView(mirrorTexts);
        mirrorSwitch = new com.junjun.sideback.MiuixSwitch(this);
        mirrorSwitch.setOnCheckedChangeListener(value -> {
            cfg.iconMirror = value;
            persist();
            refreshAll();
        });
        imageMirrorRow.addView(mirrorSwitch);
        card.addView(imageMirrorRow);
        return card;
    }

    private View buildBackgroundCard() {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);

        card.addView(sectionTitle("背景"));
        LinearLayout bgRows = new LinearLayout(this);
        bgRows.setOrientation(LinearLayout.VERTICAL);
        bgRows.setPadding(0, dp(10), 0, dp(2));
        LinearLayout bgRow1 = new LinearLayout(this);
        bgRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout bgRow2 = new LinearLayout(this);
        bgRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bgRow2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bgRow2Params.topMargin = dp(8);
        bgRow2.setLayoutParams(bgRow2Params);
        bgRows.addView(bgRow1);
        bgRows.addView(bgRow2);
        card.addView(bgRows);
        for (int i = 0; i < BG_LABELS.length; i++) {
            final int index = i;
            TextView chip = makeChip(BG_LABELS[i]);
            chip.setOnClickListener(v -> {
                String next = BG_VALUES[index];
                if (BackFxConfig.BG_GLASS.equals(next)
                        && !BackFxConfig.BG_GLASS.equals(cfg.bgStyle)) {
                    cfg.bgColor = 0xFFFFFFFF;
                    cfg.bgAlpha = 120;
                }
                cfg.bgStyle = next;
                if (BackFxConfig.BG_SYSTEM.equals(cfg.bgStyle)) {
                    cfg.bgColor = BackFxConfig.SYSTEM_BG_COLOR;
                    cfg.bgAlpha = BackFxConfig.SYSTEM_BG_ALPHA;
                }
                persist();
                refreshAll();
            });
            bgChips.add(chip);
            (i < 3 ? bgRow1 : bgRow2).addView(chip);
        }

        addDivider(card);
        card.addView(rowLabel("背景颜色"));
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setPadding(0, dp(10), 0, dp(2));
        for (int color : BG_COLORS) {
            View dot = makeColorDot(color);
            dot.setOnClickListener(v -> pickColor(cfg.bgColor, picked -> {
                cfg.bgColor = picked;
                if (BackFxConfig.BG_SYSTEM.equals(cfg.bgStyle)) {
                    cfg.bgStyle = BackFxConfig.BG_SOLID;
                }
                persist();
                refreshAll();
            }));
            bgColorDots.add(dot);
            colors.addView(dot);
        }
        bgCustomDot = makeCustomDot();
        bgCustomDot.setOnClickListener(v -> pickColor(cfg.bgColor, picked -> {
            cfg.bgColor = picked;
            if (BackFxConfig.BG_SYSTEM.equals(cfg.bgStyle)) {
                cfg.bgStyle = BackFxConfig.BG_SOLID;
            }
            persist();
            refreshAll();
        }));
        colors.addView(bgCustomDot);
        card.addView(colors);

        glassRow = new LinearLayout(this);
        glassRow.setOrientation(LinearLayout.VERTICAL);
        card.addView(glassRow);
        LinearLayout frostHeader = new LinearLayout(this);
        frostHeader.setOrientation(LinearLayout.HORIZONTAL);
        frostHeader.setGravity(Gravity.CENTER_VERTICAL);
        frostHeader.setPadding(0, dp(12), 0, 0);
        TextView frostLabel = rowLabel("磨砂强度");
        frostLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        frostHeader.addView(frostLabel);
        glassFrostValue = new TextView(this);
        glassFrostValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        glassFrostValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        glassFrostValue.setTextColor(getColor(R.color.text_primary));
        frostHeader.addView(glassFrostValue);
        glassRow.addView(frostHeader);
        glassFrostSeek = makeSeek(0, 100, cfg.glassFrost);
        glassFrostSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cfg.glassFrost = progress;
                if (glassFrostValue != null) {
                    glassFrostValue.setText(progress + "%");
                }
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        glassRow.addView(glassFrostSeek);

        gradientParams = new LinearLayout(this);
        gradientParams.setOrientation(LinearLayout.VERTICAL);
        card.addView(gradientParams);

        addDivider(gradientParams);
        gradientParams.addView(rowLabel("渐变类型"));
        LinearLayout gradRow = new LinearLayout(this);
        gradRow.setOrientation(LinearLayout.HORIZONTAL);
        gradRow.setPadding(0, dp(10), 0, dp(2));
        for (int i = 0; i < GRAD_LABELS.length; i++) {
            final int index = i;
            TextView chip = makeChip(GRAD_LABELS[i]);
            chip.setOnClickListener(v -> {
                cfg.gradientType = GRAD_VALUES[index];
                persist();
                refreshAll();
            });
            gradChips.add(chip);
            gradRow.addView(chip);
        }
        gradientParams.addView(gradRow);

        angleRow = new LinearLayout(this);
        angleRow.setOrientation(LinearLayout.VERTICAL);
        gradientParams.addView(angleRow);

        LinearLayout angleHeader = new LinearLayout(this);
        angleHeader.setOrientation(LinearLayout.HORIZONTAL);
        angleHeader.setGravity(Gravity.CENTER_VERTICAL);
        angleHeader.setPadding(0, dp(12), 0, 0);
        TextView angleLabel = rowLabel("渐变方向");
        angleLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        angleHeader.addView(angleLabel);
        angleValue = new TextView(this);
        angleValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        angleValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        angleValue.setTextColor(getColor(R.color.text_primary));
        angleHeader.addView(angleValue);
        angleRow.addView(angleHeader);

        angleSeek = makeSeek(0, 360, 90);
        angleSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cfg.gradientAngle = progress;
                if (angleValue != null) {
                    angleValue.setText(progress + "°");
                }
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        angleRow.addView(angleSeek);

        LinearLayout anglePresets = new LinearLayout(this);
        anglePresets.setOrientation(LinearLayout.HORIZONTAL);
        anglePresets.setPadding(0, dp(4), 0, dp(2));
        String[] angleLabels = {"横向", "纵向", "对角"};
        int[] angleValues = {0, 90, 45};
        for (int i = 0; i < angleLabels.length; i++) {
            final int value = angleValues[i];
            TextView chip = makeChip(angleLabels[i]);
            chip.setOnClickListener(v -> {
                cfg.gradientAngle = value;
                persist();
                refreshAll();
            });
            anglePresetChips.add(chip);
            anglePresets.addView(chip);
        }
        angleRow.addView(anglePresets);

        midColorRow = new LinearLayout(this);
        midColorRow.setOrientation(LinearLayout.HORIZONTAL);
        midColorRow.setGravity(Gravity.CENTER_VERTICAL);
        midColorRow.setPadding(0, dp(12), 0, dp(2));
        LinearLayout midTexts = new LinearLayout(this);
        midTexts.setOrientation(LinearLayout.VERTICAL);
        midTexts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView midTitle = new TextView(this);
        midTitle.setText("三色渐变");
        midTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        midTitle.setTextColor(getColor(R.color.text_primary));
        midTexts.addView(midTitle);
        TextView midDesc = new TextView(this);
        midDesc.setText("在两种颜色之间插入中间色");
        midDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        midDesc.setTextColor(getColor(R.color.text_secondary));
        midDesc.setPadding(0, dp(3), 0, 0);
        midTexts.addView(midDesc);
        midColorRow.addView(midTexts);
        midColorDot = makePlainDot();
        LinearLayout.LayoutParams midDotParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        midDotParams.rightMargin = dp(10);
        midColorDot.setLayoutParams(midDotParams);
        midColorDot.setOnClickListener(v -> pickColor(
                cfg.gradientMidColor == 0 ? 0xFFFFFFFF : cfg.gradientMidColor, picked -> {
                    cfg.gradientMidColor = picked;
                    persist();
                    refreshAll();
                }));
        midColorRow.addView(midColorDot);
        midSwitch = new com.junjun.sideback.MiuixSwitch(this);
        midSwitch.setOnCheckedChangeListener(value -> {
            if (value) {
                if (cfg.gradientMidColor == 0) {
                    cfg.gradientMidColor = 0xFFFFFFFF;
                }
            } else {
                cfg.gradientMidColor = 0;
            }
            persist();
            refreshAll();
        });
        midColorRow.addView(midSwitch);
        gradientParams.addView(midColorRow);

        midPosRow = new LinearLayout(this);
        midPosRow.setOrientation(LinearLayout.VERTICAL);
        gradientParams.addView(midPosRow);
        LinearLayout midPosHeader = new LinearLayout(this);
        midPosHeader.setOrientation(LinearLayout.HORIZONTAL);
        midPosHeader.setGravity(Gravity.CENTER_VERTICAL);
        midPosHeader.setPadding(0, dp(10), 0, 0);
        TextView midPosLabel = rowLabel("中间色位置");
        midPosLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        midPosHeader.addView(midPosLabel);
        midPosValue = new TextView(this);
        midPosValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        midPosValue.setTextColor(getColor(R.color.text_primary));
        midPosHeader.addView(midPosValue);
        midPosRow.addView(midPosHeader);

        midPosSeek = makeSeek(10, 90, 50);
        midPosSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = progress + 10;
                cfg.gradientMidPos = percent / 100f;
                if (midPosValue != null) {
                    midPosValue.setText(percent + "%");
                }
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        midPosRow.addView(midPosSeek);

        addDivider(card);
        card.addView(rowLabel("指示器大小"));
        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.HORIZONTAL);
        radiusRow.setPadding(0, dp(10), 0, dp(2));
        String[] radiusLabels = {"小", "标准", "系统"};
        int[] radiusValues = {64, 82, 104};
        for (int i = 0; i < radiusLabels.length; i++) {
            final int value = radiusValues[i];
            TextView chip = makeChip(radiusLabels[i]);
            chip.setOnClickListener(v -> {
                cfg.bgRadius = value;
                persist();
                refreshAll();
            });
            radiusChips.add(chip);
            radiusRow.addView(chip);
        }
        card.addView(radiusRow);

        addDivider(card);
        card.addView(rowLabel("不透明度"));
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setOrientation(LinearLayout.HORIZONTAL);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(0, dp(8), 0, dp(0));
        alphaSeek = makeSeek(0, 100, 75);
        alphaSeek.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        alphaSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cfg.bgAlpha = Math.round(progress / 100f * 255f);
                if (alphaEdit != null && !updatingFields) {
                    updatingFields = true;
                    alphaEdit.setText(String.valueOf(progress));
                    alphaEdit.setSelection(alphaEdit.getText().length());
                    updatingFields = false;
                }
                if (fromUser) {
                    preview.invalidate();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persist();
                refreshAll();
            }
        });
        alphaRow.addView(alphaSeek);

        alphaEdit = new EditText(this);
        alphaEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        alphaEdit.setTextColor(getColor(R.color.text_primary));
        alphaEdit.setGravity(Gravity.CENTER);
        alphaEdit.setSingleLine(true);
        alphaEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        alphaEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        alphaEdit.setBackgroundResource(R.drawable.bg_chip);
        alphaEdit.setPadding(dp(8), dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(56), dp(38));
        editParams.leftMargin = dp(10);
        alphaEdit.setLayoutParams(editParams);
        alphaEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                applyAlphaFromEdit();
                return true;
            }
            return false;
        });
        alphaEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !updatingFields) {
                applyAlphaFromEdit();
            }
        });
        alphaRow.addView(alphaEdit);

        TextView percent = new TextView(this);
        percent.setText("%");
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        percent.setTextColor(getColor(R.color.text_secondary));
        percent.setPadding(dp(4), 0, 0, 0);
        alphaRow.addView(percent);
        card.addView(alphaRow);

        addDivider(card);
        LinearLayout borderRow = new LinearLayout(this);
        borderRow.setOrientation(LinearLayout.HORIZONTAL);
        borderRow.setGravity(Gravity.CENTER_VERTICAL);
        borderRow.setPadding(0, dp(14), 0, dp(2));
        LinearLayout borderTexts = new LinearLayout(this);
        borderTexts.setOrientation(LinearLayout.VERTICAL);
        borderTexts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView borderTitle = new TextView(this);
        borderTitle.setText("高光描边");
        borderTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        borderTitle.setTextColor(getColor(R.color.text_primary));
        borderTexts.addView(borderTitle);
        TextView borderDesc = new TextView(this);
        borderDesc.setText("贴边处一层浅白描边，更像系统原生");
        borderDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        borderDesc.setTextColor(getColor(R.color.text_secondary));
        borderDesc.setPadding(0, dp(3), 0, 0);
        borderTexts.addView(borderDesc);
        borderRow.addView(borderTexts);
        borderSwitch = new com.junjun.sideback.MiuixSwitch(this);
        borderSwitch.setOnCheckedChangeListener(value -> {
            cfg.border = value;
            persist();
            refreshAll();
        });
        borderRow.addView(borderSwitch);
        card.addView(borderRow);
        return card;
    }

    private View buildFooter() {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        card.addView(sectionTitle("使用说明"));
        TextView steps = new TextView(this);
        steps.setText("1. 在 LSPosed 中启用本模块，作用域勾选「系统界面」(com.android.systemui)\n"
                + "2. 打开本应用，调整图标、背景与动效，改动即时生效\n"
                + "3. 从屏幕左/右边缘向内滑动即可看到效果\n"
                + "4. 自定义图片会压缩到 128px 存入模块本地配置（约 30KB）\n"
                + "5. 关闭「启用自定义样式」即恢复系统原样");
        steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        steps.setTextColor(getColor(R.color.text_secondary));
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, dp(8), 0, 0);
        card.addView(steps);

        addDivider(card);
        card.addView(sectionTitle("免责声明"));
        TextView disclaimer = new TextView(this);
        disclaimer.setText("本模块通过 LSPosed 修改 SystemUI 的侧滑返回指示器绘制，仅供个人学习与美化使用。\n\n"
                + "不同 ROM / 系统版本可能存在兼容性差异，使用前请自行备份重要数据；"
                + "因使用本模块导致的系统异常、界面错乱或数据丢失，由使用者自行承担。\n\n"
                + "请勿将本模块用于商业分发；转载请注明出处并保留本声明。");
        disclaimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        disclaimer.setTextColor(getColor(R.color.text_secondary));
        disclaimer.setLineSpacing(dp(4), 1f);
        disclaimer.setPadding(0, dp(8), 0, 0);
        card.addView(disclaimer);

        TextView reset = new TextView(this);
        reset.setText("恢复默认样式");
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        reset.setTextColor(getColor(R.color.accent));
        reset.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        reset.setPadding(0, dp(16), 0, dp(2));
        reset.setOnClickListener(v -> {
            cfg = new BackFxConfig();
            persist();
            refreshAll();
        });
        card.addView(reset);

        TextView version = new TextView(this);
        version.setText("侧滑返回样式 v1.2 · LSPosed 模块");
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        version.setTextColor(getColor(R.color.text_secondary));
        version.setPadding(0, dp(10), 0, 0);
        card.addView(version);
        return card;
    }

    // ------------------------------------------------------------ helpers

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(18), dp(16), dp(18), dp(14));
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setTextColor(getColor(R.color.text_primary));
        return view;
    }

    private TextView rowLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setPadding(0, dp(12), 0, 0);
        return view;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0x14000000);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(12);
        parent.addView(divider, params);
    }

    private TextView makeChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setTextColor(getColor(R.color.text_primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(selected ? Color.WHITE : getColor(R.color.text_primary));
    }

    private View makeColorDot(int color) {
        FrameLayout dot = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.rightMargin = dp(5);
        dot.setLayoutParams(params);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setStroke(dp(1), 0x1F000000);
        dot.setBackground(circle);
        dot.setTag(color);
        return dot;
    }

    /** 彩色圆环 + 加号，点开 HSV 调色盘。 */
    private View makeCustomDot() {        FrameLayout dot = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.rightMargin = dp(5);
        dot.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(1), 0xFFC6C8CE);
        dot.setBackground(bg);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        plus.setTextColor(0xFF1B1B1F);
        plus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams plusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dot.addView(plus, plusParams);
        dot.setTag("custom");
        return dot;
    }

    /** 纯色圆点（用于三色渐变的中间色，不带加号）。 */
    private View makePlainDot() {
        FrameLayout dot = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.rightMargin = dp(5);
        dot.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(1), 0xFFC6C8CE);
        dot.setBackground(bg);
        return dot;
    }

    private void styleDots(List<View> dots, int selected) {
        for (View dot : dots) {
            Object tag = dot.getTag();
            int color = tag instanceof Integer ? (Integer) tag : 0;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(dp(color == selected ? 3 : 1),
                    color == selected ? 0xFF1B1B1F : 0x1F000000);
            dot.setBackground(bg);
        }
    }

    private void styleCustomDot(View dot, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(selected ? 3 : 1), selected ? 0xFF1B1B1F : 0xFFC6C8CE);
        dot.setBackground(bg);
    }

    private boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    private SeekBar makeSeek(int min, int max, int value) {
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seek.setProgressTintList(ColorStateList.valueOf(0xFF1E7BFF));
        seek.setThumbTintList(ColorStateList.valueOf(0xFF1E7BFF));
        seek.setPadding(0, dp(6), 0, dp(6));
        seek.setTag(min);
        return seek;
    }

    private int seekValue(SeekBar seek, int min) {
        return seek.getProgress() + min;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private interface ColorCallback {
        void onColor(int color);
    }

    private void pickColor(int current, ColorCallback callback) {
        ColorPickerDialog.show(this, current, callback::onColor);
    }

    // ------------------------------------------------------------ state

    private void applyAlphaFromEdit() {
        if (alphaEdit == null || updatingFields) {
            return;
        }
        String raw = alphaEdit.getText().toString().trim();
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (Throwable t) {
            value = Math.round(cfg.bgAlpha / 255f * 100f);
        }
        value = Math.max(0, Math.min(100, value));
        cfg.bgAlpha = Math.round(value / 100f * 255f);
        updatingFields = true;
        alphaEdit.setText(String.valueOf(value));
        alphaEdit.setSelection(alphaEdit.getText().length());
        updatingFields = false;
        if (alphaSeek != null) {
            alphaSeek.setProgress(value);
        }
        persist();
        refreshAll();
    }

    private LinearLayout buildSwitchRow(String title, String desc,
                                        com.junjun.sideback.MiuixSwitch toggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(2));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(getColor(R.color.text_primary));
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText(desc);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sub.setTextColor(getColor(R.color.text_secondary));
        sub.setPadding(0, dp(3), 0, 0);
        texts.addView(sub);

        row.addView(texts);
        row.addView(toggle);
        return row;
    }

    private void updateIconSizeText(int percent) {
        if (iconSizeValue == null) {
            return;
        }
        float dp = BackFxConfig.ICON_MIN_DP
                + (BackFxConfig.ICON_MAX_DP - BackFxConfig.ICON_MIN_DP) * percent / 100f;
        iconSizeValue.setText(percent + "% · "
                + String.format(java.util.Locale.US, "%.1fdp", dp));
    }

    /** 图标中心从贴边位置挪到屏幕正中所需的偏移量（dp）。 */
    private float maxIconOffsetDp() {
        float screenDp = getResources().getDisplayMetrics().widthPixels / density;
        float baseCenter = BackFxConfig.EDGE_TRAVEL_DP - BackFxConfig.ICON_INSET_DP;
        // 留 6dp 余量，避免图标正好贴到屏幕中线时被右侧边界裁掉。
        return Math.max(12f, screenDp / 2f - baseCenter - 6f);
    }

    private int offsetPercent() {
        float max = maxIconOffsetDp();
        return Math.max(0, Math.min(100, Math.round(cfg.iconOffsetDp / max * 100f)));
    }

    private void updateIconOffsetText() {
        if (iconOffsetValue == null) {
            return;
        }
        iconOffsetValue.setText(String.format(java.util.Locale.US, "%.0fdp", cfg.iconOffsetDp));
    }

    private void applyPreviewPlacement() {
        if (previewCard == null || fixedHolder == null || scrollContent == null) {
            return;
        }
        android.view.ViewParent parent = previewCard.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(previewCard);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (cfg.previewFixed) {
            fixedHolder.setVisibility(View.VISIBLE);
            fixedHolder.addView(previewCard, lp);
        } else {
            fixedHolder.setVisibility(View.GONE);
            scrollContent.addView(previewCard, 0, lp);
        }
    }

    private void persist() {
        boolean ok = cfg.save(this);
        if (!ok && !permissionWarned) {
            permissionWarned = true;
            Toast.makeText(this,
                    "样式保存失败，请检查应用存储权限",
                    Toast.LENGTH_LONG).show();
        }
        if (preview != null) {
            preview.invalidate();
        }
    }

    private void refreshAll() {
        updatingFields = true;
        if (enableSwitch != null) {
            enableSwitch.setChecked(cfg.enabled);
        }
        for (int i = 0; i < iconChips.size(); i++) {
            styleChip(iconChips.get(i), ICON_VALUES[i].equals(cfg.icon));
        }
        int iconPercent = BackFxConfig.percentForIconScale(cfg.iconScale);
        if (iconSizeSeek != null) {
            iconSizeSeek.setProgress(iconPercent);
        }
        updateIconSizeText(iconPercent);

        int offsetPct = offsetPercent();
        if (iconOffsetSeek != null) {
            iconOffsetSeek.setProgress(offsetPct);
        }
        updateIconOffsetText();
        for (int i = 0; i < effectChips.size(); i++) {
            styleChip(effectChips.get(i), EFFECT_VALUES[i].equals(cfg.iconEffect));
        }
        if (elasticSwitch != null) {
            elasticSwitch.setChecked(cfg.elastic);
        }
        if (glowSwitch != null) {
            glowSwitch.setChecked(cfg.glow);
        }
        if (fixedSwitch != null) {
            fixedSwitch.setChecked(cfg.previewFixed);
        }
        styleDots(iconColorDots, cfg.iconColor);
        styleCustomDot(iconCustomDot, !contains(ICON_COLORS, cfg.iconColor));

        boolean customIcon = BackFxConfig.ICON_IMAGE.equals(cfg.icon);
        boolean noIcon = BackFxConfig.ICON_NONE.equals(cfg.icon);
        if (iconColorBlock != null) {
            iconColorBlock.setVisibility(customIcon || noIcon ? View.GONE : View.VISIBLE);
        }
        if (imageMirrorRow != null) {
            imageMirrorRow.setVisibility(customIcon ? View.VISIBLE : View.GONE);
        }
        if (mirrorSwitch != null) {
            mirrorSwitch.setChecked(cfg.iconMirror);
        }

        for (int i = 0; i < bgChips.size(); i++) {
            styleChip(bgChips.get(i), BG_VALUES[i].equals(cfg.bgStyle));
        }
        boolean glass = BackFxConfig.BG_GLASS.equals(cfg.bgStyle);
        if (glassRow != null) {
            glassRow.setVisibility(glass ? View.VISIBLE : View.GONE);
        }
        if (glassFrostSeek != null) {
            glassFrostSeek.setProgress(cfg.glassFrost);
        }
        if (glassFrostValue != null) {
            glassFrostValue.setText(cfg.glassFrost + "%");
        }
        styleDots(bgColorDots, cfg.bgColor);
        styleCustomDot(bgCustomDot, !contains(BG_COLORS, cfg.bgColor));
        boolean gradient = BackFxConfig.BG_GRADIENT.equals(cfg.bgStyle);
        if (gradientParams != null) {
            gradientParams.setVisibility(gradient ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < gradChips.size(); i++) {
            styleChip(gradChips.get(i), GRAD_VALUES[i].equals(cfg.gradientType));
        }
        boolean linear = BackFxConfig.GRAD_LINEAR.equals(cfg.gradientType);
        if (angleRow != null) {
            angleRow.setVisibility(linear ? View.VISIBLE : View.GONE);
        }
        if (angleSeek != null) {
            angleSeek.setProgress(cfg.gradientAngle);
        }
        if (angleValue != null) {
            angleValue.setText(cfg.gradientAngle + "°");
        }
        for (int i = 0; i < anglePresetChips.size(); i++) {
            int value = i == 0 ? 0 : (i == 1 ? 90 : 45);
            styleChip(anglePresetChips.get(i), cfg.gradientAngle == value);
        }
        boolean hasMid = (cfg.gradientMidColor >>> 24) != 0;
        if (midSwitch != null) {
            midSwitch.setChecked(hasMid);
        }
        if (midPosRow != null) {
            midPosRow.setVisibility(hasMid ? View.VISIBLE : View.GONE);
        }
        if (midColorDot != null) {
            GradientDrawable midBg = new GradientDrawable();
            midBg.setShape(GradientDrawable.OVAL);
            midBg.setColor(hasMid ? cfg.gradientMidColor : 0xFFFFFFFF);
            midBg.setStroke(dp(1), hasMid ? 0x33000000 : 0xFFC6C8CE);
            midColorDot.setBackground(midBg);
        }
        if (midPosSeek != null) {
            midPosSeek.setProgress(Math.round(cfg.gradientMidPos * 100f) - 10);
        }
        if (midPosValue != null) {
            midPosValue.setText(Math.round(cfg.gradientMidPos * 100f) + "%");
        }

        int radiusIndex = 1;
        for (int i = 0; i < radiusChips.size(); i++) {
            int value = i == 0 ? 64 : (i == 2 ? 104 : 82);
            if (value == cfg.bgRadius) {
                radiusIndex = i;
            }
        }
        for (int i = 0; i < radiusChips.size(); i++) {
            styleChip(radiusChips.get(i), i == radiusIndex);
        }

        int percent = Math.round(cfg.bgAlpha / 255f * 100f);
        if (alphaSeek != null) {
            alphaSeek.setProgress(percent);
        }
        if (alphaEdit != null) {
            alphaEdit.setText(String.valueOf(percent));
            alphaEdit.setSelection(alphaEdit.getText().length());
        }
        if (borderSwitch != null) {
            borderSwitch.setChecked(cfg.border);
        }

        if (thumb != null) {
            Bitmap bmp = BackFxConfig.decodeImage(cfg.image);
            if (bmp != null) {
                thumb.setImageBitmap(bmp);
                thumb.setVisibility(View.VISIBLE);
            } else {
                thumb.setImageDrawable(null);
                thumb.setVisibility(View.INVISIBLE);
            }
        }
        if (imageHint != null) {
            imageHint.setText(cfg.image == null || cfg.image.isEmpty()
                    ? "支持任意图片，自动裁成方形" : "已选择自定义图片");
        }
        if (preview != null) {
            preview.setConfig(cfg);
            preview.invalidate();
        }
        updatingFields = false;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Throwable t) {
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            int sample = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / sample > 512) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(in2, null, real);
                if (bmp == null) {
                    throw new IllegalStateException("decode failed");
                }
                String encoded = BackFxConfig.encodeImage(bmp);
                if (encoded.isEmpty()) {
                    throw new IllegalStateException("encode failed");
                }
                cfg.image = encoded;
                cfg.icon = BackFxConfig.ICON_IMAGE;
                persist();
                refreshAll();
                Toast.makeText(this, "图片已应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    // ------------------------------------------------------------ preview

    private final class BackFxPreview extends View {
        private BackFxConfig config;
        private float enter = 1f;
        private long lastFrame;
        private final BackFxMotion motion = new BackFxMotion();

        BackFxPreview(Activity activity) {
            super(activity);
        }

        void setConfig(BackFxConfig c) {
            this.config = c;
        }

        void pulse() {
            android.animation.ValueAnimator animator =
                    android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(420L);
            animator.addUpdateListener(a -> {
                enter = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (config == null) {
                return;
            }
            float w = getWidth();
            float h = getHeight();
            float vsW = (w / density) / 360f;
            float vs = Math.min(0.95f, vsW);
            float d = density * vs;

            drawBackdrop(canvas, w, h, d);

            long now = android.os.SystemClock.uptimeMillis();
            float dt = lastFrame == 0L ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float timeSec = now / 1000f;
            float cy = h * 0.5f + (float) Math.sin(timeSec * 1.3f) * 10f * d;

            // 演示一次贴边滑动：行程来回摆动，方便直接看到拖尾/粒子等过程动效。
            float travel = 56f * d * (0.42f + 0.58f * (0.5f + 0.5f * (float) Math.sin(timeSec * 1.8f)));
            float scale = 0.86f + 0.14f * enter;
            float size = BackFxRenderer.iconSize(config, d);
            float cx = BackFxRenderer.iconCenterX(config, 0, w, travel, d, size);
            float demoFingerX = Math.min(w - 20f * d,
                    30f * d + travel + (0.5f + 0.5f * (float) Math.sin(timeSec * 1.3f)) * 70f * d);
            motion.update(cx, cy, demoFingerX, cy, dt, now, config.iconEffect);

            BackFxRenderer.draw(canvas, config, 0, w, cy, travel, d, enter, scale,
                    timeSec, 0f, motion, now);

            boolean animated = !BackFxConfig.EFFECT_NONE.equals(config.iconEffect)
                    || config.glow || BackFxConfig.ICON_IMAGE.equals(config.icon);
            if ((animated || travel > 0) && isShown()) {
                postInvalidateOnAnimation();
            }
        }

        private void drawBackdrop(Canvas canvas, float w, float h, float d) {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xFFE6E9EF);
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.2f * d);
            stroke.setColor(0xFFD2D6DE);

            float icon = 54f * d;
            float gap = 15f * d;
            float left = 18f * d;
            float top = 14f * d;
            for (int row = 0; row < 3; row++) {
                float y = top + row * (icon + gap);
                if (y + icon > h - 70f * d) {
                    break;
                }
                for (int col = 0; col < 4; col++) {
                    float x = left + col * (icon + gap);
                    if (x + icon > w - 6f * d) {
                        break;
                    }
                    RectF r = new RectF(x, y, x + icon, y + icon);
                    canvas.drawRoundRect(r, 14f * d, 14f * d, fill);
                    canvas.drawRoundRect(r, 14f * d, 14f * d, stroke);
                }
            }

            RectF dock = new RectF(10f * d, h - 62f * d, w - 10f * d, h - 14f * d);
            Paint dockFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            dockFill.setColor(0xAAFFFFFF);
            canvas.drawRoundRect(dock, 18f * d, 18f * d, dockFill);
            canvas.drawRoundRect(dock, 18f * d, 18f * d, stroke);
        }
    }

    /** 只关心需要的回调，避免每个 SeekBar 都写一堆空方法。 */
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
