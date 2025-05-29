package com.example.translator.ui.text;

import android.content.Context;
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
        this.executor = Executors.newSingleThreadExecutor();

        this.supportedLanguages = languageRepository.getAllSupportedLanguages();
        this.userPreferences = userRepository.getUserPreferences();
    }

    public void translateText(String text, String sourceLanguage, String targetLanguage) {
        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);

                // Validate input
                if (text == null || text.trim().isEmpty()) {
                    _errorMessage.postValue("Please enter text to translate");
                    return;
                }

                if (text.length() > 5000) {
                    _errorMessage.postValue("Text too long. Maximum 5000 characters allowed.");
                    return;
                }

                // Same language check
                if (sourceLanguage.equals(targetLanguage)) {
                    _translationResult.postValue(text);
                    return;
                }

                translationService.translateText(text, sourceLanguage, targetLanguage,
                        new TranslationService.TranslationCallback() {
                            @Override
                            public void onSuccess(String translatedText) {
                                _translationResult.postValue(translatedText);
                            }

                            @Override
                            public void onFailure(Exception exception) {
                                if (exception instanceof TranslationService.NetworkException) {
                                    _errorMessage.postValue("No internet connection available");
                                } else if (exception instanceof TranslationService.TranslationException) {
                                    _errorMessage.postValue(exception.getMessage() != null ? exception.getMessage() : "Translation service error");
                                } else {
                                    _errorMessage.postValue("An unexpected error occurred: " + exception.getMessage());
                                }
                            }
                        });

            } catch (Exception e) {
                _errorMessage.postValue("An unexpected error occurred: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    public void detectLanguage(String text) {
        executor.execute(() -> {
            try {
                if (text == null || text.trim().isEmpty()) return;

                translationService.detectLanguage(text, new TranslationService.LanguageDetectionCallback() {
                    @Override
                    public void onSuccess(String detectedLanguage) {
                        // Handle detection result if needed
                        // This could be used to automatically set source language
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        // Language detection is optional, don't show error to user
                        // Just log for debugging
                    }
                });

            } catch (Exception e) {
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

        // Cancel ongoing operations
        if (executor != null) {
            executor.shutdown();
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