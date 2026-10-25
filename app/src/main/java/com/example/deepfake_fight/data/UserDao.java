package com.example.deepfake_fight.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User findByUsername(String username);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(long id);

    @Query("SELECT * FROM users WHERE username = :username AND password_hash = :passwordHash LIMIT 1")
    User login(String username, String passwordHash);

    @Insert
    long insert(User user);

    @Update
    void update(User user);

    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    int countByUsername(String username);

    @Query("SELECT COUNT(*) FROM users")
    LiveData<Integer> getUserCount();
}
