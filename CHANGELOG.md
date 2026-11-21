# Changelog

## 2.0 — 2026-11-21

设置页整体重写为 Compose（COUI 2.0），渲染层同步到最新。

**界面**

- 设置页改用 Compose + COUI，分七组：开关和恢复 / 图标形状 / 图标尺寸 / 图标特效 / 背景形状 / 背景大小 / 背景特效
- 顶部实时预览，带演示手势，可固定或跟随滚动
- 标题下加一行使用说明：勾选「系统界面」生效、改动免重启
- 颜色改成「色块 + 选取」，点开取色器，不再平铺一排预设
- 选「无」图标或「无」背景时，把用不上的选项隐藏起来

**图标**

- 图标形状新增「无」（原「透明」）
- 图标动效（残影 / 拉伸）与跟手动效（拖尾 / 粒子 / 涟漪）拆成两项，**可以同时设置**
- 涟漪改名为「涟漪（拖尾+粒子）」—— 它本来就把两者的效果都画出来

**水滴**

- 水滴大小、水滴圆润度、呼出距离都改为滑杆连续可调
- 背景形状去掉与「纯色」渲染结果完全相同的「系统」选项
- 渐变补全径向 / 扫描两种类型，并支持三色渐变

**修复**

- 取色器：拖动色相条后取色板不刷新
- 取色器：点确定后挑好的颜色被丢弃
- 预览的呼出距离偏大（把卡片位移算进了水滴伸出距离）
- 恢复默认样式漏项：光晕没重置、水滴几何与渐变停靠点不重置
- 高光描边默认值改为关闭

**构建**

- `build.py` 退场，改用 Gradle（AGP 8.10.1 / Kotlin 2.4.21 / compileSdk 37，COUI 2.0.0 的要求）
- 补回 `META-INF/xposed/` 下的 `java_init.list` / `module.prop` / `scope.list` —— 少了这三个文件
  LSPosed 里看不到模块
- 仓库不再附带旧版 APK，改由 Releases 与 CI 产物提供

## 1.2 — 2026-11-14

- 迁移到 **libxposed 新版 API**（`META-INF/xposed/java_init.list` + `module.prop` + `scope.list`），修复 legacy 模块在 LSPosed 2.2.1 / Android 17 上不被加载的问题
- 配置通道改为只读 `ContentProvider`：App 写 SharedPreferences，SystemUI 直接查询，**不需要任何权限**
- 安装后不再需要 `adb shell pm grant ... WRITE_SECURE_SETTINGS`
- 旧版 `Settings.Global` 配置保留一次性迁移兜底
- 1.1 未发布，直接以 1.2 发布

## 1.1 — 2026-11-14

- 配置从 `Settings.Global` 改为模块自己的 SharedPreferences，通过 LSPosed `xposedsharedprefs` 共享给 SystemUI
- 安装后**不再需要** `adb shell pm grant ... WRITE_SECURE_SETTINGS`，装好 + 启用模块 + 重启 SystemUI 即可
- 移除 `WRITE_SECURE_SETTINGS` 权限声明
- 旧版本写在 `junjun_sideback` 的配置会在首次打开时自动迁移
- 设置页底部更新使用说明与版本号

## 1.0 — 2026-11-14

首个独立版本：

- 图标：箭头 / 圆点 / 爱心 / 星星 / 闪电 / 猫爪 / 自定义图片（PNG、WebP、GIF）/ 透明
- HSV 拾色器与 8 个预设色
- 图标大小 8–72dp、偏移、右侧镜像
- 背景：系统 / 纯色 / 渐变（线性、径向、扫描）/ 液态玻璃 / 无
- 液态玻璃：磨砂噪点、高光、折射、双层描边、柔和投影，强度可调
- 过程动效：拖尾、粒子、残影、弹性、波纹
- 实时预览 + 固定预览
- 独立配置键 `junjun_sideback`，与其他模块互不影响
