package com.example.deepfake_fight.ui.history;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.DetectionRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<DetectionRecord> records = new ArrayList<>();
    private OnDeleteListener deleteListener;

    public interface OnDeleteListener {
        void onDelete(DetectionRecord record);
    }

    public interface OnItemClickListener {
        void onItemClick(DetectionRecord record);
    }

    private OnItemClickListener clickListener;

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setRecords(List<DetectionRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetectionRecord r = records.get(position);

        holder.typeText.setText(r.typeLabel);

        // 来源标签
        holder.sourceBadge.setVisibility(View.VISIBLE);
        switch (r.source != null ? r.source : "") {
            case "ai_local":
                holder.sourceBadge.setText("🤖 AI模型");
                holder.sourceBadge.setBackgroundColor(0xFF1B5E20);
                break;
            case "ai_server":
                holder.sourceBadge.setText("☁ 云端");
                holder.sourceBadge.setBackgroundColor(0xFF1565C0);
                break;
            case "mock":
                holder.sourceBadge.setText("⚠ 模拟");
                holder.sourceBadge.setBackgroundColor(0xFFE65100);
                break;
            case "cloud":
                holder.sourceBadge.setText("☁ 云端");
                holder.sourceBadge.setBackgroundColor(0xFF1565C0);
                break;
            default:
                holder.sourceBadge.setText("本地");
                holder.sourceBadge.setBackgroundColor(0xFF757575);
                break;
        }

        // 描述
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String desc = (r.fileName != null ? r.fileName : "") + " · " + sdf.format(new Date(r.createdAt));
        holder.descText.setText(desc);

        // 判定
        holder.verdictText.setText(String.format(Locale.getDefault(),
                "风险评分: %.0f%% · %s", r.riskScore * 100, r.verdict));

        // 风险颜色
        int color;
        if (r.riskLevel >= 4) color = 0xFFC62828;
        else if (r.riskLevel == 3) color = 0xFFE65100;
        else if (r.riskLevel == 2) color = 0xFFF57F17;
        else color = 0xFF2E7D32;
        holder.indicator.setBackgroundColor(color);

        // ★ 加载缩略图
        String thumbPath = null;
        try {
            org.json.JSONObject respJson = new org.json.JSONObject(r.detailsJson != null ? r.detailsJson : "{}");
            String annotatedPath = respJson.optString("annotated_image", "");
            if (!annotatedPath.isEmpty() && new java.io.File(annotatedPath).exists()) {
                thumbPath = annotatedPath;
            }
        } catch (Exception ignored) {}
        if (thumbPath == null && r.imagePath != null && !r.imagePath.isEmpty()) {
            java.io.File imgFile = new java.io.File(r.imagePath);
            if (imgFile.exists()) thumbPath = r.imagePath;
        }

        if (thumbPath != null) {
            Bitmap thumb = BitmapFactory.decodeFile(thumbPath);
            if (thumb != null) {
                holder.thumbImage.setImageBitmap(thumb);
                holder.thumbImage.setVisibility(View.VISIBLE);
            }
        } else {
            holder.thumbImage.setVisibility(View.GONE);
        }

        // 删除
        holder.deleteBtn.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(r);
        });

        // 点击查看详情
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(r);
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View indicator;
        ImageView thumbImage;
        TextView typeText, sourceBadge, descText, verdictText;
        ImageView deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            indicator = itemView.findViewById(R.id.v_risk_indicator);
            thumbImage = itemView.findViewById(R.id.iv_history_thumb);
            typeText = itemView.findViewById(R.id.tv_history_type);
            sourceBadge = itemView.findViewById(R.id.tv_source_badge);
            descText = itemView.findViewById(R.id.tv_history_desc);
            verdictText = itemView.findViewById(R.id.tv_history_verdict);
            deleteBtn = itemView.findViewById(R.id.btn_delete);
        }
    }
}
