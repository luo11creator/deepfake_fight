package com.example.deepfake_fight.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "community_posts")
public class CommunityPost {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "author")
    public String author;

    @ColumnInfo(name = "category")
    public String category;  // "经验分享" | "求助" | "讨论" | "骗术曝光"

    @ColumnInfo(name = "likes")
    public int likes;

    @ColumnInfo(name = "comment_count")
    public int commentCount;

    @ColumnInfo(name = "image_path")
    public String imagePath;  // 帖子附带图片的本地路径

    @ColumnInfo(name = "author_avatar")
    public String authorAvatar;  // 作者头像路径

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @Ignore
    public CommunityPost() {}

    public CommunityPost(String content, String author, String category,
                         String imagePath, long createdAt) {
        this.content = content;
        this.author = author;
        this.category = category;
        this.imagePath = imagePath;
        this.likes = 0;
        this.commentCount = 0;
        this.createdAt = createdAt;
    }
}
