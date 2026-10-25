package com.example.deepfake_fight.ui.video;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.FragmentVideoDetectBinding;
import com.example.deepfake_fight.service.ApiService;
import com.example.deepfake_fight.service.FaceRegionAnalyzer;
import com.example.deepfake_fight.service.FileUtils;
import com.example.deepfake_fight.service.LocalVideoDetector;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

public class VideoDetectFragment extends Fragment {

    private FragmentVideoDetectBinding binding;
    private Uri selectedVideoUri;
    private File pendingVideoFile;

    // 文件选择器（图片+视频）
    private final ActivityResultLauncher<String[]> videoFilePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    selectedVideoUri = uri;
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    showPreview(uri);
                }
            });

    // 拍照结果接收
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra("capture_path");
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            pendingVideoFile = file;
                            showImagePreview(file.getAbsolutePath());
                            startRealDetection();
                        }
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVideoDetectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSelectVideo.setOnClickListener(v ->
                videoFilePicker.launch(new String[]{"image/*", "video/*"}));

        binding.btnRecordVideo.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CameraActivity.class);
            cameraLauncher.launch(intent);
        });

        binding.btnRemoveFile.setOnClickListener(v -> resetSelection());
    }

    private void showPreview(Uri uri) {
        // 尝试显示图片预览
        boolean isImage = false;
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ni >= 0) {
                    String name = cursor.getString(ni).toLowerCase();
                    isImage = name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".png") || name.endsWith(".bmp")
                            || name.endsWith(".webp");
                }
            }
        } catch (Exception ignored) {}

        if (isImage) {
            try {
                Bitmap bmp = BitmapFactory.decodeStream(
                        requireContext().getContentResolver().openInputStream(uri));
                if (bmp != null) {
                    binding.ivPreview.setImageBitmap(bmp);
                    binding.ivPreview.setVisibility(View.VISIBLE);
                    binding.ivPlaceholderIcon.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {}
        }

        // 开始复制并检测
        binding.layoutPreviewPlaceholder.setVisibility(View.VISIBLE);
        binding.layoutVideoInfo.setVisibility(View.GONE);
        copyAndDetect(uri);
    }

    private void showImagePreview(String filePath) {
        try {
            Bitmap bmp = BitmapFactory.decodeFile(filePath);
            if (bmp != null && binding != null) {
                binding.ivPreview.setImageBitmap(bmp);
                binding.ivPreview.setVisibility(View.VISIBLE);
                binding.ivPlaceholderIcon.setVisibility(View.GONE);
                binding.layoutPreviewPlaceholder.setVisibility(View.VISIBLE);
                binding.layoutVideoInfo.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {}
    }

    private void copyAndDetect(Uri uri) {
        binding.layoutAnalyzing.setVisibility(View.VISIBLE);
        binding.tvAnalyzeProgress.setText("正在准备文件...");

        new Thread(() -> {
            try {
                pendingVideoFile = FileUtils.copyUriToCache(
                        requireContext(), uri, "video");
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(this::startRealDetection);
                }
            } catch (Exception e) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.layoutAnalyzing.setVisibility(View.GONE);
                            binding.layoutPreviewPlaceholder.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(getContext(),
                                "文件读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private void startRealDetection() {
        if (pendingVideoFile == null) {
            runMockDetection();
            return;
        }

        LocalVideoDetector detector = LocalVideoDetector.getInstance();

        if (detector.isModelLoaded()) {
            binding.tvAnalyzeProgress.setText("正在本地AI分析...");
            new Thread(() -> {
                try {
                    String imagePath = pendingVideoFile.getAbsolutePath();
                    LocalVideoDetector.DetectResult result = detector.detect(imagePath);
                    // 端侧检测完成后上报统计（只报类型，不传数据）
                    com.example.deepfake_fight.service.ApiService.getInstance()
                            .reportDetection("video");

                    // ★ 新增：运行面部区域可视化分析
                    String annotatedPath = null;
                    String regionDetailJson = null;
                    try {
                        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                        opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                        android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeFile(imagePath, opts);

                        if (original != null) {
                            FaceRegionAnalyzer analyzer = new FaceRegionAnalyzer();
                            FaceRegionAnalyzer.AnalysisResult analysis = analyzer.analyze(original, result.riskScore);

                            // 保存标注图像到缓存（PNG无损 + JPEG高质量双份）
                            File annotatedFile = new File(requireContext().getCacheDir(),
                                    "annotated_" + System.currentTimeMillis() + ".png");
                            try (FileOutputStream fos = new FileOutputStream(annotatedFile)) {
                                analysis.annotatedImage.compress(
                                        android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                            }
                            annotatedPath = annotatedFile.getAbsolutePath();

                            // 构建区域分析JSON
                            StringBuilder jsonBuilder = new StringBuilder("{");
                            jsonBuilder.append("\"region_scores\":{");
                            boolean first = true;
                            for (Map.Entry<String, Float> e : analysis.regionScores.entrySet()) {
                                if (!first) jsonBuilder.append(",");
                                jsonBuilder.append("\"").append(e.getKey()).append("\":")
                                        .append(String.format("%.4f", e.getValue()));
                                first = false;
                            }
                            jsonBuilder.append("},");
                            jsonBuilder.append("\"summary\":\"").append(
                                    analysis.summaryText != null ?
                                            analysis.summaryText.replace("\"", "\\\"").replace("\n", "\\n") : "")
                                    .append("\",");
                            jsonBuilder.append("\"analysis_time_ms\":").append(analysis.analysisTimeMs);
                            jsonBuilder.append("}");

                            regionDetailJson = jsonBuilder.toString();
                            Log.i("VideoDetect", "可视化分析完成: " + annotatedPath);
                        }
                    } catch (Exception vizEx) {
                        Log.w("VideoDetect", "可视化分析失败（不影响检测结果）", vizEx);
                    }

                    if (isAdded() && getActivity() != null) {
                        String finalAnnotatedPath = annotatedPath;
                        String finalRegionJson = regionDetailJson;
                        requireActivity().runOnUiThread(() -> {
                            if (binding != null) {
                                binding.layoutAnalyzing.setVisibility(View.GONE);
                            }
                            navigateToResult(result.riskScore, result.getRiskLevel(),
                                    imagePath,
                                    "{\"method\":\"本地AI模型(EfficientNet-B0)\"," +
                                    "\"risk_score\":" + result.riskScore + "," +
                                    "\"risk_level\":" + result.getRiskLevel() + "," +
                                    "\"description\":\"" + result.getRiskDescription() + "\"," +
                                    "\"annotated_image\":\"" + (finalAnnotatedPath != null ?
                                            finalAnnotatedPath.replace("\\", "\\\\") : "") + "\"," +
                                    (finalRegionJson != null ?
                                            "\"region_analysis\":" + finalRegionJson + "," : "") +
                                    "\"real_prob\":" + result.realProb + "," +
                                    "\"fake_prob\":" + result.fakeProb + "}");
                        });
                    }
                } catch (Exception e) {
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            if (binding != null) {
                                binding.tvAnalyzeProgress.setText("本地分析失败，尝试服务器...");
                            }
                            detectViaServer();
                        });
                    }
                }
            }, "local-detect").start();
        } else {
            detectViaServer();
        }
    }

    private void detectViaServer() {
        if (pendingVideoFile == null) {
            runMockDetection();
            return;
        }

        if (binding != null) {
            binding.tvAnalyzeProgress.setText("正在上传视频到服务器...");
        }

        ApiService.getInstance().detectVideo(pendingVideoFile, new ApiService.DetectCallback() {
            @Override
            public void onProgress(String status) {
                if (binding != null) binding.tvAnalyzeProgress.setText(status);
            }

            @Override
            public void onSuccess(JSONObject result) {
                if (!isAdded()) return;
                if (binding != null) {
                    binding.layoutAnalyzing.setVisibility(View.GONE);
                }
                try {
                    float score = (float) result.getDouble("risk_score");
                    int level = result.getInt("risk_level");
                    String imgPath = pendingVideoFile != null ? pendingVideoFile.getAbsolutePath() : "";
                    navigateToResult(score, level, imgPath, result.toString());
                } catch (Exception e) {
                    Toast.makeText(getContext(),
                            "结果解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                if (binding != null) {
                    binding.tvAnalyzeProgress.setText("服务器未连接，使用本地模拟检测...");
                    binding.tvAnalyzeProgress.postDelayed(
                            VideoDetectFragment.this::runMockDetection, 1500);
                }
            }
        });
    }

    private void navigateToResult(float score, int level, String imagePath, String responseJson) {
        if (!isAdded() || binding == null) return;
        Bundle args = new Bundle();
        args.putString("type", "video");
        args.putFloat("risk_score", score);
        args.putInt("risk_level", level);
        args.putString("api_response", responseJson);
        args.putString("file_path", imagePath);

        // 从responseJson提取标注图路径和区域分析
        try {
            org.json.JSONObject json = new org.json.JSONObject(responseJson);
            String annotated = json.optString("annotated_image", "");
            if (!annotated.isEmpty()) {
                args.putString("annotated_image", annotated);
            }
            org.json.JSONObject regionObj = json.optJSONObject("region_analysis");
            if (regionObj != null) {
                args.putString("region_analysis", regionObj.toString());
            }
        } catch (Exception ignored) {}

        Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
    }

    private void runMockDetection() {
        if (binding == null) return;
        binding.tvAnalyzeProgress.setText("本地分析中...");
        binding.tvAnalyzeProgress.postDelayed(() -> {
            if (binding == null || !isAdded()) return;
            binding.layoutAnalyzing.setVisibility(View.GONE);
            float score = 0.23f + (float) Math.random() * 0.3f;
            int level = score >= 0.8f ? 5 : score >= 0.6f ? 4 : score >= 0.4f ? 3 : score >= 0.2f ? 2 : 1;
            navigateToResult(score, level, "",
                    "{\"method\":\"本地模拟\",\"risk_score\":" + score + ",\"risk_level\":" + level + "}");
        }, 1000);
    }

    private void resetSelection() {
        selectedVideoUri = null;
        pendingVideoFile = null;
        if (binding != null) {
            binding.layoutPreviewPlaceholder.setVisibility(View.VISIBLE);
            binding.layoutVideoInfo.setVisibility(View.GONE);
            binding.ivPreview.setVisibility(View.GONE);
            binding.ivPlaceholderIcon.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
