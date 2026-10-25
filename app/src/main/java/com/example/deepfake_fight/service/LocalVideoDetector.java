package com.example.deepfake_fight.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 本地视频/图片 Deepfake 检测器
 * 使用 PyTorch Mobile 加载 TorchScript 模型，离线推理
 *
 * 模型: EfficientNet-B0 微调
 * 输入: 224×224 RGB 图片，ImageNet 归一化
 * 输出: [real_score, fake_score] logits
 */
public class LocalVideoDetector {
    private static final String TAG = "LocalVideoDetector";
    private static final String MODEL_ASSET_NAME = "deepfake_model_scripted.pt";
    private static final int INPUT_SIZE = 224;

    // ImageNet 归一化参数
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    private static LocalVideoDetector instance;
    private Module module;
    private boolean isLoaded = false;

    private LocalVideoDetector() {}

    public static synchronized LocalVideoDetector getInstance() {
        if (instance == null) {
            instance = new LocalVideoDetector();
        }
        return instance;
    }

    /**
     * 初始化模型（从 assets 加载）
     */
    public void initialize(Context context) {
        if (isLoaded) return;

        try {
            // 从 assets 拷贝到缓存目录（PyTorch Mobile 需要文件路径）
            File modelFile = copyAssetToCache(context, MODEL_ASSET_NAME);
            module = Module.load(modelFile.getAbsolutePath());
            isLoaded = true;
            Log.i(TAG, "模型加载成功: " + modelFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "模型加载失败", e);
            isLoaded = false;
        }
    }

    /**
     * 检测图片是否为 Deepfake
     *
     * @param imagePath 图片文件路径
     * @return 检测结果 (riskScore: 0~1, isFake: boolean)
     */
    public DetectResult detect(String imagePath) throws Exception {
        if (!isLoaded) throw new IllegalStateException("模型未加载，请先调用 initialize()");

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) throw new IOException("无法读取图片: " + imagePath);

        DetectResult result = detect(bitmap);
        bitmap.recycle();
        return result;
    }

    /**
     * 检测 Bitmap 图片
     */
    public DetectResult detect(Bitmap bitmap) throws Exception {
        if (!isLoaded) throw new IllegalStateException("模型未加载，请先调用 initialize()");

        // 1. 预处理: 缩放 + 归一化
        Tensor inputTensor = preprocess(bitmap);

        // 2. 推理
        Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        float[] outputs = outputTensor.getDataAsFloatArray();

        // 3. Softmax 得到概率
        float realLogit = outputs[0];
        float fakeLogit = outputs[1];
        float maxLogit = Math.max(realLogit, fakeLogit);

        float realProb = (float) Math.exp(realLogit - maxLogit);
        float fakeProb = (float) Math.exp(fakeLogit - maxLogit);
        float sum = realProb + fakeProb;
        realProb /= sum;
        fakeProb /= sum;

        // fakeProb 即为风险分数
        float riskScore = fakeProb;
        boolean isFake = riskScore > 0.5f;

        Log.i(TAG, String.format("检测结果: real=%.4f fake=%.4f risk=%.4f isFake=%b",
                realProb, fakeProb, riskScore, isFake));

        return new DetectResult(riskScore, isFake, realProb, fakeProb);
    }

    /**
     * 图片预处理: 缩放 → 归一化 → NCHW Tensor
     */
    private Tensor preprocess(Bitmap bitmap) {
        // 缩放到 224×224
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        resized.recycle();

        // 转为 float[N][C][H][W] 格式
        float[] inputArray = new float[3 * INPUT_SIZE * INPUT_SIZE];

        for (int i = 0; i < INPUT_SIZE * INPUT_SIZE; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8)  & 0xFF;
            int b =  pixel        & 0xFF;

            // 归一化: (value/255 - mean) / std
            inputArray[i]                              = ((r / 255.0f) - MEAN[0]) / STD[0]; // R
            inputArray[INPUT_SIZE * INPUT_SIZE + i]    = ((g / 255.0f) - MEAN[1]) / STD[1]; // G
            inputArray[2 * INPUT_SIZE * INPUT_SIZE + i] = ((b / 255.0f) - MEAN[2]) / STD[2]; // B
        }

        return Tensor.fromBlob(inputArray, new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
    }

    /**
     * 从 assets 拷贝文件到缓存目录
     */
    private File copyAssetToCache(Context context, String assetName) throws IOException {
        File cacheFile = new File(context.getCacheDir(), assetName);
        if (cacheFile.exists()) {
            return cacheFile; // 已缓存
        }

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

    /**
     * 释放模型资源
     */
    public void release() {
        if (module != null) {
            module.destroy();
            module = null;
        }
        isLoaded = false;
    }

    /**
     * 检测结果类
     */
    public static class DetectResult {
        public final float riskScore;     // 0~1, 越高越可能是deepfake
        public final boolean isFake;
        public final float realProb;
        public final float fakeProb;

        public DetectResult(float riskScore, boolean isFake, float realProb, float fakeProb) {
            this.riskScore = riskScore;
            this.isFake = isFake;
            this.realProb = realProb;
            this.fakeProb = fakeProb;
        }

        /** 风险等级: 1-5 */
        public int getRiskLevel() {
            if (riskScore >= 0.8f) return 5;
            if (riskScore >= 0.6f) return 4;
            if (riskScore >= 0.4f) return 3;
            if (riskScore >= 0.2f) return 2;
            return 1;
        }

        /** 风险描述 */
        public String getRiskDescription() {
            if (riskScore >= 0.8f) return "高风险：极可能是深度伪造";
            if (riskScore >= 0.6f) return "较高风险：疑似深度伪造";
            if (riskScore >= 0.4f) return "中等风险：需进一步确认";
            if (riskScore >= 0.2f) return "较低风险：可能真实";
            return "低风险：很可能是真实内容";
        }
    }
}
