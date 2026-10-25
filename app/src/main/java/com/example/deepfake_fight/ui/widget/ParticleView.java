package com.example.deepfake_fight.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 粒子背景 View —— 漂浮的金色粒子，营造科技感。
 *
 * 用法：作为 Header / 检测页的背景层，叠加在渐变之上。
 *   <com.example.deepfake_fight.ui.widget.ParticleView
 *       android:layout_width="match_parent"
 *       android:layout_height="match_parent" />
 */
public class ParticleView extends View {

    private static final int PARTICLE_COUNT = 26;

    private static class Particle {
        float x, y, radius, speedX, speedY, alpha, alphaSpeed;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private int w, h;
    private boolean inited = false;

    public ParticleView(Context context) {
        super(context);
    }

    public ParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void initIfNeeded() {
        if (inited) return;
        inited = true;
        // 金色系粒子，深浅不一
        int[] golds = {
                Color.argb(120, 201, 162, 39),   // 金色
                Color.argb(90, 212, 175, 55),    // 浅金
                Color.argb(70, 255, 255, 255),   // 白
                Color.argb(100, 96, 165, 250),   // 淡蓝
        };
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.radius = 1.5f + random.nextFloat() * 3.5f;
            p.speedX = (random.nextFloat() - 0.5f) * 0.5f;
            p.speedY = -(0.2f + random.nextFloat() * 0.8f);  // 缓慢上浮
            p.alpha = 0.25f + random.nextFloat() * 0.5f;
            p.alphaSpeed = (random.nextFloat() - 0.5f) * 0.01f;
            paint.setColor(golds[i % golds.length]);
            particles.add(p);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.w = w;
        this.h = h;
        if (!inited) {
            initIfNeeded();
            for (Particle p : particles) {
                p.x = random.nextFloat() * w;
                p.y = random.nextFloat() * h;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w == 0 || h == 0) return;

        for (Particle p : particles) {
            // 更新位置
            p.x += p.speedX;
            p.y += p.speedY;
            p.alpha += p.alphaSpeed;
            if (p.alpha < 0.15f || p.alpha > 0.75f) p.alphaSpeed = -p.alphaSpeed;
            // 边界回绕
            if (p.y < -10) { p.y = h + 10; p.x = random.nextFloat() * w; }
            if (p.x < -10) p.x = w + 10;
            if (p.x > w + 10) p.x = -10;

            paint.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }
        postInvalidateOnAnimation();
    }
}
