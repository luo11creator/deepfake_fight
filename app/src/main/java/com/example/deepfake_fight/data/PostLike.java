package com.example.deepfake_fight.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * 点赞追踪表 — 防止一人无限点赞
 * 联合主键 (userId, postId) 确保每个用户对每个帖子只能点赞一次
 */
@Entity(tableName = "post_likes",
        primaryKeys = {"user_id", "post_id"},
        indices = {@Index("post_id")})
public class PostLike {

    @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "post_id")
    public long postId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public PostLike(long userId, long postId, long createdAt) {
        this.userId = userId;
        this.postId = postId;
        this.createdAt = createdAt;
    }
}
