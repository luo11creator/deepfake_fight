package com.example.deepfake_fight.service;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * 权限请求辅助类
 * 提供带解释文案的权限请求流程
 */
public class PermissionHelper {

    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    /**
     * 请求录音权限（带解释弹窗）
     */
    public static void requestRecordPermission(Fragment fragment, PermissionCallback callback) {
        Context ctx = fragment.requireContext();

        if (hasPermission(ctx, Manifest.permission.RECORD_AUDIO)) {
            callback.onGranted();
            return;
        }

        // 如果用户之前拒绝过，先解释为什么需要
        if (fragment.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            showRationaleDialog(ctx,
                    "需要录音权限",
                    "多维盾卫需要使用麦克风录制语音，以检测通话中是否存在AI合成语音诈骗。\n\n录音数据仅用于实时检测，不会保存或上传。",
                    "授权录音",
                    () -> fragment.requestPermissions(
                            new String[]{Manifest.permission.RECORD_AUDIO}, 1001));
        } else {
            fragment.requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
        }
    }

    /**
     * 请求摄像头权限（带解释弹窗）
     */
    public static void requestCameraPermission(Fragment fragment, PermissionCallback callback) {
        Context ctx = fragment.requireContext();

        if (hasPermission(ctx, Manifest.permission.CAMERA)) {
            callback.onGranted();
            return;
        }

        if (fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showRationaleDialog(ctx,
                    "需要摄像头权限",
                    "多维盾卫需要使用摄像头进行实时人脸检测，以识别视频通话中的Deepfake换脸欺诈。\n\n摄像头数据仅在手机本地处理，不会上传云端。",
                    "授权摄像头",
                    () -> fragment.requestPermissions(
                            new String[]{Manifest.permission.CAMERA}, 1002));
        } else {
            fragment.requestPermissions(
                    new String[]{Manifest.permission.CAMERA}, 1002);
        }
    }

    /**
     * 检查权限是否已授予
     */
    public static boolean hasPermission(Context ctx, String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 处理权限结果 — 如果被拒绝且不再询问，引导去设置
     */
    public static void handlePermissionResult(Fragment fragment, String permission,
                                               boolean granted, PermissionCallback callback) {
        if (granted) {
            callback.onGranted();
            return;
        }

        // 用户点了"不再询问"
        if (!fragment.shouldShowRequestPermissionRationale(permission)) {
            showGoToSettingsDialog(fragment.requireContext(), permission);
        }
        callback.onDenied();
    }

    /**
     * 显示解释弹窗
     */
    private static void showRationaleDialog(Context ctx, String title, String message,
                                             String positiveText, Runnable onAccept) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, (d, w) -> onAccept.run())
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }

    /**
     * 引导用户去系统设置开启权限
     */
    private static void showGoToSettingsDialog(Context ctx, String permission) {
        String permName;
        if (Manifest.permission.CAMERA.equals(permission)) permName = "摄像头";
        else if (Manifest.permission.RECORD_AUDIO.equals(permission)) permName = "麦克风";
        else permName = "存储";

        new AlertDialog.Builder(ctx)
                .setTitle("权限已被禁止")
                .setMessage("您已禁止" + permName + "权限。\n\n"
                        + "如需使用此功能，请在系统设置中手动开启：\n"
                        + "设置 → 应用 → 多维盾卫 → 权限")
                .setPositiveButton("前往设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ctx.getPackageName()));
                    ctx.startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
