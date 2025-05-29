package com.example.translator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.translator.data.model.UserPreferences;

@Dao
public interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    LiveData<UserPreferences> getUserPreferences();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUserPreferences(UserPreferences preferences);

    @Update
    void updateUserPreferences(UserPreferences preferences);
}