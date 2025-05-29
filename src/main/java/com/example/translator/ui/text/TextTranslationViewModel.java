package com.example.translator.ui.text;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.translator.data.model.Language;
import com.example.translator.data.model.UserPreferences;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import com.example.translator.services.TranslationService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextTranslationViewModel extends ViewModel {

    private static final String TAG = "TextTranslationViewModel";

    private UserRepository userRepository;
    private LanguageRepository languageRepository;
    private TranslationService translationService;
    private ExecutorService executor;

    public final LiveData<List<Language>> supportedLanguages;
    public final LiveData<UserPreferences> userPreferences;

    private MutableLiveData<String> _translationResult = new MutableLiveData<>();
    public final LiveData<String> translationResult = _translationResult;

    private MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    private MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    public TextTranslationViewModel(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
        this.userRepository = userRepository;
        this.languageRepository = languageRepository;
        this.translationService = new TranslationService(context);
        this.executor = Executors.newFixedThreadPool(4);

        this.supportedLanguages = languageRepository.getAllSupportedLanguages();
        this.userPreferences = userRepository.getUserPreferences();

        // Initialize loading state
        _isLoading.setValue(false);

        Log.d(TAG, "TextTranslationViewModel initialized");
    }

    public void translateText(String text, String sourceLanguage, String targetLanguage) {
        Log.d(TAG, "translateText called with: " + text.substring(0, Math.min(50, text.length())) + "...");
        Log.d(TAG, "Languages: " + sourceLanguage + " -> " + targetLanguage);

        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);

                // Validate input
                if (text == null || text.trim().isEmpty()) {
                    Log.w(TAG, "Empty text provided");
                    _errorMessage.postValue("Please enter text to translate");
                    _isLoading.postValue(false);
                    return;
                }

                String cleanText = text.trim();
                if (cleanText.length() > 5000) {
                    Log.w(TAG, "Text too long: " + cleanText.length());
                    _errorMessage.postValue("Text too long. Maximum 5000 characters allowed.");
                    _isLoading.postValue(false);
                    return;
                }

                // Validate languages
                if (sourceLanguage == null || targetLanguage == null ||
                        sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
                    Log.w(TAG, "Invalid languages: " + sourceLanguage + " -> " + targetLanguage);
                    _errorMessage.postValue("Please select source and target languages");
                    _isLoading.postValue(false);
                    return;
                }

                // Same language check
                if (sourceLanguage.equals(targetLanguage)) {
                    Log.d(TAG, "Same language, returning original text");
                    _translationResult.postValue(cleanText);
                    _isLoading.postValue(false);
                    return;
                }

                Log.d(TAG, "Starting translation...");
                translationService.translateText(cleanText, sourceLanguage, targetLanguage,
                        new TranslationService.TranslationCallback() {
                            @Override
                            public void onSuccess(String translatedText) {
                                Log.d(TAG, "Translation successful");
                                _translationResult.postValue(translatedText);
                                _isLoading.postValue(false);
                            }

                            @Override
                            public void onFailure(Exception exception) {
                                Log.e(TAG, "Translation failed", exception);
                                String errorMsg = "Translation failed";

                                if (exception instanceof TranslationService.NetworkException) {
                                    errorMsg = "No internet connection available";
                                } else if (exception instanceof TranslationService.TranslationException) {
                                    String msg = exception.getMessage();
                                    if (msg != null && !msg.isEmpty()) {
                                        errorMsg = msg;
                                    } else {
                                        errorMsg = "Translation service error";
                                    }
                                } else if (exception != null && exception.getMessage() != null) {
                                    errorMsg = "Translation error: " + exception.getMessage();
                                }

                                _errorMessage.postValue(errorMsg);
                                _isLoading.postValue(false);
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in translateText", e);
                _errorMessage.postValue("An unexpected error occurred: " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                _isLoading.postValue(false);
            }
        });
    }

    public void detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Detecting language for text...");
                translationService.detectLanguage(text, new TranslationService.LanguageDetectionCallback() {
                    @Override
                    public void onSuccess(String detectedLanguage) {
                        Log.d(TAG, "Language detected: " + detectedLanguage);
                        // Handle detection result if needed
                        // This could be used to automatically set source language
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        Log.w(TAG, "Language detection failed", exception);
                        // Language detection is optional, don't show error to user
                        // Just log for debugging
                    }
                });

            } catch (Exception e) {
                Log.w(TAG, "Error in language detection", e);
                // Language detection is optional, don't show error to user
            }
        });
    }

    public void clearError() {
        _errorMessage.setValue(null);
    }

    public void clearResults() {
        _translationResult.setValue(null);
        _errorMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel cleared, cleaning up resources");

        // Cancel ongoing operations
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close translation service
        if (translationService != null) {
            translationService.closeTranslators();
        }
    }

    public static class TextTranslationViewModelFactory implements ViewModelProvider.Factory {
        private UserRepository userRepository;
        private LanguageRepository languageRepository;
        private Context context;

        public TextTranslationViewModelFactory(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
            this.userRepository = userRepository;
            this.languageRepository = languageRepository;
            this.context = context;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(TextTranslationViewModel.class)) {
                return (T) new TextTranslationViewModel(userRepository, languageRepository, context);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}