import pathlib
import shutil

root = pathlib.Path('.')
java = root / 'app/src/main/java/com/junjun/sideback'

# 1) new entry
(java / 'SideBackModule.java').write_text('''package com.junjun.sideback;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** LSPosed 新版 API 入口：只挂 SystemUI。 */
public final class SideBackModule extends XposedModule {

    private static final String TAG = "SideBackFx";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (param == null || !"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        try {
            new BackFxHook(this, param.getDefaultClassLoader()).install();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "bootstrap failed", t);
        }
    }
}
''', encoding='utf-8')

# 2) new hook using libxposed API
(java / 'BackFxHook.java').write_text('''package com.junjun.sideback;

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
''', encoding='utf-8')

# 3) remove old entry + legacy asset
for name in ('SideBackEntry.java',):
    p = java / name
    if p.exists():
        p.unlink()
asset = root / 'app/src/main/assets/xposed_init'
if asset.exists():
    asset.unlink()

# 4) manifest: modern modules are detected by META-INF/xposed/java_init.list
mf = root / 'app/src/main/AndroidManifest.xml'
s = mf.read_text(encoding='utf-8')
start = s.find('        <meta-data android:name="xposedmodule"')
if start == -1:
    raise SystemExit('meta block not found')
end = s.find('    </application>')
s = s[:start] + s[end:]
mf.write_text(s, encoding='utf-8')

# 5) vendor api jar
shutil.copyfile('libxposed-api.jar', 'vendor/libxposed-api.jar')

# 6) build.py
bp = root / 'build.py'
s = bp.read_text(encoding='utf-8')
s = s.replace('XPOSED_ENTRY = "com.junjun.sideback.SideBackEntry"',
              'XPOSED_ENTRY = "com.junjun.sideback.SideBackModule"')
s = s.replace('VERSION_CODE = "2"', 'VERSION_CODE = "3"')
s = s.replace('VERSION_NAME = "1.1"', 'VERSION_NAME = "1.2"')
s = s.replace('xp_jar = WORK / "api-82.jar"', 'xp_jar = WORK / "libxposed-api.jar"')
s = s.replace('shutil.copyfile(VENDOR / "api-82.jar", xp_jar)',
              'shutil.copyfile(VENDOR / "libxposed-api.jar", xp_jar)')
s = s.replace('''        add(z, "classes.dex", (OUT / "dex" / "classes.dex").read_bytes(), zipfile.ZIP_DEFLATED)''',
'''        add(z, "classes.dex", (OUT / "dex" / "classes.dex").read_bytes(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/java_init.list", (XPOSED_ENTRY + "\\n").encode(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/module.prop", MODULE_PROP.encode(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/scope.list", b"com.android.systemui\\n", zipfile.ZIP_DEFLATED)''')
s = s.replace('''        for required in ("AndroidManifest.xml", "classes.dex", "resources.arsc", "assets/xposed_init"):''',
'''        for required in ("AndroidManifest.xml", "classes.dex", "resources.arsc",
                         "META-INF/xposed/java_init.list", "META-INF/xposed/module.prop"):''')
s = s.replace('''        entry = z.read("assets/xposed_init").decode("utf-8").strip()
        if entry != XPOSED_ENTRY:
            raise ValueError(f"bad xposed_init: {entry}")''',
'''        entry = z.read("META-INF/xposed/java_init.list").decode("utf-8").strip()
        if entry != XPOSED_ENTRY:
            raise ValueError(f"bad java_init.list: {entry}")''')
s = s.replace('XPOSED_ENTRY = "com.junjun.sideback.SideBackModule"',
              'XPOSED_ENTRY = "com.junjun.sideback.SideBackModule"\n'
              'MODULE_PROP = ("minApiVersion=100\\n"\n'
              '               "targetApiVersion=102\\n"\n'
              '               "staticScope=true\\n"\n'
              '               "name=SideBackStyle\\n"\n'
              '               "version=1.2\\n"\n'
              '               "versionCode=3\\n"\n'
              '               "author=Jun\\n"\n'
              '               "description=ColorOS 侧滑返回自定义图标与背景\\n")')
bp.write_text(s, encoding='utf-8')
print('modern module scaffold written')
