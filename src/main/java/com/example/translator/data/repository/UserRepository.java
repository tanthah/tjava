package com.example.translator.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.local.dao.UserPreferencesDao;
import com.example.translator.data.model.UserPreferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private UserPreferencesDao userPreferencesDao;
    private ExecutorService executor;

    public UserRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        userPreferencesDao = database.userPreferencesDao();
        executor = Executors.newFixedThreadPool(4);
        Log.d(TAG, "UserRepository initialized");
    }

    public LiveData<UserPreferences> getUserPreferences() {
        return userPreferencesDao.getUserPreferences();
    }

    public void updateUserPreferences(UserPreferences preferences) {
        if (preferences == null) {
            Log.w(TAG, "Attempted to update null preferences");
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Updating user preferences");
                userPreferencesDao.insertUserPreferences(preferences);
                Log.d(TAG, "User preferences updated successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error updating user preferences", e);
            }
        });
    }

    public void initializeDefaultPreferences() {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Initializing default user preferences");
                UserPreferences defaultPrefs = new UserPreferences();
                userPreferencesDao.insertUserPreferences(defaultPrefs);
                Log.d(TAG, "Default user preferences initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing default preferences", e);
            }
        });
    }
}