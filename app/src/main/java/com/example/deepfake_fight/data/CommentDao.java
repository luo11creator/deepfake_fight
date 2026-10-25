package com.example.deepfake_fight.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CommentDao {

    /** 获取帖子的所有评论（按时间正序） */
    @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC")
    LiveData<List<Comment>> getCommentsByPost(long postId);

    /** 获取帖子的评论数 */
    @Query("SELECT COUNT(*) FROM comments WHERE post_id = :postId")
    LiveData<Integer> getCommentCount(long postId);

    /** 获取某条评论的回复 */
    @Query("SELECT * FROM comments WHERE parent_id = :commentId ORDER BY created_at ASC")
    LiveData<List<Comment>> getReplies(long commentId);

    /** 插入评论 */
    @Insert
    long insert(Comment comment);

    /** 删除评论 */
    @Delete
    void delete(Comment comment);

    /** 点赞评论 */
    @Query("UPDATE comments SET likes = likes + 1 WHERE id = :commentId")
    void likeComment(long commentId);

    /** 取消点赞 */
    @Query("UPDATE comments SET likes = MAX(0, likes - 1) WHERE id = :commentId")
    void unlikeComment(long commentId);
}
