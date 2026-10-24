package com.example.deepfake_fight.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.deepfake_fight.R;

/**
 * 屏幕检测前台服务 —— MediaProjection 要求运行在
 * FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 类型的前台服务中（Android 14+）。
 */
public class MediaProjectionService extends Service {

    private static final String CHANNEL_ID = "screen_detect";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new Binder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("多维盾卫 · 屏幕检测")
                .setContentText("正在实时检测屏幕画面中的 AI 换脸")
                .setSmallIcon(R.drawable.ic_shield)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕检测", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
