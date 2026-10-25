package com.example.deepfake_fight.ui.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.Comment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

    private final List<Comment> comments = new ArrayList<>();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public void setComments(List<Comment> list) {
        comments.clear();
        if (list != null) comments.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Comment c = comments.get(pos);
        String author = c.author != null && !c.author.isEmpty() ? c.author : "匿名";
        h.avatar.setText(author.substring(0, 1).toUpperCase());
        h.author.setText(author);
        h.time.setText(SDF.format(new Date(c.createdAt)));
        h.content.setText(c.content);
    }

    @Override
    public int getItemCount() { return comments.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, author, time, content;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.tv_comment_avatar);
            author = v.findViewById(R.id.tv_comment_author);
            time = v.findViewById(R.id.tv_comment_time);
            content = v.findViewById(R.id.tv_comment_content);
        }
    }
}
