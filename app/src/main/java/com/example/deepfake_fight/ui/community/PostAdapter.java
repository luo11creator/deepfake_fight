package com.example.deepfake_fight.ui.community;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.CommunityPost;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {

    private final List<CommunityPost> posts = new ArrayList<>();
    private OnLikeListener likeListener;
    private OnPostClickListener postClickListener;

    // 复用避免GC
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public interface OnLikeListener {
        void onLike(CommunityPost post);
    }
    public interface OnPostClickListener {
        void onPostClick(CommunityPost post);
    }

    public void setOnLikeListener(OnLikeListener listener) { this.likeListener = listener; }
    public void setOnPostClickListener(OnPostClickListener listener) { this.postClickListener = listener; }

    public void setPosts(List<CommunityPost> list) {
        posts.clear();
        if (list != null) posts.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_community_post, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommunityPost p = posts.get(position);

        String author = p.author != null && !p.author.isEmpty() ? p.author : "匿名用户";
        holder.author.setText(author);
        holder.avatar.setText(author.substring(0, 1).toUpperCase());
        holder.content.setText(p.content);
        holder.category.setText(p.category);
        holder.likes.setText("赞 " + p.likes);
        holder.commentCount.setText(String.valueOf(p.commentCount));
        holder.time.setText(SDF.format(new Date(p.createdAt)));

        // 图片缩略图
        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            File imgFile = new File(p.imagePath);
            if (imgFile.exists()) {
                holder.thumbnail.setImageBitmap(
                        BitmapFactory.decodeFile(p.imagePath));
                holder.thumbnail.setVisibility(View.VISIBLE);
            } else {
                holder.thumbnail.setVisibility(View.GONE);
            }
        } else {
            holder.thumbnail.setVisibility(View.GONE);
        }

        // 整个卡片点击 → 详情
        holder.itemView.setOnClickListener(v -> {
            if (postClickListener != null) postClickListener.onPostClick(p);
        });

        // 点赞按钮
        holder.likes.setOnClickListener(v -> {
            if (likeListener != null) likeListener.onLike(p);
        });
    }

    @Override
    public int getItemCount() { return posts.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView avatar, author, time, content, category, likes, commentCount;
        ImageView thumbnail;

        ViewHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.tv_avatar);
            author = v.findViewById(R.id.tv_post_author);
            time = v.findViewById(R.id.tv_post_time);
            content = v.findViewById(R.id.tv_post_content);
            category = v.findViewById(R.id.tv_post_category);
            likes = v.findViewById(R.id.btn_like);
            commentCount = v.findViewById(R.id.tv_comment_count);
            thumbnail = v.findViewById(R.id.iv_post_thumbnail);
        }
    }
}
