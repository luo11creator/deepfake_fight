package com.example.deepfake_fight.ui.video;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.graphics.BitmapFactory;
import java.io.File;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.ActivityCameraBinding;
import com.example.deepfake_fight.service.FaceDetectionHelper;
import com.example.deepfake_fight.service.LocalVideoDetector;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.Face;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private ActivityCameraBinding binding;

    private FaceDetectionHelper faceHelper;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private LocalVideoDetector deepfakeDetector;
    private long lastDeepfakeCheckTime = 0;
    private float lastDeepfakeScore = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        faceHelper = new FaceDetectionHelper();

        // 初始化Deepfake检测器（后台加载，避免主线程卡顿/加载失败）
        deepfakeDetector = LocalVideoDetector.getInstance();
        if (!deepfakeDetector.isModelLoaded()) {
            new Thread(() -> {
                try {
                    deepfakeDetector.initialize(CameraActivity.this);
                    runOnUiThread(() -> {
                        if (binding != null) binding.tvCameraStatus.setText("AI 模型就绪");
                    });
                } catch (Exception e) {
                    android.util.Log.e("CameraActivity", "Deepfake模型加载失败", e);
                    runOnUiThread(() -> {
                        if (binding != null) binding.tvCameraStatus.setText("模型加载失败");
                    });
                }
            }, "camera-model-init").start();
            binding.tvCameraStatus.setText("AI 模型加载中...");
        } else {
            binding.tvCameraStatus.setText("AI 模型就绪");
        }

        binding.btnCloseCamera.setOnClickListener(v -> finish());
        binding.btnSwitchCamera.setOnClickListener(v -> switchCamera());
        binding.btnCapture.setOnClickListener(v -> capturePhoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraWithRationale();
        }
    }

    private void requestCameraWithRationale() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("需要摄像头权限")
                    .setMessage("多维盾卫需要使用摄像头进行实时人脸检测，以识别视频通话中的Deepfake换脸欺诈。\n\n摄像头数据仅在手机本地处理，不会上传云端。")
                    .setPositiveButton("授权摄像头", (d, w) ->
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION))
                    .setNegativeButton("取消", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.CAMERA)) {
                // 用户选了"不再询问"
                new android.app.AlertDialog.Builder(this)
                        .setTitle("权限已被禁止")
                        .setMessage("摄像头权限已被禁止。请在系统设置中手动开启：\n设置 → 应用 → 多维盾卫 → 权限")
                        .setPositiveButton("前往设置", (d, w) -> {
                            startActivity(new Intent(android.provider.Settings
                                    .ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                            finish();
                        })
                        .setNegativeButton("取消", (d, w) -> finish())
                        .show();
            } else {
                Toast.makeText(this, "需要摄像头权限才能使用此功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Toast.makeText(this, "摄像头启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        // 预览
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

        // 图像分析（人脸检测）— 匹配预览分辨率避免坐标偏移
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new android.util.Size(480, 640))
                .build();

        boolean isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT);

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
            final int rot = imageProxy.getImageInfo().getRotationDegrees();
            faceHelper.analyze(imageProxy, new FaceDetectionHelper.FaceDetectionListener() {
                @Override
                public void onFacesDetected(List<Face> faces, int w, int h) {
                    runOnUiThread(() -> {
                        if (binding == null) return;
                        binding.faceOverlay.updateFaces(faces, w, h, rot, isFront);
                        if (!faces.isEmpty()) {
                            binding.tvFaceCount.setText(faces.size() + " 人 | " + getRiskText());
                        } else {
                            binding.tvFaceCount.setText("未检测到人脸");
                        }
                    });

                    // 每2秒自动拍照分析
                    long now = System.currentTimeMillis();
                    if (!faces.isEmpty() && now - lastDeepfakeCheckTime > 2000
                            && deepfakeDetector != null && deepfakeDetector.isModelLoaded()) {
                        lastDeepfakeCheckTime = now;
                        // 触发一次静默拍照用于分析
                        captureForAnalysis();
                    }
                }

                @Override
                public void onDetectionError(Exception e) {
                    runOnUiThread(() -> {
                        if (binding != null) binding.tvFaceCount.setText("检测异常");
                    });
                }
            });
        });

        // 选择摄像头
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        // 拍照
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
    }

    private void capturePhoto() {
        if (imageCapture == null) return;

        java.io.File photoFile = new java.io.File(getCacheDir(),
                "capture_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        // 拍照成功 → 跳回视频检测页并传入图片
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("capture_path", photoFile.getAbsolutePath());
                        setResult(RESULT_OK, resultIntent);

                        Toast.makeText(CameraActivity.this,
                                "已捕获图片，正在分析...", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(CameraActivity.this,
                                "拍照失败: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void switchCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        bindCameraUseCases();
        Toast.makeText(this,
                lensFacing == CameraSelector.LENS_FACING_FRONT ? "前置摄像头" : "后置摄像头",
                Toast.LENGTH_SHORT).show();
    }

    private String getRiskText() {
        if (lastDeepfakeScore <= 0) return "分析中...";
        if (lastDeepfakeScore >= 0.7f) return "AI伪造! " + String.format("%.0f%%", lastDeepfakeScore*100);
        if (lastDeepfakeScore >= 0.4f) return "可疑 " + String.format("%.0f%%", lastDeepfakeScore*100);
        return "真实 " + String.format("%.0f%%", (1-lastDeepfakeScore)*100);
    }

    /** 静默拍照用于实时分析 */
    private void captureForAnalysis() {
        if (imageCapture == null || deepfakeDetector == null) return;
        java.io.File tmpFile = new java.io.File(getCacheDir(), "realtime_check.jpg");
        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(tmpFile).build();
        imageCapture.takePicture(opts, Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        try {
                            LocalVideoDetector.DetectResult r = deepfakeDetector.detect(tmpFile.getAbsolutePath());
                            lastDeepfakeScore = r.riskScore;
                            runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.faceOverlay.setDeepfakeScore(r.riskScore);
                                    binding.tvDeepfakeScore.setText(
                                            String.format("%.0f%%", r.riskScore * 100));
                                    binding.tvDeepfakeVerdict.setText(getRiskText());
                                }
                            });
                        } catch (Exception ignored) {}
                        tmpFile.delete();
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException e) {}
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceHelper != null) {
            faceHelper.close();
        }
        binding = null;
    }
}
