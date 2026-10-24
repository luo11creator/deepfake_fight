package com.example.deepfake_fight.ui.community;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.Comment;
import com.example.deepfake_fight.data.CommunityPostDao;
import com.example.deepfake_fight.data.PostLike;
import com.example.deepfake_fight.databinding.FragmentPostDetailBinding;
import com.example.deepfake_fight.service.SessionManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostDetailFragment extends Fragment {

    private FragmentPostDetailBinding binding;
    private CommentAdapter commentAdapter;
    private long postId;
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPostDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        postId = getArguments() != null ? getArguments().getLong("post_id", 0) : 0;
        if (postId == 0) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        // 返回按钮
        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(view).popBackStack());

        // 评论列表
        commentAdapter = new CommentAdapter();
        binding.rvComments.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvComments.setAdapter(commentAdapter);

        // 加载帖子详情
        loadPostDetail();

        // 加载评论
        loadComments();

        // 发送评论
        binding.btnSendComment.setOnClickListener(v -> sendComment());
    }

    private void loadPostDetail() {
        AppDatabase.getInstance(requireContext())
                .communityPostDao().getPostById(postId)
                .observe(getViewLifecycleOwner(), post -> {
                    if (post == null) return;

                    String author = post.author != null && !post.author.isEmpty()
                            ? post.author : "匿名用户";
                    binding.tvDetailAvatar.setText(author.substring(0, 1).toUpperCase());
                    binding.tvDetailAuthor.setText(author);
                    binding.tvDetailTime.setText(SDF.format(new Date(post.createdAt)));
                    binding.tvDetailCategory.setText(post.category);
                    binding.tvDetailContent.setText(post.content);
                    binding.btnDetailLike.setText("赞 " + post.likes);

                    // 图片
                    if (post.imagePath != null && !post.imagePath.isEmpty()) {
                        File f = new File(post.imagePath);
                        if (f.exists()) {
                            binding.ivDetailImage.setImageBitmap(
                                    BitmapFactory.decodeFile(post.imagePath));
                            binding.ivDetailImage.setVisibility(View.VISIBLE);
                        }
                    }

                    // 点赞（防刷）
                    binding.btnDetailLike.setOnClickListener(v -> {
                        SessionManager s = SessionManager.getInstance(requireContext());
                        if (!s.isLoggedIn()) {
                            Toast.makeText(getContext(), "请先登录后点赞", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        executor.execute(() -> {
                            CommunityPostDao dao = AppDatabase.getInstance(requireContext())
                                    .communityPostDao();
                            int liked = dao.checkLiked(s.getCurrentUserId(), postId);
                            if (liked > 0) {
                                dao.deleteLike(s.getCurrentUserId(), postId);
                                dao.decrementLikes(postId);
                            } else {
                                dao.insertLike(new PostLike(s.getCurrentUserId(),
                                        postId, System.currentTimeMillis()));
                                dao.incrementLikes(postId);
                            }
                        });
                    });
                });
    }

    private void loadComments() {
        AppDatabase.getInstance(requireContext())
                .commentDao().getCommentsByPost(postId)
                .observe(getViewLifecycleOwner(), comments -> {
                    commentAdapter.setComments(comments);
                    if (binding != null) {
                        int count = comments != null ? comments.size() : 0;
                        binding.tvCommentTitle.setText("评论 (" + count + ")");
                        binding.tvCommentCountHeader.setText(count + " 回复");
                    }
                });
    }

    private void sendComment() {
        String text = binding.etComment.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(getContext(), "请输入评论内容", Toast.LENGTH_SHORT).show();
            return;
        }

        SessionManager session = SessionManager.getInstance(requireContext());
        String nickname = session.getNickname();

        Comment comment = new Comment(postId, 0, text,
                nickname, System.currentTimeMillis());

        executor.execute(() -> {
            AppDatabase.getInstance(requireContext()).commentDao().insert(comment);
            AppDatabase.getInstance(requireContext())
                    .communityPostDao().updateCommentCount(postId);
            requireActivity().runOnUiThread(() -> {
                binding.etComment.setText("");
                Toast.makeText(getContext(), "评论成功", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
