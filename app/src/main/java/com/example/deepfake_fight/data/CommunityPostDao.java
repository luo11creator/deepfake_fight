package com.example.deepfake_fight.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CommunityPostDao {

    /** 分页获取帖子（每页20条） */
    @Query("SELECT * FROM community_posts ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    LiveData<List<CommunityPost>> getPosts(int limit, int offset);

    /** 按分类获取 */
    @Query("SELECT * FROM community_posts WHERE category = :category "
            + "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    LiveData<List<CommunityPost>> getPostsByCategory(String category, int limit, int offset);

    /** 获取单个帖子 */
    @Query("SELECT * FROM community_posts WHERE id = :postId")
    LiveData<CommunityPost> getPostById(long postId);

    /** 获取帖子总数 */
    @Query("SELECT COUNT(*) FROM community_posts")
    LiveData<Integer> getPostCount();

    /** 插入帖子 */
    @Insert
    long insert(CommunityPost post);

    /** 删除帖子 */
    @Delete
    void delete(CommunityPost post);

    // ===== 点赞系统（防止无限点赞） =====

    /** 同步检查用户是否已点赞（用于点击判断） */
    @Query("SELECT COUNT(*) FROM post_likes WHERE user_id = :userId AND post_id = :postId")
    int checkLiked(long userId, long postId);

    /** 观察点赞状态 */
    @Query("SELECT COUNT(*) FROM post_likes WHERE user_id = :userId AND post_id = :postId")
    LiveData<Integer> hasUserLiked(long userId, long postId);

    /** 点赞：插入追踪记录 */
    @Insert
    void insertLike(PostLike like);

    /** 取消点赞：删除追踪记录 */
    @Query("DELETE FROM post_likes WHERE user_id = :userId AND post_id = :postId")
    void deleteLike(long userId, long postId);

    /** 增加帖子点赞数 */
    @Query("UPDATE community_posts SET likes = likes + 1 WHERE id = :postId")
    void incrementLikes(long postId);

    /** 减少帖子点赞数 */
    @Query("UPDATE community_posts SET likes = MAX(0, likes - 1) WHERE id = :postId")
    void decrementLikes(long postId);

    /** 更新评论数 */
    @Query("UPDATE community_posts SET comment_count = "
            + "(SELECT COUNT(*) FROM comments WHERE post_id = :postId) WHERE id = :postId")
    void updateCommentCount(long postId);
}
