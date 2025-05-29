package com.example.translator.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "languages")
public class Language {
    @PrimaryKey
    @NonNull
    private String languageCode;
    private String languageName;
    private String nativeName;
    private boolean isSupported;
    private boolean supportsTextTranslation;
    private boolean supportsVoiceTranslation;
    private boolean supportsCameraTranslation;

    // Primary constructor for Room (no @Ignore needed)
    public Language(@NonNull String languageCode, String languageName, String nativeName,
                    boolean isSupported, boolean supportsTextTranslation,
                    boolean supportsVoiceTranslation, boolean supportsCameraTranslation) {
        this.languageCode = languageCode;
        this.languageName = languageName;
        this.nativeName = nativeName;
        this.isSupported = isSupported;
        this.supportsTextTranslation = supportsTextTranslation;
        this.supportsVoiceTranslation = supportsVoiceTranslation;
        this.supportsCameraTranslation = supportsCameraTranslation;
    }

    // Convenience constructor - marked with @Ignore for Room
    @Ignore
    public Language(@NonNull String languageCode, String languageName, String nativeName) {
        this(languageCode, languageName, nativeName, true, true, false, false);
    }

    // Convenience constructor - marked with @Ignore for Room
    @Ignore
    public Language(@NonNull String languageCode, String languageName, String nativeName,
                    boolean supportsVoiceTranslation, boolean supportsCameraTranslation) {
        this(languageCode, languageName, nativeName, true, true,
                supportsVoiceTranslation, supportsCameraTranslation);
    }

    // Getters and Setters
    @NonNull
    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(@NonNull String languageCode) {
        this.languageCode = languageCode;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    public boolean isSupported() {
        return isSupported;
    }

    public void setSupported(boolean supported) {
        isSupported = supported;
    }

    public boolean getSupportsTextTranslation() {
        return supportsTextTranslation;
    }

    public void setSupportsTextTranslation(boolean supportsTextTranslation) {
        this.supportsTextTranslation = supportsTextTranslation;
    }

    public boolean getSupportsVoiceTranslation() {
        return supportsVoiceTranslation;
    }

    public void setSupportsVoiceTranslation(boolean supportsVoiceTranslation) {
        this.supportsVoiceTranslation = supportsVoiceTranslation;
    }

    public boolean getSupportsCameraTranslation() {
        return supportsCameraTranslation;
    }

    public void setSupportsCameraTranslation(boolean supportsCameraTranslation) {
        this.supportsCameraTranslation = supportsCameraTranslation;
    }

    @Override
    public String toString() {
        return "Language{" +
                "languageCode='" + languageCode + '\'' +
                ", languageName='" + languageName + '\'' +
                ", nativeName='" + nativeName + '\'' +
                ", isSupported=" + isSupported +
                ", supportsTextTranslation=" + supportsTextTranslation +
                ", supportsVoiceTranslation=" + supportsVoiceTranslation +
                ", supportsCameraTranslation=" + supportsCameraTranslation +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Language language = (Language) o;
        return languageCode.equals(language.languageCode);
    }

    @Override
    public int hashCode() {
        return languageCode.hashCode();
    }
}