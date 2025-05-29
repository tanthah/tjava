package com.example.translator.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.example.translator.data.local.AppDatabase;
import com.example.translator.data.local.dao.LanguageDao;
import com.example.translator.data.model.Language;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LanguageRepository {
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
        executor.execute(() -> {
            Language language = languageDao.getLanguageByCode(code);
            callback.onResult(language);
        });
    }

    public void initializeSupportedLanguages() {
        executor.execute(() -> {
            List<Language> supportedLanguages = Arrays.asList(
                    new Language("af", "Afrikaans", "Afrikaans"),
                    new Language("ar", "Arabic", "العربية"),
                    new Language("zh", "Chinese", "中文"),
                    new Language("cs", "Czech", "Čeština"),
                    new Language("da", "Danish", "Dansk"),
                    new Language("nl", "Dutch", "Nederlands"),
                    new Language("en", "English", "English", true, true),
                    new Language("fi", "Finnish", "Suomi"),
                    new Language("fr", "French", "Français", true, true),
                    new Language("de", "German", "Deutsch", true, true),
                    new Language("hi", "Hindi", "हिन्दी"),
                    new Language("it", "Italian", "Italiano", true, true),
                    new Language("ja", "Japanese", "日本語", true, true),
                    new Language("ko", "Korean", "한국어", true, true),
                    new Language("pl", "Polish", "Polski"),
                    new Language("pt", "Portuguese", "Português"),
                    new Language("ru", "Russian", "Русский"),
                    new Language("es", "Spanish", "Español", true, true),
                    new Language("sv", "Swedish", "Svenska"),
                    new Language("th", "Thai", "ไทย"),
                    new Language("tr", "Turkish", "Türkçe"),
                    new Language("vi", "Vietnamese", "Tiếng Việt", true, true)
            );

            languageDao.insertLanguages(supportedLanguages);
        });
    }

    public interface LanguageCallback {
        void onResult(Language language);
    }
}