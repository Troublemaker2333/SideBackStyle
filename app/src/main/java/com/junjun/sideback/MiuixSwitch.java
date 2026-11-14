package com.junjun.sideback;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * ColorOS 17 style switch, geometry taken from ONextBox's ColorOsSwitch:
 * 44x24dp track, 18dp thumb, 3dp inset, drag to toggle with 0.35 / 0.65 thresholds.
 */
public class MiuixSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private float density;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int accentColor = 0xFF1E7BFF;
    private int trackOffColor = 0xFFE8EAEE;
    private int thumbColor = Color.WHITE;

    private boolean checked;
    private boolean pressed;
    private float progress;
    private ValueAnimator animator;
    private float downX;
    private float startProgress;
    private boolean dragging;
    private OnCheckedChangeListener listener;

    public MiuixSwitch(Context context) {
        this(context, null);
    }

    public MiuixSwitch(Context context, AttributeSet attrs) {
        super(context);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setFocusable(true);
    }

    private float dp(float value) {
        return value * density;
    }

    public void setAccentColor(int color) {
        accentColor = color;
        invalidate();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        setChecked(value, false);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value && !animate) {
            return;
        }
        checked = value;
        if (animate) {
            animateTo(value ? 1f : 0f);
        } else {
            progress = value ? 1f : 0f;
            invalidate();
        }
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(240);
        animator.setInterpolator(new OvershootInterpolator(0.7f));
        animator.addUpdateListener(a -> {
            progress = Math.max(0f, Math.min(1f, (float) a.getAnimatedValue()));
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(dp(44)), Math.round(dp(24)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        int trackColor = blend(trackOffColor, accentColor, progress);
        trackPaint.setColor(trackColor);
        rect.set(0f, 0f, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (pressed) {
            overlayPaint.setColor(0x1F000000);
            canvas.drawRoundRect(rect, radius, radius, overlayPaint);
        }

        float thumb = dp(18);
        float inset = dp(3);
        float travel = w - inset * 2 - thumb;
        float cx = inset + thumb / 2f + travel * progress;
        float cy = h / 2f;
        thumbPaint.setColor(thumbColor);
        thumbPaint.setShadowLayer(dp(2.3f), 0f, dp(1.1f), 0x29000000);
        canvas.drawCircle(cx, cy, thumb / 2f, thumbPaint);
        thumbPaint.clearShadowLayer();
    }

    private int blend(int from, int to, float t) {
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                dragging = false;
                downX = event.getX();
                startProgress = progress;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                if (Math.abs(dx) > dp(3)) {
                    dragging = true;
                }
                float travel = getWidth() - dp(3) * 2 - dp(18);
                if (travel > 0) {
                    progress = Math.max(0f, Math.min(1f, startProgress + dx / travel));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                pressed = false;
                boolean next;
                if (!dragging) {
                    // 纯点击：直接取反，不能只看 progress（拖拽中 progress 才会变化）。
                    next = !checked;
                } else {
                    next = checked ? progress > 0.35f : progress >= 0.65f;
                }
                checked = next;
                animateTo(next ? 1f : 0f);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) {
                    listener.onCheckedChanged(next);
                }
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                animateTo(checked ? 1f : 0f);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
