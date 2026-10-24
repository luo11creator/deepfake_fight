package com.example.deepfake_fight.ui.video;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * 人脸覆盖层 — 正确处理sensor→显示坐标系映射
 *
 * 坐标变换流程:
 *   Sensor(W×H) → 旋转(rotation) → 镜像(front camera) → 缩放(→View)
 */
public class FaceOverlayView extends View {

    private final Paint boxPaint, boxFillPaint, cornerPaint;
    private final Paint landmarkPaint, contourPaint;
    private final Paint labelBgPaint, labelTextPaint;
    private final Paint riskPaint;

    private List<Face> faces;
    private int imageWidth, imageHeight;
    private int imageRotation; // 0, 90, 180, 270
    private boolean isFrontCamera = true;
    private float deepfakeScore = 0f;
    private Matrix transformMatrix;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(Color.parseColor("#4FC3F7"));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);

        boxFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxFillPaint.setColor(Color.parseColor("#104FC3F7"));
        boxFillPaint.setStyle(Paint.Style.FILL);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.parseColor("#4FC3F7"));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        landmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        landmarkPaint.setColor(Color.WHITE);
        landmarkPaint.setStyle(Paint.Style.FILL);

        contourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        contourPaint.setColor(Color.parseColor("#804FC3F7"));
        contourPaint.setStyle(Paint.Style.STROKE);
        contourPaint.setStrokeWidth(1.5f);

        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setColor(Color.parseColor("#CC000000"));
        labelBgPaint.setStyle(Paint.Style.FILL);

        labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(32f);

        riskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        riskPaint.setTextSize(28f);

        transformMatrix = new Matrix();
    }

    /**
     * 设置检测到的人脸和原始图像参数
     * @param faces ML Kit返回的人脸列表
     * @param imageW 图像宽度 (sensor坐标系, rotation校正前)
     * @param imageH 图像高度 (sensor坐标系, rotation校正前)
     * @param rotation 图像旋转角度 (0/90/180/270)
     * @param frontCamera 是否前置摄像头
     */
    public void updateFaces(List<Face> faces, int imageW, int imageH,
                            int rotation, boolean frontCamera) {
        this.faces = faces;
        this.imageWidth = imageW;
        this.imageHeight = imageH;
        this.imageRotation = rotation;
        this.isFrontCamera = frontCamera;
        this.transformMatrix = computeTransform();
        invalidate();
    }

    public void setDeepfakeScore(float score) {
        this.deepfakeScore = score;
        invalidate();
    }

    /**
     * 计算sensor坐标 → View坐标的变换矩阵
     * 步骤: 1)旋转 2)镜像(前置) 3)缩放到View
     */
    private Matrix computeTransform() {
        Matrix m = new Matrix();
        int vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0 || imageWidth <= 0 || imageHeight <= 0) return m;

        // 旋转后的图像尺寸
        boolean swap = (imageRotation == 90 || imageRotation == 270);
        int rotatedW = swap ? imageHeight : imageWidth;
        int rotatedH = swap ? imageWidth : imageHeight;

        // 在旋转后的图像空间中计算缩放(等比缩放,居中裁切)
        float scale = Math.max((float) vw / rotatedW, (float) vh / rotatedH);
        int scaledW = (int)(rotatedW * scale);
        int scaledH = (int)(rotatedH * scale);
        int offsetX = (vw - scaledW) / 2;
        int offsetY = (vh - scaledH) / 2;

        // Step 1: 平移+缩放(旋转后的空间 → View空间)
        m.postTranslate(offsetX, offsetY);
        m.postScale(scale, scale);

        // Step 2: 旋转(原始sensor → 旋转后)
        if (imageRotation != 0) {
            m.postRotate(-imageRotation, rotatedW / 2f, rotatedH / 2f);
            // 上面是绕旋转后图像中心旋转,需要调整
            // 简化:先做旋转再平移
            m.reset();
            m.postRotate(imageRotation);
            // 旋转后坐标,缩放到view
            m.postScale((float)vw / (swap?imageHeight:imageWidth),
                       (float)vh / (swap?imageWidth:imageHeight));
        } else {
            m.reset();
            m.postScale((float)vw / imageWidth, (float)vh / imageHeight);
        }

        // Step 3: 前置摄像头镜像
        if (isFrontCamera) {
            m.postScale(-1, 1, vw / 2f, vh / 2f);
        }

        return m;
    }

    /**
     * 将ML Kit坐标系的点映射到View坐标系。
     * ML Kit内部已根据传入的rotation做了旋转, 返回的坐标就是显示坐标,
     * 我们只需要缩放+镜像。
     */
    private float[] mapPoint(float mx, float my) {
        float vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return new float[]{mx, my};
        }

        // imageWidth/imageHeight 已经是FaceDetectionHelper rotation校正后的尺寸
        float scaleX = vw / (float) imageWidth;
        float scaleY = vh / (float) imageHeight;

        float x = mx * scaleX;
        float y = my * scaleY;

        // 前置摄像头水平镜像
        if (isFrontCamera) x = vw - x;

        return new float[]{x, y};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces == null || faces.isEmpty() || getWidth() <= 0) return;

        for (Face face : faces) {
            Rect b = face.getBoundingBox();
            // 四个角映射到View坐标
            float[] tl = mapPoint(b.left, b.top);
            float[] tr = mapPoint(b.right, b.top);
            float[] bl = mapPoint(b.left, b.bottom);
            float[] br = mapPoint(b.right, b.bottom);

            float left = Math.min(Math.min(tl[0], tr[0]), Math.min(bl[0], br[0]));
            float top = Math.min(Math.min(tl[1], tr[1]), Math.min(bl[1], br[1]));
            float right = Math.max(Math.max(tl[0], tr[0]), Math.max(bl[0], br[0]));
            float bottom = Math.max(Math.max(tl[1], tr[1]), Math.max(bl[1], br[1]));

            RectF box = new RectF(left, top, right, bottom);
            if (box.width() < 10 || box.height() < 10) continue;

            // 半透明填充 + 圆角框
            canvas.drawRoundRect(box, 12, 12, boxFillPaint);
            canvas.drawRoundRect(box, 12, 12, boxPaint);

            // YOLO风格角标
            float cl = 25f;
            drawCorner(canvas, left, top, cl, cl);
            drawCorner(canvas, right, top, -cl, cl);
            drawCorner(canvas, left, bottom, cl, -cl);
            drawCorner(canvas, right, bottom, -cl, -cl);

            // 关键点
            drawLM(canvas, face, FaceLandmark.NOSE_BASE);
            drawLM(canvas, face, FaceLandmark.LEFT_EYE);
            drawLM(canvas, face, FaceLandmark.RIGHT_EYE);
            drawLM(canvas, face, FaceLandmark.MOUTH_LEFT);
            drawLM(canvas, face, FaceLandmark.MOUTH_RIGHT);

            // 轮廓线
            drawContour(canvas, face, FaceContour.FACE);
            drawContour(canvas, face, FaceContour.LEFT_EYEBROW_TOP);
            drawContour(canvas, face, FaceContour.RIGHT_EYEBROW_TOP);

            // 标签
            String label = "Face";
            if (face.getTrackingId() != null) label += "#" + face.getTrackingId();
            float lw = labelTextPaint.measureText(label);
            RectF lr = new RectF(left, top - 30, left + lw + 16, top - 2);
            canvas.drawRoundRect(lr, 6, 6, labelBgPaint);
            canvas.drawText(label, left + 8, top - 8, labelTextPaint);

            // 深伪风险标签
            if (deepfakeScore > 0) {
                String t; int c;
                if (deepfakeScore >= 0.7f) {
                    t = "FAKE " + String.format("%.0f%%", deepfakeScore*100);
                    c = Color.parseColor("#FF1744");
                } else if (deepfakeScore >= 0.4f) {
                    t = "SUS " + String.format("%.0f%%", deepfakeScore*100);
                    c = Color.parseColor("#FF9100");
                } else {
                    t = "REAL " + String.format("%.0f%%", (1-deepfakeScore)*100);
                    c = Color.parseColor("#00E676");
                }
                riskPaint.setColor(c);
                float rw = riskPaint.measureText(t);
                float ry = bottom + 28;
                RectF rr = new RectF(left, ry - 22, left + rw + 14, ry + 6);
                Paint rbg = new Paint(Paint.ANTI_ALIAS_FLAG);
                rbg.setColor(Color.parseColor("#CC000000"));
                canvas.drawRoundRect(rr, 6, 6, rbg);
                canvas.drawText(t, left + 7, ry, riskPaint);
            }
        }
    }

    private void drawCorner(Canvas c, float x, float y, float dx, float dy) {
        c.drawLine(x, y, x + dx, y, cornerPaint);
        c.drawLine(x, y, x, y + dy, cornerPaint);
    }

    private void drawLM(Canvas c, Face face, int type) {
        FaceLandmark lm = face.getLandmark(type);
        if (lm != null) {
            float[] p = mapPoint(lm.getPosition().x, lm.getPosition().y);
            c.drawCircle(p[0], p[1], 5f, landmarkPaint);
        }
    }

    private void drawContour(Canvas c, Face face, int type) {
        FaceContour contour = face.getContour(type);
        if (contour != null && contour.getPoints().size() > 1) {
            Path path = new Path();
            boolean first = true;
            for (android.graphics.PointF pt : contour.getPoints()) {
                float[] p = mapPoint(pt.x, pt.y);
                if (first) { path.moveTo(p[0], p[1]); first = false; }
                else path.lineTo(p[0], p[1]);
            }
            c.drawPath(path, contourPaint);
        }
    }
}
