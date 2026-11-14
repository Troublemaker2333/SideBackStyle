import pathlib
from PIL import Image

# screenshot refresh
src = pathlib.Path(r'..\sb_provider.png')
im = Image.open(src).convert('RGB')
w, h = im.size
im.resize((900, int(h * 900 / w)), Image.LANCZOS).save(
    'docs/screenshots/glass.jpg', quality=88, optimize=True)

readme = pathlib.Path('README.md')
s = readme.read_text(encoding='utf-8')
s = s.replace('SideBackStyle-1.1.apk', 'SideBackStyle-1.2.apk')
s = s.replace('''**不需要授予 `WRITE_SECURE_SETTINGS`**：配置写在模块自己的 SharedPreferences 里，通过 LSPosed 的 `xposedsharedprefs` 共享给 SystemUI 读取。装好、启用、重启 SystemUI 即可。''',
'''**不需要授予任何权限**：配置写在模块自己的 SharedPreferences 里，SystemUI 通过只读 `ContentProvider` 读取。装好、在 LSPosed 启用、重启一次手机即可。''')
s = s.replace('''- **配置通道**：模块自己的 SharedPreferences（LSPosed `xposedsharedprefs` 共享），SystemUI 内 700ms 缓存读取，改完下一次手势即生效，无需重启''',
'''- **配置通道**：模块自己的 SharedPreferences + 只读 ContentProvider（`com.junjun.sideback.config`），SystemUI 内 700ms 缓存，改完下一次手势即生效''')
s = s.replace('| 框架 | LSPosed（需启用模块并勾选作用域「系统界面」） |',
              '| 框架 | LSPosed 2.x（libxposed API 100+，需启用模块并勾选作用域「系统界面」） |')
s = s.replace('| 权限 | 不需要额外权限（1.1 起改用 SharedPreferences，旧的 `WRITE_SECURE_SETTINGS` 已移除） |',
              '| 权限 | 不需要任何权限（不需要 adb grant，也不需要 root 授权） |')
s = s.replace('''3. 重启 SystemUI 让模块加载：

   ```bash
   adb shell su -c 'killall com.android.systemui'
   ```

   （没有 root 的话重启手机也可以）''',
'''3. 重启一次手机，让 LSPosed 重新扫描并加载模块（新装模块必须重启一次，之后升级/改设置都不需要）''')
s = s.replace('''│   │   ├── BackFxHook.java             # 侧滑手势 hook''',
'''│   │   ├── SideBackModule.java         # libxposed 新版入口
│   │   ├── BackFxHook.java             # 侧滑手势 hook''')
s = s.replace('''│   │   ├── BackFxConfig.java           # 配置模型 + SharedPreferences 读写''',
'''│   │   ├── BackFxConfig.java           # 配置模型 + SharedPreferences 读写
│   │   ├── ConfigProvider.java         # 只读配置 Provider''')
s = s.replace('├── vendor/api-82.jar                   # LSPosed API 93 存根',
              '├── vendor/libxposed-api.jar            # libxposed API 102')
s = s.replace('''- 配置写在模块自己的 SharedPreferences（`backfx`），通过 LSPosed `xposedsharedprefs` 共享给 SystemUI；如果同时启用了功能重复的模块，请只保留一个，避免画出两层指示器''',
'''- 如果同时启用了功能重复的模块，请只保留一个，避免画出两层指示器''')
s = s.replace('| 已验证机型 | OPPO Find X8 Ultra（PKU110），ColorOS 17 |',
              '| 已验证机型 | OPPO Find X8 Ultra（PKU110），ColorOS 17（LSPosed 2.2.1 / API 102） |')
readme.write_text(s, encoding='utf-8')

ch = pathlib.Path('CHANGELOG.md')
c = ch.read_text(encoding='utf-8')
entry = '''## 1.2 — 2026-11-14

- 迁移到 **libxposed 新版 API**（`META-INF/xposed/java_init.list` + `module.prop` + `scope.list`），修复 legacy 模块在 LSPosed 2.2.1 / Android 17 上不被加载的问题
- 配置通道改为只读 `ContentProvider`：App 写 SharedPreferences，SystemUI 直接查询，**不需要任何权限**
- 安装后不再需要 `adb shell pm grant ... WRITE_SECURE_SETTINGS`
- 旧版 `Settings.Global` 配置保留一次性迁移兜底
- 1.1 未发布，直接以 1.2 发布

'''
c = c.replace('# Changelog\n\n', '# Changelog\n\n' + entry)
ch.write_text(c, encoding='utf-8')

old = pathlib.Path('dist/SideBackStyle-1.1.apk')
if old.exists():
    old.unlink()
print('docs updated')
