package com.example.translator.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.local.dao.LanguageDao;
import com.example.translator.data.model.Language;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanguageRepository {
    private static final String TAG = "LanguageRepository";
    private LanguageDao languageDao;
    private ExecutorService executor;

    public LanguageRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        languageDao = database.languageDao();
        executor = Executors.newFixedThreadPool(4);
    }

    public LiveData<List<Language>> getAllSupportedLanguages() {
        return languageDao.getAllSupportedLanguages();
    }

    public void getLanguageByCode(String code, LanguageCallback callback) {
        if (callback == null) return;

        executor.execute(() -> {
            try {
                Language language = languageDao.getLanguageByCode(code);
                callback.onResult(language);
            } catch (Exception e) {
                Log.e(TAG, "Error getting language by code: " + code, e);
                callback.onResult(null);
            }
        });
    }

    public void initializeSupportedLanguages() {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Initializing supported languages...");

                // Clear existing languages first
                languageDao.clearAll();

                List<Language> supportedLanguages = Arrays.asList(
                        // Major languages with full support
                        new Language("en", "English", "English", true, true),
                        new Language("vi", "Vietnamese", "Tiếng Việt", true, true),
                        new Language("es", "Spanish", "Español", true, true),
                        new Language("fr", "French", "Français", true, true),
                        new Language("de", "German", "Deutsch", true, true),
                        new Language("it", "Italian", "Italiano", true, true),
                        new Language("pt", "Portuguese", "Português", true, true),
                        new Language("ru", "Russian", "Русский", true, true),

                        // Asian languages
                        new Language("zh", "Chinese", "中文", true, true),
                        new Language("ja", "Japanese", "日本語", true, true),
                        new Language("ko", "Korean", "한국어", true, true),
                        new Language("th", "Thai", "ไทย", false, true),
                        new Language("hi", "Hindi", "हिन्दी", false, true),

                        // European languages
                        new Language("nl", "Dutch", "Nederlands", false, false),
                        new Language("sv", "Swedish", "Svenska", false, false),
                        new Language("da", "Danish", "Dansk", false, false),
                        new Language("no", "Norwegian", "Norsk", false, false),
                        new Language("fi", "Finnish", "Suomi", false, false),
                        new Language("pl", "Polish", "Polski", false, false),
                        new Language("cs", "Czech", "Čeština", false, false),
                        new Language("hu", "Hungarian", "Magyar", false, false),

                        // Other languages
                        new Language("ar", "Arabic", "العربية", false, false),
                        new Language("tr", "Turkish", "Türkçe", false, false),
                        new Language("af", "Afrikaans", "Afrikaans", false, false)
                );

                // Insert all languages
                languageDao.insertLanguages(supportedLanguages);
                Log.d(TAG, "Successfully initialized " + supportedLanguages.size() + " languages");

            } catch (Exception e) {
                Log.e(TAG, "Error initializing languages", e);
            }
        });
    }

    public interface LanguageCallback {
        void onResult(Language language);
    }
}