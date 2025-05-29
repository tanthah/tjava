package com.example.translator.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_preferences")
public class UserPreferences {
    @PrimaryKey
    private int id;
    private String defaultSourceLanguage;
    private String defaultTargetLanguage;
    private String theme; // light, dark, system
    private boolean autoDetectLanguage;
    private boolean ttsEnabled;
    private boolean cameraAutoTranslate;
    private String fontSize; // small, medium, large

    public UserPreferences() {
        this.id = 1;
        this.defaultSourceLanguage = "en";
        this.defaultTargetLanguage = "vi";
        this.theme = "light";
        this.autoDetectLanguage = true;
        this.ttsEnabled = true;
        this.cameraAutoTranslate = true;
        this.fontSize = "medium";
    }

    public UserPreferences(String defaultSourceLanguage, String defaultTargetLanguage,
                           String theme, boolean autoDetectLanguage, boolean ttsEnabled,
                           boolean cameraAutoTranslate, String fontSize) {
        this.id = 1;
        this.defaultSourceLanguage = defaultSourceLanguage;
        this.defaultTargetLanguage = defaultTargetLanguage;
        this.theme = theme;
        this.autoDetectLanguage = autoDetectLanguage;
        this.ttsEnabled = ttsEnabled;
        this.cameraAutoTranslate = cameraAutoTranslate;
        this.fontSize = fontSize;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDefaultSourceLanguage() { return defaultSourceLanguage; }
    public void setDefaultSourceLanguage(String defaultSourceLanguage) { this.defaultSourceLanguage = defaultSourceLanguage; }

    public String getDefaultTargetLanguage() { return defaultTargetLanguage; }
    public void setDefaultTargetLanguage(String defaultTargetLanguage) { this.defaultTargetLanguage = defaultTargetLanguage; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public boolean isAutoDetectLanguage() { return autoDetectLanguage; }
    public void setAutoDetectLanguage(boolean autoDetectLanguage) { this.autoDetectLanguage = autoDetectLanguage; }

    public boolean isTtsEnabled() { return ttsEnabled; }
    public void setTtsEnabled(boolean ttsEnabled) { this.ttsEnabled = ttsEnabled; }

    public boolean isCameraAutoTranslate() { return cameraAutoTranslate; }
    public void setCameraAutoTranslate(boolean cameraAutoTranslate) { this.cameraAutoTranslate = cameraAutoTranslate; }

    public String getFontSize() { return fontSize; }
    public void setFontSize(String fontSize) { this.fontSize = fontSize; }
}