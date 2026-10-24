package com.example.deepfake_fight.ui.history;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.DetectionRecord;
import com.example.deepfake_fight.databinding.FragmentHistoryBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new HistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvHistory.setAdapter(adapter);

        // 删除（加确认弹窗防止误触）
        adapter.setOnDeleteListener(record -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("删除记录")
                    .setMessage("确定要删除这条检测记录吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            AppDatabase.getInstance(requireContext())
                                    .detectionRecordDao().delete(record);
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 点击查看详情
        adapter.setOnItemClickListener(this::showDetailDialog);

        // 观察数据库变化
        AppDatabase.getInstance(requireContext())
                .detectionRecordDao().getAllRecords()
                .observe(getViewLifecycleOwner(), records -> {
                    adapter.setRecords(records);
                    if (binding != null) {
                        binding.tvHistoryCount.setText("共 " + records.size() + " 条记录");
                        if (records.isEmpty()) {
                            binding.rvHistory.setVisibility(View.GONE);
                            binding.layoutEmpty.setVisibility(View.VISIBLE);
                        } else {
                            binding.rvHistory.setVisibility(View.VISIBLE);
                            binding.layoutEmpty.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void showDetailDialog(DetectionRecord r) {
        if (getContext() == null) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View detailView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_record_detail, null);
        dialog.setContentView(detailView);

        // 填充数据
        ((TextView) detailView.findViewById(R.id.tv_detail_type)).setText(r.typeLabel);

        // 来源标签
        TextView sourceBadge = detailView.findViewById(R.id.tv_detail_source);
        String engineName;
        switch (r.source != null ? r.source : "") {
            case "ai_local":
                sourceBadge.setText("🤖 AI模型");
                sourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF1B5E20));
                engineName = "本地AI模型 · 离线推理 · 数据不上传";
                break;
            case "ai_server":
                sourceBadge.setText("☁ 云端检测");
                sourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF1565C0));
                engineName = "云端服务器 · 网络检测";
                break;
            case "mock":
                sourceBadge.setText("⚠ 模拟数据");
                sourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFE65100));
                engineName = "离线模拟 · 仅供参考";
                break;
            default:
                sourceBadge.setText("本地");
                sourceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF757575));
                engineName = "本地检测";
                break;
        }
        ((TextView) detailView.findViewById(R.id.tv_detail_engine)).setText(engineName);

        // 风险等级
        int[] colors = {R.color.success, R.color.success, R.color.warning, R.color.danger, R.color.danger};
        int verdictColor = getResources().getColor(colors[Math.min(r.riskLevel - 1, 4)]);

        TextView levelTv = detailView.findViewById(R.id.tv_detail_level);
        levelTv.setText(String.valueOf(r.riskLevel));
        levelTv.setTextColor(verdictColor);

        TextView verdictTv = detailView.findViewById(R.id.tv_detail_verdict);
        verdictTv.setText(r.verdict);
        verdictTv.setTextColor(verdictColor);

        // 进度条
        android.widget.ProgressBar progressBar = detailView.findViewById(R.id.progress_detail_risk);
        progressBar.setProgress((int)(r.riskScore * 100));
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(verdictColor));

        // 评分
        ((TextView) detailView.findViewById(R.id.tv_detail_score))
                .setText(String.format(Locale.getDefault(), "%.0f%%", r.riskScore * 100));

        // 时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        ((TextView) detailView.findViewById(R.id.tv_detail_time))
                .setText(sdf.format(new Date(r.createdAt)));

        // 文件名
        ((TextView) detailView.findViewById(R.id.tv_detail_file))
                .setText(r.fileName != null && !r.fileName.isEmpty() ? r.fileName : "未知文件");

        // ★ 加载检测图片（优先标注图，完整显示 + 点击全屏）
        ImageView ivImage = detailView.findViewById(R.id.iv_detail_image);
        TextView tvImageLabel = detailView.findViewById(R.id.tv_image_label);
        String displayImagePath = null;

        // 先尝试从 apiResponse 中找标注图路径
        try {
            org.json.JSONObject respJson = new org.json.JSONObject(r.detailsJson != null ? r.detailsJson : "{}");
            String annotatedPath = respJson.optString("annotated_image", "");
            if (!annotatedPath.isEmpty()) {
                java.io.File annoFile = new java.io.File(annotatedPath);
                if (annoFile.exists()) {
                    displayImagePath = annotatedPath;
                    if (tvImageLabel != null) tvImageLabel.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception ignored) {}

        // 回退：原始图片路径
        if (displayImagePath == null && r.imagePath != null && !r.imagePath.isEmpty()) {
            java.io.File imgFile = new java.io.File(r.imagePath);
            if (imgFile.exists()) {
                displayImagePath = r.imagePath;
            }
        }

        if (displayImagePath != null) {
            Bitmap bmp = BitmapFactory.decodeFile(displayImagePath);
            if (bmp != null) {
                ivImage.setImageBitmap(bmp);
                ivImage.setVisibility(View.VISIBLE);
                // 点击全屏查看
                String finalPath = displayImagePath;
                ivImage.setOnClickListener(v -> {
                    // 使用 ResultFragment 的全屏逻辑
                    showFullScreenImage(finalPath);
                });
            }
        }

        // 建议
        ((TextView) detailView.findViewById(R.id.tv_detail_suggestions))
                .setText(buildSuggestions(r.riskLevel));

        // 音频播放按钮（仅音频记录）
        android.widget.Button btnPlay = detailView.findViewById(R.id.btn_play_audio);
        if ("audio".equals(r.type) && r.imagePath != null && !r.imagePath.isEmpty()) {
            java.io.File af = new java.io.File(r.imagePath);
            if (af.exists()) {
                btnPlay.setVisibility(View.VISIBLE);
                final String audioPath = r.imagePath;
                btnPlay.setOnClickListener(v -> {
                    try {
                        android.media.MediaPlayer mp = new android.media.MediaPlayer();
                        mp.setDataSource(audioPath);
                        mp.prepare();
                        mp.start();
                        btnPlay.setText("⏸ 播放中...");
                        mp.setOnCompletionListener(m -> {
                            btnPlay.setText("▶ 播放检测音频");
                            m.release();
                        });
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "播放失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
