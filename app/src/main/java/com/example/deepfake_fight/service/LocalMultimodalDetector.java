package com.example.deepfake_fight.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地多模态深度伪造反诈融合检测器 (GuardFusion)
 * ============================================================
 *
 * 这是本项目自有的多模态融合模型在 Android 端的推理实现。
 *
 * 结构：三个单模态骨干各自提取「中间特征」，再送入融合网络 GuardFusion 做
 * 跨模态瓶颈注意力融合 + 跨模态一致性判别，输出联合真伪分与跨模态不一致分。
 *
 *   视频  --[video_feature_scripted.pt]--> 1280 维特征 ─┐
 *   音频  --[audio_feature_scripted.pt]--> 32   维特征 ─┼--> [guardfusion_scripted.pt]
 *   文本  --[text_feature_scripted.pt]--> 512  维特征 ─┘      └-> [real, fake,
 *                                                                  consistent, inconsistent]
 *
 * 依赖模型 (assets/)：
 *   video_feature_scripted.pt  输入 (1,3,224,224) float
 *   audio_feature_scripted.pt  输入 (1,64600)   float
 *   text_feature_scripted.pt   输入 (1,128)     long
 *   guardfusion_scripted.pt    输入三个特征 → 输出 (1,4)
 *   textcnn_vocab.txt          文本字符词表
 */
public class LocalMultimodalDetector {
    private static final String TAG = "LocalMultimodalDetector";

    private static final String VIDEO_FEATURE_ASSET = "video_feature_scripted.pt";
    private static final String AUDIO_FEATURE_ASSET = "audio_feature_scripted.pt";
    private static final String TEXT_FEATURE_ASSET = "text_feature_scripted.pt";
    private static final String FUSION_ASSET = "guardfusion_scripted.pt";
    private static final String VOCAB_ASSET = "textcnn_vocab.txt";

    private static final int IMAGE_SIZE = 224;
    private static final int AUDIO_LEN = 64600;   // 与 audio_feature_scripted.pt 输入一致
    private static final int MAX_TEXT_LEN = 128;
    private static final int PAD_ID = 0;

    // ImageNet 归一化
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    private static LocalMultimodalDetector instance;

    private Module videoFeatureModule;
    private Module audioFeatureModule;
    private Module textFeatureModule;
    private Module fusionModule;
    private Map<Character, Integer> vocab;
    private int unkId = 1;
    private boolean isLoaded = false;

    private LocalMultimodalDetector() {}

    public static synchronized LocalMultimodalDetector getInstance() {
        if (instance == null) instance = new LocalMultimodalDetector();
        return instance;
    }

    /** 初始化 4 个模型 + 词表（从 assets 拷贝到缓存后加载） */
    public void initialize(Context context) {
        if (isLoaded) return;
        try {
            videoFeatureModule = Module.load(copyAssetToCache(context, VIDEO_FEATURE_ASSET).getAbsolutePath());
            audioFeatureModule = Module.load(copyAssetToCache(context, AUDIO_FEATURE_ASSET).getAbsolutePath());
            textFeatureModule = Module.load(copyAssetToCache(context, TEXT_FEATURE_ASSET).getAbsolutePath());
            fusionModule = Module.load(copyAssetToCache(context, FUSION_ASSET).getAbsolutePath());
            loadVocab(context);
            isLoaded = true;
            Log.i(TAG, "多模态融合模型加载成功 (4 个模型)");
        } catch (Exception e) {
            Log.e(TAG, "多模态融合模型加载失败", e);
            isLoaded = false;
        }
    }

    // ================================================================
    // 特征提取
    // ================================================================

    /** 视频/图片 → 1280 维特征 */
    public float[] extractVideoFeature(Bitmap bitmap) {
        Tensor input = preprocessImage(bitmap);
        Tensor out = videoFeatureModule.forward(IValue.from(input)).toTensor();
        return out.getDataAsFloatArray();
    }

    /** 音频 PCM(float[64600] 16kHz 单声道) → 32 维特征 */
    public float[] extractAudioFeature(float[] pcm) {
        float[] seg = new float[AUDIO_LEN];
        int copy = Math.min(pcm.length, AUDIO_LEN);
        System.arraycopy(pcm, 0, seg, 0, copy);
        // 归一化
        float maxAbs = 0;
        for (float v : seg) if (Math.abs(v) > maxAbs) maxAbs = Math.abs(v);
        if (maxAbs > 0) for (int i = 0; i < seg.length; i++) seg[i] /= maxAbs;

        Tensor input = Tensor.fromBlob(seg, new long[]{1, AUDIO_LEN});
        Tensor out = audioFeatureModule.forward(IValue.from(input)).toTensor();
        return out.getDataAsFloatArray();
    }

    /** 文本 → 512 维特征 */
    public float[] extractTextFeature(String text) {
        long[] ids = new long[MAX_TEXT_LEN];
        int pos = 0;
        for (int i = 0; i < text.length() && pos < MAX_TEXT_LEN; i++) {
            Integer id = vocab.get(text.charAt(i));
            ids[pos++] = (id != null) ? id : unkId;
        }
        Tensor input = Tensor.fromBlob(ids, new long[]{1, MAX_TEXT_LEN});
        Tensor out = textFeatureModule.forward(IValue.from(input)).toTensor();
        return out.getDataAsFloatArray();
    }

    // ================================================================
    // 融合推理
    // ================================================================

