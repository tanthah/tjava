package com.example.translator;

import android.app.Application;
import android.util.Log;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslatorApplication extends Application {

    private static final String TAG = "TranslatorApplication";

    // Application scope for background tasks
    private ExecutorService applicationExecutor;

    private AppDatabase database;
    private LanguageRepository languageRepository;
    private UserRepository userRepository;

    public AppDatabase getDatabase() {
        if (database == null) {
            synchronized (this) {
                if (database == null) {
                    database = AppDatabase.getDatabase(this);
                    Log.d(TAG, "Database initialized");
                }
            }
        }
        return database;
    }

    public LanguageRepository getLanguageRepository() {
        if (languageRepository == null) {
            synchronized (this) {
                if (languageRepository == null) {
                    languageRepository = new LanguageRepository(this);
                    Log.d(TAG, "LanguageRepository initialized");
                }
            }
        }
        return languageRepository;
    }

    public UserRepository getUserRepository() {
        if (userRepository == null) {
            synchronized (this) {
                if (userRepository == null) {
                    userRepository = new UserRepository(this);
                    Log.d(TAG, "UserRepository initialized");
                }
            }
        }
        return userRepository;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TranslatorApplication onCreate");

        try {
            // Initialize application executor
            applicationExecutor = Executors.newFixedThreadPool(4);
            Log.d(TAG, "Application executor initialized");

            // Initialize repositories to ensure database is created
            getDatabase();
            getLanguageRepository();
            getUserRepository();

            // Initialize supported languages data in background
            applicationExecutor.execute(() -> {
                try {
                    Log.d(TAG, "Starting language initialization...");
                    getLanguageRepository().initializeSupportedLanguages();

                    // Initialize default user preferences
                    getUserRepository().initializeDefaultPreferences();

                    Log.d(TAG, "Application initialization completed");
                } catch (Exception e) {
                    Log.e(TAG, "Error during application initialization", e);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in application onCreate", e);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "TranslatorApplication onTerminate");

        try {
            if (applicationExecutor != null && !applicationExecutor.isShutdown()) {
                applicationExecutor.shutdown();
                Log.d(TAG, "Application executor shutdown");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down application executor", e);
        }
    }
}