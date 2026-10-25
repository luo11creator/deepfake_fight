package com.example.deepfake_fight.service;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * 本地音频真伪检测器 (端侧推理)
 *
 * 模型: AASIST (Audio Anti-Spoofing using Graph Attention Networks)
 * 框架: PyTorch Mobile (TorchScript)
 * 输入: 4秒 16kHz 单声道原始波形 → 64000个float采样点
 * 输出: [bonafide_score, spoof_score] → spoof概率即为风险分数
 *
 * 特点:
 *   - 完全离线推理，数据不上传
 *   - 模型仅 492KB，加载迅速
 *   - 支持录音 + 音频文件双模式
 *   - 自动分段分析长音频
 */
public class LocalAudioDetector {
    private static final String TAG = "LocalAudioDetector";
    private static final String MODEL_ASSET_NAME = "audio_spoof_model.pt";

    // 音频参数
    public static final int SAMPLE_RATE = 16000;
    public static final int SEGMENT_DURATION_SEC = 4;
    public static final int SEGMENT_SAMPLES = 64600;  // AASIST-L 输入长度
    public static final int MIN_SEGMENT_SAMPLES = SAMPLE_RATE;  // 最少1秒

    private static LocalAudioDetector instance;
    private Module module;
    private boolean isLoaded = false;
    private Context appContext;

    private LocalAudioDetector() {}

    public static synchronized LocalAudioDetector getInstance() {
        if (instance == null) {
            instance = new LocalAudioDetector();
        }
        return instance;
    }

    /**
     * 初始化模型（从 assets 加载 TorchScript 模型）
     */
    public void initialize(Context context) {
        if (isLoaded) return;
        this.appContext = context.getApplicationContext();

        try {
            File modelFile = copyAssetToCache(context, MODEL_ASSET_NAME);
            module = Module.load(modelFile.getAbsolutePath());
            isLoaded = true;
            Log.i(TAG, "[OK] 音频检测模型加载成功: " + modelFile.getAbsolutePath()
                    + " (" + modelFile.length() / 1024 + " KB)");
        } catch (Exception e) {
            Log.e(TAG, "[ERR] 音频模型加载失败", e);
            isLoaded = false;
        }
    }

    // ================================================================
    // 核心检测方法
    // ================================================================

    /**
     * 检测音频文件 (支持 WAV/AAC/MP3 等)
     *
     * @param audioFile 音频文件
     * @return 检测结果
     */
    public AudioDetectResult detect(File audioFile) throws Exception {
        if (!isLoaded) throw new IllegalStateException("模型未加载，请先调用 initialize()");

        // 1. 解码为 PCM float 数组
        float[] pcmData = decodeToPCM(audioFile);
        Log.i(TAG, "解码完成: " + pcmData.length + " samples ("
                + String.format("%.1f", pcmData.length / (float) SAMPLE_RATE) + "s)");

        // 2. 检测
        return detect(pcmData);
    }

    /**
     * 检测 PCM 音频数据
     *
     * @param pcmData 16kHz 单声道 float 数据 [-1.0, 1.0]
     * @return 检测结果
     */
    public AudioDetectResult detect(float[] pcmData) throws Exception {
        if (!isLoaded) throw new IllegalStateException("模型未加载");

        // 1. 预处理: 切分片段
        List<float[]> segments = segmentAudio(pcmData);
        Log.i(TAG, "音频切分: " + pcmData.length / (float) SAMPLE_RATE + "s → "
                + segments.size() + " 个片段");

        // 2. 逐片段推理
        List<Float> segmentScores = new ArrayList<>();
        for (float[] seg : segments) {
            float score = inferSegment(seg);
            segmentScores.add(score);
        }

        // 3. 聚合结果
        return aggregateResults(segmentScores);
    }

    // ================================================================
    // 音频预处理
    // ================================================================

    /**
     * 将长音频切分为4秒片段
     */
    private List<float[]> segmentAudio(float[] audio) {
        List<float[]> segments = new ArrayList<>();

        for (int i = 0; i < audio.length; i += SEGMENT_SAMPLES) {
            int end = Math.min(i + SEGMENT_SAMPLES, audio.length);

            if (end - i >= MIN_SEGMENT_SAMPLES) {  // 至少1秒
                float[] seg = new float[SEGMENT_SAMPLES];
                System.arraycopy(audio, i, seg, 0, end - i);
                // 不足4秒的部分已自动补零（Java float默认值）
                segments.add(seg);
            }
        }

        // 如果音频太短（< 1秒），复制填充到4秒
        if (segments.isEmpty() && audio.length > 0) {
            float[] seg = new float[SEGMENT_SAMPLES];
            int copyLen = Math.min(audio.length, SEGMENT_SAMPLES);
            System.arraycopy(audio, 0, seg, 0, copyLen);
            segments.add(seg);
        }

        return segments;
    }

    // ================================================================
    // 模型推理
    // ================================================================

