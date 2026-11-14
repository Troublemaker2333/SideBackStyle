# 侧滑返回样式 · SideBackStyle

给 ColorOS 侧滑返回手势换一套「图标 + 背景」的 LSPosed 模块。

这是从「君君工具箱」里独立出来的单功能版本：只保留侧滑返回美化，代码、配置、构建完全独立，和工具箱互不影响。

> 适用：ColorOS 16 / 17（Android 16 / 17），SystemUI 使用 `com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector` 的机型。
> 已在 OPPO Find X8 Ultra（PKU110，ColorOS 17 / Android 17）实机验证。

---

## 截图

| 液态玻璃 + 拖尾 | 图标「透明」 | 箭头跟随方向 |
| --- | --- | --- |
| ![glass](docs/screenshots/glass.jpg) | ![transparent](docs/screenshots/transparent.jpg) | ![arrow](docs/screenshots/gesture-arrow.jpg) |

| 设置页 | 拖尾过程动效 | 右缘滑动 |
| --- | --- | --- |
| ![settings](docs/screenshots/settings.jpg) | ![trail](docs/screenshots/trail.jpg) | ![right](docs/screenshots/gesture-right.jpg) |

---

## 功能

- **图标**：箭头、圆点、爱心、星星、闪电、猫爪、自定义图片（PNG / WebP / GIF）、透明
- **颜色**：8 个预设 + HSV 拾色器（色相/饱和度/明度、HEX 输入）
- **尺寸**：0–100% 滑杆，映射 8–72dp；支持图标偏移（最多到屏幕中线）与左右镜像
- **背景**：系统原样 / 纯色 / 线性或径向或扫描渐变（角度、三色、中间色位置）/ 液态玻璃 / 无
- **液态玻璃**：半透明着色 + 磨砂噪点 + 顶部高光 + 底部折射 + 双层高光描边 + 柔和投影，磨砂强度 0–100%
- **过程动效**（滑动过程中可见，不是待机循环）：拖尾、粒子、残影、弹性、波纹
- **实时预览**：设置页直接预览，可固定预览卡片不随列表滚动
- **配置通道**：`Settings.Global` 的 `junjun_sideback`，SystemUI 内 700ms 缓存读取，改完下一次手势即生效，无需重启

## 兼容性

| 项目 | 说明 |
| --- | --- |
| 系统 | ColorOS 16 / 17（Android 16 / 17） |
| 框架 | LSPosed（需启用模块并勾选作用域「系统界面」） |
| 权限 | `WRITE_SECURE_SETTINGS`（用 adb 授权一次） |
| 已验证机型 | OPPO Find X8 Ultra（PKU110），ColorOS 17 |
| 其他 ROM | 类路径不同则不生效，可自行修改 `BackFxHook` 里的类名适配 |

## 安装

1. 安装 APK：`adb install -r SideBackStyle-1.0.apk`
   如果系统安装器拦截 adb 安装（部分 ColorOS），改用 root 安装：
   `adb shell su -c 'pm install -r /data/local/tmp/SideBackStyle-1.0.apk'`
2. 在 LSPosed 中启用「侧滑返回样式」，作用域勾选 **系统界面**（`com.android.systemui`）
3. 授予写系统设置权限（否则设置无法下发）：

   ```bash
   adb shell pm grant com.junjun.sideback android.permission.WRITE_SECURE_SETTINGS
   ```

4. 重启 SystemUI 让模块加载：

   ```bash
   adb shell su -c 'killall com.android.systemui'
   ```

   （没有 root 的话重启手机也可以）

5. 打开「侧滑返回样式」开始配置

## 使用说明

1. 顶部「启用自定义样式」打开后，从屏幕左/右边缘向内滑动即可看到效果
2. 改动会即时下发；如果没变化，确认 LSPosed 作用域是否勾了「系统界面」
3. 「图标 → 透明」只保留背景水滴，不画图标
4. 自定义图片会压缩到 128px 存进系统设置（约 30KB），GIF 会按时间逐帧播放
5. 液态玻璃是**自绘磨砂**，不是系统 backdrop blur——全屏悬浮窗开系统模糊会把整块屏幕都糊掉
6. 想恢复系统原样：关掉「启用自定义样式」，或点底部「恢复默认样式」

## 构建

不依赖 Gradle，直接用 Android SDK 的 `aapt2 / d8 / zipalign / apksigner`：

```bash
# 需要：JDK 17、Android SDK（build-tools 35.0.0、platforms/android-35）
export ANDROID_SDK_ROOT=/path/to/android-sdk
python build.py
# 产物：dist/SideBackStyle-1.0.apk
```

`build.py` 会自动复制源码到临时目录、编译资源与 Java、打包 dex、对齐并签名（默认用 `~/.android/debug.keystore`）。

## 目录结构

```
SideBackStyle/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/xposed_init              # LSPosed 入口
│   ├── java/com/junjun/sideback/
│   │   ├── BackFxActivity.java         # 设置页（唯一 Activity）
│   │   ├── BackFxConfig.java           # 配置模型 + Settings.Global 读写
│   │   ├── BackFxHook.java             # 侧滑手势 hook
│   │   ├── BackFxOverlay.java          # 悬浮窗与帧循环
│   │   ├── BackFxRenderer.java         # 水滴背景 + 图标绘制
│   │   ├── BackFxMotion.java           # 拖尾/粒子/残影/波纹
│   │   ├── ColorPickerDialog.java      # HSV 拾色器
│   │   ├── MiuixSwitch.java            # COUI 风格开关
│   │   ├── PageAnim.java
│   │   └── PredictiveBack.java
│   └── res/
├── vendor/api-82.jar                   # LSPosed API 93 存根
├── build.py
├── gen_icon.py
└── dist/SideBackStyle-1.0.apk
```

## 与君君工具箱的关系

- 本仓库是**独立复制**，君君工具箱里的原功能不会被删除
- 两者使用不同的配置键：工具箱 `junjun_backfx`，本模块 `junjun_sideback`
- 两个模块都 hook 同一个 SystemUI 方法，**不要同时启用**，否则会画两层指示器

## 已知限制

- ColorOS 17 的原生侧滑指示器 `SideGestureNavView` 一直是 `GONE`，所以模块自己画悬浮窗；如果以后 ROM 开始绘制原生图标，模块会在自定义样式开启时把它吞掉
- 液态玻璃没有真实背景模糊（原因见上）
- 不同 ColorOS 版本的类路径/字段可能变化，需要对应修改 `BackFxHook` 与 `BackFxOverlay`

## 免责声明

本模块通过 LSPosed 修改 SystemUI 的侧滑返回指示器绘制，仅供个人学习与美化使用。

不同 ROM / 系统版本可能存在兼容性差异，使用前请自行备份重要数据；因使用本模块导致的系统异常、界面错乱或数据丢失，由使用者自行承担。

请勿将本模块用于商业分发；转载请注明出处并保留本声明。

## License

[MIT](LICENSE) © 2026 Jun
