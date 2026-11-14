package com.junjun.sideback;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed 入口：只挂 SystemUI。 */
public class SideBackEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null) {
            return;
        }
        try {
            if ("com.android.systemui".equals(lpparam.packageName)) {
                BackFxHook.bootstrap(lpparam.classLoader);
            }
        } catch (Throwable t) {
            Log.e("SideBackFx", "bootstrap failed for " + lpparam.packageName, t);
        }
    }
}
