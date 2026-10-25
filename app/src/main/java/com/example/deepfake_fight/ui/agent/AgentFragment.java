package com.example.deepfake_fight.ui.agent;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.deepfake_fight.databinding.FragmentAgentBinding;
import com.example.deepfake_fight.service.ApiService;

import org.json.JSONObject;

/**
 * 反诈智能体页 —— 大模型智能体（LLM Agent）对话界面
 *
 * 用户描述可疑信息 → 云端智能体（后端 /api/v1/agent/chat）自动调用检测工具
 * （文本诈骗检测 / 案例检索 / 多模态融合）→ 返回综合研判与防骗建议。
 */
public class AgentFragment extends Fragment {

    private static final String TAG = "AgentFragment";
    private FragmentAgentBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAgentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnAgentSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String message = binding.etAgentInput.getText() != null
                ? binding.etAgentInput.getText().toString().trim() : "";
        if (message.isEmpty()) {
            Toast.makeText(getContext(), "请先描述你遇到的可疑信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 追加用户消息到历史
        appendHistory("\n\n【你】\n" + message);

        binding.etAgentInput.setText("");
        binding.btnAgentSend.setEnabled(false);
        binding.tvAgentProgress.setText("智能体分析中，正在调用检测工具...");

        ApiService.getInstance().agentChat(message, new ApiService.DetectCallback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (!isAdded()) return;
                binding.btnAgentSend.setEnabled(true);
                binding.tvAgentProgress.setText("");
                binding.waveSpeaking.stop();
                binding.waveSpeaking.setVisibility(View.GONE);

                String answer = result.optString("answer", "（未返回分析结果）");
                appendHistory("\n\n【智能体】\n" + answer);

                // 若有工具调用记录，追加简要说明
                if (result.has("tools_used")) {
                    binding.tvAgentProgress.setText("已调用检测工具完成研判");
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                binding.btnAgentSend.setEnabled(true);
                binding.tvAgentProgress.setText("");
                binding.waveSpeaking.stop();
                binding.waveSpeaking.setVisibility(View.GONE);
                appendHistory("\n\n【提示】智能体服务未连接（" + errorMessage
                        + "）。请启动后端服务后再试。");
                Log.w(TAG, "智能体调用失败: " + errorMessage);
            }

            @Override
            public void onProgress(String status) {
                if (isAdded()) {
                    binding.tvAgentProgress.setText(status);
                    binding.waveSpeaking.setVisibility(View.VISIBLE);
                    binding.waveSpeaking.start();
                }
            }
        });
    }

    private void appendHistory(String text) {
        if (binding == null) return;
        binding.tvAgentHistory.append(text);
        // 滚动到底部
        binding.svAgentChat.post(() ->
                binding.svAgentChat.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
