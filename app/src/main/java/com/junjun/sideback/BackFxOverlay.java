package com.junjun.sideback;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

/**
 * 侧滑返回指示器悬浮层，运行在 SystemUI 进程。
 *
 * <p>ColorOS 17 的侧滑返回本身不绘制任何指示器（{@code SideGestureNavView} 一直没有真正显示），
 * 所以这里跟随 {@code SideGestureDetector} 的触摸事件自己画一个：背景是贴边的贝塞尔"水滴"，
 * 图标由设置页决定，位置跟随手指、松手淡出。</p>
 */
public final class BackFxOverlay {
    private static final String TAG = "SideBackFx";

    private static volatile BackFxOverlay instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WindowManager windowManager;
    private final float density;

    private BackFxView view;
    private boolean added;

    private boolean tracking;
    private boolean showing;
    private int side;
    private float downX;
    private float downY;
    private float fingerY;
    private float fingerX;
    private float travel;
    private float enter;
    private float velocityX;
    private float lastMoveX;
    private long lastMoveTime;
    private long showStart;
    private String effect = BackFxConfig.EFFECT_NONE;

    private ValueAnimator animator;
    private boolean frameRunning;

    private static volatile boolean threeButtonCache;
    private static volatile long threeButtonAt = -10_000L;

    /** 手势事件断流时兜底收起，避免停留在屏幕上。 */
    private final Runnable idleTimeout = () -> {
        tracking = false;
        stop();
    };

