package com.junjun.sideback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

/**
 * 侧滑返回样式配置。
 *
 * <p>设置页写入 {@link Settings.Global}（模块持有 WRITE_SECURE_SETTINGS，一次 root 授权即可），
 * SystemUI 内的 hook 直接读同一个 key，全程无 IPC、不唤醒模块进程。</p>
 */
public final class BackFxConfig {
    private static final String TAG = "SideBackFx";

    public static final String PACKAGE = "com.junjun.sideback";
    public static final String PREFS = "backfx";
    public static final String PREF_KEY = "config";
    /** 仅用于从旧版本迁移，新版本不再写入。 */
    public static final String SETTINGS_KEY = "junjun_sideback";

    public static final String ICON_ARROW = "arrow";
    public static final String ICON_DOT = "dot";
    public static final String ICON_HEART = "heart";
    public static final String ICON_STAR = "star";
    public static final String ICON_BOLT = "bolt";
    public static final String ICON_PAW = "paw";
    public static final String ICON_IMAGE = "image";
    /** 透明：不绘制图标，只保留背景。 */
    public static final String ICON_NONE = "none";

    public static final String EFFECT_NONE = "none";
    /** 手势过程中留下彗星拖尾。 */
    public static final String EFFECT_TRAIL = "trail";
    /** 沿路径迸发粒子。 */
    public static final String EFFECT_PARTICLES = "particles";
    /** 残影：最近几个位置的淡色副本。 */
    public static final String EFFECT_GHOST = "ghost";
    /** 强弹性：弹簧滞后 + 速度拉伸/倾斜。 */
    public static final String EFFECT_STRETCH = "stretch";
    /** 松手时从图标位置扩散的冲击波。 */
    public static final String EFFECT_RIPPLE = "ripple";

    public static final String BG_SYSTEM = "system";
    public static final String BG_SOLID = "solid";
    public static final String BG_GRADIENT = "gradient";
    public static final String BG_GLASS = "glass";
    public static final String BG_NONE = "none";

    public static final String GRAD_LINEAR = "linear";
    public static final String GRAD_RADIAL = "radial";
    public static final String GRAD_SWEEP = "sweep";

    public static final int SYSTEM_BG_COLOR = 0xFF2F2F2F;
    public static final int SYSTEM_BG_ALPHA = 191;

    /** 图标理论尺寸区间（dp）：8dp 可见下限，72dp 为设计上限（约 1/5 屏高）。 */
    public static final float ICON_MIN_DP = 8f;
    public static final float ICON_MAX_DP = 72f;
    public static final float ICON_BASE_DP = 18f;
    /** 水滴最大行程与图标中心到指尖的距离，用于计算偏移 100% 的位置。 */
    public static final float EDGE_TRAVEL_DP = 56f;
    public static final float ICON_INSET_DP = 17.75f;

    public static float iconScaleForPercent(int percent) {
        float p = Math.max(0, Math.min(100, percent)) / 100f;
        return (ICON_MIN_DP + (ICON_MAX_DP - ICON_MIN_DP) * p) / ICON_BASE_DP;
    }

    public static int percentForIconScale(float scale) {
        float dp = Math.max(ICON_MIN_DP, Math.min(ICON_MAX_DP, scale * ICON_BASE_DP));
        return Math.round((dp - ICON_MIN_DP) / (ICON_MAX_DP - ICON_MIN_DP) * 100f);
    }

    public boolean enabled;
    public String icon = ICON_ARROW;
    public int iconColor = 0xFFFFFFFF;
    public float iconScale = 1f;
    public boolean iconMirror;
    /** 图标相对贴边位置的横向偏移（dp），0 表示待在水滴里，越大越靠近屏幕中间。 */
    public float iconOffsetDp = 0f;
    /** 设置页预览是否固定在顶部（纯 UI 偏好，不下发到 SystemUI 绘制逻辑）。 */
    public boolean previewFixed = false;
    /** 图标动效：none / breath / heartbeat / sway / spin / jelly。 */
    public String iconEffect = EFFECT_NONE;
    /** 跟手弹性：图标纵向弹簧跟随 + 横向速度形变。 */
    public boolean elastic = true;
    /** 图标外圈呼吸光晕。 */
    public boolean glow = false;
    public String image = "";
    public String bgStyle = BG_SYSTEM;
    public int bgColor = SYSTEM_BG_COLOR;
    public int bgColor2 = 0xFFFF7EB6;
    public int bgAlpha = SYSTEM_BG_ALPHA;
    public boolean border = true;
    /** 液态玻璃磨砂强度 0-100。 */
    public int glassFrost = 55;
    public int bgRadius = 82;
    public String gradientType = GRAD_LINEAR;
    /** 线性渐变方向：0° 从左到右，90° 从上到下（屏幕坐标，顺时针）。 */
    public int gradientAngle = 90;
    /** 0 表示不启用中间色。 */
    public int gradientMidColor = 0;
    public float gradientMidPos = 0.5f;

