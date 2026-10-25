package com.example.deepfake_fight.ui.knowledge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.service.LocalVideoDetector;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * Deepfake演示工具 — 教育用户了解AI换脸
 * 功能: 上传照片 → 模拟Deepfake效果 → AI检测对比
 * 仅供教育用途，所有处理均在本地完成
 */
public class DeepfakeDemoFragment extends Fragment {

    private ImageView ivOriginal, ivSimulated;
    private TextView tvOriginalScore, tvSimulatedScore, tvExplanation;
    private Bitmap originalBitmap;
    private FaceDetector faceDetector;
    private LocalVideoDetector deepfakeDetector;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        java.io.InputStream in = requireContext()
                                .getContentResolver().openInputStream(uri);
                        originalBitmap = BitmapFactory.decodeStream(in);
                        if (in != null) in.close();
                        processImage();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 纯代码构建UI
        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackgroundColor(0xFF0B1020);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 32);

        // 标题
        TextView title = new TextView(getContext());
        title.setText("Deepfake 演示实验室");
        title.setTextSize(22);
        title.setTextColor(0xFFE5E7EB);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("了解AI换脸的工作原理，提升防范意识\n上传照片查看原始图像与模拟Deepfake效果的对比");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFFA8B0BC);
        subtitle.setPadding(0, 8, 0, 24);
        subtitle.setLineSpacing(4f, 1.2f);
        root.addView(subtitle);

        // 原始图
        TextView lab1 = new TextView(getContext());
        lab1.setText("原始照片");
        lab1.setTextSize(14);
        lab1.setTextColor(0xFFE5E7EB);
        lab1.setTypeface(null, android.graphics.Typeface.BOLD);
        lab1.setPadding(0, 0, 0, 8);
        root.addView(lab1);

        ivOriginal = new ImageView(getContext());
        ivOriginal.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));
        ivOriginal.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivOriginal.setBackgroundColor(0xFF161F33);
        root.addView(ivOriginal);

        tvOriginalScore = new TextView(getContext());
        tvOriginalScore.setTextSize(12);
        tvOriginalScore.setTextColor(0xFFA8B0BC);
        tvOriginalScore.setPadding(0, 4, 0, 20);
        root.addView(tvOriginalScore);

        // 模拟Deepfake图
        TextView lab2 = new TextView(getContext());
        lab2.setText("模拟Deepfake效果（教育演示）");
        lab2.setTextSize(14);
        lab2.setTextColor(0xFFDC2626);
        lab2.setTypeface(null, android.graphics.Typeface.BOLD);
        lab2.setPadding(0, 0, 0, 8);
        root.addView(lab2);

        ivSimulated = new ImageView(getContext());
        ivSimulated.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));
        ivSimulated.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivSimulated.setBackgroundColor(0xFF161F33);
        root.addView(ivSimulated);

        tvSimulatedScore = new TextView(getContext());
        tvSimulatedScore.setTextSize(12);
        tvSimulatedScore.setPadding(0, 4, 0, 16);
        root.addView(tvSimulatedScore);

        // 说明
        tvExplanation = new TextView(getContext());
        tvExplanation.setTextSize(12);
        tvExplanation.setTextColor(0xFFA8B0BC);
        tvExplanation.setLineSpacing(4f, 1.2f);
        root.addView(tvExplanation);

        // 上传按钮
        Button btn = new Button(getContext());
        btn.setText("选择照片进行演示");
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFFC9A227);
        btn.setPadding(24, 12, 24, 12);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 20;
        btn.setLayoutParams(bp);
        btn.setOnClickListener(v -> imagePicker.launch("image/*"));
        root.addView(btn);

        scroll.addView(root);
        return scroll;
    }

    private void processImage() {
        if (originalBitmap == null || getContext() == null) return;

        // 初始化检测器
        if (faceDetector == null) {
            faceDetector = FaceDetection.getClient(
                    new FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                            .build());
        }
        if (deepfakeDetector == null) {
            deepfakeDetector = LocalVideoDetector.getInstance();
            if (!deepfakeDetector.isModelLoaded()) {
                deepfakeDetector.initialize(requireContext());
            }
        }

        // 显示原图
        ivOriginal.setImageBitmap(originalBitmap);

        // 生成模拟Deepfake（添加伪影）
        Bitmap simulated = simulateDeepfake(originalBitmap);
        ivSimulated.setImageBitmap(simulated);

        // AI检测
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                LocalVideoDetector.DetectResult r1 = deepfakeDetector.detect(originalBitmap);
                LocalVideoDetector.DetectResult r2 = deepfakeDetector.detect(simulated);

                requireActivity().runOnUiThread(() -> {
                    tvOriginalScore.setText(String.format(
                            "AI检测: 风险 %.0f%% — %s",
                            r1.riskScore * 100,
                            r1.riskScore >= 0.5f ? "检测到异常!" : "判定为真实"));
                    tvOriginalScore.setTextColor(r1.riskScore >= 0.5f
                            ? 0xFFDC2626 : 0xFF059669);

                    tvSimulatedScore.setText(String.format(
                            "AI检测: 风险 %.0f%% — %s",
                            r2.riskScore * 100,
                            r2.riskScore >= 0.5f ? "检测到Deepfake!" : "判定为真实"));
                    tvSimulatedScore.setTextColor(r2.riskScore >= 0.5f
                            ? 0xFFDC2626 : 0xFF059669);

                    tvExplanation.setText(
                            "Deepfake识别要点:\n"
                            + "• 面部边缘模糊/光照不一致 → 换脸痕迹\n"
                            + "• 眼睛周围异常 → AI最难生成自然的眼睛\n"
                            + "• 纹理重复/网格化 → GAN生成常见缺陷\n"
                            + "• 皮肤过于光滑 → AI降噪导致细节丢失\n\n"
                            + "模拟效果包含: 面部边缘融合 + 光照异常 + 纹理损失\n"
                            + "AI模型通过学习数百万真实/伪造样本的差异来识别这些特征。");
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        tvExplanation.setText("检测失败: " + e.getMessage()));
            }
        });
    }

    /** 模拟Deepfake效果 — 教育演示用 */
    private Bitmap simulateDeepfake(Bitmap src) {
        Bitmap result = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        int w = result.getWidth(), h = result.getHeight();

        // 1. 面部区域轻微模糊（模拟换脸融合）
        Paint blurHint = new Paint();
        blurHint.setColor(Color.argb(30, 255, 255, 255));
        blurHint.setMaskFilter(new android.graphics.BlurMaskFilter(3,
                android.graphics.BlurMaskFilter.Blur.NORMAL));
        canvas.drawRect(w/4f, h/4f, w*3/4f, h*3/4f, blurHint);

        // 2. 边缘光照异常（模拟不一致光照）
        Paint edgePaint = new Paint();
        edgePaint.setColor(Color.argb(15, 0, 0, 0));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(w/15f);
        canvas.drawRoundRect(w/6f, h/6f, w*5/6f, h*5/6f, w/20f, h/20f, edgePaint);

        // 3. 纹理损失条纹
        Paint stripePaint = new Paint();
        stripePaint.setColor(Color.argb(10, 128, 128, 128));
        for (int y = h/3; y < h*2/3; y += 4) {
            canvas.drawLine(0, y, w, y, stripePaint);
        }

        // 4. 水印标注
        Paint watermark = new Paint(Paint.ANTI_ALIAS_FLAG);
        watermark.setColor(Color.argb(100, 255, 0, 0));
        watermark.setTextSize(w / 12f);
        watermark.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.save();
        canvas.rotate(-20, w/2f, h/2f);
        canvas.drawText("DEEPFAKE DEMO", w/6f, h/2f, watermark);
        canvas.restore();

        return result;
    }
}
