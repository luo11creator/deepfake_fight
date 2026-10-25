package com.example.deepfake_fight.ui.community;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.CommunityPost;
import com.example.deepfake_fight.data.CommunityPostDao;
import com.example.deepfake_fight.data.PostLike;
import com.example.deepfake_fight.databinding.FragmentCommunityBinding;
import com.example.deepfake_fight.service.FileUtils;
import com.example.deepfake_fight.service.PrefsManager;
import com.example.deepfake_fight.service.SessionManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunityFragment extends Fragment {

    private FragmentCommunityBinding binding;
    private PostAdapter adapter;
    private String currentCategory = null; // null = 全部
    private String selectedImagePath = null;

    // 共享线程池（不再每次new）
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static final String[] CATEGORIES = {"经验分享", "求助讨论", "骗术曝光"};

    // 帖子图片选择器
    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        selectedImagePath = FileUtils.copyUriToCache(
                                requireContext(), uri, "post_img").getAbsolutePath();
                        Toast.makeText(getContext(), "图片已添加", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "图片读取失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    // 封面图选择器
    private final ActivityResultLauncher<String> coverPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        File coverFile = FileUtils.copyUriToCache(
                                requireContext(), uri, "cover");
                        PrefsManager.getInstance(requireContext())
                                .setCommunityCoverPath(coverFile.getAbsolutePath());
                        loadCoverImage();
                        Toast.makeText(getContext(), "封面已更新", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "封面设置失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommunityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new PostAdapter();
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvPosts.setAdapter(adapter);

        // 帖子点击 → 详情页
        adapter.setOnPostClickListener(post -> {
            Bundle args = new Bundle();
            args.putLong("post_id", post.id);
            Navigation.findNavController(requireView())
                    .navigate(R.id.nav_post_detail, args);
        });

        // 点赞/取消点赞
        adapter.setOnLikeListener(post -> toggleLike(post));

        // 分类筛选
        setupCategoryChips();
        showPosts(null);

        // 发帖
        binding.btnPost.setOnClickListener(v -> {
            String text = binding.etPostContent.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(getContext(), "请输入内容", Toast.LENGTH_SHORT).show();
                return;
            }
            createPost(text);
        });

        // 添加图片
        binding.btnAddImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        // 封面图
        loadCoverImage();
        binding.btnChangeCover.setOnClickListener(v -> coverPicker.launch("image/*"));

        // 观察帖子总数
        AppDatabase.getInstance(requireContext())
                .communityPostDao().getPostCount()
                .observe(getViewLifecycleOwner(), count -> {
                    if (count != null && binding != null) {
                        binding.tvPostCount.setText(count + " 帖子");
                    }
                });
    }

    private void setupCategoryChips() {
        binding.chipAll.setOnClickListener(v -> {
            currentCategory = null;
            selectChip(binding.chipAll);
            showPosts(null);
        });
        binding.chipExperience.setOnClickListener(v -> {
            currentCategory = "经验分享";
            selectChip(binding.chipExperience);
            showPosts("经验分享");
        });
        binding.chipDiscuss.setOnClickListener(v -> {
            currentCategory = "求助讨论";
            selectChip(binding.chipDiscuss);
            showPosts("求助讨论");
        });
        binding.chipReport.setOnClickListener(v -> {
            currentCategory = "骗术曝光";
            selectChip(binding.chipReport);
            showPosts("骗术曝光");
        });
    }

    private void selectChip(TextView selected) {
        for (TextView chip : new TextView[]{binding.chipAll, binding.chipExperience,
                binding.chipDiscuss, binding.chipReport}) {
            if (chip == selected) {
                chip.setTextColor(getResources().getColor(R.color.primary, null));
                chip.getPaint().setFakeBoldText(true);
            } else {
                chip.setTextColor(getResources().getColor(R.color.text_secondary, null));
                chip.getPaint().setFakeBoldText(false);
            }
        }
    }

    private void showPosts(String category) {
        if (category == null) {
            AppDatabase.getInstance(requireContext())
                    .communityPostDao().getPosts(50, 0)
                    .observe(getViewLifecycleOwner(), posts -> adapter.setPosts(posts));
        } else {
            AppDatabase.getInstance(requireContext())
                    .communityPostDao().getPostsByCategory(category, 50, 0)
                    .observe(getViewLifecycleOwner(), posts -> adapter.setPosts(posts));
        }
    }

    // ===== 发帖（支持图片） =====
    private void createPost(String text) {
        SessionManager session = SessionManager.getInstance(requireContext());
        String nickname = session.getNickname();
        String category = currentCategory != null ? currentCategory : "经验分享";
        String imagePath = selectedImagePath;
        selectedImagePath = null; // 重置

        CommunityPost post = new CommunityPost(
                text, nickname, category, imagePath, System.currentTimeMillis());

        executor.execute(() -> {
            AppDatabase.getInstance(requireContext())
                    .communityPostDao().insert(post);
            requireActivity().runOnUiThread(() -> {
                binding.etPostContent.setText("");
                Toast.makeText(getContext(), "发布成功!", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ===== 封面图 =====
    private void loadCoverImage() {
        String path = PrefsManager.getInstance(requireContext()).getCommunityCoverPath();
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                binding.ivCommunityCover.setImageBitmap(
                        android.graphics.BitmapFactory.decodeFile(path));
            }
        }
    }

    // ===== 点赞/取消点赞（防刷） =====
    private void toggleLike(CommunityPost post) {
        SessionManager session = SessionManager.getInstance(requireContext());
        if (!session.isLoggedIn()) {
            Toast.makeText(getContext(), "请先登录/设置昵称后再点赞", Toast.LENGTH_SHORT).show();
            return;
        }
        long userId = session.getCurrentUserId();

        executor.execute(() -> {
            CommunityPostDao dao = AppDatabase.getInstance(requireContext()).communityPostDao();
            int alreadyLiked = dao.checkLiked(userId, post.id);

            if (alreadyLiked > 0) {
                // 取消点赞
                dao.deleteLike(userId, post.id);
                dao.decrementLikes(post.id);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "已取消点赞", Toast.LENGTH_SHORT).show());
            } else {
                // 点赞
                dao.insertLike(new PostLike(userId, post.id, System.currentTimeMillis()));
                dao.incrementLikes(post.id);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "点赞成功!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
