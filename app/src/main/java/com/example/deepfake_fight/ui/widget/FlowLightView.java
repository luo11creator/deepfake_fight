package com.example.deepfake_fight.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 流光背景 —— 几个金色光斑缓慢横向移动，营造流光溢彩的科技感。
 * 用于「社区」页差异化背景。
 */
public class FlowLightView extends View {

    private static class Light {
        float x, y, radius, speedX, speedY, alpha;
    }

    private final List<Light> lights = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private int w, h;
    private boolean inited = false;

    public FlowLightView(Context context) {
        super(context);
    }

    public FlowLightView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void initIfNeeded() {
        if (inited) return;
        inited = true;
        for (int i = 0; i < 6; i++) {
            Light l = new Light();
            l.radius = 120 + random.nextFloat() * 160;
            l.speedX = 0.4f + random.nextFloat() * 0.8f;
            l.speedY = (random.nextFloat() - 0.5f) * 0.3f;
            l.alpha = 0.10f + random.nextFloat() * 0.15f;
            lights.add(l);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.w = w;
        this.h = h;
        if (!inited) {
            initIfNeeded();
            for (Light l : lights) {
                l.x = random.nextFloat() * w;
                l.y = random.nextFloat() * h;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w == 0 || h == 0) return;

        for (Light l : lights) {
            l.x += l.speedX;
            l.y += l.speedY;
            if (l.x > w + l.radius) { l.x = -l.radius; l.y = random.nextFloat() * h; }
            if (l.y < -l.radius) l.y = h + l.radius;
            if (l.y > h + l.radius) l.y = -l.radius;

            // 金色光斑（径向渐变，中心亮、边缘透明）
            Shader shader = new RadialGradient(
                    l.x, l.y, l.radius,
                    new int[]{Color.argb((int) (l.alpha * 255), 212, 175, 55), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawCircle(l.x, l.y, l.radius, paint);
        }
        paint.setShader(null);
        postInvalidateOnAnimation();
    }
}
