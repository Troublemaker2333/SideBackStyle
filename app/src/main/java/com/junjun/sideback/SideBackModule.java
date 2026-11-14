package com.junjun.sideback;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** LSPosed 新版 API 入口：只挂 SystemUI。 */
public final class SideBackModule extends XposedModule {

    private static final String TAG = "SideBackFx";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (param == null || !"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        try {
            new BackFxHook(this, param.getClassLoader()).install();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "bootstrap failed", t);
        }
    }
}
