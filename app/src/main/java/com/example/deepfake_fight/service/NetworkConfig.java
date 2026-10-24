package com.example.deepfake_fight.service;

/**
 * 网络配置 - 后端API端点路径定义
 * 实际服务器地址由 PrefsManager 动态获取，可在设置页面修改
 */
public class NetworkConfig {

    // API版本
    public static final String API_VERSION = "v1";

    // 各检测端点
    public static final String ENDPOINT_AUDIO_DETECT = "/api/" + API_VERSION + "/detect/audio";
    public static final String ENDPOINT_VIDEO_DETECT = "/api/" + API_VERSION + "/detect/video";
    public static final String ENDPOINT_TEXT_ANALYZE = "/api/" + API_VERSION + "/detect/text";
    public static final String ENDPOINT_MULTIMODAL = "/api/" + API_VERSION + "/detect/multimodal";
    public static final String ENDPOINT_AGENT = "/api/" + API_VERSION + "/agent/chat";
    public static final String ENDPOINT_RESULT = "/api/" + API_VERSION + "/result/";

    // 请求超时配置 (毫秒)
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = 30000;
    public static final int WRITE_TIMEOUT = 60000;

    // 模型配置
    public static final String MODEL_AUDIO_AASIST = "aasist";
    public static final String MODEL_AUDIO_RAWNET2 = "rawnet2";
    public static final String MODEL_VIDEO_EFFICIENTNET = "efficientnet-b4";
    public static final String MODEL_VIDEO_XCEPTION = "xception";
    public static final String MODEL_TEXT_BERT = "bert-base-chinese";

    // 检测参数
    public static final int AUDIO_SAMPLE_RATE = 16000;
    public static final int VIDEO_FRAME_SAMPLE_RATE = 5; // 每秒采样帧数
    public static final int MAX_AUDIO_DURATION_SEC = 60;
    public static final int MAX_TEXT_LENGTH = 2000;
}
