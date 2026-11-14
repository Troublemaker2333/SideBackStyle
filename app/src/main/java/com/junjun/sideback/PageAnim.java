package com.junjun.sideback;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.animation.PathInterpolator;

/** c001apk route motion: pages slide in from the right edge (320 ms, COUI move curve). */
public final class PageAnim {

    public static final int SLIDE_MILLIS = 320;

    private PageAnim() {
    }

    public static PathInterpolator moveCurve() {
        return new PathInterpolator(0.3f, 0f, 0.1f, 1f);
    }

    /** Call right after setContentView: the page enters from the right edge. */
    public static void slideIn(final Activity activity, final View page) {
        if (page == null) {
            return;
        }
        page.post(() -> {
            page.setTranslationX(page.getWidth());
            page.animate()
                    .translationX(0f)
                    .setDuration(SLIDE_MILLIS)
                    .setInterpolator(moveCurve())
                    .start();
        });
    }
}
