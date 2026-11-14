package com.junjun.sideback;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 手势过程中的动态素材：拖尾、粒子、残影、冲击波。
 *
 * <p>全部按时间衰减，由 {@link BackFxOverlay} / 预览的逐帧循环驱动；
 * 坐标使用当前画布坐标，渲染器先画背景、再画这些素材、最后画图标。</p>
 */
public final class BackFxMotion {

    private static final long TRAIL_LIFE_MS = 300L;
    private static final long PARTICLE_LIFE_MS = 720L;
    private static final long GHOST_LIFE_MS = 320L;
    private static final long RING_LIFE_MS = 360L;

    private static final class Point {
        final float x;
        final float y;
        final long t;

        Point(float x, float y, long t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        long born;
    }

    private static final class Ring {
        final float x;
        final float y;
        final long born;

        Ring(float x, float y, long born) {
            this.x = x;
            this.y = y;
            this.born = born;
        }
    }

    private final List<Point> trail = new ArrayList<>();
    private final List<Point> ghosts = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final Random random = new Random();

    private float lastX = Float.NaN;
    private float lastY = Float.NaN;
    private long lastTrailAt;
    private long lastGhostAt;
    private long lastSpawnAt;

    public int trailCount() {
        return trail.size();
    }

    public int particleCount() {
        return particles.size();
    }

    public void reset() {
        trail.clear();
        ghosts.clear();
        particles.clear();
        rings.clear();
        lastX = Float.NaN;
        lastY = Float.NaN;
        lastTrailAt = 0L;
        lastGhostAt = 0L;
        lastSpawnAt = 0L;
    }

    /**
     * @param x      图标中心 x（画布坐标）
     * @param y      图标中心 y
     * @param dt     距上一帧的秒数
     * @param now    毫秒时钟
     * @param effect 当前动效
     */
    public void update(float iconX, float iconY, float fingerX, float fingerY,
                       float dt, long now, String effect) {
        if (Float.isNaN(lastX)) {
            lastX = fingerX;
            lastY = fingerY;
        }
        float dx = fingerX - lastX;
        float dy = fingerY - lastY;
        float dist = (float) Math.hypot(dx, dy);
        float speed = dt > 0.0001f ? dist / dt : 0f;
        lastX = fingerX;
        lastY = fingerY;

        boolean wantTrail = BackFxConfig.EFFECT_TRAIL.equals(effect)
                || BackFxConfig.EFFECT_RIPPLE.equals(effect);
        if (wantTrail) {
            if (now - lastTrailAt >= 14L || dist > 1.2f) {
                trail.add(0, new Point(fingerX, fingerY, now));
                lastTrailAt = now;
            }
            pruneTrail(now);
        }

        if (BackFxConfig.EFFECT_GHOST.equals(effect)
                && now - lastGhostAt >= 42L && dist > 1.2f) {
            ghosts.add(0, new Point(iconX, iconY, now));
            lastGhostAt = now;
            while (ghosts.size() > 3) {
                ghosts.remove(ghosts.size() - 1);
            }
        }

        boolean wantParticles = BackFxConfig.EFFECT_PARTICLES.equals(effect)
                || BackFxConfig.EFFECT_RIPPLE.equals(effect);
        if (wantParticles && speed > 40f) {
            long interval = (long) Math.max(6f, 20f - Math.min(14f, speed / 200f));
            if (now - lastSpawnAt >= interval) {
                lastSpawnAt = now;
                int count = speed > 900f ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    spawnParticle(fingerX, fingerY,
                            -dx / Math.max(dt, 0.001f), -dy / Math.max(dt, 0.001f), now);
                }
            }
        }