    /**
     * 对单个4秒片段进行推理
     *
     * @param segment 64000个float采样点
     * @return spoof概率 (0~1)
     */
    private float inferSegment(float[] segment) {
        // 归一化
        float maxAbs = 0;
        for (float v : segment) {
            if (Math.abs(v) > maxAbs) maxAbs = Math.abs(v);
        }
        if (maxAbs > 0) {
            for (int i = 0; i < segment.length; i++) {
                segment[i] /= maxAbs;
            }
        }

        // 创建 Tensor: shape (1, 64000)
        Tensor inputTensor = Tensor.fromBlob(segment, new long[]{1, SEGMENT_SAMPLES});

        // 前向推理
        Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        float[] logits = outputTensor.getDataAsFloatArray();

        // AASIST-L 输出: [spoof_logit, bonafide_logit] (注意顺序!)
        float spoofLogit = logits[0];
        float bonafideLogit = logits[1];
        float maxLogit = Math.max(bonafideLogit, spoofLogit);

        float spoofExp = (float) Math.exp(spoofLogit - maxLogit);
        float bonafideExp = (float) Math.exp(bonafideLogit - maxLogit);
        float spoofProb = spoofExp / (spoofExp + bonafideExp);

        return spoofProb;
    }

    // ================================================================
    // 结果聚合
    // ================================================================

    /**
     * 聚合多个片段的分析结果
     */
    private AudioDetectResult aggregateResults(List<Float> scores) {
        if (scores.isEmpty()) {
            return new AudioDetectResult(0.1f, 1, false, new float[0]);
        }

        float[] scoreArray = new float[scores.size()];
        float maxScore = 0;
        float sum = 0;
        for (int i = 0; i < scores.size(); i++) {
            scoreArray[i] = scores.get(i);
            if (scoreArray[i] > maxScore) maxScore = scoreArray[i];
            sum += scoreArray[i];
        }
        float meanScore = sum / scores.size();

        // 聚合策略: 取最大值（最可疑片段决定）
        float finalScore = maxScore;
        boolean isSpoofed = finalScore > 0.5f;

        // 风险等级
        int riskLevel;
        if (finalScore >= 0.8f) riskLevel = 5;
        else if (finalScore >= 0.6f) riskLevel = 4;
        else if (finalScore >= 0.4f) riskLevel = 3;
        else if (finalScore >= 0.2f) riskLevel = 2;
        else riskLevel = 1;

        // 异常分析
        List<String> artifacts = new ArrayList<>();
        if (maxScore > 0.7f) {
            artifacts.add("多个片段检测到合成语音特征");
        }
        if (scores.size() > 1) {
            // 计算方差
            float variance = 0;
            for (float s : scoreArray) {
                variance += (s - meanScore) * (s - meanScore);
            }
            variance /= scores.size();
            if (variance > 0.05f) {
                artifacts.add("片段间存在异常差异(疑似拼接合成)");
            }
        }
        if (maxScore >= 0.4f && maxScore < 0.7f) {
            artifacts.add("部分片段存在微弱合成痕迹");
        }

        AudioDetectResult result = new AudioDetectResult(
                finalScore, riskLevel, isSpoofed, scoreArray
        );
        result.artifacts = artifacts.toArray(new String[0]);
        result.segmentsAnalyzed = scores.size();

        return result;
    }

    // ================================================================
    // 音频解码 (AAC/MP3/WAV → 16kHz mono PCM float)
    // ================================================================

    /**
     * 将音频文件解码为 16kHz 单声道 PCM float 数组
     * 支持 WAV, AAC, MP3, M4A 等格式
     */
    public float[] decodeToPCM(File audioFile) throws IOException {
        String path = audioFile.getAbsolutePath();
        String ext = path.substring(path.lastIndexOf('.')).toLowerCase();

        // WAV 文件: 直接读取
        if (ext.equals(".wav") || ext.equals(".pcm")) {
            return decodeWavFile(audioFile);
        }

        // 压缩格式 (AAC/MP3/M4A): 使用 MediaExtractor + MediaCodec
        return decodeCompressedAudio(audioFile);
    }

    /**
     * 读取 WAV 文件 → float 数组
     */
    private float[] decodeWavFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[44];
            int read = fis.read(header);
            if (read < 44) throw new IOException("无效的WAV文件(头部不足44字节)");

            // 解析WAV头
            int channels = header[22] & 0xFF;         // 声道数
            int sampleRate = (header[24] & 0xFF) | ((header[25] & 0xFF) << 8)
                    | ((header[26] & 0xFF) << 16) | ((header[27] & 0xFF) << 24);
            int bitsPerSample = header[34] & 0xFF;     // 位深
            int dataSize = (header[40] & 0xFF) | ((header[41] & 0xFF) << 8)
                    | ((header[42] & 0xFF) << 16) | ((header[43] & 0xFF) << 24);

            // 读取PCM数据
            byte[] pcmBytes = new byte[dataSize];
            fis.read(pcmBytes);

