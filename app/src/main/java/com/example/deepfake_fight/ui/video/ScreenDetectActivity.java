package com.example.deepfake_fight.ui.video;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.deepfake_fight.databinding.ActivityScreenDetectBinding;
import com.example.deepfake_fight.service.LocalVideoDetector;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

/**
 * 屏幕实时换脸检测 —— MediaProjection 录屏 + 周期性抽帧检测
 *
 * 场景：微信视频通话时，授权录屏，App 周期性截取屏幕画面（含对方视频），
 * 用 EfficientNet 判断是否为 AI 换脸。截图仅本地实时分析，不保存、不上传。
 */
public class ScreenDetectActivity extends AppCompatActivity {

    private static final int REQUEST_SCREEN_CAPTURE = 2001;
    private static final int CAPTURE_INTERVAL_MS = 5000;

    private ActivityScreenDetectBinding binding;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private LocalVideoDetector detector;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable captureRunnable = new Runnable() {
        @Override public void run() {
            captureAndDetect();
            if (isDetecting()) handler.postDelayed(this, CAPTURE_INTERVAL_MS);
        }
    };

    private boolean detecting = false;
    private int detectCount = 0;
    private Bitmap currentPreview;
    private float maxRisk = 0f;

    private int pendingResultCode;
    private Intent pendingData;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // 前台服务已就绪（startForeground 已执行），此时才能获取 MediaProjection
            android.util.Log.e("ScreenDetect", "onServiceConnected 服务已连接");
            try {
                mediaProjection = projectionManager.getMediaProjection(pendingResultCode, pendingData);
                startCapture();
            } catch (Exception e) {
                android.util.Log.e("ScreenDetect", "MediaProjection 获取失败", e);
                Toast.makeText(ScreenDetectActivity.this, "录屏启动失败，请重试", Toast.LENGTH_SHORT).show();
            }
            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScreenDetectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);
        detector = LocalVideoDetector.getInstance();

        if (!detector.isModelLoaded()) {
            new Thread(() -> {
                try {
                    detector.initialize(this);
                    runOnUiThread(() -> {
                        if (binding != null) binding.tvScreenStatus.setText("模型就绪");
                    });
                } catch (Exception e) {
                    android.util.Log.e("ScreenDetect", "模型加载失败", e);
                }
            }, "screen-model-init").start();
        } else {
            binding.tvScreenStatus.setText("模型就绪");
        }

        binding.btnScreenToggle.setOnClickListener(v -> {
            if (detecting) stopCapture();
            else requestScreenCapture();
        });
    }

    private boolean isDetecting() { return detecting; }

    private void requestScreenCapture() {
        Intent intent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        android.util.Log.e("ScreenDetect", "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 先启动前台服务，再绑定等待其完成 startForeground 后获取 MediaProjection
                pendingResultCode = resultCode;
                pendingData = data;
                Intent serviceIntent = new Intent(this,
                        com.example.deepfake_fight.service.MediaProjectionService.class);
                startForegroundService(serviceIntent);
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            } else {
                Toast.makeText(this, "未授权录屏，无法检测", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCapture() {
        android.util.Log.e("ScreenDetect", "startCapture 开始截取");
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenDetect",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        detecting = true;
        binding.btnScreenToggle.setText("停止检测");
        binding.tvScreenStatus.setText("检测中");
        binding.tvScreenVerdict.setText("正在分析屏幕画面...");
        binding.waveScreen.start();
        handler.post(captureRunnable);

        Toast.makeText(this, "已开始屏幕检测（每 2 秒分析一次）", Toast.LENGTH_SHORT).show();
    }

    private void captureAndDetect() {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (imageReader == null) {
                android.util.Log.e("ScreenDetect", "imageReader 为 null");
                return;
            }
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image == null) {
                    android.util.Log.e("ScreenDetect", "acquireLatestImage 返回 null（无新帧）");
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvScreenVerdict.setText("未获取到画面，请确认选择了「整个屏幕」");
                            binding.tvScreenStatus.setText("等待画面...");
                        }
                    });
                    return;
                }
                android.util.Log.e("ScreenDetect", "截到帧 " + image.getWidth() + "x" + image.getHeight());
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap == null) {
                    android.util.Log.e("ScreenDetect", "imageToBitmap 返回 null");
                    return;
                }
                LocalVideoDetector.DetectResult r = detector.detect(bitmap);
                android.util.Log.e("ScreenDetect", "检测结果 riskScore=" + r.riskScore);
                runOnUiThread(() -> {
                    // 回收上一帧预览，显示新帧
                    if (currentPreview != null && !currentPreview.isRecycled()) {
                        currentPreview.recycle();
                    }
                    currentPreview = bitmap;
                    binding.ivScreenPreview.setImageBitmap(bitmap);
                    updateUI(r.riskScore);
                });
            } catch (Exception e) {
                android.util.Log.e("ScreenDetect", "截帧检测失败", e);
            } finally {
                if (image != null) image.close();
            }
        });
    }

    private void updateUI(float riskScore) {
        detectCount++;
        if (riskScore > maxRisk) maxRisk = riskScore;

        // 大数字显示「当前帧」分数，会实时波动
        binding.tvScreenRiskScore.setText(String.format("%.0f%%", riskScore * 100));
        if (riskScore >= 0.7f) binding.tvScreenVerdict.setText("高度可疑：疑似 AI 换脸");
        else if (riskScore >= 0.4f) binding.tvScreenVerdict.setText("存在疑点：建议保持警惕");
        else if (riskScore >= 0.2f) binding.tvScreenVerdict.setText("低风险");
        else binding.tvScreenVerdict.setText("真实：未发现换脸痕迹");
        binding.tvScreenStatus.setText("已分析 " + detectCount + " 次 · 峰值 "
                + String.format("%.0f%%", maxRisk * 100));
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return null;
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void stopCapture() {
        detecting = false;
        handler.removeCallbacks(captureRunnable);
        binding.btnScreenToggle.setText("开始检测");
        binding.tvScreenStatus.setText("已停止");
        binding.waveScreen.stop();
        releaseCapture();
        stopService(new Intent(this,
                com.example.deepfake_fight.service.MediaProjectionService.class));
    }

    private void releaseCapture() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(captureRunnable);
        releaseCapture();
        if (currentPreview != null && !currentPreview.isRecycled()) {
            currentPreview.recycle();
            currentPreview = null;
        }
        binding = null;
    }
}
