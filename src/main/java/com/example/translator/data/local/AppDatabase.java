package com.example.translator.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.translator.data.local.dao.LanguageDao;
import com.example.translator.data.local.dao.UserPreferencesDao;
import com.example.translator.data.model.Language;
import com.example.translator.data.model.UserPreferences;

@Database(
        entities = {Language.class, UserPreferences.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract LanguageDao languageDao();
    public abstract UserPreferencesDao userPreferencesDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "translator_database"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}