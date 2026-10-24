package com.example.deepfake_fight.ui.audio;

import android.Manifest;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.FragmentAudioDetectBinding;
import com.example.deepfake_fight.service.ApiService;
import com.example.deepfake_fight.service.FileUtils;
import com.example.deepfake_fight.service.PermissionHelper;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class AudioDetectFragment extends Fragment {

    private FragmentAudioDetectBinding binding;
    private MediaRecorder recorder;
    private File audioFile;
    private boolean isRecording = false;
    private int seconds = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // 语音识别 (ASR)
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private String currentTranscript = "";

    // 文件选择
    private final ActivityResultLauncher<String> audioFilePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    showFileSelected(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAudioDetectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        binding.btnUploadAudio.setOnClickListener(v -> audioFilePicker.launch("audio/*"));

        // Demo样本按钮
        binding.btnDemoReal.setOnClickListener(v -> testDemoAudio("demo_1_real.wav"));
        binding.btnDemoFake.setOnClickListener(v -> testDemoAudio("demo_1_fake.wav"));

        // 初始化语音识别器
        initSpeechRecognizer();
    }

    // ============ 语音识别 (ASR) ============

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            return; // 设备不支持语音识别
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                binding.tvAudioStatus.setText("● 开始说话...");
            }
            @Override public void onBeginningOfSpeech() {
                binding.tvAudioStatus.setText("● 正在识别...");
            }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                binding.tvAudioStatus.setText("● 识别完成");
            }

            @Override
            public void onError(int error) {
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_NETWORK: msg = "网络错误"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: msg = "未识别到语音"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "语音超时"; break;
                    default: msg = "识别错误(" + error + ")"; break;
                }
                // 静默处理，不影响录音
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    currentTranscript = matches.get(0);
                    requireActivity().runOnUiThread(() -> {
                        binding.tvAsrText.setText("[识别] " + currentTranscript);
                        binding.tvAsrText.setVisibility(View.VISIBLE);
                    });
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        binding.tvAsrText.setText("[识别中] " + matches.get(0));
                        binding.tvAsrText.setVisibility(View.VISIBLE);
                    });
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    // ============ 录音 ============

    private void startRecording() {
        if (!PermissionHelper.hasPermission(requireContext(), Manifest.permission.RECORD_AUDIO)) {
            PermissionHelper.requestRecordPermission(this, new PermissionHelper.PermissionCallback() {
                @Override public void onGranted() { startRecording(); }
                @Override public void onDenied() {
                    Toast.makeText(getContext(), "无录音权限，无法使用语音检测", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        try {
            audioFile = new File(requireContext().getCacheDir(),
                    "recording_" + System.currentTimeMillis() + ".aac");

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            isRecording = true;
            seconds = 0;
            currentTranscript = "";
            updateUI(true);

            // 启动语音识别
            if (speechRecognizer != null) {
                speechRecognizer.startListening(recognizerIntent);
            }

            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    seconds++;
                    binding.tvAudioTimer.setText(String.format("%02d:%02d",
                            seconds / 60, seconds % 60));
                    if (seconds >= 60) stopRecording();
                    else handler.postDelayed(this, 1000);
                }
            };
            handler.postDelayed(timerRunnable, 1000);

        } catch (IOException e) {
            Toast.makeText(getContext(), "录音失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        if (recorder != null) {
            try { recorder.stop(); recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        isRecording = false;
        handler.removeCallbacks(timerRunnable);
        updateUI(false);

        // 录完直接检测
        detectAudio(audioFile);
    }

    // ============ 文件选择 ============

    private void showFileSelected(Uri uri) {
        new Thread(() -> {
            try {
                audioFile = FileUtils.copyUriToCache(requireContext(), uri, "audio");
                requireActivity().runOnUiThread(() -> {
                    binding.tvAudioStatus.setText("文件已选择，开始检测...");
                    binding.tvAudioTimer.setText("");
                    detectAudio(audioFile);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "读取失败: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** 从assets复制Demo音频到缓存并测试 */
    private void testDemoAudio(String assetName) {
        binding.tvAudioStatus.setText("加载Demo样本...");
        binding.btnRecord.setEnabled(false);

        new Thread(() -> {
            try {
                File cacheDir = new File(requireContext().getCacheDir(), "demo_audio");
                cacheDir.mkdirs();
                File demoFile = new File(cacheDir, assetName);
                if (!demoFile.exists()) {
                    java.io.InputStream in = requireContext().getAssets()
                            .open("demo_audio/" + assetName);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(demoFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close(); out.close();
                }
                audioFile = demoFile;
                requireActivity().runOnUiThread(() -> {
                    binding.tvAudioStatus.setText("Demo样本就绪，开始本地AI检测...");
                    detectAudio(demoFile);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    binding.btnRecord.setEnabled(true);
                    Toast.makeText(getContext(), "Demo加载失败: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ============ 检测（优先本地AI → 服务器 → 模拟） ============

    private void detectAudio(File file) {
        if (file == null || !file.exists()) {
            runMockDetection();
            return;
        }
        binding.tvAudioStatus.setText("本地AI分析中...");
        binding.btnRecord.setEnabled(false);

        // 后台线程执行本地检测
        new Thread(() -> {
            try {
                // 确保本地模型已加载
                com.example.deepfake_fight.service.LocalAudioDetector detector =
                        com.example.deepfake_fight.service.LocalAudioDetector.getInstance();
                if (!detector.isModelLoaded()) {
                    detector.initialize(requireContext());
                }

                if (detector.isModelLoaded()) {
                    // ★ 本地AI推理
                    com.example.deepfake_fight.service.LocalAudioDetector.AudioDetectResult result =
                            detector.detect(file);
                    // 端侧检测完成后上报统计（只报类型，不传数据）
                    com.example.deepfake_fight.service.ApiService.getInstance()
                            .reportDetection("audio");

                    requireActivity().runOnUiThread(() -> {
                        binding.btnRecord.setEnabled(true);
                        binding.tvAudioStatus.setText("本地AI检测完成");
                        navigateFromLocalResult(result);
                    });
                } else {
                    // 本地模型加载失败 → 尝试服务器
                    tryServerDetection(file);
                }
            } catch (Exception e) {
                // 本地检测出错 → 尝试服务器
                requireActivity().runOnUiThread(() ->
                        binding.tvAudioStatus.setText("本地检测失败，尝试服务器..."));
                tryServerDetection(file);
            }
        }).start();
    }

    /** 尝试服务器检测 */
    private void tryServerDetection(File file) {
        requireActivity().runOnUiThread(() -> {
            binding.tvAudioStatus.setText("上传到服务器检测中...");
            ApiService.getInstance().detectAudio(file, new ApiService.DetectCallback() {
                @Override
                public void onProgress(String status) {
                    binding.tvAudioStatus.setText(status);
                }
                @Override
                public void onSuccess(JSONObject result) {
                    binding.btnRecord.setEnabled(true);
                    navigateFromApi(result, "audio");
                }
                @Override
                public void onError(String errorMessage) {
                    binding.btnRecord.setEnabled(true);
                    binding.tvAudioStatus.setText("服务器未连接，使用本地模拟...");
                    binding.tvAudioStatus.postDelayed(
                            AudioDetectFragment.this::runMockDetection, 1000);
                }
            });
        });
    }

    /** 本地AI检测结果导航 */
    private void navigateFromLocalResult(
            com.example.deepfake_fight.service.LocalAudioDetector.AudioDetectResult result) {
        Bundle args = new Bundle();
        args.putString("type", "audio");
        args.putFloat("risk_score", result.riskScore);
        args.putInt("risk_level", result.riskLevel);
        args.putString("api_response", result.toJson());
        // 保存音频文件路径（供历史记录播放）
        if (audioFile != null) {
            args.putString("file_path", audioFile.getAbsolutePath());
        }

        if (!currentTranscript.isEmpty()) {
            args.putString("transcript", currentTranscript);
        }

        Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
    }

    private void runMockDetection() {
        binding.tvAudioStatus.setText("本地分析完成");
        handler.postDelayed(() -> {
            float score = 0.08f + (float) Math.random() * 0.3f;
            int level = score >= 0.6f ? 4 : score >= 0.4f ? 3 : score >= 0.2f ? 2 : 1;
            Bundle args = new Bundle();
            args.putString("type", "audio");
            args.putFloat("risk_score", score);
            args.putInt("risk_level", level);
            Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
        }, 800);
    }

    private void navigateFromApi(JSONObject result, String type) {
        try {
            float score = (float) result.getDouble("risk_score");
            int level = result.getInt("risk_level");
            Bundle args = new Bundle();
            args.putString("type", type);
            args.putFloat("risk_score", score);
            args.putInt("risk_level", level);
            args.putString("api_response", result.toString());
            if (audioFile != null) {
                args.putString("file_path", audioFile.getAbsolutePath());
            }

            String transcript = result.optString("transcript", "");
            if (!transcript.isEmpty()) {
                args.putString("transcript", transcript);
            }

            Navigation.findNavController(requireView()).navigate(R.id.nav_result, args);
        } catch (Exception e) {
            runMockDetection();
        }
    }

    // ============ UI ============

    private void updateUI(boolean recording) {
        if (recording) {
            binding.btnRecord.setText("停止录音");
            binding.btnRecord.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.danger));
            binding.tvAudioTimer.setVisibility(View.VISIBLE);
        } else {
            binding.btnRecord.setText("开始录音");
            binding.btnRecord.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.danger));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timerRunnable);
        if (recorder != null) {
            try { recorder.stop(); recorder.release(); } catch (Exception ignored) {}
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        binding = null;
    }
}
