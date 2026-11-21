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
    private static final String TAG = "JunjunBackFx";

    public static final String PREFS = "backfx";
    public static final String SETTINGS_KEY = "junjun_backfx";

    /**
     * This app's package. The config lives in its private preferences, so that file is only readable from inside
     * its own process — anywhere else it has to be reached through the provider.
     */
    private static final String MODULE_PKG = "com.junjun.sideback";

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
    /** 图标动效：残影 / 拉伸 — transforms on the icon itself. */
    public String iconEffect = EFFECT_NONE;

    /** 跟手动效：拖尾 / 粒子 / 涟漪 — what is drawn along the finger's path. Independent of {@link #iconEffect}. */
    public String trackEffect = EFFECT_NONE;
    /** 跟手弹性：图标纵向弹簧跟随 + 横向速度形变。 */
    public boolean elastic = true;
    /** 图标外圈呼吸光晕。 */
    public boolean glow = false;
    public String image = "";
    public String bgStyle = BG_SYSTEM;
    public int bgColor = SYSTEM_BG_COLOR;
    public int bgColor2 = 0xFFFF7EB6;
    public int bgAlpha = SYSTEM_BG_ALPHA;
    /** 高光描边：贴边处一层浅白边。默认关闭 —— 它是额外效果，不是基础外观。 */
    public boolean border = false;
    /** 液态玻璃磨砂强度 0-100。 */
    public int glassFrost = 55;
    public int bgRadius = 82;
    /**
     * How pointed the drop is: 0 is the roundest outline, 100 the sharpest. Scales the two cubic control points
     * together, so the shape keeps its proportions however far it is pushed.
     */
    public int bgCurve = 50;
    /**
     * How far the drop may reach from the edge, in dp. The gesture still tracks the finger; this is only the cap.
     */
    public int edgeTravelDp = 56;
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
        c.trackEffect = trackEffect;
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
        c.bgCurve = bgCurve;
        c.edgeTravelDp = edgeTravelDp;
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
        o.put("bg_curve", bgCurve);
        o.put("edge_travel", edgeTravelDp);
            o.put("grad_type", gradientType);
            o.put("grad_angle", gradientAngle);
            o.put("grad_mid_color", gradientMidColor);
            o.put("grad_mid_pos", Math.round(gradientMidPos * 1000f) / 1000.0);
            o.put("icon_mirror", iconMirror);
            o.put("icon_offset", Math.round(iconOffsetDp * 10f) / 10.0);
            o.put("preview_fixed", previewFixed);
            o.put("icon_effect", iconEffect);
        o.put("track_effect", trackEffect);
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
            c.border = o.optBoolean("border", false);
            c.glassFrost = o.optInt("glass_frost", 55);
            c.bgRadius = o.optInt("bg_radius", 82);
        c.bgCurve = o.optInt("bg_curve", 50);
        c.edgeTravelDp = o.optInt("edge_travel", 56);
            c.gradientType = o.optString("grad_type", GRAD_LINEAR);
            c.gradientAngle = o.optInt("grad_angle", 90);
            c.gradientMidColor = o.optInt("grad_mid_color", 0);
            c.gradientMidPos = (float) o.optDouble("grad_mid_pos", 0.5);
            c.iconMirror = o.optBoolean("icon_mirror", false);
            c.iconOffsetDp = (float) o.optDouble("icon_offset", 0.0);
            c.previewFixed = o.optBoolean("preview_fixed", false);
            c.iconEffect = o.optString("icon_effect", EFFECT_NONE);
        c.trackEffect = o.optString("track_effect", EFFECT_NONE);
        // Configs written before the split stored 跟手动效 in icon_effect; move it across so an upgrade keeps the
        // effect the user chose instead of showing 无 in both rows.
        if (EFFECT_TRAIL.equals(c.iconEffect) || EFFECT_PARTICLES.equals(c.iconEffect)
                || EFFECT_RIPPLE.equals(c.iconEffect)) {
            c.trackEffect = c.iconEffect;
            c.iconEffect = EFFECT_NONE;
        }
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
        // 图标动效 only accepts the two that transform the icon.
        if (!EFFECT_GHOST.equals(c.iconEffect) && !EFFECT_STRETCH.equals(c.iconEffect)) {
            c.iconEffect = EFFECT_NONE;
        }
        if (!EFFECT_TRAIL.equals(c.trackEffect) && !EFFECT_PARTICLES.equals(c.trackEffect)
                && !EFFECT_RIPPLE.equals(c.trackEffect)) {
            c.trackEffect = EFFECT_NONE;
        }
        return c;
    }

    // ------------------------------------------------------------ option lists

    /** Icon ids, in the order the settings screen shows them. */
    public static final String[] ICON_VALUES = {
            ICON_ARROW, ICON_DOT, ICON_HEART, ICON_STAR,
            ICON_BOLT, ICON_PAW, ICON_IMAGE, ICON_NONE,
    };

    public static final String[] ICON_LABELS = {
            // 无, not 透明: ICON_NONE draws no icon at all, and "透明" read as a transparency setting next to 图标颜色.
            "箭头", "圆点", "爱心", "星星", "闪电", "猫爪", "图片", "无",
    };

    /** Background style ids, in the order the settings screen shows them. */
    /**
     * The background styles the screen offers.
     *
     * 系统 is not among them: `drawBackground` renders every style that is not NONE, GLASS or GRADIENT from
     * `bgColor` alone, so 系统 and 纯色 were the same picture, the first only differing by also writing
     * `SYSTEM_BG_COLOR` into the config. The constant stays defined — existing configs use it.
     */
    public static final String[] BG_VALUES = {
            BG_SOLID, BG_GRADIENT, BG_GLASS, BG_NONE,
    };

    public static final String[] BG_LABELS = {
            "纯色", "渐变", "玻璃", "无",
    };

    /**
     * The palette the colour row offers before the custom picker.
     *
     * <p>Declared here rather than in the screen because the first entry is the phone's own backdrop colour,
     * which is a config constant — a copy in the UI would go stale the moment that constant changed.
     */
    public static final int[] BG_PALETTE = {
            SYSTEM_BG_COLOR, 0xFF000000, 0xFFFFFFFF, 0xFFFFD7E6,
            0xFFFF7EB6, 0xFF1E7BFF, 0xFF7C4DFF, 0xFF22C55E,
    };

    /** Indicator-size ids, in the order the settings screen shows them. */
    /** Indicator sizes. 系统 was the largest of the three under another name, so it is gone. */
    public static final String[] SIZE_VALUES = {"small", "standard"};

    public static final String[] SIZE_LABELS = {"小", "标准"};

    /** The capsule radius each 指示器大小 step maps to. */
    public static final int[] BG_RADII = {64, 82};

    /** The opacity steps the slider moves through, as percentages. */
    public static final int[] ALPHA_STEPS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    /** The icon colours, matching the palette the Java screen offered. */
    public static final int[] ICON_PALETTE = {
            0xFFFFFFFF, 0xFF1B1B1F, 0xFF1E7BFF, 0xFFFF4D6D,
            0xFFFFB300, 0xFFA855F7, 0xFF22C55E, 0xFFFF7EB6,
    };

    /** Every effect id, for parsing and validation. The two rows below are subsets of this. */
    public static final String[] EFFECT_VALUES = {
            EFFECT_NONE, EFFECT_GHOST, EFFECT_STRETCH,
            EFFECT_TRAIL, EFFECT_PARTICLES, EFFECT_RIPPLE,
    };

    public static final String[] EFFECT_LABELS = {
            "无", "残影", "拉伸", "拖尾", "粒子", "涟漪",
    };

    /**
     * 图标动效 — what the icon itself does while the indicator is out.
     */
    public static final String[] ICON_EFFECT_VALUES = {
            EFFECT_NONE, EFFECT_GHOST, EFFECT_STRETCH,
    };

    public static final String[] ICON_EFFECT_LABELS = {
            "无", "残影", "拉伸",
    };

    /**
     * 跟手动效 — what is left along the finger's path. These draw trails, particles and rings, so they only mean
     * something while the finger is moving.
     */
    public static final String[] TRACK_EFFECT_VALUES = {
            EFFECT_NONE, EFFECT_TRAIL, EFFECT_PARTICLES, EFFECT_RIPPLE,
    };

    public static final String[] TRACK_EFFECT_LABELS = {
            // 涟漪 draws everything 拖尾 and 粒子 draw, plus rings and a slower exit; the label says so rather than
            // presenting it as a third independent choice.
            "无", "拖尾", "粒子", "涟漪（拖尾+粒子）",
    };

    /**
     * Gradient shapes, as `BackFxRenderer.buildGradient` implements them.
     *
     * The row used to offer only 线性, which made the type look unimplemented — the renderer has handled all three
     * since the code was carried over, and a third colour whenever {@code gradientMidColor} is not transparent.
     */
    public static final String[] GRAD_VALUES = {GRAD_LINEAR, GRAD_RADIAL, GRAD_SWEEP};

    public static final String[] GRAD_LABELS = {"线性", "径向", "扫描"};

    public static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ app side

    /**
     * 单一数据源就是 Settings.Global：设置页读它、SystemUI 也读它，
     * 不存在两份配置互相覆盖的问题，也不需要模块进程常驻。
     */
    public static BackFxConfig load(Context ctx) {
        try {
            // Same order as fetch(): the local prefs are what publish() keeps in sync with the system
            // copy, so they are the reliable record. Reading only Settings.Global here was a silent
            // data-loss bug: onCreate() publishes whatever load() returns straight back, so when the
            // global was missing (cleared, never written, or unwritable on this install) load()
            // produced the defaults and publish() wrote them over the user's saved style. Every
            // switch appeared to reset itself after the app was force stopped.
            String raw = readLocal(ctx);
            if (raw == null || raw.isEmpty()) {
                raw = readGlobal(ctx);
            }
            if (raw != null && !raw.isEmpty()) {
                return fromJson(raw);
            }
        } catch (Throwable t) {
            Log.e(TAG, "load failed", t);
        }
        return new BackFxConfig();
    }

    public boolean save(Context ctx) {
        return publish(ctx);
    }

    /**
     * Writes the config everywhere the injected side can read it from.
     *
     * <p>Both copies have to be written, not one-or-the-other. SystemUI reads the provider first,
     * and the provider serves the {@code backfx_local} preferences; Settings.Global is only its
     * fallback, because SystemUI is denied WRITE_SECURE_SETTINGS on this ROM and cannot read the
     * key at all. Writing only Settings.Global (what this did before) left the provider answering
     * with whatever was last stored in prefs, so every saved change was invisible to the renderer:
     * the gesture kept drawing the defaults no matter what the settings page showed.
     */
    public static boolean publish(Context ctx, BackFxConfig c) {
        String json = c.toJson();
        boolean stored = false;
        try {
            ctx.getSharedPreferences("backfx_local", 0).edit()
                    .putString(SETTINGS_KEY, json).commit();
            stored = true;
        } catch (Throwable t) {
            Log.w(TAG, "local publish failed", t);
        }
        try {
            Settings.Global.putString(ctx.getContentResolver(), SETTINGS_KEY, json);
            stored = true;
        } catch (Throwable t) {
            // No root / no WRITE_SECURE_SETTINGS: the local copy above is what carries the config.
            Log.w(TAG, "publish to Settings.Global failed", t);
        }
        return stored;
    }

    public boolean publish(Context ctx) {
        return publish(ctx, this);
    }

    // ------------------------------------------------------------ SystemUI side

    private static volatile BackFxConfig cache;
    private static volatile long cacheAt = -10_000L;

    /**
     * Reads the current settings.
     *
     * <p>Order matters here, and it took a while to get right. The settings screen writes to its own
     * preferences because it cannot hold WRITE_SECURE_SETTINGS, so those preferences are the source of
     * truth. The global setting is only a legacy copy: it can hold an old value from a build that used
     * to write there, and reading it first made the screen look saved while SystemUI kept drawing the
     * previous style. So the app's provider is asked first, then the local preferences (which resolves
     * inside the app's own process), and the global setting is the last resort.
     */
    public static BackFxConfig fetch(Context ctx) {
        long now = SystemClock.elapsedRealtime();
        BackFxConfig cached = cache;
        if (cached != null && now - cacheAt < 700L) {
            return cached;
        }
        String json = queryProvider(ctx);
        if (json == null || json.isEmpty()) {
            json = readLocal(ctx);
        }
        if (json != null && !json.isEmpty()) {
            try {
                cache = fromJson(json);
                cacheAt = now;
                return cache;
            } catch (Throwable ignored) {
            }
        }
        // Nothing in either synced copy. Adopt whatever the platform still holds and store it, so the
        // global can only ever seed the config once — never override a real saved value.
        //
        // This matters because the provider goes briefly unavailable whenever the app's process is
        // killed. Falling back to Settings.Global in that window is what made the style flip to a
        // stale one after a force-stop and back again once the app restarted.
        json = readGlobal(ctx);
        if (json != null && !json.isEmpty()) {
            try {
                cache = fromJson(json);
                cacheAt = now;
                writeLocal(ctx, json);
                return cache;
            } catch (Throwable ignored) {
            }
        }
        return cache;
    }

    /** Store the config where the provider serves it from. */
    private static void writeLocal(Context ctx, String json) {
        try {
            ctx.getSharedPreferences("backfx_local", 0).edit()
                    .putString(SETTINGS_KEY, json).commit();
        } catch (Throwable ignored) {
        }
    }

    /** Ask the app for its stored settings; this is the only path that works across processes. */
    private static String queryProvider(Context ctx) {
        try {
            android.database.Cursor cursor = ctx.getContentResolver().query(
                    android.net.Uri.parse("content://com.junjun.sideback.config/backfx"),
                    null, null, null, null);
            if (cursor == null) {
                return null;
            }
            try {
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    String value = cursor.getString(0);
                    android.util.Log.i("sideback_fx", "provider returned: " + value);
                    return value;
                }
                android.util.Log.w("sideback_fx", "provider cursor empty");
            } finally {
                cursor.close();
            }
        } catch (Throwable error) {
            android.util.Log.w("sideback_fx", "provider query failed", error);
        }
        return null;
    }

    private static String readLocal(Context ctx) {
        // Only meaningful inside the toolbox's own process. In SystemUI this call reads SystemUI's own
        // preferences, which nothing ever writes, so it silently reports "nothing saved" while looking like a
        // real source — and because it sits ahead of the global copy in the fallback order, the reader believed
        // it had consulted the truth and fell through to whatever the global still held.
        if (!ownsPreferences(ctx)) {
            return null;
        }
        try {
            return ctx.getSharedPreferences("backfx_local", 0).getString(SETTINGS_KEY, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Whether this process is the toolbox, i.e. the package the {@code backfx_local} file belongs to. */
    private static boolean ownsPreferences(Context ctx) {
        try {
            return MODULE_PKG.equals(ctx.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readGlobal(Context ctx) {
        try {
            return Settings.Global.getString(ctx.getContentResolver(), SETTINGS_KEY);
        } catch (Throwable ignored) {
            return null;
        }
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
