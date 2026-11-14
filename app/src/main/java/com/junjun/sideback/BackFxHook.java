package com.junjun.sideback;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * ColorOS 17 侧滑返回 hook（libxposed 新版 API）。
 *
 * <p>入口是 {@code com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector
 * #onMotionEventImpl}：所有贴边滑动都会经过这里。指示器由模块自己绘制，
 * 因此不依赖 {@code SideGestureNavView} 是否可见。</p>
 */
final class BackFxHook {

    private static final String TAG = "SideBackFx";
    private static final String CLS_DETECTOR =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector";
    private static final String CLS_NAV_VIEW =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureNavView";

    private final XposedInterface xp;
    private final ClassLoader cl;

    BackFxHook(XposedInterface xp, ClassLoader cl) {
        this.xp = xp;
        this.cl = cl;
    }

    void install() throws Throwable {
        Class<?> detector = Class.forName(CLS_DETECTOR, false, cl);
        int count = 0;
        for (Method method : detector.getDeclaredMethods()) {
            if (!"onMotionEventImpl".equals(method.getName())
                    || method.getParameterTypes().length < 1
                    || !MotionEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            try {
                xp.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    dispatch(chain);
                    return result;
                });
                count++;
            } catch (Throwable t) {
                xp.log(Log.ERROR, TAG, "hook failed: " + method, t);
            }
        }
        xp.log(Log.INFO, TAG, "hooked SideGestureDetector.onMotionEventImpl x" + count);

        // 若系统以后开始绘制原生指示器，开启自定义样式时把它吞掉，避免两个图标叠加。
        try {
            Class<?> navView = Class.forName(CLS_NAV_VIEW, false, cl);
            for (Method method : navView.getDeclaredMethods()) {
                if (!"onDraw".equals(method.getName())) {
                    continue;
                }
                xp.hook(method).intercept(chain -> {
                    try {
                        Context ctx = ((View) chain.getThisObject()).getContext();
                        BackFxConfig cfg = BackFxConfig.fetch(ctx);
                        if (cfg != null && cfg.enabled) {
                            return null;
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private void dispatch(XposedInterface.Chain chain) {
        try {
            MotionEvent event = (MotionEvent) chain.getArg(0);
            Object self = chain.getThisObject();
            Field field = self.getClass().getDeclaredField("mContext");
            field.setAccessible(true);
            Context ctx = (Context) field.get(self);
            if (ctx != null && event != null) {
                BackFxOverlay.get(ctx).onMotionEvent(event, ctx);
            }
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "dispatch failed", t);
        }
    }
}
