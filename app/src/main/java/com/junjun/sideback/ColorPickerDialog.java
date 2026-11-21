package com.junjun.sideback;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * COUI 风格取色器：HSV 色板 + 色相条 + HEX 输入 + 常用色。
 * 不透明度由设置页单独控制，这里只取 RGB。
 */
public final class ColorPickerDialog {

    public interface OnColorPicked {
        void onPicked(int color);
    }

    private static final int[] PRESETS = {
            0xFFFFFFFF, 0xFF1B1B1F, 0xFF1E7BFF, 0xFFFF4D6D,
            0xFFFFB300, 0xFFA855F7, 0xFF22C55E, 0xFFFF7EB6};

    private ColorPickerDialog() {
    }

    public static void show(Context ctx, int initial, OnColorPicked callback) {
        final float density = ctx.getResources().getDisplayMetrics().density;

        final int[] color = {initial | 0xFF000000};
        final float[] hsv = new float[3];
        Color.colorToHSV(color[0], hsv);

        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density + 0.5f), (int) (18 * density + 0.5f), (int) (20 * density + 0.5f), (int) (14 * density + 0.5f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius((int) (24 * density + 0.5f));
        bg.setStroke((int) (1 * density + 0.5f), 0xFFECEDF0);
        root.setBackground(bg);

        TextView title = new TextView(ctx);
        title.setText("自定义颜色");
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        title.setTextColor(0xFF1B1B1F);
        root.addView(title);

        final SvSquare square = new SvSquare(ctx, density);
        LinearLayout.LayoutParams squareParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (170 * density));
        squareParams.topMargin = (int) (14 * density + 0.5f);
        root.addView(square, squareParams);

