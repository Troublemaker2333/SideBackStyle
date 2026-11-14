# Changelog

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
