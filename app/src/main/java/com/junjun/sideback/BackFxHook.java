package com.junjun.sideback;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * ColorOS 17 侧滑返回 hook。
 *
 * <p>入口是 {@code com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector
 * #onMotionEventImpl}：所有贴边滑动都会经过这里。指示器由模块自己绘制，
 * 因此不依赖 {@code SideGestureNavView} 是否可见（在 ColorOS 17 上它始终保持 GONE）。</p>
 */
public final class BackFxHook {
    private static final String TAG = "SideBackFx";

    private static final String CLS_DETECTOR =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector";
    private static final String CLS_NAV_VIEW =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureNavView";

    private static volatile boolean installed;

    private BackFxHook() {
    }

    public static void bootstrap(ClassLoader cl) {
        if (installed) {
            return;
        }
        synchronized (BackFxHook.class) {
            if (installed) {
                return;
            }
            installed = true;
        }
        try {
            Class<?> detector = XposedHelpers.findClass(CLS_DETECTOR, cl);
            int count = 0;
            for (Method method : detector.getDeclaredMethods()) {
                if (!"onMotionEventImpl".equals(method.getName())
                        || method.getParameterTypes().length < 1
                        || !MotionEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!(param.args[0] instanceof MotionEvent)) {
                                return;
                            }
                            Context ctx = (Context) XposedHelpers.getObjectField(
                                    param.thisObject, "mContext");
                            if (ctx == null) {
                                return;
                            }
                            BackFxOverlay.get(ctx).onMotionEvent((MotionEvent) param.args[0], ctx);
                        } catch (Throwable t) {
                            Log.e(TAG, "dispatch failed", t);
                        }
                    }
                });
                count++;
            }
            Log.i(TAG, "hooked SideGestureDetector.onMotionEventImpl x" + count);

            // 若系统以后开始绘制原生指示器，开启自定义样式时把它吞掉，避免两个图标叠加。
            try {
                Class<?> navView = XposedHelpers.findClass(CLS_NAV_VIEW, cl);
                XposedBridge.hookAllMethods(navView, "onDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = ((android.view.View) param.thisObject).getContext();
                            BackFxConfig cfg = BackFxConfig.fetch(ctx);
                            if (cfg != null && cfg.enabled) {
                                param.setResult(null);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.e(TAG, "bootstrap failed", t);
        }
    }
}