    public BackFxConfig copy() {
        BackFxConfig c = new BackFxConfig();
        c.enabled = enabled;
        c.icon = icon;
        c.iconColor = iconColor;
        c.iconScale = iconScale;
        c.iconMirror = iconMirror;
        c.iconOffsetDp = iconOffsetDp;
        c.previewFixed = previewFixed;
        c.iconEffect = iconEffect;
        c.elastic = elastic;
        c.glow = glow;
        c.image = image;
        c.bgStyle = bgStyle;
        c.bgColor = bgColor;
        c.bgColor2 = bgColor2;
        c.bgAlpha = bgAlpha;
        c.border = border;
        c.glassFrost = glassFrost;
        c.bgRadius = bgRadius;
        c.gradientType = gradientType;
        c.gradientAngle = gradientAngle;
        c.gradientMidColor = gradientMidColor;
        c.gradientMidPos = gradientMidPos;
        return c;
    }

    // ------------------------------------------------------------ json

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", enabled);
            o.put("icon", icon);
            o.put("icon_color", iconColor);
            o.put("icon_scale", Math.round(iconScale * 1000f) / 1000.0);
            o.put("image", image == null ? "" : image);
            o.put("bg_style", bgStyle);
            o.put("bg_color", bgColor);
            o.put("bg_color2", bgColor2);
            o.put("bg_alpha", bgAlpha);
            o.put("border", border);
            o.put("glass_frost", glassFrost);
            o.put("bg_radius", bgRadius);
            o.put("grad_type", gradientType);
            o.put("grad_angle", gradientAngle);
            o.put("grad_mid_color", gradientMidColor);
            o.put("grad_mid_pos", Math.round(gradientMidPos * 1000f) / 1000.0);
            o.put("icon_mirror", iconMirror);
            o.put("icon_offset", Math.round(iconOffsetDp * 10f) / 10.0);
            o.put("preview_fixed", previewFixed);
            o.put("icon_effect", iconEffect);
            o.put("elastic", elastic);
            o.put("glow", glow);
            return o.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static BackFxConfig fromJson(String raw) {
        BackFxConfig c = new BackFxConfig();
        if (raw == null || raw.isEmpty()) {
            return c;
        }
        try {
            JSONObject o = new JSONObject(raw);
            c.enabled = o.optBoolean("enabled", false);
            c.icon = o.optString("icon", ICON_ARROW);
            c.iconColor = o.optInt("icon_color", 0xFFFFFFFF);
            c.iconScale = (float) o.optDouble("icon_scale", 1.0);
            c.image = o.optString("image", "");
            c.bgStyle = o.optString("bg_style", BG_SYSTEM);
            c.bgColor = o.optInt("bg_color", SYSTEM_BG_COLOR);
            c.bgColor2 = o.optInt("bg_color2", 0xFFFF7EB6);
            c.bgAlpha = o.optInt("bg_alpha", SYSTEM_BG_ALPHA);
            c.border = o.optBoolean("border", true);
            c.glassFrost = o.optInt("glass_frost", 55);
            c.bgRadius = o.optInt("bg_radius", 82);
            c.gradientType = o.optString("grad_type", GRAD_LINEAR);
            c.gradientAngle = o.optInt("grad_angle", 90);
            c.gradientMidColor = o.optInt("grad_mid_color", 0);
            c.gradientMidPos = (float) o.optDouble("grad_mid_pos", 0.5);
            c.iconMirror = o.optBoolean("icon_mirror", false);
            c.iconOffsetDp = (float) o.optDouble("icon_offset", 0.0);
            c.previewFixed = o.optBoolean("preview_fixed", false);
            c.iconEffect = o.optString("icon_effect", EFFECT_NONE);
            c.elastic = o.optBoolean("elastic", true);
            c.glow = o.optBoolean("glow", false);
        } catch (Throwable t) {
            Log.e(TAG, "bad config json", t);
        }
        if (c.iconScale < ICON_MIN_DP / ICON_BASE_DP || c.iconScale > ICON_MAX_DP / ICON_BASE_DP) {
            c.iconScale = 1f;
        }
        if (c.bgRadius < 40 || c.bgRadius > 130) {
            c.bgRadius = 82;
        }
        if (!GRAD_LINEAR.equals(c.gradientType) && !GRAD_RADIAL.equals(c.gradientType)
                && !GRAD_SWEEP.equals(c.gradientType)) {
            c.gradientType = GRAD_LINEAR;
        }
        c.gradientAngle = ((c.gradientAngle % 360) + 360) % 360;
        if (c.gradientMidPos < 0.05f || c.gradientMidPos > 0.95f) {
            c.gradientMidPos = 0.5f;
        }
        c.iconOffsetDp = Math.max(0f, Math.min(400f, c.iconOffsetDp));
        c.glassFrost = Math.max(0, Math.min(100, c.glassFrost));
        if (!EFFECT_TRAIL.equals(c.iconEffect) && !EFFECT_PARTICLES.equals(c.iconEffect)
                && !EFFECT_GHOST.equals(c.iconEffect) && !EFFECT_STRETCH.equals(c.iconEffect)
                && !EFFECT_RIPPLE.equals(c.iconEffect)) {
            c.iconEffect = EFFECT_NONE;
        }
        return c;
    }