    /** 三个特征 → 融合输出 [joint_real, joint_fake, cons_consistent, cons_inconsistent] */
    public float[] fuse(float[] vFeat, float[] aFeat, float[] tFeat) {
        Tensor v = Tensor.fromBlob(vFeat, new long[]{1, vFeat.length});
        Tensor a = Tensor.fromBlob(aFeat, new long[]{1, aFeat.length});
        Tensor t = Tensor.fromBlob(tFeat, new long[]{1, tFeat.length});
        Tensor out = fusionModule.forward(IValue.from(v), IValue.from(a), IValue.from(t)).toTensor();
        return out.getDataAsFloatArray();
    }

    /** 完整检测：原始输入 → 融合结果 */
    public MultimodalResult detect(Bitmap image, float[] audioPcm, String text) {
        if (!isLoaded) throw new IllegalStateException("模型未加载，请先调用 initialize()");

        float[] vFeat = extractVideoFeature(image);
        float[] aFeat = extractAudioFeature(audioPcm);
        float[] tFeat = extractTextFeature(text);

        float[] fused = fuse(vFeat, aFeat, tFeat);
        // fused = [joint_real, joint_fake, cons_consistent, cons_inconsistent]
        float jointReal = fused[0], jointFake = fused[1];
        float consConsistent = fused[2], consInconsistent = fused[3];

        float jointFakeProb = softmax2(jointReal, jointFake);
        float inconsistencyProb = softmax2(consConsistent, consInconsistent);

        return new MultimodalResult(jointFakeProb, inconsistencyProb);
    }

    // ================================================================
    // 预处理工具
    // ================================================================

    private Tensor preprocessImage(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
        resized.recycle();

        float[] data = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
        for (int i = 0; i < IMAGE_SIZE * IMAGE_SIZE; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            data[i]                            = ((r / 255f) - MEAN[0]) / STD[0];
            data[IMAGE_SIZE * IMAGE_SIZE + i]  = ((g / 255f) - MEAN[1]) / STD[1];
            data[2 * IMAGE_SIZE * IMAGE_SIZE + i] = ((b / 255f) - MEAN[2]) / STD[2];
        }
        return Tensor.fromBlob(data, new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE});
    }

    private float softmax2(float logit0, float logit1) {
        float max = Math.max(logit0, logit1);
        float e0 = (float) Math.exp(logit0 - max);
        float e1 = (float) Math.exp(logit1 - max);
        return e1 / (e0 + e1);  // 返回第二个通道（fake / inconsistent）的概率
    }

    private void loadVocab(Context context) throws IOException {
        vocab = new HashMap<>();
        try (InputStream in = context.getAssets().open(VOCAB_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    String key = parts[0];
                    int id = Integer.parseInt(parts[1]);
                    if ("[UNK]".equals(key)) unkId = id;
                    else if (key.length() == 1) vocab.put(key.charAt(0), id);
                }
            }
        }
    }

    private File copyAssetToCache(Context context, String assetName) throws IOException {
        File cacheFile = new File(context.getCacheDir(), assetName);
        // 每次都重新复制：避免模型更新后仍复用旧缓存（曾导致旧算子残留、加载失败）
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(cacheFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
        return cacheFile;
    }

    public boolean isModelLoaded() { return isLoaded; }

    public void release() {
        if (videoFeatureModule != null) { videoFeatureModule.destroy(); videoFeatureModule = null; }
        if (audioFeatureModule != null) { audioFeatureModule.destroy(); audioFeatureModule = null; }
        if (textFeatureModule != null) { textFeatureModule.destroy(); textFeatureModule = null; }
        if (fusionModule != null) { fusionModule.destroy(); fusionModule = null; }
        isLoaded = false;
    }

    // ================================================================
    // 结果
    // ================================================================

    public static class MultimodalResult {
        public final float riskScore;          // 联合真伪分（0~1，越大越可能是伪造/诈骗）
        public final float inconsistencyScore; // 跨模态不一致分（0~1，越大说明模态间越矛盾）

        public MultimodalResult(float riskScore, float inconsistencyScore) {
            this.riskScore = riskScore;
            this.inconsistencyScore = inconsistencyScore;
        }

        public int getRiskLevel() {
            if (riskScore >= 0.8f) return 5;
            if (riskScore >= 0.6f) return 4;
            if (riskScore >= 0.4f) return 3;
            if (riskScore >= 0.2f) return 2;
            return 1;
        }

        public String getVerdict() {
            // 跨模态不一致是重要告警：即使单模态分不高，模态矛盾也提示风险
            if (riskScore >= 0.6f || inconsistencyScore >= 0.6f) return "高风险：疑似伪造/跨模态矛盾";
            if (riskScore >= 0.4f || inconsistencyScore >= 0.4f) return "中风险：存在疑点";
            if (riskScore >= 0.2f) return "低风险";
            return "真实内容";
        }

        /** 转为 ResultFragment 可识别的 JSON（method 含 "AI" → 判定为本地 AI 检测） */
        public String toJson() {
            return "{\"method\":\"本地AI模型(GuardFusion多模态融合)\","
                    + "\"risk_score\":" + riskScore + ","
                    + "\"risk_level\":" + getRiskLevel() + ","
                    + "\"inconsistency_score\":" + inconsistencyScore + ","
                    + "\"description\":\"" + getVerdict() + "\"}";
        }
    }
}
