package com.example.deepfake_fight.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @ColumnInfo(name = "nickname")
    public String nickname;

    @ColumnInfo(name = "avatar_letter")
    public String avatarLetter;  // 头像首字母

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @Ignore
    public User() {}

    public User(String username, String passwordHash, String nickname, long createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.avatarLetter = nickname.substring(0, 1).toUpperCase();
        this.createdAt = createdAt;
    }
}
