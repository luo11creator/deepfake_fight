package com.example.deepfake_fight.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.deepfake_fight.MainActivity;
import com.example.deepfake_fight.R;

/** 首次启动引导页 — 3屏介绍 + 跳过/完成 */
public class IntroActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "intro_prefs";
    private static final String KEY_DONE = "intro_done";

    private ViewPager2 viewPager;
    private Button btnNext;
    private TextView tvSkip;
    private View dotsContainer;
    private View[] dots;
    private int currentPage = 0;

    private static final int[] TITLES = {
            R.string.intro_title_1, R.string.intro_title_2, R.string.intro_title_3
    };
    private static final int[] DESCS = {
            R.string.intro_desc_1, R.string.intro_desc_2, R.string.intro_desc_3
    };
    private static final int[] ICONS = {
            R.drawable.ic_videocam, R.drawable.ic_mic, R.drawable.ic_shield
    };

    public static boolean shouldShow(android.content.Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        return !prefs.getBoolean(KEY_DONE, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        viewPager = findViewById(R.id.viewpager_intro);
        btnNext = findViewById(R.id.btn_intro_next);
        tvSkip = findViewById(R.id.tv_intro_skip);
        dotsContainer = findViewById(R.id.layout_intro_dots);

        IntroAdapter adapter = new IntroAdapter(TITLES, DESCS, ICONS);
        viewPager.setAdapter(adapter);

        // 指示器圆点
        setupDots(TITLES.length);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int pos) {
                currentPage = pos;
                updateDots(pos);
                btnNext.setText(pos == TITLES.length - 1 ? "开始使用" : "下一步");
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentPage < TITLES.length - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                finishIntro();
            }
        });

        tvSkip.setOnClickListener(v -> finishIntro());
    }

    private void setupDots(int count) {
        dots = new View[count];
        android.widget.LinearLayout layout =
                (android.widget.LinearLayout) dotsContainer;
        layout.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams p =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            p.setMargins(6, 0, 6, 0);
            dot.setLayoutParams(p);
            dot.setBackgroundResource(i == 0
                    ? R.drawable.indicator_active_new
                    : R.drawable.indicator_inactive);
            layout.addView(dot);
            dots[i] = dot;
        }
    }

    private void updateDots(int pos) {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(i == pos
                    ? R.drawable.indicator_active_new
                    : R.drawable.indicator_inactive);
        }
    }

    private void finishIntro() {
        getSharedPreferences(PREFS_NAME, 0).edit()
                .putBoolean(KEY_DONE, true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
