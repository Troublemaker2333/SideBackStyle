import pathlib

p = pathlib.Path('app/src/main/java/com/junjun/sideback/BackFxConfig.java')
s = p.read_text(encoding='utf-8')

start = s.index('    static String readStored(Context ctx) {')
end = s.index('    public boolean save(Context ctx)')
new_block = '''    static String readStored(Context ctx) {
        if (ctx == null) {
            return null;
        }
        if (PACKAGE.equals(ctx.getPackageName())) {
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
            return null;
        }
        // SystemUI 进程：通过只读 ContentProvider 拿配置，App 不需要任何权限。
        try {
            android.os.Bundle result = ctx.getContentResolver().call(
                    android.net.Uri.parse("content://" + ConfigProvider.AUTHORITY),
                    ConfigProvider.METHOD_GET, null, null);
            if (result != null) {
                String raw = result.getString(ConfigProvider.KEY_JSON);
                if (raw != null && !raw.isEmpty()) {
                    return raw;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "provider read failed", t);
        }
        // 旧版本兜底
        try {
            String legacy = Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
            if (legacy != null && !legacy.isEmpty()) {
                return legacy;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

'''
s = s[:start] + new_block + s[end:]
p.write_text(s, encoding='utf-8')

# delete XPrefs
xp = pathlib.Path('app/src/main/java/com/junjun/sideback/XPrefs.java')
if xp.exists():
    xp.unlink()

# manifest: remove xposedsharedprefs, add provider
m = pathlib.Path('app/src/main/AndroidManifest.xml')
t = m.read_text(encoding='utf-8')
t = t.replace('        <meta-data android:name="xposedsharedprefs" android:value="true" />\n', '')
t = t.replace('''        <meta-data android:name="xposedmodule" android:value="true" />''',
              '''        <provider
            android:name=".ConfigProvider"
            android:authorities="com.junjun.sideback.config"
            android:exported="true" />

        <meta-data android:name="xposedmodule" android:value="true" />''')
m.write_text(t, encoding='utf-8')
print('provider transport patched')
