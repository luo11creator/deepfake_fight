package com.example.deepfake_fight.ui.call;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deepfake_fight.databinding.FragmentCallDetectBinding;
import com.example.deepfake_fight.service.LocalAudioDetector;
import com.example.deepfake_fight.service.PermissionHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 通话实时录音检测页 —— 事中防御
 *
 * 场景：接听可疑电话时开免提，实时录音，每隔约 4 秒判断一次对方声音
 * 是否为 AI 合成（AASIST 检测）。录音仅本地实时分析，不保存、不上传。
 */
public class CallDetectFragment extends Fragment {

    private static final String TAG = "CallDetect";
    private static final int SAMPLE_RATE = 16000;
    private static final int SEGMENT_SAMPLES = 64600;   // 与 AASIST 输入长度一致

    private FragmentCallDetectBinding binding;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private volatile boolean isRecording = false;

    private final List<Float> segmentScores = new ArrayList<>();
    private ValueAnimator rippleAnimator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCallDetectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnCallToggle.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            else ensureModelAndStart();
        });
        ensureModelLoaded();
    }

    private void ensureModelLoaded() {
        LocalAudioDetector d = LocalAudioDetector.getInstance();
        if (d.isModelLoaded()) return;
        new Thread(() -> {
            try {
                d.initialize(requireContext().getApplicationContext());
            } catch (Exception e) {
                android.util.Log.e(TAG, "音频模型加载失败", e);
            }
        }, "call-model-init").start();
    }

    private void ensureModelAndStart() {
        if (!LocalAudioDetector.getInstance().isModelLoaded()) {
            Toast.makeText(getContext(), "音频模型加载中，请稍候", Toast.LENGTH_SHORT).show();
            ensureModelLoaded();
            return;
        }
        PermissionHelper.requestRecordPermission(this, new PermissionHelper.PermissionCallback() {
            @Override
            public void onGranted() { startRecording(); }
            @Override
            public void onDenied() {
                Toast.makeText(getContext(), "未授权麦克风，无法检测", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startRecording() {
        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
        audioRecord.startRecording();

        isRecording = true;
        binding.btnCallToggle.setText("停止");
        binding.tvCallVerdict.setText("正在监听中...");
        binding.waveCall.start();
        startRipple();

        recordThread = new Thread(this::recordLoop, "call-record");
        recordThread.start();
    }

    private void recordLoop() {
        int bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        short[] buf = new short[bufSize];
        float[] segment = new float[SEGMENT_SAMPLES];
        int pos = 0;

        while (isRecording) {
            int read = audioRecord.read(buf, 0, buf.length);
            if (read <= 0) continue;
            for (int i = 0; i < read; i++) {
                segment[pos] = buf[i] / 32768f;
                pos++;
                if (pos >= SEGMENT_SAMPLES) {
                    float[] seg = segment.clone();
                    pos = 0;
                    detectSegment(seg);
                }
            }
        }
    }

    private void detectSegment(float[] pcm) {
        try {
            LocalAudioDetector.AudioDetectResult r =
                    LocalAudioDetector.getInstance().detect(pcm);
            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> updateUI(r.riskScore));
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "检测失败", e);
        }
    }

    private void updateUI(float riskScore) {
        segmentScores.add(riskScore);
        // 只保留最近 20 段
        while (segmentScores.size() > 20) segmentScores.remove(0);

        // 历史最高（单调，用于提醒），当前片段（实时波动）
        float maxRisk = 0;
        for (float s : segmentScores) if (s > maxRisk) maxRisk = s;

        // 大数字显示「当前片段」分数，会随内容实时波动，不再只涨不跌
        binding.tvCallRiskScore.setText(String.format("%.0f%%", riskScore * 100));
        binding.tvCallVerdict.setText(verdict(riskScore));

        // 片段时间轴 + 历史最高提示
        StringBuilder sb = new StringBuilder();
        for (float s : segmentScores) {
            sb.append(s >= 0.7f ? "■" : s >= 0.4f ? "▨" : "□").append(' ');
        }
        binding.tvCallSegments.setText("峰值 " + String.format("%.0f%%", maxRisk * 100)
                + " · " + sb.toString().trim());
    }

    private String verdict(float risk) {
        if (risk >= 0.8f) return "高度可疑：疑似 AI 合成语音";
        if (risk >= 0.6f) return "较高风险：可能存在合成痕迹";
        if (risk >= 0.4f) return "存在疑点：建议保持警惕";
        if (risk >= 0.2f) return "低风险";
        return "正常：未发现合成痕迹";
    }

    private void stopRecording() {
        isRecording = false;
        if (recordThread != null) {
            try { recordThread.join(500); } catch (InterruptedException ignored) {}
            recordThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
        binding.btnCallToggle.setText("开始检测");
        binding.waveCall.stop();
        stopRipple();
        if (segmentScores.isEmpty()) {
            binding.tvCallVerdict.setText("本次未检测到完整片段");
        }
    }

    /** 脉冲波纹动画：录音时从按钮向外扩散 */
    private void startRipple() {
        stopRipple();
        rippleAnimator = ValueAnimator.ofFloat(1f, 1.55f);
        rippleAnimator.setDuration(1600);
        rippleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rippleAnimator.setInterpolator(new LinearInterpolator());
        rippleAnimator.addUpdateListener(anim -> {
            if (binding == null) return;
            float v = (float) anim.getAnimatedValue();
            binding.rippleRing.setScaleX(v);
            binding.rippleRing.setScaleY(v);
            binding.rippleRing.setAlpha(Math.max(0f, 0.5f - (v - 1f) / 0.55f));
        });
        rippleAnimator.start();
    }

    private void stopRipple() {
        if (rippleAnimator != null) {
            rippleAnimator.cancel();
            rippleAnimator = null;
        }
        if (binding != null) {
            binding.rippleRing.setScaleX(1f);
            binding.rippleRing.setScaleY(1f);
            binding.rippleRing.setAlpha(0.3f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isRecording) stopRecording();
        binding = null;
    }
}
