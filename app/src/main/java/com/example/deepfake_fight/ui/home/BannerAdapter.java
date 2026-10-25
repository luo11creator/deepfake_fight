package com.example.deepfake_fight.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.databinding.ItemBannerBinding;

import java.util.ArrayList;
import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    public static class BannerItem {
        public final String tag;
        public final String title;
        public final String desc;
        public final int bgRes;       // 背景图资源 (drawable)
        public final String topic;    // 点击跳转的知识主题

        public BannerItem(String tag, String title, String desc,
                          int bgRes, String topic) {
            this.tag = tag;
            this.title = title;
            this.desc = desc;
            this.bgRes = bgRes;
            this.topic = topic;
        }
    }

    private final List<BannerItem> items = new ArrayList<>();
    private OnBannerClickListener listener;

    public interface OnBannerClickListener {
        void onBannerClick(BannerItem item);
    }

    public void setOnBannerClickListener(OnBannerClickListener l) { this.listener = l; }

    public BannerAdapter() {
        // 默认轮播 — 使用渐变背景图
        items.add(new BannerItem("AI换脸检测",
                "Deepfake换脸视频诈骗激增300%，学会识别AI伪造人脸",
                "查看EfficientNet检测技术原理 →",
                R.drawable.banner_bg_2,
                "ai_fraud"));
        items.add(new BannerItem("AI语音检测",
                "AI合成语音以假乱真? AASIST-L模型EER 0.99%精准识别",
                "了解声码器痕迹检测技术 →",
                R.drawable.banner_bg_1,
                "phone_fraud"));
        items.add(new BannerItem("紧急求助",
                "怀疑遇到诈骗? 记住这个号码: 96110",
                "查看全部举报与求助渠道 →",
                R.drawable.banner_bg_3,
                "report"));
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBannerBinding b = ItemBannerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        BannerItem item = items.get(pos % items.size());
        h.bg.setImageResource(item.bgRes);
        h.tag.setText(item.tag);
        h.title.setText(item.title);
        h.desc.setText(item.desc);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBannerClick(item);
        });
    }

    @Override public int getItemCount() { return Integer.MAX_VALUE; }
    public int getRealCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView bg;
        TextView tag, title, desc;
        VH(ItemBannerBinding b) {
            super(b.getRoot());
            bg = b.ivBannerBg;
            tag = b.tvBannerTag;
            title = b.tvBannerTitle;
            desc = b.tvBannerDesc;
        }
    }
}
