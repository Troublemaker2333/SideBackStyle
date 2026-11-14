import pathlib

p = pathlib.Path('app/src/main/java/com/junjun/sideback/BackFxConfig.java')
s = p.read_text(encoding='utf-8')

s = s.replace('    public static final String PREFS = "backfx";\n'
              '    public static final String SETTINGS_KEY = "junjun_sideback";',
              '    public static final String PACKAGE = "com.junjun.sideback";\n'
              '    public static final String PREFS = "backfx";\n'
              '    public static final String PREF_KEY = "config";\n'
              '    /** 仅用于从旧版本迁移，新版本不再写入。 */\n'
              '    public static final String SETTINGS_KEY = "junjun_sideback";')

old_load = '''    public static BackFxConfig load(Context ctx) {
        try {
            String raw = Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
            if (raw != null && !raw.isEmpty()) {
                return fromJson(raw);
            }
        } catch (Throwable t) {
            Log.e(TAG, "load failed", t);
        }
        return new BackFxConfig();
    }'''
new_load = '''    public static BackFxConfig load(Context ctx) {
        try {
            String raw = readStored(ctx);
            if (raw != null && !raw.isEmpty()) {
                return fromJson(raw);
            }
        } catch (Throwable t) {
            Log.e(TAG, "load failed", t);
        }
        return new BackFxConfig();
    }

    /**
     * 配置读取：
     * <ul>
     *   <li>SystemUI 进程（被注入）：XSharedPreferences 直接读模块的 prefs 文件</li>
     *   <li>App 进程：自己的 SharedPreferences；旧版本 Settings.Global 自动迁移一次</li>
     * </ul>
     * 两条路都不需要 WRITE_SECURE_SETTINGS。
     */
    static String readStored(Context ctx) {
        boolean hooked = false;
        try {
            Class.forName("de.robv.android.xposed.XSharedPreferences");
            hooked = true;
        } catch (Throwable ignored) {
        }
        if (hooked) {
            try {
                String raw = XPrefs.read();
                if (raw != null && !raw.isEmpty()) {
                    return raw;
                }
            } catch (Throwable t) {
                Log.e(TAG, "xprefs read failed", t);
            }
        }
        if (!hooked && ctx != null) {
            try {
                String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(PREF_KEY, null);
                if (raw != null && !raw.isEmpty()) {
                    return raw;
                }
            } catch (Throwable t) {
                Log.e(TAG, "prefs read failed", t);
            }
            try {
                String legacy = Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
                if (legacy != null && !legacy.isEmpty()) {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(PREF_KEY, legacy).apply();
                    return legacy;
                }
            } catch (Throwable ignored) {
            }
        }
        if (ctx != null) {
            try {
                String legacy = Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
                if (legacy != null && !legacy.isEmpty()) {
                    return legacy;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }'''
if old_load not in s:
    raise SystemExit('load block not found')
s = s.replace(old_load, new_load)

old_publish = '''    /** 写入 Settings.Global，SystemUI 侧 700ms 内生效。 */
    public static boolean publish(Context ctx, BackFxConfig c) {
        try {
            Settings.Global.putString(ctx.getContentResolver(), SETTINGS_KEY, c.toJson());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "publish failed", t);
            return false;
        }
    }'''
new_publish = '''    /** 写入模块自己的 SharedPreferences，SystemUI 侧 700ms 内生效；不需要任何权限。 */
    public static boolean publish(Context ctx, BackFxConfig c) {
        String json = c.toJson();
        boolean ok = false;
        try {
            ok = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_KEY, json).commit();
        } catch (Throwable t) {
            Log.e(TAG, "publish prefs failed", t);
        }
        // 已授权 WRITE_SECURE_SETTINGS 的旧安装仍写一份，方便回退；没权限就跳过。
        try {
            if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putString(ctx.getContentResolver(), SETTINGS_KEY, json);
            }
        } catch (Throwable ignored) {
        }
        return ok;
    }'''
if old_publish not in s:
    raise SystemExit('publish block not found')
s = s.replace(old_publish, new_publish)

# fetch: use readStored
old_fetch = '''        try {
            String raw = Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
            if (raw != null && !raw.isEmpty()) {
                cache = fromJson(raw);
                cacheAt = now;
                return cache;
            }
        } catch (Throwable ignored) {
        }
        return cache;'''
new_fetch = '''        try {
            String raw = readStored(ctx);
            if (raw != null && !raw.isEmpty()) {
                cache = fromJson(raw);
                cacheAt = now;
                return cache;
            }
        } catch (Throwable ignored) {
        }
        return cache;'''
if old_fetch not in s:
    raise SystemExit('fetch block not found')
s = s.replace(old_fetch, new_fetch)

p.write_text(s, encoding='utf-8')
print('config transport switched to prefs')
