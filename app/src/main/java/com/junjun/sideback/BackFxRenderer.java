package com.junjun.sideback;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import java.util.Random;

/**
 * 侧滑返回指示器的绘制器。设置页预览与 SystemUI 注入都调用同一份代码，
 * 只依赖 framework 的 Canvas/Paint，避免把模块资源带进 SystemUI 进程。
 *
 * <p>左右两侧共用一套几何：背景按屏幕中线镜像，图标保持正立（自定义图片不会被翻转），
 * 只有箭头/闪电这类方向性矢量在右侧才镜像。</p>
 */
public final class BackFxRenderer {

    private static final float CTRL1_DP = 56f;
    private static final float CTRL2_DP = 39f;
    private static final float ICON_DP = 18f;
    private static final float ICON_INSET_DP = 17.75f;

    private BackFxRenderer() {
    }

    /**
     * @param side       0 = 左侧滑，1 = 右侧滑
     * @param width      画布/屏幕宽（px）
     * @param fingerY    手指所在的 y（px）
     * @param travel     指示器从边缘伸出的距离（px），0..约 56dp
     * @param density    屏幕密度
     * @param outerAlpha 整体透明度（进出场动画）
     * @param scale      整体缩放（进出场动画）
     */
    public static void draw(Canvas canvas, BackFxConfig cfg, int side, float width, float fingerY,
                            float travel, float density, float outerAlpha, float scale) {
        draw(canvas, cfg, side, width, fingerY, travel, density, outerAlpha, scale, 0f, 0f);
    }

    public static void draw(Canvas canvas, BackFxConfig cfg, int side, float width, float fingerY,
                            float travel, float density, float outerAlpha, float scale,
                            float timeSec, float velocityX) {
        draw(canvas, cfg, side, width, fingerY, travel, density, outerAlpha, scale,
                timeSec, velocityX, null, 0L);
    }

    public static float iconSize(BackFxConfig cfg, float density) {
        return ICON_DP * density * (cfg == null ? 1f : cfg.iconScale);
    }

    /** 图标中心的 x（屏幕坐标，已含偏移与边界夹取）。 */
    public static float iconCenterX(BackFxConfig cfg, int side, float width, float travel,
                                    float density, float iconSize) {
        if (cfg == null) {
            return 0f;
        }
        boolean right = side == 1;
        float centerLeft = Math.max(iconSize / 2f + 2f * density, travel - ICON_INSET_DP * density);
        float offsetPx = cfg.iconOffsetDp * density;
        float cx = centerLeft + offsetPx;
        if (right) {
            cx = width - cx;
        }
        float margin = iconSize / 2f + 2f * density;
        return Math.max(margin, Math.min(width - margin, cx));
    }

    public static void drawIconAt(Canvas canvas, BackFxConfig cfg, float cx, float cy, float size,
                                  int alpha, float density, boolean right, float timeSec) {
        if (cfg == null || alpha <= 0) {
            return;
        }
        drawIcon(canvas, cfg, cx, cy, size, Math.min(1f, alpha / 255f), density, right, timeSec);
    }

    /**
     * @param timeSec   动效/动图使用的秒级时间
     * @param velocityX 手势横向速度（px/s），用于跟手弹性形变
     * @param motion    手势过程素材（拖尾/粒子/残影/冲击波），可为 null
     */
    public static void draw(Canvas canvas, BackFxConfig cfg, int side, float width, float fingerY,
                            float travel, float density, float outerAlpha, float scale,
                            float timeSec, float velocityX, BackFxMotion motion, long now) {
        if (canvas == null || cfg == null || outerAlpha <= 0f || scale <= 0f) {
            return;
        }
        boolean right = side == 1;
        float cy = fingerY;
        float iconSize = iconSize(cfg, density);
        float centerLeft = Math.max(iconSize / 2f + 2f * density, travel - ICON_INSET_DP * density);

        // 背景：整体镜像到对应侧边。
        canvas.save();
        if (right) {
            canvas.scale(-1f, 1f, width / 2f, 0f);
        }
        canvas.scale(scale, scale, 0f, cy);
        drawBackground(canvas, cfg, cy, travel, density, outerAlpha, centerLeft);
        canvas.restore();

        float cx = iconCenterX(cfg, side, width, travel, density, iconSize);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);

        if (motion != null) {
            motion.drawBack(canvas, cfg, cx, cy, iconSize, density, outerAlpha, right, timeSec, now);
        }

