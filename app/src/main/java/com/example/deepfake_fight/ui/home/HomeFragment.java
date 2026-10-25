package com.example.deepfake_fight.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.DetectionRecordDao;
import com.example.deepfake_fight.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private BannerAdapter bannerAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoScrollRunnable;
    private int currentBannerPage;

    private ValueAnimator statusPulse;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupBanner();
        setupClickListeners();
        loadRealStats();
        startStatusPulse(); // ✅ 在页面创建后开始呼吸
    }

    // ===== 轮播图 (B站风格) =====

    private void setupBanner() {
        bannerAdapter = new BannerAdapter();
        binding.viewpagerBanner.setAdapter(bannerAdapter);
        binding.viewpagerBanner.setOffscreenPageLimit(1);

        int startPos = Integer.MAX_VALUE / 2
                - (Integer.MAX_VALUE / 2 % bannerAdapter.getRealCount());
        binding.viewpagerBanner.setCurrentItem(startPos, false);
        currentBannerPage = startPos;

        createIndicators();
        updateIndicator(0);

        binding.viewpagerBanner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                currentBannerPage = pos;
                updateIndicator(pos % bannerAdapter.getRealCount());
            }
        });

        // 点击 → 使用 BannerItem.topic 精确跳转
        bannerAdapter.setOnBannerClickListener(item -> {
            Bundle args = new Bundle();
            args.putString("topic", item.topic);
            Navigation.findNavController(requireView())
                    .navigate(R.id.nav_knowledge, args);
        });

        startAutoScroll();
    }

    private void createIndicators() {
        binding.layoutIndicator.removeAllViews();
        int count = bannerAdapter.getRealCount();
        for (int i = 0; i < count; i++) {
            View dot = new View(getContext());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(20, 6);
            p.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(p);
            dot.setBackgroundResource(R.drawable.indicator_inactive);
            binding.layoutIndicator.addView(dot);
        }
    }

    private void updateIndicator(int pos) {
        for (int i = 0; i < binding.layoutIndicator.getChildCount(); i++) {
            binding.layoutIndicator.getChildAt(i).setBackgroundResource(
                    i == pos ? R.drawable.indicator_active_new : R.drawable.indicator_inactive);
        }
    }

    private void startAutoScroll() {
        stopAutoScroll();
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding != null && isAdded()) {
                    binding.viewpagerBanner.setCurrentItem(++currentBannerPage, true);
                    handler.postDelayed(this, 4000);
                }
            }
        };
        handler.postDelayed(autoScrollRunnable, 4000);
    }

    private void stopAutoScroll() {
        if (autoScrollRunnable != null) {
            handler.removeCallbacks(autoScrollRunnable);
        }
    }

    // ===== 点击事件 =====

    private void setupClickListeners() {
        binding.cardQuickScan.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_video_detect));
        binding.cardVideo.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_video_detect));
        binding.cardAudio.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_audio_detect));
        binding.cardText.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_text_analyze));
        binding.cardMultimodal.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_multimodal_detect));
        binding.cardAgent.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_agent));
        binding.cardCall.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_call_detect));
        binding.cardVideoStream.setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.example.deepfake_fight.ui.video.CameraActivity.class)));
        binding.cardMap.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_map));
        binding.cardScreenDetect.setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.example.deepfake_fight.ui.video.ScreenDetectActivity.class)));

        // 反诈知识卡片
        binding.cardKnowledge1.setOnClickListener(v -> navigateToKnowledge("ai_fraud"));
        binding.cardKnowledge2.setOnClickListener(v -> navigateToKnowledge("phone_fraud"));
        binding.cardKnowledge3.setOnClickListener(v ->
                Navigation.findNavController(requireView())
                        .navigate(R.id.nav_deepfake_demo));
        binding.tvViewMoreKnowledge.setOnClickListener(v -> navigateToKnowledge("ai_fraud"));
    }

    private void navigateToKnowledge(String topic) {
        Bundle args = new Bundle();
        args.putString("topic", topic);
        Navigation.findNavController(requireView())
                .navigate(R.id.nav_knowledge, args);
    }

    // ===== 真实统计数据（从数据库） =====

    private void loadRealStats() {
        if (getContext() == null) return;
        DetectionRecordDao dao = AppDatabase.getInstance(requireContext()).detectionRecordDao();

        // 总检测次数
        dao.getRecordCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && binding != null) {
                binding.tvTotalScans.setText(String.format("%,d", count));
            }
        });

        // 高风险检测次数 (level >= 4)
        dao.getHighRiskCount().observe(getViewLifecycleOwner(), fraudCount -> {
            if (fraudCount != null && binding != null) {
                binding.tvFraudCaught.setText(String.format("%,d", fraudCount));
            }
        });

        binding.tvAccuracy.setText("端侧AI");
    }

    // ===== 状态点呼吸动画（AI质感） =====

    private void startStatusPulse() {
        if (binding == null) return;
        try {
            if (binding.viewStatusDot == null) return; // 若 XML 没加 id，这里会编译不过
        } catch (Exception ignored) {
            return;
        }

        stopStatusPulse();

        statusPulse = ValueAnimator.ofFloat(1f, 1.7f);
        statusPulse.setDuration(900);
        statusPulse.setInterpolator(new LinearInterpolator());
        statusPulse.setRepeatCount(ValueAnimator.INFINITE);
        statusPulse.setRepeatMode(ValueAnimator.REVERSE);
        statusPulse.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            binding.viewStatusDot.setScaleX(v);
            binding.viewStatusDot.setScaleY(v);
            binding.viewStatusDot.setAlpha(0.9f);
        });
        statusPulse.start();
    }

    private void stopStatusPulse() {
        if (statusPulse != null) {
            statusPulse.cancel();
            statusPulse = null;
        }
    }

    // ===== 生命周期 =====

    @Override
    public void onResume() {
        super.onResume();
        startAutoScroll();
        startStatusPulse(); // ✅ 返回前台继续呼吸
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoScroll();
        stopStatusPulse(); // ✅ 暂停时停止呼吸
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoScroll();
        stopStatusPulse();
        binding = null;
    }
}