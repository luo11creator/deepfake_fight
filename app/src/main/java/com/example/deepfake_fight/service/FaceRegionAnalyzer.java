package com.example.deepfake_fight.service;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 面部区域分析器 — 为检测结果添加可视化标注
 *
 * 核心功能（不重新训练模型）：
 * 1. 使用ML Kit检测人脸 + 关键点 → 绘制人脸框
 * 2. 提取7个面部子区域（眼/鼻/口/额/脸颊）→ 独立推理
 * 3. 根据各区域风险分数生成热力图叠加层
 * 4. 在原图上叠加标注信息（置信度、区域得分、风险判定依据）
 *
 * 技术方案：逐区域推理（Region-based Occlusion Analysis）
 * - 将面部各区域裁剪后独立送入EfficientNet-B0
 * - 各区域的fake_score揭示哪些部位最可疑
 * - 无需修改模型，无需重新训练
 */
public class FaceRegionAnalyzer {
    private static final String TAG = "FaceRegionAnalyzer";

    // 7个分析区域定义
    public enum RegionType {
        FULL_FACE("全脸", 1.0f),
        LEFT_EYE("左眼区域", 0.6f),
        RIGHT_EYE("右眼区域", 0.6f),
        NOSE("鼻部区域", 0.5f),
        MOUTH("嘴部区域", 0.7f),
        FOREHEAD("额头区域", 0.5f),
        LEFT_CHEEK("左脸颊", 0.4f),
        RIGHT_CHEEK("右脸颊", 0.4f);

        public final String label;
        public final float cropScale;  // 裁剪区域相对于关键点间距的缩放比例

        RegionType(String label, float cropScale) {
            this.label = label;
            this.cropScale = cropScale;
        }
    }

    // 风险颜色映射
    private static final int COLOR_LOW_RISK    = 0x664CAF50;  // 绿色半透明
    private static final int COLOR_MED_RISK    = 0x66FFC107;  // 黄色半透明
    private static final int COLOR_HIGH_RISK   = 0x66FF5722;  // 红色半透明
    private static final int COLOR_BOX_LOW     = 0xFF4CAF50;
    private static final int COLOR_BOX_MED     = 0xFFFFC107;
    private static final int COLOR_BOX_HIGH    = 0xFFFF5722;
    private static final int COLOR_TEXT_SHADOW = 0xAA000000;

    private FaceDetector faceDetector;
    private LocalVideoDetector deepfakeDetector;

