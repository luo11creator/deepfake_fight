package com.example.deepfake_fight.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.FragmentSettingsBinding;
import com.example.deepfake_fight.service.ApiService;
import com.example.deepfake_fight.service.NetworkConfig;
import com.example.deepfake_fight.service.PrefsManager;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private PrefsManager prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = PrefsManager.getInstance(requireContext());

        loadSettings();
        setupListeners();
    }

    private void loadSettings() {
        binding.etServerUrl.setText(prefs.getServerUrl());

        // 音频模型
        if ("rawnet2".equals(prefs.getModelAudio())) {
            binding.rbAudioRawnet.setChecked(true);
        } else {
            binding.rbAudioAasist.setChecked(true);
        }

        // 视频模型
        if ("xception".equals(prefs.getModelVideo())) {
            binding.rbVideoXception.setChecked(true);
        } else {
            binding.rbVideoEfficient.setChecked(true);
        }

        // 文本模型
        if ("llm".equals(prefs.getModelText())) {
            binding.rbTextLlm.setChecked(true);
        } else {
            binding.rbTextBert.setChecked(true);
        }

        binding.swSaveHistory.setChecked(prefs.isSaveHistory());
        binding.swAutoUpload.setChecked(prefs.isAutoUpload());
        binding.swDarkMode.setChecked(prefs.isDarkMode());
    }

    private void setupListeners() {
        // 测试连接
        binding.btnTestConnection.setOnClickListener(v -> testConnection());

        // 保存
        binding.btnSaveSettings.setOnClickListener(v -> saveSettings());

        // 恢复默认
        binding.btnResetSettings.setOnClickListener(v -> {
            prefs.resetToDefaults();
            loadSettings();
            Toast.makeText(getContext(), "已恢复默认设置", Toast.LENGTH_SHORT).show();
        });
    }

    private void testConnection() {
        String url = binding.etServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        final String testUrl = url + "/api/health";

        binding.btnTestConnection.setText("测试中...");
        binding.btnTestConnection.setEnabled(false);

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Response resp = client.newCall(
                        new Request.Builder().url(testUrl).get().build()
                ).execute();

                String body = resp.body() != null ? resp.body().string() : "";
                // /api/health 返回 {"status":"ok","service":"多维盾卫..."}
                boolean success = resp.isSuccessful() && body.contains("\"status\":\"ok\"");

                requireActivity().runOnUiThread(() -> {
                    binding.btnTestConnection.setText("测试连接");
                    binding.btnTestConnection.setEnabled(true);
                    if (success) {
                        Toast.makeText(getContext(), "✓ 连接成功! 服务运行正常", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "✗ 连接失败: HTTP " + resp.code(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException e) {
                requireActivity().runOnUiThread(() -> {
                    binding.btnTestConnection.setText("测试连接");
                    binding.btnTestConnection.setEnabled(true);
                    Toast.makeText(getContext(), "✗ 无法连接: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void saveSettings() {
        String url = binding.etServerUrl.getText().toString().trim();
        if (url.isEmpty()) url = PrefsManager.DEFAULT_SERVER_URL;

        prefs.setServerUrl(url);
        prefs.setModelAudio(binding.rbAudioRawnet.isChecked() ? "rawnet2" : "aasist");
        prefs.setModelVideo(binding.rbVideoXception.isChecked() ? "xception" : "efficientnet-b4");
        prefs.setModelText(binding.rbTextLlm.isChecked() ? "llm" : "bert-base-chinese");
        prefs.setSaveHistory(binding.swSaveHistory.isChecked());
        prefs.setAutoUpload(binding.swAutoUpload.isChecked());
        prefs.setDarkMode(binding.swDarkMode.isChecked());

        // 应用深色模式
        if (binding.swDarkMode.isChecked()) {
            androidx.appcompat.app.AppCompatDelegate
                    .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate
                    .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        // 刷新 ApiService 上下文
        ApiService.getInstance().initContext(requireContext().getApplicationContext());

        Toast.makeText(getContext(), "设置已保存，重启后深色模式生效", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
