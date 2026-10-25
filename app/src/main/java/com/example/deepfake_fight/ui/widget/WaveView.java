package com.example.deepfake_fight.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 说话波形 —— 智能体"正在说话/思考"时的跳动竖条，营造数字人对话感。
 *
 * 用法：
 *   waveView.start();   // 开始跳动
 *   waveView.stop();    // 停止
 */
public class WaveView extends View {

    private static final int BAR_COUNT = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = new float[BAR_COUNT];

    private boolean animating = false;
    private long lastFrame = 0;

    public WaveView(Context context) {
        super(context);
        paint.setColor(Color.parseColor("#C9A227"));  // 金色
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.parseColor("#C9A227"));
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void start() {
        animating = true;
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    public void stop() {
        animating = false;
        for (int i = 0; i < BAR_COUNT; i++) heights[i] = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animating) {
            long now = System.currentTimeMillis();
            if (now - lastFrame > 80) {
                lastFrame = now;
                for (int i = 0; i < BAR_COUNT; i++) {
                    // 随机高度，制造声波跳动感
                    heights[i] = 5 + (float) Math.random() * (getHeight() - 10);
                }
            }
        }

        float cx = getWidth() / 2f;
        float gap = dp(10);
        float totalWidth = (BAR_COUNT - 1) * gap;
        float startX = cx - totalWidth / 2f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float h = heights[i];
            if (h <= 0) h = dp(4);  // 静止时显示矮条
            float x = startX + i * gap;
            canvas.drawLine(x, getHeight() / 2f - h / 2f, x, getHeight() / 2f + h / 2f, paint);
        }

        if (animating) postInvalidateOnAnimation();
    }
}