    public FaceRegionAnalyzer() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector = FaceDetection.getClient(options);
        deepfakeDetector = LocalVideoDetector.getInstance();
    }

    /**
     * 分析结果：包含标注后的图像和各区域详细得分
     */
    public static class AnalysisResult {
        public Bitmap annotatedImage;             // 标注后的图像
        public Map<String, Float> regionScores;    // 各区域 → 风险分数
        public List<FaceInfo> detectedFaces;       // 检测到的人脸信息
        public String summaryText;                 // 分析摘要文案
        public long analysisTimeMs;                // 分析耗时

        public static class FaceInfo {
            public Rect boundingBox;
            public float riskScore;
            public List<LandmarkInfo> landmarks;

            public static class LandmarkInfo {
                public String type;
                public PointF position;
                public float regionScore;
            }
        }
    }

    /**
     * 对图片进行完整的可视化分析
     *
     * @param originalImage 原始图片
     * @param overallScore  模型整体风险分数 (来自 LocalVideoDetector)
     * @return 包含标注图像和详细得分的分析结果
     */
    // 动态计算的标注尺寸（根据图片分辨率自适应）
    private float annotationTextSize;
    private float annotationSmallSize;
    private float boxStrokeWidth;
    private float pointRadius;
    private int imgWidth;
    private int imgHeight;

    public AnalysisResult analyze(Bitmap originalImage, float overallScore) {
        long startTime = System.currentTimeMillis();
        AnalysisResult result = new AnalysisResult();
        result.regionScores = new HashMap<>();

        // 保持原图分辨率（不要过度压缩），最大边长提升到3072
        Bitmap workingImage = resizeIfNeeded(originalImage, 3072);
        this.imgWidth = workingImage.getWidth();
        this.imgHeight = workingImage.getHeight();
        // 根据图片分辨率动态计算标注尺寸
        float scaleFactor = Math.min(imgWidth, imgHeight) / 800f;
        this.annotationTextSize = Math.max(13f, 18f * scaleFactor);
        this.annotationSmallSize = Math.max(9f, 12f * scaleFactor);
        this.boxStrokeWidth = Math.max(3f, 5f * scaleFactor);
        this.pointRadius = Math.max(4f, 6f * scaleFactor);

        Bitmap annotatedImage = workingImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotatedImage);

        List<Face> faces = null;
        try {
            // 第一步：人脸检测
            InputImage inputImage = InputImage.fromBitmap(workingImage, 0);
            try {
                faces = Tasks.await(faceDetector.process(inputImage));
            } catch (Exception e) {
                Log.e(TAG, "人脸检测失败", e);
                // 无人脸时做整图网格分析
                drawNoFaceAnalysis(canvas, workingImage, overallScore, result);
                result.annotatedImage = annotatedImage;
                result.analysisTimeMs = System.currentTimeMillis() - startTime;
                return result;
            }

            if (faces.isEmpty()) {
                Log.w(TAG, "未检测到人脸，降级为整图分析");
                drawNoFaceAnalysis(canvas, workingImage, overallScore, result);
                result.annotatedImage = annotatedImage;
                result.analysisTimeMs = System.currentTimeMillis() - startTime;
                return result;
            }

            result.detectedFaces = new ArrayList<>();

            // 第二步：对每张人脸进行分析
            for (int fIdx = 0; fIdx < faces.size(); fIdx++) {
                Face face = faces.get(fIdx);
                Rect faceBounds = face.getBoundingBox();

                // 裁剪全脸区域推理
                Bitmap faceCrop = cropBitmap(workingImage, faceBounds, 224);
                float faceScore = overallScore;
                try {
                    LocalVideoDetector.DetectResult faceResult = deepfakeDetector.detect(faceCrop);
                    faceScore = faceResult.riskScore;
                } catch (Exception e) {
                    Log.w(TAG, "全脸区域推理失败，使用整体分数");
                }
                faceCrop.recycle();

                // 记录人脸信息
                AnalysisResult.FaceInfo faceInfo = new AnalysisResult.FaceInfo();
                faceInfo.boundingBox = faceBounds;
                faceInfo.riskScore = faceScore;

                // 第三步：对每个面部子区域独立推理
                Map<String, Float> regionScoreMap = analyzeFaceRegions(workingImage, face, faceBounds);

                // 合并到总结果
                for (Map.Entry<String, Float> entry : regionScoreMap.entrySet()) {
                    String key = faces.size() > 1 ? "人脸" + (fIdx + 1) + "-" + entry.getKey() : entry.getKey();
                    result.regionScores.put(key, entry.getValue());
                }
                result.regionScores.put(faces.size() > 1 ? "人脸" + (fIdx + 1) + "-全脸" : "全脸", faceScore);

                // 第四步：绘制标注
                drawFaceAnnotations(canvas, face, faceBounds, faceScore, regionScoreMap, fIdx, faces.size());

                result.detectedFaces.add(faceInfo);
            }

            // 第五步：绘制全局信息
            drawGlobalOverlay(canvas, workingImage.getWidth(), workingImage.getHeight(),
                    overallScore, result.regionScores, faces.size());

            // 生成摘要文案
            result.summaryText = buildSummary(overallScore, result.regionScores, faces.size());

        } catch (Exception e) {
            Log.e(TAG, "分析过程异常", e);
        }

        result.annotatedImage = annotatedImage;
        result.analysisTimeMs = System.currentTimeMillis() - startTime;
        Log.i(TAG, String.format("分析完成: %d张人脸, %d个区域, 耗时%dms",
                faces != null ? faces.size() : 0, result.regionScores.size(), result.analysisTimeMs));

        return result;
    }

    /**
     * 分析面部各子区域
     */
    private Map<String, Float> analyzeFaceRegions(Bitmap image, Face face, Rect faceBounds) {
        Map<String, Float> scores = new HashMap<>();

        // 基于关键点计算各区域的裁剪矩形
        Map<String, Rect> regions = extractFaceRegions(face, faceBounds);
        for (Map.Entry<String, Rect> entry : regions.entrySet()) {
            String regionName = entry.getKey();
            Rect cropRect = entry.getValue();

            // 确保裁剪区域在图像范围内
            cropRect.left = Math.max(0, cropRect.left);
            cropRect.top = Math.max(0, cropRect.top);
            cropRect.right = Math.min(image.getWidth(), cropRect.right);
            cropRect.bottom = Math.min(image.getHeight(), cropRect.bottom);

            if (cropRect.width() < 20 || cropRect.height() < 20) {
                scores.put(regionName, -1f); // 区域太小，跳过
                continue;
            }

            Bitmap regionCrop = cropBitmap(image, cropRect, 224);
            try {
                LocalVideoDetector.DetectResult regionResult = deepfakeDetector.detect(regionCrop);
                scores.put(regionName, regionResult.riskScore);
            } catch (Exception e) {
                Log.w(TAG, regionName + " 区域推理失败");
                scores.put(regionName, -1f);
            }
            regionCrop.recycle();
        }

        return scores;
    }

    /**
     * 基于ML Kit关键点提取面部子区域
     */
    private Map<String, Rect> extractFaceRegions(Face face, Rect faceBounds) {
        Map<String, Rect> regions = new HashMap<>();
        int faceWidth = faceBounds.width();
        int faceHeight = faceBounds.height();

        // 使用 landmark 作为锚点，结合人脸框计算区域
        PointF noseBase = getLandmarkPosition(face, FaceLandmark.NOSE_BASE);
        PointF leftEye = getLandmarkPosition(face, FaceLandmark.LEFT_EYE);
        PointF rightEye = getLandmarkPosition(face, FaceLandmark.RIGHT_EYE);
        PointF mouthLeft = getLandmarkPosition(face, FaceLandmark.MOUTH_LEFT);
        PointF mouthRight = getLandmarkPosition(face, FaceLandmark.MOUTH_RIGHT);
        PointF mouthBottom = getLandmarkPosition(face, FaceLandmark.MOUTH_BOTTOM);
        PointF leftEar = getLandmarkPosition(face, FaceLandmark.LEFT_EAR);
        PointF rightEar = getLandmarkPosition(face, FaceLandmark.RIGHT_EAR);

        // 眼睛间距 (用于计算区域尺寸)
        float eyeDist = (leftEye != null && rightEye != null)
                ? Math.abs(rightEye.x - leftEye.x) : faceWidth * 0.3f;

        // 1. 左眼区域
        if (leftEye != null) {
            int eyeW = (int)(eyeDist * 0.8f);
            int eyeH = (int)(eyeW * 0.6f);
            regions.put("左眼区域", new Rect(
                    (int)(leftEye.x - eyeW / 2f),
                    (int)(leftEye.y - eyeH / 2f),
                    (int)(leftEye.x + eyeW / 2f),
                    (int)(leftEye.y + eyeH / 2f)
            ));
        }

        // 2. 右眼区域
        if (rightEye != null) {
            int eyeW = (int)(eyeDist * 0.8f);
            int eyeH = (int)(eyeW * 0.6f);
            regions.put("右眼区域", new Rect(
                    (int)(rightEye.x - eyeW / 2f),
                    (int)(rightEye.y - eyeH / 2f),
                    (int)(rightEye.x + eyeW / 2f),
                    (int)(rightEye.y + eyeH / 2f)
            ));
        }

        // 3. 鼻部区域
        if (noseBase != null) {
            int noseW = (int)(eyeDist * 0.5f);
            int noseH = (int)(noseW * 1.2f);
            regions.put("鼻部区域", new Rect(
                    (int)(noseBase.x - noseW / 2f),
                    (int)(noseBase.y - noseH * 0.4f),
                    (int)(noseBase.x + noseW / 2f),
                    (int)(noseBase.y + noseH * 0.6f)
            ));
        }

        // 4. 嘴部区域
        if (mouthLeft != null && mouthRight != null) {
            int mouthW = (int)(Math.abs(mouthRight.x - mouthLeft.x) * 1.3f);
            int mouthH = (int)(eyeDist * 0.75f);
            float mouthCenterX = (mouthLeft.x + mouthRight.x) / 2f;
            float mouthCenterY = (mouthLeft.y + mouthRight.y) / 2f;
            if (mouthBottom != null) mouthCenterY = (mouthCenterY + mouthBottom.y) / 2f;
            regions.put("嘴部区域", new Rect(
                    (int)(mouthCenterX - mouthW / 2f),
                    (int)(mouthCenterY - mouthH / 2f),
                    (int)(mouthCenterX + mouthW / 2f),
                    (int)(mouthCenterY + mouthH / 2f)
            ));
        }

        // 5. 额头区域 (从眉毛到人脸框顶部)
        if (leftEye != null && rightEye != null) {
            float eyeCenterY = (leftEye.y + rightEye.y) / 2f;
            int foreTop = faceBounds.top;
            int foreBottom = (int)(eyeCenterY - eyeDist * 0.3f);
            int foreLeft = faceBounds.left + faceWidth / 6;
            int foreRight = faceBounds.right - faceWidth / 6;
            if (foreBottom > foreTop + 20) {
                regions.put("额头区域", new Rect(foreLeft, foreTop, foreRight, foreBottom));
            }
        }

        // 6. 左脸颊
        if (leftEye != null && noseBase != null) {
            int cheekW = (int)(eyeDist * 0.7f);
            int cheekH = (int)(eyeDist * 0.9f);
            float cheekCenterX = faceBounds.left + faceWidth * 0.22f;
            float cheekCenterY = noseBase.y;
            regions.put("左脸颊", new Rect(
                    (int)(cheekCenterX - cheekW / 2f),
                    (int)(cheekCenterY - cheekH / 2f),
                    (int)(cheekCenterX + cheekW / 2f),
                    (int)(cheekCenterY + cheekH / 2f)
            ));
        }

        // 7. 右脸颊
        if (rightEye != null && noseBase != null) {
            int cheekW = (int)(eyeDist * 0.7f);
            int cheekH = (int)(eyeDist * 0.9f);
            float cheekCenterX = faceBounds.right - faceWidth * 0.22f;
            float cheekCenterY = noseBase.y;
            regions.put("右脸颊", new Rect(
                    (int)(cheekCenterX - cheekW / 2f),
                    (int)(cheekCenterY - cheekH / 2f),
                    (int)(cheekCenterX + cheekW / 2f),
                    (int)(cheekCenterY + cheekH / 2f)
            ));
        }

        return regions;
    }

    /**
     * 绘制人脸标注（框 + 区域热力图 + 关键点 + 标签）
     */
    private void drawFaceAnnotations(Canvas canvas, Face face, Rect faceBounds,
                                     float faceScore, Map<String, Float> regionScores,
                                     int faceIdx, int totalFaces) {
        // --- ① 绘制人脸边框 ---
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(boxStrokeWidth);
        boxPaint.setAntiAlias(true);
        int boxColor = getRiskColor(faceScore, false);
        boxPaint.setColor(boxColor);
        canvas.drawRect(faceBounds, boxPaint);

        // 外发光效果
        Paint glowPaint = new Paint();
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(boxStrokeWidth * 2f);
        glowPaint.setAntiAlias(true);
        glowPaint.setColor(getRiskColor(faceScore, true));
        glowPaint.setAlpha(80);
        canvas.drawRect(faceBounds, glowPaint);

        // --- ② 绘制区域热力图叠加 ---
        for (Map.Entry<String, Float> entry : regionScores.entrySet()) {
            float score = entry.getValue();
            if (score < 0) continue;

            // 根据区域名找到对应矩形（重新提取以获取裁剪位置）
            // 这里使用已推理过的区域分数，绘制半透明覆盖
            drawRegionHeatOverlay(canvas, face, faceBounds, entry.getKey(), score);
        }

        // --- ③ 绘制人脸标签 ---
        String faceLabel = String.format("%s人脸 风险: %.0f%%",
                totalFaces > 1 ? "#" + (faceIdx + 1) + " " : "",
                faceScore * 100);
        drawLabel(canvas, faceLabel, faceBounds.left, faceBounds.top - 12, boxColor, annotationTextSize);

        // --- ④ 绘制关键点 ---
        Paint dotPaint = new Paint();
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);
        dotPaint.setAntiAlias(true);

        int[] landmarkTypes = {
                FaceLandmark.NOSE_BASE, FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE,
                FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM,
                FaceLandmark.LEFT_EAR, FaceLandmark.RIGHT_EAR
        };
        for (int type : landmarkTypes) {
            FaceLandmark lm = face.getLandmark(type);
            if (lm != null) {
                canvas.drawCircle(lm.getPosition().x, lm.getPosition().y, pointRadius, dotPaint);
            }
        }

        // --- ⑤ 绘制各区域风险分数的标注线 ---
        drawRegionScoreLabels(canvas, face, faceBounds, regionScores);
    }

    /**
     * 绘制区域热力图覆盖层
     */
    private void drawRegionHeatOverlay(Canvas canvas, Face face, Rect faceBounds,
                                       String regionName, float score) {
        Map<String, Rect> regions = extractFaceRegions(face, faceBounds);
        Rect rect = regions.get(regionName);
        if (rect == null) return;

        Paint heatPaint = new Paint();
        heatPaint.setStyle(Paint.Style.FILL);
        heatPaint.setAntiAlias(true);
        heatPaint.setColor(getRiskColor(score, true));
        heatPaint.setAlpha(Math.round(80 + score * 60)); // 分数越高越不透明

        // 圆角矩形效果：使用较小的圆角
        canvas.drawRoundRect(new RectF(rect), 8, 8, heatPaint);

        // 区域边框
        Paint borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);
        borderPaint.setColor(getRiskColor(score, false));
        borderPaint.setAlpha(160);
        canvas.drawRoundRect(new RectF(rect), 8, 8, borderPaint);
    }

    /**
     * 绘制各区域风险分数标签
     */
    private void drawRegionScoreLabels(Canvas canvas, Face face, Rect faceBounds,
                                       Map<String, Float> regionScores) {
        // 只在有足够空间时绘制
        if (faceBounds.width() < 150) return;

        Paint labelBgPaint = new Paint();
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(COLOR_TEXT_SHADOW);
        labelBgPaint.setAlpha(160);
        labelBgPaint.setAntiAlias(true);

        Paint labelTextPaint = new Paint();
        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(annotationSmallSize);
        labelTextPaint.setAntiAlias(true);
        labelTextPaint.setFakeBoldText(true);

        Map<String, Rect> regions = extractFaceRegions(face, faceBounds);
        int drawn = 0;
        for (Map.Entry<String, Float> entry : regionScores.entrySet()) {
            if (drawn >= 4) break; // 最多显示4个分数，避免画面太乱
            Rect rect = regions.get(entry.getKey());
            if (rect == null || entry.getValue() < 0) continue;

            String shortLabel = entry.getKey().replace("区域", "");
            String text = String.format("%s:%.0f%%", shortLabel, entry.getValue() * 100);

            float textWidth = labelTextPaint.measureText(text);
            float textX = rect.centerX() - textWidth / 2f;
            float textY = rect.centerY() + 4f;

            // 背景
            canvas.drawRoundRect(
                    textX - 4, textY - 12, textX + textWidth + 4, textY + 4,
                    4, 4, labelBgPaint);
            // 文字
            canvas.drawText(text, textX, textY, labelTextPaint);
            drawn++;
        }
    }

    /**
     * 绘制全局信息叠加层
     */
    private void drawGlobalOverlay(Canvas canvas, int imgWidth, int imgHeight,
                                   float overallScore, Map<String, Float> regionScores, int faceCount) {
        float textBase = annotationTextSize;
        float textSmall = annotationSmallSize;
        int overlayHeight = Math.round(50 + textBase * 3.5f + (regionScores.size() > 0 ? textSmall * 2 : 0));
        int margin = Math.round(16 * Math.min(imgWidth, imgHeight) / 800f);

        // 顶部半透明背景条
        Paint bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xCC1A1A2E);
        bgPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, imgWidth, overlayHeight, bgPaint);

        // 底部渐变线
        Paint linePaint = new Paint();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f);
        linePaint.setColor(getRiskColor(overallScore, false));
        linePaint.setAlpha(200);
        canvas.drawLine(0, overlayHeight, imgWidth, overlayHeight, linePaint);

        // 风险等级文字
        Paint riskTextPaint = new Paint();
        riskTextPaint.setColor(Color.WHITE);
        riskTextPaint.setTextSize(textBase * 1.3f);
        riskTextPaint.setAntiAlias(true);
        riskTextPaint.setFakeBoldText(true);

        String verdict = overallScore >= 0.8f ? "⚠ 高风险伪造" :
                overallScore >= 0.6f ? "⚠ 疑似伪造" :
                        overallScore >= 0.4f ? "🔍 需确认" : "✅ 可能真实";
        float lineY = margin + textBase * 1.3f;
        canvas.drawText(verdict, margin, lineY, riskTextPaint);

        // 整体置信度
        Paint scorePaint = new Paint();
        scorePaint.setColor(getRiskColor(overallScore, false));
        scorePaint.setTextSize(textBase);
        scorePaint.setAntiAlias(true);
        String scoreText = String.format("模型置信度: %.1f%% | 检测到%d张人脸",
                overallScore * 100, faceCount);
        lineY += textBase * 1.5f;
        canvas.drawText(scoreText, margin, lineY, scorePaint);

        // 最高风险区域提示
        if (!regionScores.isEmpty()) {
            String highRegion = null;
            float highScore = 0;
            for (Map.Entry<String, Float> e : regionScores.entrySet()) {
                if (e.getValue() > highScore) {
                    highScore = e.getValue();
                    highRegion = e.getKey();
                }
            }
            if (highRegion != null && highScore > 0.5f) {
                lineY += textSmall * 1.8f;
                Paint hintPaint = new Paint();
                hintPaint.setColor(0xFFFFC107);
                hintPaint.setTextSize(textSmall);
                hintPaint.setAntiAlias(true);
                canvas.drawText(String.format("🔴 最高风险区域: %s (%.0f%%)", highRegion, highScore * 100),
                        margin, lineY, hintPaint);
            }
        }

        // 底部水印
        Paint watermarkPaint = new Paint();
        watermarkPaint.setColor(0x66FFFFFF);
        watermarkPaint.setTextSize(textSmall);
        watermarkPaint.setAntiAlias(true);
        float waterW = watermarkPaint.measureText("多维盾卫 · AI视觉分析");
        canvas.drawText("多维盾卫 · AI视觉分析", imgWidth - waterW - margin, imgHeight - margin, watermarkPaint);
    }

    /**
     * 无人脸时的降级分析：网格遮挡分析
     */
    private void drawNoFaceAnalysis(Canvas canvas, Bitmap image, float overallScore,
                                    AnalysisResult result) {
        int gridCols = 4;
        int gridRows = 4;
        int cellW = image.getWidth() / gridCols;
        int cellH = image.getHeight() / gridRows;

        Paint gridPaint = new Paint();
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setColor(0x66FFFFFF);
        gridPaint.setAntiAlias(true);

        Paint cellBgPaint = new Paint();
        cellBgPaint.setStyle(Paint.Style.FILL);
        cellBgPaint.setAntiAlias(true);

        // 逐网格推理
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                int x = col * cellW;
                int y = row * cellH;
                Rect cellRect = new Rect(x, y, x + cellW, y + cellH);

                // 对该网格区域做推理
                Bitmap cellCrop = cropBitmap(image, cellRect, 224);
                float cellScore = overallScore; // 默认使用整体分数
                try {
                    LocalVideoDetector.DetectResult cellResult = deepfakeDetector.detect(cellCrop);
                    cellScore = cellResult.riskScore;
                } catch (Exception e) {
                    // 保持默认
                }
                cellCrop.recycle();

                String cellKey = String.format("网格[%d,%d]", row, col);
                result.regionScores.put(cellKey, cellScore);

                // 绘制网格颜色
                cellBgPaint.setColor(getRiskColor(cellScore, true));
                cellBgPaint.setAlpha(Math.round(40 + cellScore * 50));
                canvas.drawRect(cellRect, cellBgPaint);
                canvas.drawRect(cellRect, gridPaint);

                // 低分辨率时只画颜色不写文字
                if (image.getWidth() >= 800) {
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(9f);
                    textPaint.setAntiAlias(true);
                    canvas.drawText(String.format("%.0f", cellScore * 100),
                            x + 4, y + cellH - 4, textPaint);
                }
            }
        }

        // 无人脸提示
        drawGlobalOverlay(canvas, image.getWidth(), image.getHeight(),
                overallScore, result.regionScores, 0);

        Paint noFacePaint = new Paint();
        noFacePaint.setColor(0xFFFFC107);
        noFacePaint.setTextSize(16f);
        noFacePaint.setAntiAlias(true);
        noFacePaint.setFakeBoldText(true);
        canvas.drawText("未检测到清晰人脸 · 使用网格分析模式",
                20, canvas.getHeight() - 50, noFacePaint);

        result.summaryText = "未检测到人脸。已使用4×4网格遮挡分析，每个网格独立推理。颜色越红的区域越可疑。";
    }

    // ==================== 工具方法 ====================

    private PointF getLandmarkPosition(Face face, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        return landmark != null ? landmark.getPosition() : null;
    }

    private Bitmap cropBitmap(Bitmap source, Rect rect, int targetSize) {
        int left = Math.max(0, rect.left);
        int top = Math.max(0, rect.top);
        int right = Math.min(source.getWidth(), rect.right);
        int bottom = Math.min(source.getHeight(), rect.bottom);
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) {
            // fallback: 返回原图缩放
            return Bitmap.createScaledBitmap(source, targetSize, targetSize, true);
        }
        Bitmap crop = Bitmap.createBitmap(source, left, top, w, h);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, targetSize, targetSize, true);
        crop.recycle();
        return scaled;
    }

    private Bitmap resizeIfNeeded(Bitmap bitmap, int maxSize) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= maxSize && h <= maxSize) return bitmap;
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        return Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
    }

    private int getRiskColor(float score, boolean translucent) {
        if (score >= 0.7f) return translucent ? COLOR_HIGH_RISK : COLOR_BOX_HIGH;
        if (score >= 0.4f) return translucent ? COLOR_MED_RISK : COLOR_BOX_MED;
        return translucent ? COLOR_LOW_RISK : COLOR_BOX_LOW;
    }

    private void drawLabel(Canvas canvas, String text, float x, float y, int color, float textSize) {
        Paint bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(COLOR_TEXT_SHADOW);
        bgPaint.setAlpha(180);
        bgPaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        float textW = textPaint.measureText(text);
        float drawX = Math.max(4, Math.min(x, canvas.getWidth() - textW - 8));
        float drawY = Math.max(textSize + 4, y);

        canvas.drawRoundRect(drawX - 6, drawY - textSize - 6,
                drawX + textW + 6, drawY + 4, 6, 6, bgPaint);
        canvas.drawText(text, drawX, drawY, textPaint);
    }

    private String buildSummary(float overallScore, Map<String, Float> regionScores, int faceCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("检测到%d张人脸，整体风险: %.0f%%\n", faceCount, overallScore * 100));

        if (!regionScores.isEmpty()) {
            // 排序取前三高风险区域
            List<Map.Entry<String, Float>> sorted = new ArrayList<>(regionScores.entrySet());
            sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

            sb.append("可疑区域 TOP 3:\n");
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                Map.Entry<String, Float> e = sorted.get(i);
                if (e.getValue() >= 0) {
                    sb.append(String.format("  %d. %s: %.0f%%%s\n",
                            i + 1, e.getKey(), e.getValue() * 100,
                            e.getValue() > 0.6f ? " ⚠" : ""));
                }
            }
        }

        if (overallScore > 0.6f) {
            sb.append("\n⚠ 检测到明显伪造特征，请高度警惕。");
        }
        return sb.toString();
    }

    public void release() {
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}