    // ------------------------------------------------------------ app side

    /**
     * 单一数据源就是 Settings.Global：设置页读它、SystemUI 也读它，
     * 不存在两份配置互相覆盖的问题，也不需要模块进程常驻。
     */
    public static BackFxConfig load(Context ctx) {
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

    public boolean save(Context ctx) {
        return publish(ctx);
    }

    /** 写入模块自己的 SharedPreferences，SystemUI 侧 700ms 内生效；不需要任何权限。 */
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
    }

    public boolean publish(Context ctx) {
        return publish(ctx, this);
    }

    // ------------------------------------------------------------ SystemUI side

    private static volatile BackFxConfig cache;
    private static volatile long cacheAt = -10_000L;

    public static BackFxConfig fetch(Context ctx) {
        long now = SystemClock.elapsedRealtime();
        BackFxConfig c = cache;
        if (c != null && now - cacheAt < 700L) {
            return c;
        }
        try {
            String raw = readStored(ctx);
            if (raw != null && !raw.isEmpty()) {
                cache = fromJson(raw);
                cacheAt = now;
                return cache;
            }
        } catch (Throwable ignored) {
        }
        return cache;
    }

    // ------------------------------------------------------------ bitmap transport

    private static volatile Bitmap iconCache;
    private static volatile String iconCacheKey = "";

    private static volatile Movie movieCache;
    private static volatile String movieCacheKey = "";

    /** 动图（GIF）用 Movie 逐帧按时间绘制；非 GIF 返回 null。 */
    public static Movie decodeMovie(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        Movie cached = movieCache;
        if (cached != null && movieCacheKey.equals(base64)) {
            return cached;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length < 6 || bytes[0] != 'G' || bytes[1] != 'I' || bytes[2] != 'F') {
                return null;
            }
            Movie movie = Movie.decodeByteArray(bytes, 0, bytes.length);
            if (movie != null) {
                movieCache = movie;
                movieCacheKey = base64;
            }
            return movie;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeImage(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        Bitmap bmp = iconCache;
        if (bmp != null && !bmp.isRecycled() && iconCacheKey.equals(base64)) {
            return bmp;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (decoded != null) {
                iconCache = decoded;
                iconCacheKey = base64;
            }
            return decoded;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把用户图片压到 128px 以内并编码为 base64，控制 Settings 值大小。 */
    public static String encodeImage(Bitmap src) {
        if (src == null) {
            return "";
        }
        try {
            int max = 128;
            int w = src.getWidth();
            int h = src.getHeight();
            float scale = Math.min(1f, max / (float) Math.max(w, h));
            int tw = Math.max(1, (int) (w * scale));
            int th = Math.max(1, (int) (h * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(src, tw, th, true);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] bytes = out.toByteArray();
            if (bytes.length > 30_000) {
                out.reset();
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
                bytes = out.toByteArray();
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Throwable t) {
            Log.e(TAG, "encode image failed", t);
            return "";
        }
    }
}
