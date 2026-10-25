package com.example.deepfake_fight.service;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * 人脸检测辅助类
 * 使用 Google ML Kit (内置TFLite模型), 离线可用
 */
public class FaceDetectionHelper {

    private static final String TAG = "FaceDetectionHelper";

    public interface FaceDetectionListener {
        void onFacesDetected(List<Face> faces, int imageWidth, int imageHeight);
        void onDetectionError(Exception e);
    }

    private final FaceDetector detector;

    public FaceDetectionHelper() {
        // 精确模式：更紧密的人脸框 + 关键点(类似YOLO级别的检测质量)
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f)
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
    }

    /**
     * 分析摄像头帧
     */
    public void analyze(ImageProxy imageProxy, FaceDetectionListener listener) {
        @SuppressWarnings("UnsafeOptInUsageError")
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(), rotation);

        // 根据旋转方向交换宽高（摄像头sensor是横的，手机是竖的）
        int imgW = imageProxy.getWidth();
        int imgH = imageProxy.getHeight();
        if (rotation == 90 || rotation == 270) {
            imgW = imageProxy.getHeight();
            imgH = imageProxy.getWidth();
        }

        final int fw = imgW, fh = imgH;
        detector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    listener.onFacesDetected(faces, fw, fh);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed", e);
                    listener.onDetectionError(e);
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close();
                });
    }

    public void close() {
        detector.close();
    }
}
