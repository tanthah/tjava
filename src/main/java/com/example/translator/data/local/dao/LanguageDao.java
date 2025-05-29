package com.example.translator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.translator.data.model.Language;
import java.util.List;

@Dao
public interface LanguageDao {
    @Query("SELECT * FROM languages WHERE isSupported = 1")
    LiveData<List<Language>> getAllSupportedLanguages();

    @Query("SELECT * FROM languages WHERE languageCode = :code")
    Language getLanguageByCode(String code);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLanguages(List<Language> languages);

    @Query("DELETE FROM languages")
    void clearAll();
}