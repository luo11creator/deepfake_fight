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
 * 星空背景 —— 深色背景上闪烁的星点，营造夜空感。
 * 用于「我的」页差异化背景。
 */
public class StarFieldView extends View {

    private static class Star {
        float x, y, radius, alpha, alphaSpeed, twinkle;
    }

    private final List<Star> stars = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private int w, h;
    private boolean inited = false;

    public StarFieldView(Context context) {
        super(context);
    }

    public StarFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void initIfNeeded() {
        if (inited) return;
        inited = true;
        for (int i = 0; i < 50; i++) {
            Star s = new Star();
            s.radius = 0.8f + random.nextFloat() * 2.0f;
            s.alpha = 0.3f + random.nextFloat() * 0.7f;
            s.alphaSpeed = 0.01f + random.nextFloat() * 0.03f;
            s.twinkle = random.nextFloat() * (float) Math.PI * 2;
            stars.add(s);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.w = w;
        this.h = h;
        if (!inited) {
            initIfNeeded();
            for (Star s : stars) {
                s.x = random.nextFloat() * w;
                s.y = random.nextFloat() * h;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w == 0 || h == 0) return;

        for (Star s : stars) {
            s.twinkle += s.alphaSpeed;
            float twinkleAlpha = s.alpha * (0.5f + 0.5f * (float) Math.sin(s.twinkle));
            paint.setColor(Color.argb((int) (twinkleAlpha * 255), 255, 255, 255));
            canvas.drawCircle(s.x, s.y, s.radius, paint);
        }
        postInvalidateOnAnimation();
    }
}
