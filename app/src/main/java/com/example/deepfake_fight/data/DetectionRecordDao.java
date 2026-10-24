package com.example.deepfake_fight.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 检测记录 DAO (数据访问对象)
 */
@Dao
public interface DetectionRecordDao {

    @Query("SELECT * FROM detection_history ORDER BY created_at DESC")
    LiveData<List<DetectionRecord>> getAllRecords();

    @Query("SELECT * FROM detection_history ORDER BY created_at DESC LIMIT 3")
    LiveData<List<DetectionRecord>> getRecentRecords();

    @Query("SELECT * FROM detection_history WHERE type = :type ORDER BY created_at DESC")
    LiveData<List<DetectionRecord>> getRecordsByType(String type);

    @Insert
    long insert(DetectionRecord record);

    @Delete
    void delete(DetectionRecord record);

    @Query("DELETE FROM detection_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM detection_history")
    LiveData<Integer> getRecordCount();

    /** 高风险检测次数 (level >= 4) — 首页"识破欺诈"统计 */
    @Query("SELECT COUNT(*) FROM detection_history WHERE risk_level >= 4")
    LiveData<Integer> getHighRiskCount();
}
