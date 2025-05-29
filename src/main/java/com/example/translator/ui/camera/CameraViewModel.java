package com.example.translator.ui.camera;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.mlkit.vision.common.InputImage;
import com.example.translator.data.model.Language;
import com.example.translator.data.model.UserPreferences;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import com.example.translator.services.TextRecognitionService;
import com.example.translator.services.TranslationService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraViewModel extends ViewModel {

    private static final String TAG = "CameraViewModel";

    private UserRepository userRepository;
    private LanguageRepository languageRepository;
    private TextRecognitionService textRecognitionService;
    private TranslationService translationService;
    private ExecutorService executor;

    public final LiveData<List<Language>> supportedLanguages;
    public final LiveData<UserPreferences> userPreferences;

    private MutableLiveData<String> _detectedText = new MutableLiveData<>();
    public final LiveData<String> detectedText = _detectedText;

    private MutableLiveData<String> _translationResult = new MutableLiveData<>();
    public final LiveData<String> translationResult = _translationResult;

    private MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    private MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    // Callback interfaces
    public interface TextRecognitionCallback {
        void onSuccess(String recognizedText);
        void onFailure(Exception exception);
    }

    public CameraViewModel(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
        this.userRepository = userRepository;
        this.languageRepository = languageRepository;
        this.textRecognitionService = new TextRecognitionService();
        this.translationService = new TranslationService(context);
        this.executor = Executors.newFixedThreadPool(4);

        // Initialize LiveData from repositories
        this.supportedLanguages = languageRepository.getAllSupportedLanguages();
        this.userPreferences = userRepository.getUserPreferences();
    }

    public void recognizeText(InputImage inputImage, TextRecognitionCallback callback) {
        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);

                Log.d(TAG, "Starting text recognition from camera image...");

                textRecognitionService.recognizeTextFromImage(inputImage, new TextRecognitionService.TextRecognitionCallback() {
                    @Override
                    public void onSuccess(String recognizedText) {
                        Log.d(TAG, "Text recognition successful: " + (recognizedText != null ? recognizedText.length() : 0) + " characters");
                        _detectedText.postValue(recognizedText);

                        if (callback != null) {
                            callback.onSuccess(recognizedText);
                        }
                        _isLoading.postValue(false);
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        Log.e(TAG, "Text recognition failed", exception);
                        handleError("Text recognition failed", exception);

                        if (callback != null) {
                            callback.onFailure(exception);
                        }
                        _isLoading.postValue(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in text recognition process", e);
                handleError("Text recognition failed", e);

                if (callback != null) {
                    callback.onFailure(e);
                }
                _isLoading.postValue(false);
            }
        });
    }

    // Overloaded method for direct recognition without callback
    public void recognizeText(InputImage inputImage) {
        recognizeText(inputImage, null);
    }

    public void translateDetectedText(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "No text to translate");
            return;
        }

        if (sourceLanguage.equals(targetLanguage)) {
            Log.d(TAG, "Source and target languages are the same, skipping translation");
            _translationResult.postValue(text);
            return;
        }

        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);

                Log.d(TAG, "Translating text from " + sourceLanguage + " to " + targetLanguage);

                translationService.translateText(text, sourceLanguage, targetLanguage,
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
                                _translationResult.postValue(null);
                                handleError("Translation failed", exception);
                                _isLoading.postValue(false);
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Error in translation process", e);
                handleError("Translation failed", e);
                _isLoading.postValue(false);
            }
        });
    }

    private void handleError(String message, Exception exception) {
        String errorMessage = message;

        if (exception != null) {
            String exceptionMessage = exception.getMessage();

            if (exception instanceof TranslationService.NetworkException) {
                errorMessage = "No internet connection available";
            } else if (exception instanceof TranslationService.TranslationException) {
                errorMessage = exceptionMessage != null ? exceptionMessage : "Translation service error";
            } else if (exceptionMessage != null) {
                String lowerMessage = exceptionMessage.toLowerCase();
                if (lowerMessage.contains("network") || lowerMessage.contains("internet")) {
                    errorMessage = "No internet connection available";
                } else if (lowerMessage.contains("timeout")) {
                    errorMessage = "Request timed out. Please try again.";
                } else {
                    errorMessage = message + ": " + exceptionMessage;
                }
            } else {
                errorMessage = message + ": Unknown error";
            }
        }

        Log.e(TAG, "Error handled: " + errorMessage);
        _errorMessage.postValue(errorMessage);
    }

    // Method to get detected text synchronously (for testing or immediate access)
    public String getDetectedTextValue() {
        return _detectedText.getValue();
    }

    // Method to get translation result synchronously
    public String getTranslationResultValue() {
        return _translationResult.getValue();
    }

    // Method to check if currently processing
    public boolean isCurrentlyLoading() {
        Boolean loading = _isLoading.getValue();
        return loading != null && loading;
    }

    public void clearError() {
        _errorMessage.setValue(null);
    }

    public void clearResults() {
        _detectedText.setValue(null);
        _translationResult.setValue(null);
        _errorMessage.setValue(null);
    }

    public void clearDetectedText() {
        _detectedText.setValue(null);
    }

    public void clearTranslationResult() {
        _translationResult.setValue(null);
    }

    // Method to force update detected text (useful for testing)
    public void setDetectedText(String text) {
        _detectedText.setValue(text);
    }

    // Method to force update translation result (useful for testing)
    public void setTranslationResult(String result) {
        _translationResult.setValue(result);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        Log.d(TAG, "ViewModel cleared, cleaning up resources");

        // Cancel any ongoing operations
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                // Wait a bit for tasks to complete
                if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close services
        try {
            if (textRecognitionService != null) {
                textRecognitionService.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing text recognition service", e);
        }

        try {
            if (translationService != null) {
                translationService.closeTranslators();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing translation service", e);
        }
    }

    public static class CameraViewModelFactory implements ViewModelProvider.Factory {
        private UserRepository userRepository;
        private LanguageRepository languageRepository;
        private Context context;

        public CameraViewModelFactory(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
            this.userRepository = userRepository;
            this.languageRepository = languageRepository;
            this.context = context;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CameraViewModel.class)) {
                return (T) new CameraViewModel(userRepository, languageRepository, context);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}