        // 粒子推进 + 过期回收
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            long age = now - p.born;
            if (age > PARTICLE_LIFE_MS) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.96f;
            p.vy *= 0.96f;
        }
        // 残影过期
        while (!ghosts.isEmpty() && now - ghosts.get(ghosts.size() - 1).t > GHOST_LIFE_MS) {
            ghosts.remove(ghosts.size() - 1);
        }
        // 冲击波过期
        Iterator<Ring> rit = rings.iterator();
        while (rit.hasNext()) {
            if (now - rit.next().born > RING_LIFE_MS) {
                rit.remove();
            }
        }
    }

    public void release(float x, float y, String effect, long now) {
        if (BackFxConfig.EFFECT_RIPPLE.equals(effect)) {
            rings.add(new Ring(x, y, now));
        }
        if (BackFxConfig.EFFECT_PARTICLES.equals(effect)
                || BackFxConfig.EFFECT_RIPPLE.equals(effect)) {
            for (int i = 0; i < 14; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                float speed = 220f + random.nextFloat() * 380f;
                Particle p = new Particle();
                p.x = x;
                p.y = y;
                p.vx = (float) Math.cos(angle) * speed;
                p.vy = (float) Math.sin(angle) * speed;
                p.size = 0.05f + random.nextFloat() * 0.05f;
                p.born = now;
                particles.add(p);
            }
        }
    }

    private void pruneTrail(long now) {
        while (!trail.isEmpty() && now - trail.get(trail.size() - 1).t > TRAIL_LIFE_MS) {
            trail.remove(trail.size() - 1);
        }
        while (trail.size() > 26) {
            trail.remove(trail.size() - 1);
        }
    }

    private void spawnParticle(float x, float y, float vx, float vy, long now) {
        if (particles.size() > 80) {
            return;
        }
        Particle p = new Particle();
        p.x = x + (random.nextFloat() - 0.5f) * 8f;
        p.y = y + (random.nextFloat() - 0.5f) * 8f;
        p.vx = vx * 0.18f + (random.nextFloat() - 0.5f) * 140f;
        p.vy = vy * 0.18f + (random.nextFloat() - 0.5f) * 140f;
        p.size = 0.08f + random.nextFloat() * 0.08f;
        p.born = now;
        particles.add(p);
    }

    /** 画在图标下面：残影、拖尾。 */
    public void drawBack(Canvas canvas, BackFxConfig cfg, float cx, float cy, float iconSize,
                         float density, float outerAlpha, boolean right, float timeSec, long now) {
        int color = cfg.iconColor;
        for (int i = 0; i < ghosts.size(); i++) {
            Point g = ghosts.get(i);
            float a = (0.36f - i * 0.09f) * outerAlpha;
            if (a > 0.01f) {
                BackFxRenderer.drawIconAt(canvas, cfg, g.x, g.y, iconSize,
                        (int) (a * 255f), density, right, timeSec);
            }
        }

        if (trail.size() >= 2) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            int n = trail.size();
            for (int i = n - 1; i >= 0; i--) {
                Point p = trail.get(i);
                float k = 1f - i / (float) (n - 1);
                paint.setAlpha((int) (0xE8 * (0.25f + 0.75f * k) * outerAlpha));
                canvas.drawCircle(p.x, p.y, Math.max(1.5f, iconSize * 0.17f * (0.35f + 0.65f * k)), paint);
            }
            Point head = trail.get(0);
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(color);
            glow.setAlpha((int) (0x99 * outerAlpha));
            canvas.drawCircle(head.x, head.y, iconSize * 0.30f, glow);
        }
    }

    /** 画在图标上面：粒子、冲击波。 */
    public void drawFront(Canvas canvas, BackFxConfig cfg, float cx, float cy, float iconSize,
                          float density, float outerAlpha, long now) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(cfg.iconColor);
        for (Particle p : particles) {
            long age = now - p.born;
            float k = 1f - age / (float) PARTICLE_LIFE_MS;
            if (k <= 0f) {
                continue;
            }
            paint.setAlpha((int) (0xE8 * k * outerAlpha));
            canvas.drawCircle(p.x, p.y, Math.max(1f, iconSize * p.size * (0.6f + 0.4f * k)), paint);
        }

        for (Ring ring : rings) {
            long age = now - ring.born;
            float k = age / (float) RING_LIFE_MS;
            if (k < 0f || k > 1f) {
                continue;
            }
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setColor(cfg.iconColor);
            ringPaint.setStrokeWidth(Math.max(1.5f, iconSize * 0.12f * (1f - k)));
            ringPaint.setAlpha((int) (0xC0 * (1f - k) * outerAlpha));
            canvas.drawCircle(ring.x, ring.y, iconSize * (0.5f + 2.1f * k), ringPaint);
        }
    }
}
