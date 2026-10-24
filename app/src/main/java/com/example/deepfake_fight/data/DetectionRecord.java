package com.example.deepfake_fight.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 检测记录实体 — Room数据库表
 */
@Entity(tableName = "detection_history")
public class DetectionRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "type")
    public String type;              // "video" | "audio" | "text"

    @ColumnInfo(name = "type_label")
    public String typeLabel;         // "视频真伪检测" | "音频真伪检测" | "文本风险分析"

    @ColumnInfo(name = "risk_score")
    public float riskScore;          // 0.0 ~ 1.0

    @ColumnInfo(name = "risk_level")
    public int riskLevel;            // 1 ~ 5

    @ColumnInfo(name = "verdict")
    public String verdict;           // "真实" | "低风险" | "可疑" | "高风险" | "伪造"

    @ColumnInfo(name = "source")
    public String source;            // "cloud" | "local"

    @ColumnInfo(name = "file_name")
    public String fileName;          // 文件名或文本摘要

    @ColumnInfo(name = "details_json")
    public String detailsJson;       // 详细信息JSON字符串

    @ColumnInfo(name = "image_path")
    public String imagePath;         // 检测图片的本地路径

    @ColumnInfo(name = "created_at")
    public long createdAt;           // 时间戳 (System.currentTimeMillis())

    @Ignore
    public DetectionRecord() {}

    public DetectionRecord(String type, String typeLabel, float riskScore, int riskLevel,
                           String verdict, String source, String fileName,
                           String detailsJson, String imagePath, long createdAt) {
        this.type = type;
        this.typeLabel = typeLabel;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.verdict = verdict;
        this.source = source;
        this.fileName = fileName;
        this.detailsJson = detailsJson;
        this.imagePath = imagePath;
        this.createdAt = createdAt;
    }
}
