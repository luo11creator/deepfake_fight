package com.example.deepfake_fight.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deepfake_fight.R;

/** 引导页适配器 */
public class IntroAdapter extends RecyclerView.Adapter<IntroAdapter.VH> {

    private final int[] titles, descs, icons;

    public IntroAdapter(int[] titles, int[] descs, int[] icons) {
        this.titles = titles;
        this.descs = descs;
        this.icons = icons;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_intro, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.icon.setImageResource(icons[pos]);
        h.title.setText(titles[pos]);
        h.desc.setText(descs[pos]);
    }

    @Override public int getItemCount() { return titles.length; }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, desc;
        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.iv_intro_icon);
            title = v.findViewById(R.id.tv_intro_title);
            desc = v.findViewById(R.id.tv_intro_desc);
        }
    }
}
