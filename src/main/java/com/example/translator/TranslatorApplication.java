package com.example.translator;

import android.app.Application;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslatorApplication extends Application {

    // Application scope for background tasks
    private ExecutorService applicationExecutor;

    private AppDatabase database;
    private LanguageRepository languageRepository;
    private UserRepository userRepository;

    public AppDatabase getDatabase() {
        if (database == null) {
            database = AppDatabase.getDatabase(this);
        }
        return database;
    }

    public LanguageRepository getLanguageRepository() {
        if (languageRepository == null) {
            languageRepository = new LanguageRepository(this);
        }
        return languageRepository;
    }

    public UserRepository getUserRepository() {
        if (userRepository == null) {
            userRepository = new UserRepository(this);
        }
        return userRepository;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize application executor
        applicationExecutor = Executors.newFixedThreadPool(4);

        // Initialize supported languages data in background
        applicationExecutor.execute(() -> {
            try {
                getLanguageRepository().initializeSupportedLanguages();
            } catch (Exception e) {
                // Handle initialization error gracefully
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (applicationExecutor != null) {
            applicationExecutor.shutdown();
        }
    }
}