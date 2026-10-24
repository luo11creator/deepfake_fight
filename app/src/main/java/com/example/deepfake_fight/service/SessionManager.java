package com.example.deepfake_fight.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.deepfake_fight.data.User;

/**
 * 用户会话管理 - 单例
 * 管理当前登录用户状态
 */
public class SessionManager {

    private static final String PREFS_NAME = "user_session";
    private static SessionManager instance;
    private final SharedPreferences prefs;
    private User currentUser;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    // ===== 登录/登出 =====

    public void setCurrentUser(User user) {
        this.currentUser = user;
        prefs.edit()
                .putLong("user_id", user.id)
                .putString("username", user.username)
                .putString("nickname", user.nickname)
                .apply();
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public long getUserId() {
        return currentUser != null ? currentUser.id : -1;
    }

    /** 获取当前用户ID（供社区点赞等使用） */
    public long getCurrentUserId() {
        return getUserId();
    }

    public String getNickname() {
        return currentUser != null ? currentUser.nickname : "匿名用户";
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void logout() {
        currentUser = null;
        prefs.edit().clear().apply();
    }

    /** 更新昵称（持久化到SharedPreferences） */
    public void setNickname(String nickname) {
        if (currentUser != null) {
            currentUser.nickname = nickname;
            prefs.edit().putString("nickname", nickname).apply();
        }
    }

    // ===== 密码哈希（简单实现，生产环境用BCrypt） =====

    public static String hashPassword(String password) {
        // 简单SHA-256哈希
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return password;
        }
    }
}
