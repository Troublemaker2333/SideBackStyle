package com.junjun.sideback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.junjun.sideback.R;

/**
 * The live gesture preview shown on the 侧滑返回 screen.
 *
 * <p>It reproduces what the gesture actually looks like, from the user's two reference screenshots:
 *
 * <ol>
 *   <li>Before the swipe the foreground app fills the screen, with only its top-left corner rounded.</li>
 *   <li>Mid-swipe the app card shrinks, slides right, rounds all four corners, and the home screen behind it is
 *       blurred.</li>
 * </ol>
 *
 * <p>So there are two images and one animated card:
 *
 * <ul>
 *   <li>{@code preview_desktop.png} — the home screen, drawn blurred as the backdrop;</li>
 *   <li>{@code preview_app.png} — a capture of a real app, drawn as the card that moves;</li>
 *   <li>the indicator, drawn by {@link BackFxRenderer} against the card's left edge.</li>
 * </ul>
 *
 * <p>An earlier version drew the desktop at full sharpness with the indicator floating on top, which is why the
 * indicator looked off-centre: there was nothing for it to be centred against. In the real gesture it sits on the
 * card's rounded edge, and the card is what carries the motion.
 */
public final class BackFxPreview extends View {

    // ---- the slide, as fractions of the frame ----
    /** How far the card has moved right at full travel. */
    private static final float CARD_SHIFT_X = 0.18f;
    /** Corner radius of the card at full travel, in dp. */
    private static final float CARD_CORNER_DP = 22f;
    /**
     * The card is drawn this much taller and wider than the frame, so sliding it right cannot expose the backdrop
     * along the top or bottom edge. The real app covers the whole screen; it does not shrink.
     */
    private static final float CARD_OVERSCAN = 0.04f;

    private BackFxConfig config;
    private Bitmap backdrop;
    private Bitmap foreground;
    private float enter = 1f;
    private long lastFrame;
    private final BackFxMotion motion = new BackFxMotion();

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final Path cardPath = new Path();
    private final RectF cardRect = new RectF();

    public BackFxPreview(Context context) {
        this(context, null);
    }

    public BackFxPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadImages(context);
    }

    private void loadImages(Context context) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            backdrop = BitmapFactory.decodeResource(context.getResources(), R.drawable.preview_desktop, opts);
            foreground = BitmapFactory.decodeResource(context.getResources(), R.drawable.preview_app, opts);
        } catch (Throwable t) {
            android.util.Log.w("BackFxPreview", "images", t);
        }
    }

    public void setConfig(BackFxConfig c) {
        this.config = c;
        invalidate();
    }

    /** Restarts the entry animation, so tapping the preview shows the slide again. */
    public void pulse() {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
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
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float timeSec = now / 1000f;

        // The swing that drives everything. 0 = app filling the screen, 1 = fully pulled.
        float pull = 0.5f + 0.5f * (float) Math.sin(timeSec * 1.8f);

        // ---- the home screen, blurred, behind everything ----
        if (backdrop != null) {
            drawFill(canvas, backdrop, w, h, blurPaint);
        } else {
            canvas.drawColor(0xFF1B1B1F);
        }

        // ---- the app card, sliding right ----
        // No scaling: a shrinking card leaves a wedge of backdrop in the corner it moves away from, and the top
        // right must stay covered. The card is oversized instead, so its edges never come into frame.
        float shiftX = w * CARD_SHIFT_X * pull;
        float cardW = w * (1f + CARD_OVERSCAN);
        float cardH = h * (1f + CARD_OVERSCAN);
        float cardLeft = shiftX + (w - cardW) / 2f;
        float cardTop = (h - cardH) / 2f;
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH);

        float corner = CARD_CORNER_DP * density * pull;
        cardPath.reset();
        cardPath.addRoundRect(cardRect, corner, corner, Path.Direction.CW);

        if (foreground != null) {
            canvas.save();
            canvas.clipPath(cardPath);
            drawFill(canvas, foreground, cardRect, bitmapPaint);
            canvas.restore();
        } else {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xFFF7F7FA);
            canvas.drawPath(cardPath, fill);
        }

        // ---- the gesture indicator, on the card's left edge ----
        // travel is how far it has emerged; the card's own shift is added so it stays glued to the edge.
        // The same cap the gesture uses; a hardcoded 56 here left 呼出距离 invisible in the preview.
        float maxTravel = Math.max(10, Math.min(100, config.edgeTravelDp)) * density;
        float travel = maxTravel * (0.42f + 0.58f * pull);
        float iconScale = 0.86f + 0.14f * enter;
        float size = BackFxRenderer.iconSize(config, density);
        float iconCx = BackFxRenderer.iconCenterX(config, 0, w, travel, density, size);

        // The indicator rides the card's vertical centre, which is what "centred" means here.
        float cy = cardTop + cardH / 2f + (float) Math.sin(timeSec * 1.3f) * 6f * density;
        float demoFingerX = Math.min(
                w - 20f * density,
                30f * density + travel
                        + (0.5f + 0.5f * (float) Math.sin(timeSec * 1.3f)) * 70f * density);
        motion.update(iconCx, cy, demoFingerX, cy, dt, now, config.iconEffect, config.trackEffect);

        // The demo finger is a sine wave, so its slope is the velocity a real gesture would report. Passing it through
        // is what lets 弹性 bend the icon in the preview as it does under a finger; with 0f the two settings looked
        // the same.
        float demoVelocity = (float) (
                Math.cos(timeSec * 1.3f) * 0.65f * 70f * density * 1.3f * 1000f);

        try {
            BackFxRenderer.draw(canvas, config, 0, w, cy, travel, density, enter, iconScale,
                    timeSec, demoVelocity, motion, now);
        } catch (Throwable t) {
            android.util.Log.w("BackFxPreview", "draw failed", t);
        }

        boolean animated = !BackFxConfig.EFFECT_NONE.equals(config.iconEffect)
                || config.glow
                || BackFxConfig.ICON_IMAGE.equals(config.icon);
        if ((animated || pull > 0f) && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    /** Draws a bitmap centre-cropped to fill a rect, so nothing is ever stretched. */
    private void drawFill(Canvas canvas, Bitmap bmp, float w, float h, Paint paint) {
        dstRect.set(0f, 0f, w, h);
        drawFill(canvas, bmp, dstRect, paint);
    }

    private void drawFill(Canvas canvas, Bitmap bmp, RectF target, Paint paint) {
        float targetAspect = target.width() / target.height();
        float bmpAspect = bmp.getWidth() / (float) bmp.getHeight();
        if (bmpAspect > targetAspect) {
            // Bitmap is wider: crop the sides.
            int cropW = (int) (bmp.getHeight() * targetAspect);
            int left = (bmp.getWidth() - cropW) / 2;
            srcRect.set(left, 0, left + cropW, bmp.getHeight());
        } else {
            int cropH = (int) (bmp.getWidth() / targetAspect);
            int top = (bmp.getHeight() - cropH) / 2;
            srcRect.set(0, top, bmp.getWidth(), top + cropH);
        }
        canvas.drawBitmap(bmp, srcRect, target, paint);
    }
}