            // 转换为 short 数组
            ShortBuffer shortBuf = ByteBuffer.wrap(pcmBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer();
            short[] shortData = new short[shortBuf.remaining()];
            shortBuf.get(shortData);

            // 重采样 + 转单声道 + 转float
            return convertToMonoFloat(shortData, channels, sampleRate);
        }
    }

    /**
     * 使用 MediaExtractor 解码压缩音频
     */
    private float[] decodeCompressedAudio(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());

            // 找到音频轨道
            int audioTrackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = f;
                    break;
                }
            }

            if (audioTrackIndex < 0) {
                throw new IOException("文件中没有音频轨道");
            }

            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            extractor.selectTrack(audioTrackIndex);

            // 创建解码器
            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            // 解码循环
            List<Short> allSamples = new ArrayList<>();
            boolean isEOS = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!isEOS) {
                int inIndex = codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize,
                                extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
                    ShortBuffer shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer();
                    short[] chunk = new short[shortBuf.remaining()];
                    shortBuf.get(chunk);
                    for (short s : chunk) allSamples.add(s);
                    codec.releaseOutputBuffer(outIndex, false);
                }
            }

            codec.stop();
            codec.release();
            extractor.release();

            // 转换为基本类型数组
            short[] shortData = new short[allSamples.size()];
            for (int i = 0; i < shortData.length; i++) shortData[i] = allSamples.get(i);

            return convertToMonoFloat(shortData, channels, sampleRate);

        } finally {
            extractor.release();
        }
    }

    /**
     * PCM short → 16kHz 单声道 float
     */
    private float[] convertToMonoFloat(short[] input, int channels, int srcSampleRate) {
        int totalSamples = input.length / channels;

        // 简单重采样: 线性插值
        double resampleRatio = (double) SAMPLE_RATE / srcSampleRate;
        int outputLen = (int) (totalSamples * resampleRatio);

        float[] output = new float[outputLen];

        for (int i = 0; i < outputLen; i++) {
            double srcIndex = i / resampleRatio;
            int idx0 = (int) srcIndex;
            int idx1 = Math.min(idx0 + 1, totalSamples - 1);
            double frac = srcIndex - idx0;

            // 取多声道平均值（转单声道）
            float val0 = 0, val1 = 0;
            for (int ch = 0; ch < channels; ch++) {
                val0 += input[idx0 * channels + ch];
                val1 += input[idx1 * channels + ch];
            }
            val0 /= channels;
            val1 /= channels;

            // 线性插值 + 归一化到 [-1, 1]
            output[i] = (float) ((val0 + (val1 - val0) * frac) / 32768.0);
        }

        return output;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private File copyAssetToCache(Context context, String assetName) throws IOException {
        File cacheFile = new File(context.getCacheDir(), assetName);
        if (cacheFile.exists()) {
            return cacheFile;
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

    public void release() {
        if (module != null) {
            module.destroy();
            module = null;
        }
        isLoaded = false;
    }

    // ================================================================
    // 检测结果类
    // ================================================================

    public static class AudioDetectResult {
        public final float riskScore;         // 0~1, AI合成概率
        public final int riskLevel;           // 1~5
        public final boolean isSpoofed;       // 是否判定为AI伪造
        public final float[] segmentScores;   // 每个片段的分数
        public int segmentsAnalyzed;          // 分析的片段数
        public String[] artifacts;            // 检测到的异常

        public AudioDetectResult(float riskScore, int riskLevel,
                                  boolean isSpoofed, float[] segmentScores) {
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.isSpoofed = isSpoofed;
            this.segmentScores = segmentScores;
            this.segmentsAnalyzed = segmentScores.length;
            this.artifacts = new String[0];
        }

        /** 风险等级文字 */
        public String getVerdict() {
            if (riskScore >= 0.8f) return "AI伪造语音";
            if (riskScore >= 0.6f) return "高度可疑";
            if (riskScore >= 0.4f) return "存在疑点";
            if (riskScore >= 0.2f) return "低风险";
            return "真实人声";
        }

        /** 风险等级格式化显示 */
        public String getRiskStars() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append(i < riskLevel ? "★" : "☆");
            }
            return sb.toString();
        }

        /** 转为JSON字符串 (用于存储到Room数据库) */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"method\":\"本地AI模型(AASIST)\",");
            sb.append("\"risk_score\":").append(riskScore).append(",");
            sb.append("\"risk_level\":").append(riskLevel).append(",");
            sb.append("\"is_spoofed\":").append(isSpoofed).append(",");
            sb.append("\"verdict\":\"").append(getVerdict()).append("\",");
            sb.append("\"segments_analyzed\":").append(segmentsAnalyzed).append(",");
            sb.append("\"segment_scores\":[");
            for (int i = 0; i < segmentScores.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format("%.4f", segmentScores[i]));
            }
            sb.append("],");
            sb.append("\"artifacts\":[");
            if (artifacts != null) {
                for (int i = 0; i < artifacts.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(artifacts[i]).append("\"");
                }
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }
    }
}
