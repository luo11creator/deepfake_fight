package com.example.deepfake_fight.ui.result;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.DetectionRecord;
import com.example.deepfake_fight.databinding.FragmentResultBinding;
import com.example.deepfake_fight.service.NotificationHelper;

import org.json.JSONObject;

import java.util.concurrent.Executors;

public class ResultFragment extends Fragment {

    private FragmentResultBinding binding;
    private float currentScore;
    private int currentLevel;
    private String currentType;
    private String currentVerdict;
    private String currentSource;     // "ai_local" | "ai_server" | "mock"
    private String currentModelName;  // 模型名称，如 "EfficientNet-B0"
    private String currentFileName;
    private String currentImagePath;
    private String currentAnnotatedPath;  // 带标注的图像路径
    private String currentRegionJson;     // 区域分析JSON
    private String currentApiResponse;
    private long savedRecordId = -1;  // 保存后的数据库ID
    // 音频检测特有字段
    private String currentTranscript;    // ASR语音识别文字
    private String currentReportImageUrl;// 报告图URL

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            parseSource(args);
            updateUI(args);
            autoSave();
        }

        binding.btnDelete.setOnClickListener(v -> deleteRecord());
        binding.btnReport.setOnClickListener(v -> exportReport());
    }

    /**
     * 智能判断检测来源
     * api_response JSON 中如果有 "method" 字段且包含 "AI" → 本地AI模型
     * 如果有 "method" 且包含 "模拟" → 模拟数据
     * 如果是服务器返回的完整JSON → 云端检测
     * 如果 api_response 为 null → 本地模拟
     */
    private void parseSource(Bundle args) {
        currentApiResponse = args.getString("api_response");
        currentAnnotatedPath = args.getString("annotated_image", "");
        currentRegionJson = args.getString("region_analysis", "");
        currentTranscript = args.getString("transcript", "");
        currentReportImageUrl = args.getString("annotated_image_url", "");

        if (currentApiResponse == null || currentApiResponse.isEmpty()) {
            currentSource = "mock";
            currentModelName = "基础规则";
            return;
        }

        try {
            JSONObject json = new JSONObject(currentApiResponse);
            String method = json.optString("method", "");

            if (method.contains("AI") || method.contains("EfficientNet")
                    || method.contains("TextCNN") || method.contains("BERT")) {
                currentSource = "ai_local";
                // 提取模型名
                if (method.contains("EfficientNet")) currentModelName = "EfficientNet-B0";
                else if (method.contains("TextCNN")) currentModelName = "TextCNN";
                else if (method.contains("BERT")) currentModelName = "BERT";
                else currentModelName = method.replace("本地AI模型", "").replace("(", "").replace(")", "").trim();
            } else if (method.contains("服务器") || method.contains("云端")) {
                currentSource = "ai_server";
                currentModelName = "云端服务器";
            } else if (method.contains("模拟") || method.contains("mock")) {
                currentSource = "mock";
                currentModelName = "离线模拟";
            } else if (json.has("risk_score") && json.has("risk_level")) {
                // 有标准字段但没有method → 云端API
                currentSource = "ai_server";
                currentModelName = "云端服务器";
            } else {
                currentSource = "mock";
                currentModelName = "基础规则";
            }
        } catch (Exception e) {
            // JSON解析失败 → 判断为mock
            currentSource = "mock";
            currentModelName = "基础规则";
        }

        Log.i("ResultFragment", "来源判断: source=" + currentSource + " model=" + currentModelName);
    }

    private void updateUI(Bundle args) {
        currentType = args.getString("type", "video");
        currentScore = args.getFloat("risk_score", 0.5f);
        currentLevel = args.getInt("risk_level", 3);
        currentImagePath = args.getString("file_path", "");
        currentFileName = new java.io.File(currentImagePath).getName();
        if (currentFileName.isEmpty()) currentFileName = "未知文件";

        // 类型标签
        String typeLabel;
        switch (currentType) {
            case "video": typeLabel = "视频真伪检测"; break;
            case "audio": typeLabel = "音频真伪检测"; break;
            case "text":  typeLabel = "文本风险分析"; break;
            default:      typeLabel = "综合检测"; break;
        }
        binding.tvDetectType.setText(typeLabel);

        // 来源标签 - 三种不同颜色和文字
        switch (currentSource) {
            case "ai_local":
                binding.tvSourceBadge.setText("AI 模型检测");
                binding.tvSourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF1B5E20));
                break;
            case "ai_server":
                binding.tvSourceBadge.setText("云端检测");
                binding.tvSourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF1565C0));
                break;
            default:
                binding.tvSourceBadge.setText("模拟数据");
                binding.tvSourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFE65100));
                break;
        }

        // ★ 优先显示带标注的分析图像
        // 从api_response中解析标注图路径（兼容直接跳转场景）
        if (currentAnnotatedPath == null || currentAnnotatedPath.isEmpty()) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                String ap = json.optString("annotated_image", "");
                if (!ap.isEmpty()) currentAnnotatedPath = ap;
            } catch (Exception ignored) {}
        }
        if (currentRegionJson == null || currentRegionJson.isEmpty()) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                org.json.JSONObject regionObj = json.optJSONObject("region_analysis");
                if (regionObj != null) currentRegionJson = regionObj.toString();
            } catch (Exception ignored) {}
        }

        // 显示检测图片（优先用标注图）
        boolean imageShown = false;
        if ("video".equals(currentType)) {
            // 先尝试加载标注图像
            final String displayImagePath;  // 用于全屏查看
            if (currentAnnotatedPath != null && !currentAnnotatedPath.isEmpty()) {
                java.io.File annotatedFile = new java.io.File(currentAnnotatedPath);
                if (annotatedFile.exists()) {
                    displayImagePath = currentAnnotatedPath;
                    Bitmap bmp = BitmapFactory.decodeFile(currentAnnotatedPath);
                    if (bmp != null && binding != null) {
                        binding.ivResultImage.setImageBitmap(bmp);
                        binding.ivResultImage.setVisibility(View.VISIBLE);
                        binding.tvImageTag.setVisibility(View.VISIBLE);
                        binding.tvImageTag.setText("AI 可视化分析图");
                        binding.tvTapHint.setVisibility(View.VISIBLE);
                        imageShown = true;
                    }
                } else {
                    displayImagePath = currentImagePath;
                }
            } else {
                displayImagePath = currentImagePath;
            }

            // 回退：显示原始图片
            if (!imageShown && currentImagePath != null && !currentImagePath.isEmpty()) {
                java.io.File imgFile = new java.io.File(currentImagePath);
                if (imgFile.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(currentImagePath);
                    if (bmp != null && binding != null) {
                        binding.ivResultImage.setImageBitmap(bmp);
                        binding.ivResultImage.setVisibility(View.VISIBLE);
                        binding.tvImageTag.setVisibility(View.GONE);
                        binding.tvTapHint.setVisibility(View.VISIBLE);
                    }
                }
            }

            // ★ 点击图片全屏查看
            if (imageShown || (currentImagePath != null && !currentImagePath.isEmpty())) {
                final String fullScreenPath = displayImagePath;
                binding.ivResultImage.setOnClickListener(v -> showFullScreenImage(fullScreenPath));
            }
        }

        // ★ 新增：显示区域分析详情
        if (currentRegionJson != null && !currentRegionJson.isEmpty()) {
            try {
                org.json.JSONObject regionObj = new org.json.JSONObject(currentRegionJson);
                String summary = regionObj.optString("summary", "");
                org.json.JSONObject scores = regionObj.optJSONObject("region_scores");

                if (scores != null && scores.length() > 0) {
                    StringBuilder detailText = new StringBuilder();
                    if (!summary.isEmpty()) {
                        detailText.append(summary.replace("\\n", "\n")).append("\n\n");
                    }

                    // 按分数排序显示
                    java.util.List<java.util.AbstractMap.SimpleEntry<String, Float>> list = new java.util.ArrayList<>();
                    for (java.util.Iterator<String> it = scores.keys(); it.hasNext(); ) {
                        String key = it.next();
                        list.add(new java.util.AbstractMap.SimpleEntry<>(key,
                                (float) scores.getDouble(key)));
                    }
                    list.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

                    detailText.append("各区域风险得分:\n");
                    for (java.util.AbstractMap.SimpleEntry<String, Float> e : list) {
                        float s = e.getValue();
                        if (s < 0) continue;
                        String icon = s >= 0.7f ? "🔴" : s >= 0.4f ? "🟡" : "🟢";
                        detailText.append(String.format("  %s %s: %.1f%%\n", icon, e.getKey(), s * 100));
                    }

                    if (binding != null) {
                        binding.tvRegionDetail.setText(detailText.toString());
                        binding.cardRegionAnalysis.setVisibility(View.VISIBLE);
                    }
                }

                long analysisTime = regionObj.optLong("analysis_time_ms", 0);
                if (analysisTime > 0 && binding != null) {
                    binding.tvAnalysisTime.setText("区域分析耗时: " + analysisTime + "ms");
                    binding.tvAnalysisTime.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                Log.w("ResultFragment", "解析区域分析数据失败", e);
            }
        }

        // ★ 显示检测文本（文本分析）— 带可视化高亮
        if ("text".equals(currentType)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                org.json.JSONObject textAnno = json.optJSONObject("text_annotation");

                if (textAnno != null && binding != null) {
                    String originalText = textAnno.optString("original_text", "");
                    org.json.JSONArray matchesArr = textAnno.optJSONArray("matches");

                    if (!originalText.isEmpty() && matchesArr != null && matchesArr.length() > 0) {
                        // 重建Spannable标注文本
                        SpannableString spannable = new SpannableString(originalText);

                        // 颜色定义（与TextVisualAnnotator保持一致）
                        int[] bgColors = {
                                0x44FF5252, 0x44FF6D00, 0x44FF1744, 0x44FF9100,
                                0x44FFD600, 0x44FF4081, 0x4490CAF9
                        };
                        int[] textColors = {
                                0xFFD32F2F, 0xFFE65100, 0xFFC62828, 0xFFEF6C00,
                                0xFFF9A825, 0xFFC2185B, 0xFF1565C0
                        };

                        for (int i = 0; i < matchesArr.length(); i++) {
                            org.json.JSONObject match = matchesArr.getJSONObject(i);
                            int start = match.getInt("start");
                            int end = match.getInt("end");
                            int catIdx = match.getInt("category_idx");

                            if (catIdx >= 0 && catIdx < bgColors.length) {
                                spannable.setSpan(
                                        new BackgroundColorSpan(bgColors[catIdx]),
                                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                spannable.setSpan(
                                        new ForegroundColorSpan(textColors[catIdx]),
                                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                spannable.setSpan(
                                        new StyleSpan(Typeface.BOLD),
                                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }

                        binding.tvResultText.setText(spannable);
                        binding.cardResultText.setVisibility(View.VISIBLE);
                    }

                    // 显示风险色条
                    String riskBar = textAnno.optString("risk_bar", "");
                    if (!riskBar.isEmpty() && binding != null && binding.tvRegionDetail != null) {
                        binding.tvRegionDetail.setText(riskBar);
                        binding.cardRegionAnalysis.setVisibility(View.VISIBLE);
                    }

                    // 显示分析摘要
                    String summary = textAnno.optString("summary", "");
                    if (!summary.isEmpty() && binding != null && binding.tvSuggestions != null) {
                        // 在建议区域显示文本分析摘要
                        binding.tvSuggestions.setText(summary);
                    }
                } else {
                    // 无标注信息时回退旧逻辑
                    String detail = json.optString("detail", "");
                    if (!detail.isEmpty() && binding != null) {
                        binding.tvResultText.setText("分析文本: " + detail);
                        binding.cardResultText.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                Log.w("ResultFragment", "文本标注渲染失败", e);
                // 回退显示
                try {
                    org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                    String detail = json.optString("detail", "");
                    if (!detail.isEmpty() && binding != null) {
                        binding.tvResultText.setText("分析文本: " + detail);
                        binding.cardResultText.setVisibility(View.VISIBLE);
                    }
                } catch (Exception ignored) {}
            }
        }

        // ★ 音频检测：显示ASR识别文字
        if ("audio".equals(currentType)) {
            // 从api_response中提取transcript
            if (currentTranscript == null || currentTranscript.isEmpty()) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                    currentTranscript = json.optString("transcript", "");
                } catch (Exception ignored) {}
            }

            if (currentTranscript != null && !currentTranscript.isEmpty()) {
                if (binding != null) {
                    binding.tvResultText.setText("语音识别内容:\n\"" + currentTranscript + "\"");
                    binding.cardResultText.setVisibility(View.VISIBLE);
                }
            }

            // 显示片段分数
            try {
                org.json.JSONObject json = new org.json.JSONObject(currentApiResponse);
                org.json.JSONObject details = json.optJSONObject("details");
                if (details != null) {
                    org.json.JSONArray segScores = details.optJSONArray("segment_scores");
                    org.json.JSONArray artifacts = details.optJSONArray("audio_artifacts");
                    org.json.JSONArray asrFlags = details.optJSONArray("asr_risk_flags");

                    StringBuilder audioDetail = new StringBuilder();
                    audioDetail.append("音频分析详情:\n");

                    if (segScores != null && segScores.length() > 0) {
                        // 伪造区域可视化条
                        audioDetail.append("声音伪造区域分析:\n");
                        audioDetail.append("  时间轴: ");
                        for (int i = 0; i < segScores.length(); i++) {
                            float s = (float) segScores.getDouble(i);
                            String block = s >= 0.7f ? "■" : s >= 0.4f ? "■" : "□";
                            audioDetail.append(block);
                        }
                        audioDetail.append("\n  ");
                        for (int i = 0; i < segScores.length(); i++) {
                            float s = (float) segScores.getDouble(i);
                            audioDetail.append(String.format("%.0fs ", i * 4));
                        }
                        audioDetail.append("\n\n逐段AI合成概率:\n");
                        int highCount = 0;
                        for (int i = 0; i < segScores.length(); i++) {
                            float s = (float) segScores.getDouble(i);
                            String icon = s >= 0.7f ? "[HIGH]" : s >= 0.4f ? "[MID]" : "[LOW]";
                            audioDetail.append(String.format("  段%d %s: %.0f%%\n", i + 1, icon, s * 100));
                            if (s >= 0.7f) highCount++;
                        }
                        if (highCount > 0) {
                            audioDetail.append(String.format("\n>>> %d/%d 片段检测到AI合成特征 <<<\n",
                                    highCount, segScores.length()));
                        }
                    }

                    if (artifacts != null && artifacts.length() > 0) {
                        audioDetail.append("\n检测到的音频异常:\n");
                        for (int i = 0; i < artifacts.length(); i++) {
                            audioDetail.append("  • ").append(artifacts.getString(i)).append("\n");
                        }
                    }

                    if (asrFlags != null && asrFlags.length() > 0) {
                        audioDetail.append("\n文本风险标记:\n");
                        for (int i = 0; i < asrFlags.length(); i++) {
                            org.json.JSONObject flag = asrFlags.getJSONObject(i);
                            audioDetail.append("  • ").append(flag.optString("category", "未知"));
                            org.json.JSONArray kws = flag.optJSONArray("keywords");
                            if (kws != null && kws.length() > 0) {
                                audioDetail.append(" [");
                                for (int j = 0; j < kws.length(); j++) {
                                    if (j > 0) audioDetail.append(", ");
                                    audioDetail.append(kws.getString(j));
                                }
                                audioDetail.append("]");
                            }
                            audioDetail.append("\n");
                        }
                    }

                    if (audioDetail.length() > 0 && binding != null) {
                        binding.tvRegionDetail.setText(audioDetail.toString());
                        binding.cardRegionAnalysis.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                Log.w("ResultFragment", "音频详情解析失败", e);
            }

            // 显示服务器报告图
            if (currentReportImageUrl != null && !currentReportImageUrl.isEmpty()) {
                // TODO: 使用Glide或OkHttp下载报告图
                // 目前先显示提示
                if (binding != null) {
                    binding.tvImageTag.setVisibility(View.VISIBLE);
                    binding.tvImageTag.setText("📊 服务器检测报告图已生成");
                }
            }
        }

        // 模型详细信息
        if (currentModelName != null && !currentModelName.isEmpty()) {
            String modelInfo;
            if ("ai_local".equals(currentSource)) {
                modelInfo = "引擎: " + currentModelName + " | 离线推理 | 数据不上传";
            } else if ("ai_server".equals(currentSource)) {
                modelInfo = "引擎: " + currentModelName + " | 网络检测";
            } else {
                modelInfo = "引擎: " + currentModelName + " | 非AI检测，仅供参考";
            }
            binding.tvModelInfo.setText(modelInfo);
            binding.tvModelInfo.setVisibility(View.VISIBLE);
        }

        // 风险等级
        String[] verdicts = {"真实", "低风险", "可疑", "高风险", "伪造"};
        int[] colors = {R.color.success, R.color.success, R.color.warning,
                R.color.danger, R.color.danger};

        currentVerdict = verdicts[Math.min(currentLevel - 1, 4)];
        int verdictColor = getResources().getColor(colors[Math.min(currentLevel - 1, 4)]);

        binding.tvRiskLevelNum.setText(String.valueOf(currentLevel));
        binding.tvRiskLevelNum.setTextColor(verdictColor);
        binding.tvRiskVerdict.setText(currentVerdict);
        binding.tvRiskVerdict.setTextColor(verdictColor);

        int progressPercent = (int)(currentScore * 100);
        binding.progressRisk.setProgress(progressPercent);
        binding.progressRisk.setProgressTintList(
                android.content.res.ColorStateList.valueOf(verdictColor));

        // 维度得分
        setScore(binding.tvVideoScore, currentType.equals("video") ? currentScore : -1);
        setScore(binding.tvAudioScore, currentType.equals("audio") ? currentScore : -1);
        setScore(binding.tvTextScore, currentType.equals("text") ? currentScore : -1);

        // 高风险推送通知
        if (currentLevel >= 4) {
            NotificationHelper.showFraudAlert(requireContext(),
                    "⚠ 高风险检测结果",
                    typeLabel + " · 风险等级 " + currentLevel + " · " + currentVerdict);
        }

        // 建议
        binding.tvSuggestions.setText(buildSuggestions(currentLevel));
    }

    private void setScore(android.widget.TextView tv, float score) {
        if (score < 0) {
            tv.setText("--");
            tv.setTextColor(tv.getResources().getColor(R.color.text_hint));
            return;
        }
        tv.setText(String.format("%.0f%%", score * 100));
        if (score >= 0.6f) tv.setTextColor(tv.getResources().getColor(R.color.danger));
        else if (score >= 0.4f) tv.setTextColor(tv.getResources().getColor(R.color.warning));
        else tv.setTextColor(tv.getResources().getColor(R.color.success));
    }

    private String buildSuggestions(int level) {
        if (level >= 4) return "1. 该内容极大概率为AI伪造，请高度警惕\n"
                + "2. 立刻停止与对方的所有通讯\n"
                + "3. 切勿进行任何形式的转账汇款\n"
                + "4. 保存检测报告作为报警证据\n"
                + "5. 拨打 96110 反诈专线举报";
        else if (level == 3) return "1. 该内容存在可疑特征，建议进一步核实\n"
                + "2. 谨慎对待对方的资金要求\n"
                + "3. 通过其他渠道独立验证对方身份\n"
                + "4. 勿点击对方发送的任何链接";
        else return "1. 本次检测未发现明显伪造痕迹\n"
                + "2. 但仍建议保持基本安全防范意识\n"
                + "3. 定期使用本工具进行安全检测\n"
                + "4. 遇到可疑情况及时举报";
    }

    /**
     * 自动保存到历史记录
     */
    private void autoSave() {
        String typeLabel = binding.tvDetectType.getText().toString();
        String fileName = currentFileName.isEmpty() ? "未知文件" : currentFileName;

        DetectionRecord record = new DetectionRecord(
                currentType,
                typeLabel,
                currentScore,
                currentLevel,
                currentVerdict,
                currentSource,
                fileName,
                currentApiResponse != null ? currentApiResponse : "{}",
                currentImagePath != null ? currentImagePath : "",
                System.currentTimeMillis()
        );

        Executors.newSingleThreadExecutor().execute(() -> {
            savedRecordId = AppDatabase.getInstance(requireContext())
                    .detectionRecordDao().insert(record);
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.btnDelete.setText("删除此记录 · 已自动保存");
                    }
                });
            }
        });
    }

    /**
     * 删除当前记录
     */
    private void deleteRecord() {
        if (savedRecordId < 0) {
            Toast.makeText(getContext(), "记录尚未保存", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            DetectionRecord record = new DetectionRecord();
            record.id = savedRecordId;
            AppDatabase.getInstance(requireContext())
                    .detectionRecordDao().delete(record);
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "记录已删除", Toast.LENGTH_SHORT).show();
                    savedRecordId = -1;
                    if (binding != null) {
                        binding.btnDelete.setText("已删除");
                        binding.btnDelete.setEnabled(false);
                    }
                });
            }
        });
    }

    /**
     * 全屏查看检测图片
     */
    private void showFullScreenImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return;

        java.io.File file = new java.io.File(imagePath);
        if (!file.exists()) {
            Toast.makeText(getContext(), "图片文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bmp = BitmapFactory.decodeFile(imagePath);
        if (bmp == null) {
            Toast.makeText(getContext(), "无法加载图片", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_fullscreen);

        ImageView imageView = dialog.findViewById(R.id.iv_fullscreen_preview);
        View closeBtn = dialog.findViewById(R.id.btn_close_fullscreen);

        if (imageView != null) {
            imageView.setImageBitmap(bmp);
            imageView.setOnClickListener(v -> dialog.dismiss());
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dialog.dismiss());
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        dialog.show();
    }

    /** 导出检测报告：截图当前结果页并添加水印 */
    private void exportReport() {
        if (binding == null || getContext() == null) return;
        try {
            View rootView = binding.getRoot();
            rootView.setDrawingCacheEnabled(true);
            rootView.buildDrawingCache();
            Bitmap screenshot = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);

            // 加水印
            android.graphics.Canvas canvas = new android.graphics.Canvas(screenshot);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(0x30FFFFFF);
            paint.setTextSize(36);
            paint.setAntiAlias(true);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            String watermark = "多维盾卫 · AI检测报告 · "
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            canvas.rotate(-30, screenshot.getWidth() / 2f, screenshot.getHeight() / 2f);
            canvas.drawText(watermark, 40, screenshot.getHeight() - 80, paint);

            // 保存
            java.io.File dir = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES), "多维盾卫");
            dir.mkdirs();
            java.io.File file = new java.io.File(dir,
                    "report_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            screenshot.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            screenshot.recycle();

            Toast.makeText(getContext(), "报告已保存: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "导出失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
