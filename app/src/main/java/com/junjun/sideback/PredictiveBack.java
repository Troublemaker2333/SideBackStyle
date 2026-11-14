package com.junjun.sideback;

import android.app.Activity;
import android.graphics.Outline;
import android.os.Build;
import android.view.Display;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * c001apk card-stack back gesture: the page is dragged to the right edge by the system gesture
 * progress, clipped to the display's corner radius, revealing the shell underneath. Releasing
 * early settles back in 200 ms; completing continues the travel and finishes the page.
 */
public final class PredictiveBack {

    private static final int SETTLE_MILLIS = 200;

    private PredictiveBack() {
    }

    public static void install(final Activity activity, final View page) {
        if (page == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        final float cornerRadius = displayCornerRadius(activity);
        final float[] radius = {0f};
        try {
            page.setClipToOutline(true);
            page.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius[0]);
                }
            });

            final Runnable reset = () -> {
                page.animate().cancel();
                page.animate()
                        .translationX(0f)
                        .setDuration(SETTLE_MILLIS)
                        .setInterpolator(PageAnim.moveCurve())
                        .start();
                radius[0] = 0f;
                page.invalidateOutline();
            };

            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new OnBackAnimationCallback() {
                        @Override
                        public void onBackStarted(BackEvent event) {
                            page.animate().cancel();
                            radius[0] = cornerRadius;
                            page.invalidateOutline();
                        }

                        @Override
                        public void onBackProgressed(BackEvent event) {
                            float progress = Math.max(0f, Math.min(1f, event.getProgress()));
                            page.setTranslationX(page.getWidth() * progress);
                        }

                        @Override
                        public void onBackCancelled() {
                            reset.run();
                        }

                        @Override
                        public void onBackInvoked() {
                            page.animate().cancel();
                            page.animate()
                                    .translationX(page.getWidth())
                                    .setDuration(SETTLE_MILLIS)
                                    .setInterpolator(PageAnim.moveCurve())
                                    .withEndAction(activity::finish)
                                    .start();
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static float displayCornerRadius(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Display display = activity.getDisplay();
                if (display != null) {
                    RoundedCorner corner =
                            display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    if (corner != null) {
                        return Math.max(corner.getRadius(), 24f * activity.getResources()
                                .getDisplayMetrics().density);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 24f * activity.getResources().getDisplayMetrics().density;
    }
}