    private BackFxOverlay(Context ctx) {
        this.context = ctx;
        this.windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public static BackFxOverlay get(Context ctx) {
        BackFxOverlay o = instance;
        if (o == null) {
            synchronized (BackFxOverlay.class) {
                o = instance;
                if (o == null) {
                    o = new BackFxOverlay(ctx);
                    instance = o;
                }
            }
        }
        return o;
    }

    // ------------------------------------------------------------ event entry

    /** 由 hook 在 SystemUI 的任意线程调用。 */
    public void onMotionEvent(MotionEvent event, Context ctx) {
        if (event == null || ctx == null) {
            return;
        }
        BackFxConfig cfg = BackFxConfig.fetch(ctx);
        if (cfg == null || !cfg.enabled) {
            if (tracking) {
                tracking = false;
                stop();
            }
            return;
        }
        if (event.getPointerCount() != 1) {
            return;
        }
        if (isThreeButtonNav(ctx)) {
            return;
        }
        int action = event.getActionMasked();
        final float x = event.getX();
        final float y = event.getY();
        final float screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        main.removeCallbacks(idleTimeout);
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            main.postDelayed(idleTimeout, 1200L);
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float edge = 30f * density;
                boolean atEdge = x <= edge || x >= screenW - edge;
                tracking = atEdge && y > 40f * density;
                if (tracking) {
                    side = x <= screenW / 2f ? 0 : 1;
                    downX = x;
                    downY = y;
                    fingerX = x;
                    fingerY = y;
                    travel = 0f;
                    velocityX = 0f;
                    lastMoveX = x;
                    lastMoveTime = 0L;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) {
                    return;
                }
                float dx = Math.abs(x - downX);
                float dy = Math.abs(y - downY);
                if (dx < 4f * density && travel <= 0f) {
                    return;
                }
                if (dy > dx * 1.6f && travel <= 0f) {
                    tracking = false;
                    return;
                }
                float max = 56f * density;
                travel = Math.min(dx, max);
                long eventTime = event.getEventTime();
                if (lastMoveTime > 0L) {
                    float dt = (eventTime - lastMoveTime) / 1000f;
                    if (dt > 0.001f && dt < 0.25f) {
                        float instant = (x - lastMoveX) / dt;
                        velocityX = velocityX * 0.55f + instant * 0.45f;
                    }
                }
                lastMoveX = x;
                lastMoveTime = eventTime;
                fingerX = x;
                fingerY = y;
                if (travel > 0f) {
                    show(cfg);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!tracking) {
                    return;
                }
                tracking = false;
                stop();
                break;
            }
            default:
                break;
        }
    }

    private boolean isThreeButtonNav(Context ctx) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - threeButtonAt < 5000L) {
            return threeButtonCache;
        }
        try {
            boolean three = Settings.Secure.getInt(ctx.getContentResolver(), "navigation_mode", 2) != 2;
            threeButtonCache = three;
            threeButtonAt = now;
            return three;
        } catch (Throwable t) {
            return threeButtonCache;
        }
    }

    // ------------------------------------------------------------ window

    private void show(final BackFxConfig cfg) {
        main.post(() -> {
            try {
                ensureView();
                if (view == null) {
                    return;
                }
                view.setConfig(cfg);
                effect = cfg.iconEffect;
                view.setFinger(fingerX, fingerY);
                view.setGeometry(side, fingerY, travel);
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                }
                if (!showing) {
                    showing = true;
                    showStart = android.os.SystemClock.uptimeMillis();
                    if (view != null) {
                        view.resetMotion(fingerY);
                    }
                    startEnter();
                    frameRunning = false;
                    startFrames();
                }
                view.invalidate();
            } catch (Throwable t) {
                Log.e(TAG, "show failed", t);
            }
        });
    }

    private void stop() {
        main.post(() -> {
            if (!showing) {
                return;
            }
            showing = false;
            if (view == null) {
                return;
            }
            if (animator != null) {
                animator.cancel();
            }
            if (view != null) {
                view.releaseMotion();
            }
            long duration = BackFxConfig.EFFECT_RIPPLE.equals(effect) ? 320L : 150L;
            animator = ValueAnimator.ofFloat(enter, 0f);
            animator.setDuration(duration);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                enter = (float) a.getAnimatedValue();
                if (view != null) {
                    view.invalidate();
                }
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (view != null) {
                        view.setVisibility(View.GONE);
                    }
                    frameRunning = false;
                }
            });
            animator.start();
        });
    }

    private void startEnter() {
        if (animator != null) {
            animator.cancel();
        }
        enter = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(120L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            enter = (float) a.getAnimatedValue();
            if (view != null) {
                view.invalidate();
            }
        });
        animator.start();
    }

    /** 可见期间按 vsync 重绘，驱动时间动效与循环动图。 */
    private void startFrames() {
        if (view == null || frameRunning) {
            return;
        }
        frameRunning = true;
        view.postOnAnimation(frameTick);
    }

    private final Runnable frameTick = new Runnable() {
        @Override
        public void run() {
            if (view != null && view.getVisibility() == View.VISIBLE) {
                view.invalidate();
                view.postOnAnimation(this);
            } else {
                frameRunning = false;
            }
        }
    };

    private void ensureView() {
        if (view != null) {
            if (!added) {
                try {
                    windowManager.addView(view, buildParams());
                    added = true;
                } catch (Throwable t) {
                    Log.e(TAG, "addView failed", t);
                    view = null;
                }
            }
            return;
        }
        try {
            view = new BackFxView(context);
            view.setVisibility(View.GONE);
            windowManager.addView(view, buildParams());
            added = true;
        } catch (Throwable t) {
            Log.e(TAG, "create overlay failed", t);
            view = null;
            added = false;
        }
    }

    private WindowManager.LayoutParams buildParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = 2024;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(lp);
            } catch (Throwable ignored) {
            }
        }
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.x = 0;
        lp.y = 0;
        lp.windowAnimations = 0;
        try {
            WindowManager.LayoutParams.class.getField("title").set(lp, "SideBackFx");
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return lp;
    }

    // ------------------------------------------------------------ view

    private final class BackFxView extends View {
        private BackFxConfig cfg;
        private int drawSide;
        private float targetY;
        private float displayY;
        private float drawTravel;
        private long lastDrawTime;
        private float fingerX;
        private float fingerY;
        private final BackFxMotion motion = new BackFxMotion();

        BackFxView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
        }

        void setConfig(BackFxConfig c) {
            this.cfg = c;
        }

        void setFinger(float x, float y) {
            this.fingerX = x;
            this.fingerY = y;
        }

        void resetMotion(float y) {
            targetY = y;
            displayY = y;
            lastDrawTime = 0L;
            motion.reset();
        }

        void releaseMotion() {
            long now = android.os.SystemClock.uptimeMillis();
            float size = BackFxRenderer.iconSize(cfg, density);
            float cx = BackFxRenderer.iconCenterX(cfg, drawSide, getWidth(), drawTravel,
                    density, size);
            motion.release(cx, displayY, cfg.iconEffect, now);
        }

        void setGeometry(int side, float y, float travel) {
            this.drawSide = side;
            this.targetY = y;
            this.drawTravel = travel;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (cfg == null) {
                return;
            }
            try {
                long now = android.os.SystemClock.uptimeMillis();
                float dt = lastDrawTime == 0L ? 0.016f
                        : Math.min(0.05f, (now - lastDrawTime) / 1000f);
                lastDrawTime = now;
                if (cfg.elastic || BackFxConfig.EFFECT_STRETCH.equals(cfg.iconEffect)) {
                    displayY += (targetY - displayY) * Math.min(1f, dt * 16f);
                } else {
                    displayY = targetY;
                }
                float timeSec = (now - showStart) / 1000f;
                float scale = 0.86f + 0.14f * enter;
                float size = BackFxRenderer.iconSize(cfg, density);
                float cx = BackFxRenderer.iconCenterX(cfg, drawSide, getWidth(), drawTravel,
                        density, size);
                motion.update(cx, displayY, fingerX, fingerY, dt, now, cfg.iconEffect);
                BackFxRenderer.draw(canvas, cfg, drawSide, getWidth(), displayY, drawTravel,
                        density, enter, scale, timeSec, velocityX, motion, now);
            } catch (Throwable t) {
                Log.e(TAG, "draw failed", t);
            }
        }
    }
}
