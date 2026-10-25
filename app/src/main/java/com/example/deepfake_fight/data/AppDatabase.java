package com.example.deepfake_fight.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room数据库 — 单例模式
 */
@Database(entities = {
        DetectionRecord.class,
        CommunityPost.class,
        Comment.class,
        PostLike.class,
        User.class
}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract DetectionRecordDao detectionRecordDao();
    public abstract CommunityPostDao communityPostDao();
    public abstract CommentDao commentDao();
    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "voiceguard_db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
