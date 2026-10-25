package com.example.deepfake_fight.service;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地文本反诈检测器
 * 使用 PyTorch Mobile 加载轻量模型（TextCNN），离线推理
 *
 * 模型: TextCNN (字符级嵌入 + 多尺度卷积)
 * 输入: 字符ID序列 (max_len=128)
 * 输出: [safe_score, fraud_score] logits
 *
 * 对于 BERT+SVM 方案，Android端使用本地关键词+SVM权重做fallback
 */
public class LocalTextDetector {
    private static final String TAG = "LocalTextDetector";
    private static final String MODEL_ASSET_NAME = "textcnn_model_scripted.pt";
    private static final String VOCAB_ASSET_NAME = "textcnn_vocab.txt";
    private static final int MAX_LEN = 128;

    private static LocalTextDetector instance;
    private Module module;
    private Map<Character, Integer> vocab;  // 字符→ID映射
    private int unkId = 1;   // 未知字符ID（默认1）
    private static final int PAD_ID = 0;  // 填充ID
    private boolean isLoaded = false;

    // 关键词评分fallback（当模型不可用时）
    private static final String[][] FRAUD_KEYWORDS = {
        {"马上", "立即", "快", "紧急", "速", "迅速", "尽快"},
        {"转账", "汇款", "打款", "付款", "支付", "缴费"},
        {"警察", "公安", "法院", "检察院", "逮捕", "通缉", "冻结", "洗钱", "涉案"},
        {"领导", "老板", "李总", "王总", "客服", "老师"},
        {"刷单", "返利", "返现", "兼职", "赚钱", "投资", "理财", "股票"},
        {"安全账户", "保证金", "验证码", "密码", "身份证号", "银行卡号"},
        {"恭喜", "中奖", "幸运", "名额有限", "最后一天"},
    };

    private static final float[] KEYWORD_WEIGHTS = {0.15f, 0.25f, 0.25f, 0.15f, 0.2f, 0.3f, 0.1f};

    private LocalTextDetector() {}

    public static synchronized LocalTextDetector getInstance() {
        if (instance == null) {
            instance = new LocalTextDetector();
        }
        return instance;
    }

    /**
     * 初始化模型（从 assets 加载）
     * 如果模型不存在，自动回退到关键词模式
     */
    public void initialize(Context context) {
        if (isLoaded) return;

        try {
            File modelFile = copyAssetToCache(context, MODEL_ASSET_NAME);
            module = Module.load(modelFile.getAbsolutePath());
            loadVocab(context);
            isLoaded = true;
            Log.i(TAG, "TextCNN模型加载成功: " + modelFile.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "TextCNN模型不可用，使用关键词评分模式");
            isLoaded = false; // 回退到关键词模式
        }
    }

    /**
     * 分析文本是否为诈骗信息
     */
    public DetectResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new DetectResult(0.0f, false, "输入文本为空");
        }

        if (isLoaded && module != null) {
            try {
                return analyzeWithModel(text);
            } catch (Exception e) {
                Log.e(TAG, "模型推理失败，回退关键词", e);
            }
        }

        return analyzeWithKeywords(text);
    }

    /**
     * 使用PyTorch模型推理
     */
    private DetectResult analyzeWithModel(String text) {
        // 文本 → 字符ID
        int[] inputIds = new int[MAX_LEN];
        int pos = 0;
        for (int i = 0; i < text.length() && pos < MAX_LEN; i++) {
            char c = text.charAt(i);
            Integer id = vocab.get(c);
            inputIds[pos++] = (id != null) ? id : unkId;
        }
        // 剩余位置填PAD_ID=0

        long[] inputData = new long[MAX_LEN];
        for (int i = 0; i < MAX_LEN; i++) {
            inputData[i] = inputIds[i];
        }

        Tensor inputTensor = Tensor.fromBlob(inputData, new long[]{1, MAX_LEN});
        Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        float[] outputs = outputTensor.getDataAsFloatArray();

        // Softmax
        float safeLogit = outputs[0];
        float fraudLogit = outputs[1];
        float maxLogit = Math.max(safeLogit, fraudLogit);
        float safeProb = (float) Math.exp(safeLogit - maxLogit);
        float fraudProb = (float) Math.exp(fraudLogit - maxLogit);
        float sum = safeProb + fraudProb;
        fraudProb /= sum;

        boolean isFraud = fraudProb > 0.5f;
        String detail = String.format("AI模型检测 (置信度: %.1f%%)", fraudProb * 100);
        return new DetectResult(fraudProb, isFraud, detail);
    }

    /**
     * 关键词评分 fallback
     */
    private DetectResult analyzeWithKeywords(String text) {
        float score = 0.05f; // 基础分

        StringBuilder matchedDetails = new StringBuilder();
        for (int i = 0; i < FRAUD_KEYWORDS.length; i++) {
            for (String kw : FRAUD_KEYWORDS[i]) {
                if (text.contains(kw)) {
                    score += KEYWORD_WEIGHTS[i];
                    if (matchedDetails.length() < 80) {
                        matchedDetails.append(kw).append(" ");
                    }
                    break; // 每组只计一次
                }
            }
        }

        score = Math.min(score, 1.0f);
        boolean isFraud = score > 0.5f;

        String detail = matchedDetails.length() > 0
                ? "本地关键词匹配: " + matchedDetails.toString().trim()
                : "本地分析完成";

        return new DetectResult(score, isFraud, detail);
    }

    private void loadVocab(Context context) throws IOException {
        vocab = new HashMap<>();
        try (InputStream in = context.getAssets().open(VOCAB_ASSET_NAME)) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    String key = parts[0];
                    int id = Integer.parseInt(parts[1]);
                    // 跳过特殊多字符token，单独处理
                    if ("[PAD]".equals(key)) {
                        continue; // PAD_ID = 0 已定义
                    } else if ("[UNK]".equals(key)) {
                        unkId = id;
                    } else if (key.length() == 1) {
                        vocab.put(key.charAt(0), id);
                    }
                }
            }
        }
        Log.i(TAG, "词表加载: " + vocab.size() + " 个字符, UNK=" + unkId);
    }

    private File copyAssetToCache(Context context, String assetName) throws IOException {
        File cacheFile = new File(context.getCacheDir(), assetName);
        if (cacheFile.exists()) return cacheFile;

        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(cacheFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        return cacheFile;
    }

    public boolean isModelLoaded() {
        return isLoaded;
    }

    public void release() {
        if (module != null) {
            module.destroy();
            module = null;
        }
        isLoaded = false;
    }

    /**
     * 检测结果
     */
    public static class DetectResult {
        public final float riskScore;
        public final boolean isFraud;
        public final String detail;

        public DetectResult(float riskScore, boolean isFraud, String detail) {
            this.riskScore = riskScore;
            this.isFraud = isFraud;
            this.detail = detail;
        }

        public int getRiskLevel() {
            if (riskScore >= 0.8f) return 5;
            if (riskScore >= 0.6f) return 4;
            if (riskScore >= 0.4f) return 3;
            if (riskScore >= 0.2f) return 2;
            return 1;
        }

        public String getRiskDescription() {
            if (riskScore >= 0.8f) return "高风险：极可能是诈骗信息";
            if (riskScore >= 0.6f) return "较高风险：疑似诈骗信息";
            if (riskScore >= 0.4f) return "中等风险：需进一步确认";
            if (riskScore >= 0.2f) return "较低风险：可能正常";
            return "低风险：很可能是正常内容";
        }
    }
}
