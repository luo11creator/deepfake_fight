package com.example.deepfake_fight.service;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences 配置管理
 * 存储服务器地址、模型选择、隐私设置
 */
public class PrefsManager {

    private static final String PREFS_NAME = "voiceguard_prefs";
    private static PrefsManager instance;
    private final SharedPreferences prefs;

    // 配置键
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_MODEL_AUDIO = "model_audio";
    private static final String KEY_MODEL_VIDEO = "model_video";
    private static final String KEY_MODEL_TEXT = "model_text";
    private static final String KEY_SAVE_HISTORY = "save_history";
    private static final String KEY_AUTO_UPLOAD = "auto_upload";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_COVER_IMAGE = "cover_image_path";
    private static final String KEY_AVATAR_PATH = "avatar_path";
    private static final String KEY_BIO = "user_bio";

    // 默认值
    public static final String DEFAULT_SERVER_URL = "https://that-uncover-ludicrous.ngrok-free.dev";
    public static final String DEFAULT_MODEL_AUDIO = "aasist";
    public static final String DEFAULT_MODEL_VIDEO = "efficientnet-b4";
    public static final String DEFAULT_MODEL_TEXT = "bert-base-chinese";

    private PrefsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    // ===== Getter/Setter =====

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getModelAudio() {
        return prefs.getString(KEY_MODEL_AUDIO, DEFAULT_MODEL_AUDIO);
    }

    public void setModelAudio(String model) {
        prefs.edit().putString(KEY_MODEL_AUDIO, model).apply();
    }

    public String getModelVideo() {
        return prefs.getString(KEY_MODEL_VIDEO, DEFAULT_MODEL_VIDEO);
    }

    public void setModelVideo(String model) {
        prefs.edit().putString(KEY_MODEL_VIDEO, model).apply();
    }

    public String getModelText() {
        return prefs.getString(KEY_MODEL_TEXT, DEFAULT_MODEL_TEXT);
    }

    public void setModelText(String model) {
        prefs.edit().putString(KEY_MODEL_TEXT, model).apply();
    }

    public boolean isSaveHistory() {
        return prefs.getBoolean(KEY_SAVE_HISTORY, true);
    }

    public void setSaveHistory(boolean save) {
        prefs.edit().putBoolean(KEY_SAVE_HISTORY, save).apply();
    }

    public boolean isAutoUpload() {
        return prefs.getBoolean(KEY_AUTO_UPLOAD, true);
    }

    public void setAutoUpload(boolean auto) {
        prefs.edit().putBoolean(KEY_AUTO_UPLOAD, auto).apply();
    }

    /**
     * 获取当前生效的 BASE_URL
     */
    public String getBaseUrl() {
        String url = getServerUrl();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkMode(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply();
    }

    // 封面图
    public String getCoverImagePath() {
        return prefs.getString(KEY_COVER_IMAGE, "");
    }
    public void setCoverImagePath(String path) {
        prefs.edit().putString(KEY_COVER_IMAGE, path).apply();
    }

    // 头像
    public String getAvatarPath() {
        return prefs.getString(KEY_AVATAR_PATH, "");
    }
    public void setAvatarPath(String path) {
        prefs.edit().putString(KEY_AVATAR_PATH, path).apply();
    }

    // 社区封面
    private static final String KEY_COMMUNITY_COVER = "community_cover_path";
    public String getCommunityCoverPath() {
        return prefs.getString(KEY_COMMUNITY_COVER, "");
    }
    public void setCommunityCoverPath(String path) {
        prefs.edit().putString(KEY_COMMUNITY_COVER, path).apply();
    }

    // 个人简介
    public String getBio() {
        return prefs.getString(KEY_BIO, "");
    }
    public void setBio(String bio) {
        prefs.edit().putString(KEY_BIO, bio).apply();
    }

    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
