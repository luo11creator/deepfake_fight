package com.example.deepfake_fight.ui.text;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.FragmentTextAnalyzeBinding;
import com.example.deepfake_fight.service.LocalTextDetector;
import com.example.deepfake_fight.service.TextVisualAnnotator;

import org.json.JSONArray;
import org.json.JSONObject;

public class TextAnalyzeFragment extends Fragment {

    private FragmentTextAnalyzeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTextAnalyzeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnAnalyze.setOnClickListener(v -> {
            String text = binding.etTextInput.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(getContext(), "请先输入需要分析的文本", Toast.LENGTH_SHORT).show();
                return;
            }
            analyzeText(text);
        });

        binding.btnClear.setOnClickListener(v -> binding.etTextInput.setText(""));

        binding.btnExample1.setOnClickListener(v ->
                binding.etTextInput.setText("小王，我是李总，刚才在开会。你马上给这个账户转50万，账号6222xxxx，这是急用项目款。"));
        binding.btnExample2.setOnClickListener(v ->
                binding.etTextInput.setText("您好，我们是市公安局的，你的身份证涉嫌洗钱案件，需要配合调查。请将资金转入安全账户核查。"));
        binding.btnExample3.setOnClickListener(v ->
                binding.etTextInput.setText("恭喜！你被选中为幸运用户，参与刷单返利。先充值1000元，完成任务后返现1500元，名额有限！"));
    }

    private void analyzeText(String text) {
        binding.btnAnalyze.setEnabled(false);
        binding.btnAnalyze.setText("分析中...");

        // 使用本地检测器（AI模型优先，自动回退关键词评分）
        LocalTextDetector detector = LocalTextDetector.getInstance();
        LocalTextDetector.DetectResult result = detector.analyze(text);
        // 端侧检测完成后上报统计（只报类型，不传数据）
        com.example.deepfake_fight.service.ApiService.getInstance()
                .reportDetection("text");

        // ★ 文本可视化标注
        String annotationJson = null;
        try {
            TextVisualAnnotator annotator = new TextVisualAnnotator();
            TextVisualAnnotator.TextAnnotation textAnno = annotator.analyze(text, result.riskScore);

            // 构建标注JSON（供ResultFragment重建Spannable）
            JSONObject annoObj = new JSONObject();
            annoObj.put("original_text", text);
            annoObj.put("summary", textAnno.summary);
            annoObj.put("risk_bar", textAnno.riskBarText);

            JSONArray matchesArr = new JSONArray();
            for (TextVisualAnnotator.TextAnnotation.MatchInfo m : textAnno.allMatches) {
                JSONObject matchObj = new JSONObject();
                matchObj.put("keyword", m.keyword);
                matchObj.put("category", m.categoryName);
                matchObj.put("category_idx", m.categoryIndex);
                matchObj.put("start", m.startPos);
                matchObj.put("end", m.endPos);
                matchObj.put("weight", m.weight);
                matchesArr.put(matchObj);
            }
            annoObj.put("matches", matchesArr);

            // 类别得分
            JSONObject catScores = new JSONObject();
            for (java.util.Map.Entry<String, Float> e : textAnno.categoryScores.entrySet()) {
                catScores.put(e.getKey(), e.getValue());
            }
            annoObj.put("category_scores", catScores);

            annotationJson = annoObj.toString();
        } catch (Exception e) {
            android.util.Log.w("TextAnalyze", "文本可视化失败", e);
        }

        // 导航到结果页
        binding.btnAnalyze.setEnabled(true);
        binding.btnAnalyze.setText("开始分析");

        String textDetail = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        Bundle args = new Bundle();
        args.putString("type", "text");
        args.putFloat("risk_score", result.riskScore);
        args.putInt("risk_level", result.getRiskLevel());
        args.putString("file_path", textDetail);

        StringBuilder respJson = new StringBuilder();
        respJson.append("{\"method\":\"")
                .append(detector.isModelLoaded() ? "本地AI模型(TextCNN)" : "关键词评分")
                .append("\",\"risk_score\":").append(result.riskScore)
                .append(",\"risk_level\":").append(result.getRiskLevel())
                .append(",\"description\":\"").append(result.getRiskDescription()).append("\"")
                .append(",\"detail\":\"").append(text.replace("\"", "'").replace("\n", " "))
                .append("\"");
        if (annotationJson != null) {
            respJson.append(",\"text_annotation\":").append(annotationJson);
        }
        respJson.append("}");
        args.putString("api_response", respJson.toString());

        Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
