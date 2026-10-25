package com.example.deepfake_fight.ui.multimodal;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.deepfake_fight.databinding.FragmentMultimodalDetectBinding;
import com.example.deepfake_fight.service.FileUtils;
import com.example.deepfake_fight.service.LocalAudioDetector;
import com.example.deepfake_fight.service.LocalMultimodalDetector;
import com.example.deepfake_fight.service.LocalTextDetector;
import com.example.deepfake_fight.service.LocalVideoDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 多模态融合检测页 —— 本项目自有多模态模型 GuardFusion 的 UI 入口
 *
 * 流程：选择图片(视频帧) + 可选音频 + 可选文本 → LocalMultimodalDetector 融合推理
 *       → 联合真伪分 + 跨模态不一致分 → ResultFragment 展示
 */
public class MultimodalDetectFragment extends Fragment {

    private static final String TAG = "MultimodalDetect";
    private FragmentMultimodalDetectBinding binding;

    private Uri selectedImageUri;
    private Uri selectedAudioUri;
    private Bitmap currentBitmap;

    private final ActivityResultLauncher<String[]> imagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    selectedImageUri = uri;
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    loadImagePreview(uri);
                }
            });

    private final ActivityResultLauncher<String[]> audioPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    selectedAudioUri = uri;
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String name = uri.getLastPathSegment();
                    binding.tvMmAudioName.setText(name != null ? name : "已选择音频");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMultimodalDetectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnMmPickImage.setOnClickListener(v ->
                imagePicker.launch(new String[]{"image/*"}));
        binding.btnMmPickAudio.setOnClickListener(v ->
                audioPicker.launch(new String[]{"audio/*"}));
        binding.btnMmDetect.setOnClickListener(v -> startDetection());
        ensureModelLoaded();
    }

    /** 懒加载融合模型（进入页面时后台加载，避免启动时内存峰值） */
    private void ensureModelLoaded() {
        LocalMultimodalDetector mm = LocalMultimodalDetector.getInstance();
        if (mm.isModelLoaded()) return;

        binding.tvMmProgress.setText("正在加载多模态融合模型...");
        new Thread(() -> {
            try {
                mm.initialize(requireContext().getApplicationContext());
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvMmProgress.setText(mm.isModelLoaded()
                                    ? "融合模型就绪，可开始检测"
                                    : "融合模型加载失败，请重启 App 重试");
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "多模态模型加载失败", e);
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvMmProgress.setText("模型加载失败: " + e.getMessage());
                        }
                    });
                }
            }
        }, "multimodal-model-init").start();
    }

    private void loadImagePreview(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            currentBitmap = BitmapFactory.decodeStream(in);
            if (currentBitmap != null) {
                binding.ivMmPreview.setImageBitmap(currentBitmap);
                binding.tvMmImageName.setText(uri.getLastPathSegment());
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "图片读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startDetection() {
        if (currentBitmap == null && selectedImageUri == null) {
            Toast.makeText(getContext(), "请先选择一张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        LocalMultimodalDetector mm = LocalMultimodalDetector.getInstance();
        if (!mm.isModelLoaded()) {
            Toast.makeText(getContext(), "融合模型加载中，请稍候再试", Toast.LENGTH_SHORT).show();
            ensureModelLoaded();
            return;
        }

        binding.tvMmProgress.setText("正在跨模态融合分析中...");
        binding.btnMmDetect.setEnabled(false);

        new Thread(() -> {
            try {
                // 1. 图片（如已加载 bitmap 则复用，否则重新读取）
                Bitmap image = currentBitmap;
                if (image == null) {
                    try (InputStream in = requireContext().getContentResolver()
                            .openInputStream(selectedImageUri)) {
                        image = BitmapFactory.decodeStream(in);
                    }
                }

                // 2. 音频（可选）：复制到缓存并解码为 16k PCM
                float[] audioPcm = null;
                if (selectedAudioUri != null) {
                    File audioFile = FileUtils.copyUriToCache(requireContext(), selectedAudioUri, "mm_audio");
                    audioPcm = LocalAudioDetector.getInstance().decodeToPCM(audioFile);
                }

                // 3. 文本（可选）
                String text = binding.etMmText.getText() != null
                        ? binding.etMmText.getText().toString().trim() : "";

                // 3.5 保存图片到缓存，供历史记录显示缩略图
                String imagePath = "";
                try {
                    File imgFile = new File(requireContext().getCacheDir(),
                            "mm_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(imgFile)) {
                        image.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    }
                    imagePath = imgFile.getAbsolutePath();
                } catch (Exception ignored) {}

                // 4. 融合检测（缺某模态时传空，模型仍可给出联合判定）
                LocalMultimodalDetector.MultimodalResult result =
                        mm.detect(image, audioPcm != null ? audioPcm : new float[64600], text);

                // 5. 单模态兜底：反诈场景宁可漏报也不误放。
                //    融合头在随机配对数据上训练，joint 判定可能不如单模态可靠，
                //    因此取「视觉单模态 / 文本单模态 / 融合」三者的最大风险作为最终分。
                float videoRisk = 0f;
                try {
                    LocalVideoDetector vd = LocalVideoDetector.getInstance();
                    if (vd.isModelLoaded()) videoRisk = vd.detect(image).riskScore;
                } catch (Exception ignored) {}
                float textRisk = 0f;
                if (!text.isEmpty()) {
                    try {
                        LocalTextDetector td = LocalTextDetector.getInstance();
                        if (td.isModelLoaded()) textRisk = td.analyze(text).riskScore;
                    } catch (Exception ignored) {}
                }
                float finalRisk = Math.max(result.riskScore, Math.max(videoRisk, textRisk));
                LocalMultimodalDetector.MultimodalResult combined =
                        new LocalMultimodalDetector.MultimodalResult(finalRisk, result.inconsistencyScore);

                if (isAdded() && getActivity() != null) {
                    String finalPath = imagePath;
                    requireActivity().runOnUiThread(() -> navigateToResult(combined, finalPath));
                }
            } catch (Exception e) {
                Log.e(TAG, "融合检测失败", e);
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        binding.btnMmDetect.setEnabled(true);
                        binding.tvMmProgress.setText("检测失败: " + e.getMessage());
                    });
                }
            }
        }, "multimodal-detect").start();
    }

    private void navigateToResult(LocalMultimodalDetector.MultimodalResult result, String imagePath) {
        if (!isAdded() || binding == null) return;
        binding.btnMmDetect.setEnabled(true);

        Bundle args = new Bundle();
        args.putString("type", "video");  // 复用结果页，视频维度显示联合分
        args.putFloat("risk_score", result.riskScore);
        args.putInt("risk_level", result.getRiskLevel());
        args.putString("api_response", result.toJson());
        args.putString("file_path", imagePath);

        Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}
