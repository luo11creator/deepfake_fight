package com.example.deepfake_fight.service;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 文件工具类
 */
public class FileUtils {

    /**
     * 将Uri对应的文件拷贝到缓存目录
     * Android的Uri不能直接给OkHttp用，需要先拷贝
     */
    public static File copyUriToCache(Context context, Uri uri, String prefix) throws Exception {
        String ext = getExtension(context, uri);
        if (ext == null || ext.isEmpty()) ext = ".tmp";
        String fileName = prefix + "_" + System.currentTimeMillis() + ext;
        File destFile = new File(context.getCacheDir(), fileName);

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destFile)) {
            if (in == null) throw new Exception("无法读取文件");

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        return destFile;
    }

    private static String getExtension(Context context, Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf("."));
        }
        return "";
    }
}
