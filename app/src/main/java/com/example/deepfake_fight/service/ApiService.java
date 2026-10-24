package com.example.deepfake_fight.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * API服务 - 与Python FastAPI后端通信
 * 负责上传音视频文件、文本内容，获取检测结果
 * 服务器地址从 PrefsManager 动态获取
 */
public class ApiService {
    private static final String TAG = "ApiService";
    private static ApiService instance;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private android.content.Context appContext;

    private ApiService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(NetworkConfig.CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(NetworkConfig.READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(NetworkConfig.WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }

    /** 初始化上下文（Application或首次调用时设置） */
    public void initContext(android.content.Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    private String baseUrl() {
        if (appContext != null) {
            return PrefsManager.getInstance(appContext).getBaseUrl();
        }
        return PrefsManager.DEFAULT_SERVER_URL;
    }

    /**
     * 检测回调接口
     */
    public interface DetectCallback {
        void onSuccess(JSONObject result);
        void onError(String errorMessage);
        void onProgress(String status);
    }

    /**
     * 上传音频文件进行检测
     */
    public void detectAudio(File audioFile, DetectCallback callback) {
        callback.onProgress("正在上传音频...");

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", audioFile.getName(),
                        RequestBody.create(audioFile, MediaType.parse("audio/wav")))
                .addFormDataPart("model", NetworkConfig.MODEL_AUDIO_AASIST)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl() + NetworkConfig.ENDPOINT_AUDIO_DETECT)
                .post(requestBody)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 上传视频文件进行检测
     */
    public void detectVideo(File videoFile, DetectCallback callback) {
        callback.onProgress("正在上传视频...");

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("video", videoFile.getName(),
                        RequestBody.create(videoFile, MediaType.parse("video/mp4")))
                .addFormDataPart("model", NetworkConfig.MODEL_VIDEO_EFFICIENTNET)
                .addFormDataPart("frame_rate", String.valueOf(NetworkConfig.VIDEO_FRAME_SAMPLE_RATE))
                .build();

        Request request = new Request.Builder()
                .url(baseUrl() + NetworkConfig.ENDPOINT_VIDEO_DETECT)
                .post(requestBody)
                .build();

        executeRequest(request, callback);
    }

    /**
     * 提交文本进行分析
     */
    public void analyzeText(String text, DetectCallback callback) {
        callback.onProgress("正在分析文本...");

        try {
            JSONObject json = new JSONObject();
            json.put("text", text);
            json.put("model", NetworkConfig.MODEL_TEXT_BERT);

            RequestBody requestBody = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(baseUrl() + NetworkConfig.ENDPOINT_TEXT_ANALYZE)
                    .post(requestBody)
                    .build();

            executeRequest(request, callback);
        } catch (Exception e) {
            callback.onError("请求构建失败: " + e.getMessage());
        }
    }

    /**
     * 调用反诈智能体（LLM Agent，云端）
     * 后端 /api/v1/agent/chat 返回 {"answer": "...", "tools_used": [...]}
     */
    public void agentChat(String message, DetectCallback callback) {
        callback.onProgress("智能体分析中...");

        try {
            JSONObject json = new JSONObject();
            json.put("message", message);

            RequestBody requestBody = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(baseUrl() + NetworkConfig.ENDPOINT_AGENT)
                    .post(requestBody)
                    .build();

            executeRequest(request, callback);
        } catch (Exception e) {
            callback.onError("请求构建失败: " + e.getMessage());
        }
    }

    /**
     * 端侧检测完成后的统计上报（只报类型，不传数据本身，保护隐私）
     * 异步静默执行，失败不影响检测结果。
     */
    public void reportDetection(String type) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.parse("application/json"));
            String url = baseUrl() + "/api/v1/report";
            Log.i(TAG, "上报统计: " + type + " → " + url);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "上报失败: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    Log.i(TAG, "上报响应 code=" + response.code());
                    if (response.body() != null) response.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "上报异常: " + e.getMessage());
        }
    }

    /**
     * 查询异步检测结果
     */
    public void queryResult(String taskId, DetectCallback callback) {
        Request request = new Request.Builder()
                .url(baseUrl() + NetworkConfig.ENDPOINT_RESULT + taskId)
                .get()
                .build();

        executeRequest(request, callback);
    }

    private void executeRequest(Request request, DetectCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    Log.e(TAG, "请求失败", e);
                    callback.onError("网络请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                mainHandler.post(() -> {
                    try {
                        if (response.isSuccessful()) {
                            JSONObject result = new JSONObject(responseBody);
                            callback.onSuccess(result);
                        } else {
                            callback.onError("服务器错误: " + response.code());
                        }
                    } catch (Exception e) {
                        callback.onError("解析响应失败: " + e.getMessage());
                    }
                });
            }
        });
    }
}