        // 动效：跟手弹性 / 强弹性围绕图标中心做拉伸和倾斜。
        float[] fx = effectTransform(cfg, timeSec, velocityX);
        float rot = fx[0];
        float sx = fx[1];
        float sy = fx[2];
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rot);
        canvas.scale(sx, sy);
        canvas.translate(-cx, -cy);

        if (cfg.glow && !BackFxConfig.ICON_NONE.equals(cfg.icon)) {
            drawGlow(canvas, cfg, cx, cy, iconSize, outerAlpha, timeSec);
        }
        drawIcon(canvas, cfg, cx, cy, iconSize, outerAlpha, density, right, timeSec);
        canvas.restore();

        if (motion != null) {
            motion.drawFront(canvas, cfg, cx, cy, iconSize, density, outerAlpha, now);
        }
        canvas.restore();
    }

    private static float[] effectTransform(BackFxConfig cfg, float t, float velocityX) {
        float rot = 0f;
        float sx = 1f;
        float sy = 1f;
        if (cfg.elastic || BackFxConfig.EFFECT_STRETCH.equals(cfg.iconEffect)) {
            float strength = BackFxConfig.EFFECT_STRETCH.equals(cfg.iconEffect) ? 1.7f : 1f;
            float v = Math.max(-1f, Math.min(1f, velocityX / 2200f));
            sx *= 1f + 0.10f * strength * v;
            sy *= 1f - 0.07f * strength * v;
            rot += -6f * strength * v;
        }
        return new float[]{rot, sx, sy};
    }

    private static void drawGlow(Canvas canvas, BackFxConfig cfg, float cx, float cy,
                                 float iconSize, float outerAlpha, float t) {
        float pulse = 1f + 0.12f * (float) Math.sin(t * Math.PI * 2 / 1.5);
        float radius = iconSize * 1.3f * pulse;
        int color = (cfg.iconColor & 0x00FFFFFF) | 0x5A000000;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(cx, cy, Math.max(radius, 1f),
                new int[]{withAlpha(color, (int) (0x66 * clamp01(outerAlpha))), 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private static void drawBackground(Canvas canvas, BackFxConfig cfg, float cy, float travel,
                                       float density, float outerAlpha, float iconCenterX) {
        if (BackFxConfig.BG_NONE.equals(cfg.bgStyle)) {
            return;
        }
        float r = Math.max(40, Math.min(130, cfg.bgRadius)) * density;
        float c1 = CTRL1_DP * density;
        float c2 = CTRL2_DP * density;
        float tip = Math.max(travel, 1f);

        Path path = new Path();
        path.moveTo(0f, cy - r);
        path.cubicTo(0f, cy - r + c1, tip, cy - c2, tip, cy);
        path.cubicTo(tip, cy + c2, 0f, cy + r - c1, 0f, cy + r);
        path.close();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        int alpha = (int) (((cfg.bgAlpha & 0xFF) * clamp01(outerAlpha)) + 0.5f);
        if (BackFxConfig.BG_GLASS.equals(cfg.bgStyle)) {
            drawGlass(canvas, cfg, path, cy, r, density, outerAlpha);
            return;
        }
        if (BackFxConfig.BG_GRADIENT.equals(cfg.bgStyle)) {
            paint.setShader(buildGradient(cfg, iconCenterX, cy, r, alpha));
            paint.setAlpha(255);
        } else {
            paint.setColor(cfg.bgColor);
            paint.setAlpha(alpha);
        }
        canvas.drawPath(path, paint);

        if (cfg.border) {
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, 0.6f * density));
            border.setColor(0xFFFFFFFF);
            border.setAlpha((int) (0x4D * clamp01(outerAlpha)));
            canvas.drawPath(path, border);
        }
    }

    private static Shader buildGradient(BackFxConfig cfg, float cx, float cy, float r, int alpha) {
        int c1 = withAlpha(cfg.bgColor, alpha);
        int c2 = withAlpha(cfg.bgColor2, alpha);
        boolean hasMid = (cfg.gradientMidColor >>> 24) != 0;
        int[] colors;
        float[] positions = null;
        if (hasMid) {
            colors = new int[]{c1, withAlpha(cfg.gradientMidColor, alpha), c2};
            float mid = Math.max(0.05f, Math.min(0.95f, cfg.gradientMidPos));
            positions = new float[]{0f, mid, 1f};
        } else {
            colors = new int[]{c1, c2};
        }
        if (BackFxConfig.GRAD_RADIAL.equals(cfg.gradientType)) {
            return new RadialGradient(cx, cy, Math.max(r * 1.08f, 1f), colors, positions,
                    Shader.TileMode.CLAMP);
        }
        if (BackFxConfig.GRAD_SWEEP.equals(cfg.gradientType)) {
            return new SweepGradient(cx, cy, colors, positions);
        }
        double angle = Math.toRadians(cfg.gradientAngle);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        float len = r * 1.2f;
        return new LinearGradient(cx - dx * len, cy - dy * len,
                cx + dx * len, cy + dy * len, colors, positions, Shader.TileMode.CLAMP);
    }

    private static void drawIcon(Canvas canvas, BackFxConfig cfg, float cx, float cy,
                                 float size, float outerAlpha, float density, boolean right,
                                 float timeSec) {
        String icon = cfg.icon == null ? BackFxConfig.ICON_ARROW : cfg.icon;
        if (BackFxConfig.ICON_NONE.equals(icon)) {
            return;
        }
        if (BackFxConfig.ICON_IMAGE.equals(icon)) {
            android.graphics.Movie movie = BackFxConfig.decodeMovie(cfg.image);
            if (movie != null && movie.width() > 0 && movie.height() > 0) {
                int duration = movie.duration();
                movie.setTime(duration > 0 ? (int) ((timeSec * 1000f) % duration) : 0);
                float half = size / 2f;
                float scale = size / Math.max(movie.width(), movie.height());
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setAlpha((int) (255 * clamp01(outerAlpha)));
                boolean flip = right && cfg.iconMirror;
                canvas.save();
                if (flip) {
                    canvas.scale(-1f, 1f, cx, cy);
                }
                canvas.translate(cx - half, cy - half);
                canvas.scale(scale, scale);
                movie.draw(canvas, 0f, 0f, paint);
                canvas.restore();
                return;
            }
            Bitmap bmp = BackFxConfig.decodeImage(cfg.image);
            if (bmp != null && !bmp.isRecycled()) {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setAlpha((int) (255 * clamp01(outerAlpha)));
                float half = size / 2f;
                RectF dst = new RectF(cx - half, cy - half, cx + half, cy + half);
                boolean flip = right && cfg.iconMirror;
                if (flip) {
                    canvas.save();
                    canvas.scale(-1f, 1f, cx, cy);
                }
                canvas.drawBitmap(bmp, null, dst, paint);
                if (flip) {
                    canvas.restore();
                }
                return;
            }
            // 图片缺失时退回箭头，避免指示器变成空白。
            icon = BackFxConfig.ICON_ARROW;
        }

        // 方向性图形在右侧做水平镜像；心形/星星/圆点/猫爪本身对称，镜像后无感。
        if (right) {
            canvas.save();
            canvas.scale(-1f, 1f, cx, cy);
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(cfg.iconColor);
        paint.setAlpha((int) (255 * clamp01(outerAlpha)));

        switch (icon) {
            case BackFxConfig.ICON_DOT: {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, size * 0.26f, paint);
                break;
            }
            case BackFxConfig.ICON_HEART: {
                Path p = new Path();
                float w = size * 0.46f;
                float h = size * 0.46f;
                p.moveTo(cx, cy + h * 0.95f);
                p.cubicTo(cx - w * 2.1f, cy - h * 0.25f, cx - w * 0.95f, cy - h * 1.45f, cx, cy - h * 0.35f);
                p.cubicTo(cx + w * 0.95f, cy - h * 1.45f, cx + w * 2.1f, cy - h * 0.25f, cx, cy + h * 0.95f);
                p.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(p, paint);
                break;
            }
            case BackFxConfig.ICON_STAR: {
                Path p = new Path();
                float outer = size * 0.48f;
                float inner = outer * 0.44f;
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    float radius = (i % 2 == 0) ? outer : inner;
                    float x = cx + (float) (Math.cos(angle) * radius);
                    float y = cy + (float) (Math.sin(angle) * radius);
                    if (i == 0) {
                        p.moveTo(x, y);
                    } else {
                        p.lineTo(x, y);
                    }
                }
                p.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(p, paint);
                break;
            }
            case BackFxConfig.ICON_BOLT: {
                Path p = new Path();
                p.moveTo(cx + size * 0.10f, cy - size * 0.48f);
                p.lineTo(cx - size * 0.28f, cy + size * 0.06f);
                p.lineTo(cx - size * 0.02f, cy + size * 0.06f);
                p.lineTo(cx - size * 0.10f, cy + size * 0.48f);
                p.lineTo(cx + size * 0.30f, cy - size * 0.08f);
                p.lineTo(cx + size * 0.03f, cy - size * 0.08f);
                p.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(p, paint);
                break;
            }
            case BackFxConfig.ICON_PAW: {
                paint.setStyle(Paint.Style.FILL);
                float pad = size * 0.20f;
                canvas.drawOval(new RectF(cx - pad * 1.25f, cy + size * 0.02f,
                        cx + pad * 1.25f, cy + size * 0.02f + pad * 1.9f), paint);
                canvas.drawCircle(cx - size * 0.30f, cy - size * 0.16f, size * 0.105f, paint);
                canvas.drawCircle(cx - size * 0.10f, cy - size * 0.30f, size * 0.105f, paint);
                canvas.drawCircle(cx + size * 0.13f, cy - size * 0.30f, size * 0.105f, paint);
                canvas.drawCircle(cx + size * 0.32f, cy - size * 0.14f, size * 0.105f, paint);
                break;
            }
            case BackFxConfig.ICON_ARROW:
            default: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(Math.max(2f, 2.5f * density) * (size / (ICON_DP * density)));
                Path p = new Path();
                float len = size * 0.52f;
                float h = size * 0.40f;
                // 箭头指向滑动方向（左缘向右拉 → 指向右），右缘镜像后自动反向
                p.moveTo(cx - len / 2f, cy - h);
                p.lineTo(cx + len / 2f, cy);
                p.lineTo(cx - len / 2f, cy + h);
                canvas.drawPath(p, paint);
                break;
            }
        }
        if (right) {
            canvas.restore();
        }
    }

    private static volatile Bitmap frostNoise;

    private static Bitmap frostNoise() {
        Bitmap cached = frostNoise;
        if (cached != null) {
            return cached;
        }
        int size = 72;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];
        Random random = new Random(20261114L);
        for (int i = 0; i < pixels.length; i++) {
            int value = 140 + random.nextInt(116);
            int a = 10 + random.nextInt(20);
            pixels[i] = (a << 24) | (value << 16) | (value << 8) | value;
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        frostNoise = bitmap;
        return bitmap;
    }

    /**
     * 液态玻璃：半透明着色 + 磨砂噪点 + 高光内缘 + 柔和投影。
     * 不依赖系统 backdrop blur，任何机型都能画出稳定的玻璃质感。
     */
    private static void drawGlass(Canvas canvas, BackFxConfig cfg, Path path,
                                  float cy, float r, float density, float outerAlpha) {
        // 柔和外投影
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setColor(0x00000000);
        for (int i = 2; i >= 0; i--) {
            shadow.setStrokeWidth((3f + i * 4f) * density);
            shadow.setColor(0xFF000000);
            shadow.setAlpha((int) ((16 - i * 4) * outerAlpha));
            canvas.drawPath(path, shadow);
        }

        int tint = cfg.bgColor;
        int baseAlpha = (int) ((cfg.bgAlpha & 0xFF) * clamp01(outerAlpha));
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0f, cy - r, 0f, cy + r,
                withAlpha(tint, (int) (baseAlpha * 0.62f)),
                withAlpha(tint, (int) (baseAlpha * 0.30f)),
                Shader.TileMode.CLAMP));
        canvas.drawPath(path, fill);

        // 磨砂噪点
        int frost = Math.max(0, Math.min(100, cfg.glassFrost));
        if (frost > 0) {
            BitmapShader noiseShader = new BitmapShader(frostNoise(),
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Paint noise = new Paint(Paint.ANTI_ALIAS_FLAG);
            noise.setShader(noiseShader);
            noise.setAlpha((int) (frost * 0.42f * outerAlpha));
            canvas.drawPath(path, noise);
        }

        // 顶部高光 + 底部折射色
        canvas.save();
        canvas.clipPath(path);
        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        gloss.setShader(new LinearGradient(0f, cy - r, 0f, cy,
                0x5AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        gloss.setAlpha((int) (255 * clamp01(outerAlpha)));
        canvas.drawRect(0f, cy - r, 100000f, cy, gloss);

        Paint refract = new Paint(Paint.ANTI_ALIAS_FLAG);
        refract.setShader(new LinearGradient(0f, cy, 0f, cy + r,
                0x00000000, withAlpha(tint, 0x33), Shader.TileMode.CLAMP));
        canvas.drawRect(0f, cy, 100000f, cy + r, refract);
        canvas.restore();

        if (cfg.border) {
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.5f * density);
            rim.setColor(0xFFFFFFFF);
            rim.setAlpha((int) (0x8C * outerAlpha));
            canvas.drawPath(path, rim);
            rim.setStrokeWidth(0.6f * density);
            rim.setAlpha((int) (0x40 * outerAlpha));
            canvas.drawPath(path, rim);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