        final HueBar hueBar = new HueBar(ctx, density);
        LinearLayout.LayoutParams hueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (26 * density));
        hueParams.topMargin = (int) (12 * density + 0.5f);
        root.addView(hueBar, hueParams);

        LinearLayout hexRow = new LinearLayout(ctx);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hexRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexRowParams.topMargin = (int) (14 * density + 0.5f);
        hexRow.setLayoutParams(hexRowParams);

        final View preview = new View(ctx);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams((int) (32 * density + 0.5f), (int) (32 * density + 0.5f));
        previewParams.rightMargin = (int) (10 * density + 0.5f);
        preview.setLayoutParams(previewParams);
        hexRow.addView(preview);

        final EditText hexInput = new EditText(ctx);
        hexInput.setText(String.format("#%06X", color[0] & 0xFFFFFF));
        hexInput.setTextSize(14);
        hexInput.setSingleLine(true);
        hexInput.setTextColor(0xFF1B1B1F);
        hexInput.setGravity(Gravity.CENTER);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hexInput.setBackgroundResource(com.junjun.sideback.R.drawable.bg_chip);
        hexInput.setPadding((int) (10 * density + 0.5f), (int) (6 * density + 0.5f), (int) (10 * density + 0.5f), (int) (6 * density + 0.5f));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                (int) (110 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        hexInput.setLayoutParams(inputParams);
        hexRow.addView(hexInput);

        TextView hint = new TextView(ctx);
        hint.setText("HEX");
        hint.setTextSize(11);
        hint.setTextColor(0xFF8A8A90);
        hint.setPadding((int) (8 * density + 0.5f), 0, 0, 0);
        hexRow.addView(hint);
        root.addView(hexRow);

        final boolean[] selfUpdate = {false};

        final Runnable refresh = () -> {
            // The square is painted from the hue, so it has to be told when the hue moves. Without this the preview
            // dot and the hex field updated while the square stayed the colour from before the drag.
            square.setHue(hsv[0]);
            int c = Color.HSVToColor(new float[]{hsv[0], hsv[1], hsv[2]});
            color[0] = c;
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(c);
            circle.setStroke((int) (1 * density + 0.5f), 0x33000000);
            preview.setBackground(circle);
            if (!selfUpdate[0]) {
                selfUpdate[0] = true;
                String text = String.format("#%06X", c & 0xFFFFFF);
                hexInput.setText(text);
                hexInput.setSelection(text.length());
                selfUpdate[0] = false;
            }
        };
        square.setHsv(hsv);
        hueBar.setHue(hsv[0]);
        refresh.run();

        LinearLayout presets = new LinearLayout(ctx);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetParams.topMargin = (int) (14 * density + 0.5f);
        presets.setLayoutParams(presetParams);
        for (int preset : PRESETS) {
            View dot = new View(ctx);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    (int) (26 * density + 0.5f), (int) (26 * density + 0.5f));
            dotParams.rightMargin = (int) (8 * density + 0.5f);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(preset);
            dotBg.setStroke((int) (1 * density + 0.5f), 0x22000000);
            dot.setBackground(dotBg);
            dot.setOnClickListener(v -> {
                color[0] = preset | 0xFF000000;
                Color.colorToHSV(color[0], hsv);
                square.setHsv(hsv);
                hueBar.setHue(hsv[0]);
                refresh.run();
            });
            presets.addView(dot, dotParams);
        }
        root.addView(presets);

        Runnable refreshFromHex = () -> {
            String raw = hexInput.getText().toString().trim();
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            if (raw.length() == 6) {
                try {
                    int c = 0xFF000000 | Integer.parseInt(raw, 16);
                    color[0] = c;
                    Color.colorToHSV(c, hsv);
                    square.setHsv(hsv);
                    hueBar.setHue(hsv[0]);
                    refresh.run();
                } catch (Throwable ignored) {
                }
            }
        };

        square.setOnChanged(() -> {
            hsv[1] = square.getSat();
            hsv[2] = square.getVal();
            refresh.run();
        });
        hueBar.setOnChanged(() -> {
            hsv[0] = hueBar.getHue();
            refresh.run();
        });
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!selfUpdate[0]) {
                    refreshFromHex.run();
                }
            }
        });
        hexInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                refreshFromHex.run();
            }
            return false;
        });

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = (int) (18 * density + 0.5f);
        buttons.setLayoutParams(buttonsParams);

        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(0xFF8A8A90);
        cancel.setPadding((int) (14 * density + 0.5f), (int) (8 * density + 0.5f), (int) (14 * density + 0.5f), (int) (8 * density + 0.5f));
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);

        TextView ok = new TextView(ctx);
        ok.setText("确定");
        ok.setTextSize(14);
        ok.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        ok.setTextColor(0xFF1E7BFF);
        ok.setPadding((int) (16 * density + 0.5f), (int) (8 * density + 0.5f), (int) (8 * density + 0.5f), (int) (8 * density + 0.5f));
        ok.setOnClickListener(v -> {
            // Do not re-read the hex field here. `refreshFromHex` assigns to color[0], and the field can still
            // hold a value from before the last drag — confirming then replaced the picked colour with that older
            // one, which looked exactly like the choice being discarded. color[0] is kept current by refresh on
            // every interaction, so it is already what the user sees.
            callback.onPicked(color[0]);
            dialog.dismiss();
        });
        buttons.addView(ok);
        root.addView(buttons);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            int width = ctx.getResources().getDisplayMetrics().widthPixels - (int) (56 * density);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            int width = ctx.getResources().getDisplayMetrics().widthPixels - (int) (56 * density);
            shown.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.CENTER);
        }
    }

    // ------------------------------------------------------------ widgets

    private interface Change {
        void onChanged();
    }

    private static final class SvSquare extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private final Paint indicator = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorDark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue = 0f;
        private float sat = 1f;
        private float val = 1f;
        private Change change;

        SvSquare(Context ctx, float density) {
            super(ctx);
            this.density = density;
            indicator.setStyle(Paint.Style.STROKE);
            indicator.setStrokeWidth(2.2f * density);
            indicator.setColor(0xFFFFFFFF);
            indicatorDark.setStyle(Paint.Style.STROKE);
            indicatorDark.setStrokeWidth(1f * density);
            indicatorDark.setColor(0x66000000);
        }

        void setHsv(float[] hsv) {
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            invalidate();
        }

        void setHue(float h) {
            hue = h;
            invalidate();
        }

        float getSat() {
            return sat;
        }

        float getVal() {
            return val;
        }

        void setOnChanged(Change c) {
            change = c;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = 14f * density;
            rect.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            int base = Color.HSVToColor(new float[]{hue, 1f, 1f});
            paint.setShader(new LinearGradient(0, 0, w, 0,
                    Color.WHITE, base, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            canvas.restore();

            float x = sat * w;
            float y = (1f - val) * h;
            canvas.drawCircle(x, y, 8.5f * density, indicatorDark);
            canvas.drawCircle(x, y, 8.5f * density, indicator);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                sat = Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
                val = 1f - Math.max(0f, Math.min(1f, event.getY() / Math.max(1, getHeight())));
                invalidate();
                if (change != null) {
                    change.onChanged();
                }
                return true;
            }
            return true;
        }
    }

    private static final class HueBar extends View {
        private final float density;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float hue = 0f;
        private Change change;

        HueBar(Context ctx, float density) {
            super(ctx);
            this.density = density;
        }

        void setHue(float h) {
            hue = h;
            invalidate();
        }

        float getHue() {
            return hue;
        }

        void setOnChanged(Change c) {
            change = c;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            rect.set(0, 0, w, h);
            int[] colors = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                    0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
            paint.setShader(new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
            paint.setShader(null);

            float x = Math.max(6f * density, Math.min(w - 6f * density, hue / 360f * w));
            Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeWidth(2.4f * density);
            mark.setColor(0xFFFFFFFF);
            canvas.drawLine(x, 2f * density, x, h - 2f * density, mark);
            mark.setStrokeWidth(1f * density);
            mark.setColor(0x55000000);
            canvas.drawLine(x, 2f * density, x, h - 2f * density, mark);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                hue = Math.max(0f, Math.min(359.9f, event.getX() / Math.max(1, getWidth()) * 360f));
                invalidate();
                if (change != null) {
                    change.onChanged();
                }
                return true;
            }
            return true;
        }
    }
}
