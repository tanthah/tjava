package com.example.translator.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.local.dao.UserPreferencesDao;
import com.example.translator.data.model.UserPreferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private UserPreferencesDao userPreferencesDao;
    private ExecutorService executor;

    public UserRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        userPreferencesDao = database.userPreferencesDao();
        executor = Executors.newFixedThreadPool(4);
    }

    public LiveData<UserPreferences> getUserPreferences() {
        return userPreferencesDao.getUserPreferences();
    }

    public void updateUserPreferences(UserPreferences preferences) {
        executor.execute(() -> {
            userPreferencesDao.insertUserPreferences(preferences);
        });
    }

    public void initializeDefaultPreferences() {
        executor.execute(() -> {
            userPreferencesDao.insertUserPreferences(new UserPreferences());
        });
    }
}