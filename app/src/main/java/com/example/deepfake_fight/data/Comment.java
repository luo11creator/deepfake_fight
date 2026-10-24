package com.example.deepfake_fight.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 论坛评论实体 — 支持楼中楼回复
 * parentId=0 表示对帖子的直接评论
 * parentId>0 表示对某条评论的回复
 */
@Entity(tableName = "comments",
        foreignKeys = @ForeignKey(entity = CommunityPost.class,
                parentColumns = "id", childColumns = "post_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("post_id"), @Index("author")})
public class Comment {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "post_id")
    public long postId;          // 所属帖子

    @ColumnInfo(name = "parent_id")
    public long parentId;        // 父评论ID (0=直接评论帖子)

    @ColumnInfo(name = "content")
    public String content;       // 评论内容

    @ColumnInfo(name = "author")
    public String author;        // 评论者

    @ColumnInfo(name = "image_url")
    public String imageUrl;      // 可选: 评论附带图片

    @ColumnInfo(name = "likes")
    public int likes;            // 点赞数

    @ColumnInfo(name = "created_at")
    public long createdAt;       // 时间戳

    public Comment(long postId, long parentId, String content,
                   String author, long createdAt) {
        this.postId = postId;
        this.parentId = parentId;
        this.content = content;
        this.author = author;
        this.likes = 0;
        this.createdAt = createdAt;
    }
}
