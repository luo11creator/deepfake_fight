package com.example.deepfake_fight;

import android.os.Bundle;
import android.view.Menu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.deepfake_fight.databinding.ActivityMainBinding;
import com.example.deepfake_fight.service.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 首次启动显示引导页
        if (com.example.deepfake_fight.ui.IntroActivity.shouldShow(this)) {
            startActivity(new android.content.Intent(this,
                    com.example.deepfake_fight.ui.IntroActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        // 初始化Session
        SessionManager.getInstance(getApplicationContext());

        // 初始化API服务上下文
        com.example.deepfake_fight.service.ApiService.getInstance()
                .initContext(getApplicationContext());

        // 初始化本地AI模型（后台线程）
        // 注意：只预加载常用的视频/文本模型；音频与多模态模型体积较大，
        // 改为进入对应页面时懒加载，避免启动时内存峰值过高导致崩溃。
        new Thread(() -> {
            try {
                com.example.deepfake_fight.service.LocalVideoDetector.getInstance()
                        .initialize(getApplicationContext());
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "视频模型加载失败: " + e.getMessage());
            }
            try {
                com.example.deepfake_fight.service.LocalTextDetector.getInstance()
                        .initialize(getApplicationContext());
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "文本模型加载失败: " + e.getMessage());
            }
        }, "model-init").start();

        // 创建通知渠道
        com.example.deepfake_fight.service.NotificationHelper.createChannel(this);

        BottomNavigationView bottomNav = binding.bottomNav;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home,
                R.id.nav_video_detect,
                R.id.nav_audio_detect,
                R.id.nav_text_analyze,
                R.id.nav_multimodal_detect,
                R.id.nav_agent,
                R.id.nav_call_detect,
                R.id.nav_map,
                R.id.nav_result,
                R.id.nav_history,
                R.id.nav_settings,
                R.id.nav_knowledge,
                R.id.nav_community,
                R.id.nav_profile)
                .build();

        navController = Navigation.findNavController(this,
                R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // 修复底部导航bug: 点击当前页时返回根页面
        bottomNav.setOnItemSelectedListener(item -> {
            int currentId = navController.getCurrentDestination() != null
                    ? navController.getCurrentDestination().getId() : -1;

            if (item.getItemId() == currentId) {
                // 已在当前页 → 不做任何事
                return true;
            }

            // 如果当前在非底部导航页面(如结果页/检测页)，点击首页时pop回首页
            if (item.getItemId() == R.id.nav_home && !isTopLevelDestination(currentId)) {
                navController.popBackStack(R.id.nav_home, false);
                return true;
            }

            NavigationUI.onNavDestinationSelected(item, navController);
            return true;
        });
    }

    private boolean isTopLevelDestination(int destId) {
        return destId == R.id.nav_home
                || destId == R.id.nav_community
                || destId == R.id.nav_history
                || destId == R.id.nav_profile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
