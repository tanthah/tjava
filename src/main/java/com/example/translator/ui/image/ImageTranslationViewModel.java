package com.example.translator.ui.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.translator.data.model.Language;
import com.example.translator.data.model.UserPreferences;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import com.example.translator.services.TextRecognitionService;
import com.example.translator.services.TranslationService;
import com.example.translator.services.TextSummarizationService;
import com.example.translator.services.SpeechService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageTranslationViewModel extends ViewModel {

    private static final String TAG = "ImageTranslationViewModel";

    private UserRepository userRepository;
    private LanguageRepository languageRepository;
    private TextRecognitionService textRecognitionService;
    private TranslationService translationService;
    private TextSummarizationService summarizationService;
    private SpeechService speechService;
    private ExecutorService executor;

    public final LiveData<List<Language>> supportedLanguages;
    public final LiveData<UserPreferences> userPreferences;

    private MutableLiveData<String> _detectedText = new MutableLiveData<>();
    public final LiveData<String> detectedText = _detectedText;

    private MutableLiveData<String> _translationResult = new MutableLiveData<>();
    public final LiveData<String> translationResult = _translationResult;

    private MutableLiveData<String> _summaryResult = new MutableLiveData<>();
    public final LiveData<String> summaryResult = _summaryResult;

    private MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    private MutableLiveData<Boolean> _isSummarizing = new MutableLiveData<>();
    public final LiveData<Boolean> isSummarizing = _isSummarizing;

    private MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private MutableLiveData<Float> _speechRate = new MutableLiveData<>();
    public final LiveData<Float> speechRate = _speechRate;

    // Speech settings
    private float currentSpeechRate = SpeechService.SPEED_NORMAL;

    public ImageTranslationViewModel(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
        this.userRepository = userRepository;
        this.languageRepository = languageRepository;
        this.textRecognitionService = new TextRecognitionService();
        this.translationService = new TranslationService(context);
        this.summarizationService = new TextSummarizationService(context);
        this.speechService = new SpeechService(context);
        this.executor = Executors.newFixedThreadPool(4);

        this.supportedLanguages = languageRepository.getAllSupportedLanguages();
        this.userPreferences = userRepository.getUserPreferences();

        // Initialize speech service
        speechService.initializeTextToSpeech(success -> {
            if (!success) {
                Log.w(TAG, "Text-to-speech not available");
            }
        });
        _speechRate.setValue(currentSpeechRate);
        _isLoading.setValue(false);
        _isSummarizing.setValue(false);

        Log.d(TAG, "ImageTranslationViewModel initialized");
    }

    public void processImage(Bitmap bitmap, String sourceLanguage, String targetLanguage) {
        Log.d(TAG, "Starting image processing...");
        Log.d(TAG, "Languages: " + sourceLanguage + " -> " + targetLanguage);

        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);
                _detectedText.postValue(null);
                _translationResult.postValue(null);

                // Step 1: Validate inputs
                if (!isValidBitmap(bitmap)) {
                    Log.e(TAG, "Invalid bitmap provided");
                    _errorMessage.postValue("Invalid image. Please select a different image.");
                    _isLoading.postValue(false);
                    return;
                }

                if (sourceLanguage == null || targetLanguage == null ||
                        sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
                    Log.e(TAG, "Invalid languages: " + sourceLanguage + " -> " + targetLanguage);
                    _errorMessage.postValue("Please select source and target languages.");
                    _isLoading.postValue(false);
                    return;
                }

                Log.d(TAG, "Bitmap info: " + bitmap.getWidth() + "x" + bitmap.getHeight() +
                        ", Config: " + bitmap.getConfig() + ", Bytes: " + bitmap.getByteCount());

                // Step 2: Recognize text from image
                Log.d(TAG, "Starting text recognition...");
                textRecognitionService.recognizeTextFromBitmap(bitmap, new TextRecognitionService.TextRecognitionCallback() {
                    @Override
                    public void onSuccess(String recognizedText) {
                        Log.d(TAG, "Text recognition successful");
                        Log.d(TAG, "Recognized text: '" + recognizedText + "'");

                        if (recognizedText == null || recognizedText.trim().isEmpty()) {
                            Log.w(TAG, "No text detected in image");
                            _detectedText.postValue("");
                            _errorMessage.postValue("No text detected in the selected area. Try selecting a different area or image with clearer text.");
                            _isLoading.postValue(false);
                            return;
                        }

                        String cleanText = recognizedText.trim();
                        _detectedText.postValue(cleanText);

                        // Step 3: Translate the recognized text if languages are different
                        if (sourceLanguage.equals(targetLanguage)) {
                            Log.d(TAG, "Source and target languages are the same, skipping translation");
                            _translationResult.postValue(cleanText);
                            _isLoading.postValue(false);
                        } else {
                            Log.d(TAG, "Starting translation: " + sourceLanguage + " -> " + targetLanguage);
                            translationService.translateText(cleanText, sourceLanguage, targetLanguage,
                                    new TranslationService.TranslationCallback() {
                                        @Override
                                        public void onSuccess(String translatedText) {
                                            Log.d(TAG, "Translation successful");
                                            Log.d(TAG, "Translated text: '" + translatedText + "'");

                                            if (translatedText == null || translatedText.trim().isEmpty()) {
                                                Log.w(TAG, "Translation returned empty result");
                                                _translationResult.postValue("Translation failed - empty result");
                                                _errorMessage.postValue("Translation failed. Please check your internet connection and try again.");
                                            } else {
                                                _translationResult.postValue(translatedText.trim());
                                            }
                                            _isLoading.postValue(false);
                                        }

                                        @Override
                                        public void onFailure(Exception exception) {
                                            Log.e(TAG, "Translation failed", exception);
                                            _translationResult.postValue(null);

                                            String errorMsg = "Translation failed";
                                            if (exception instanceof TranslationService.NetworkException) {
                                                errorMsg = "No internet connection. Please check your network and try again.";
                                            } else if (exception instanceof TranslationService.TranslationException) {
                                                String msg = exception.getMessage();
                                                errorMsg = (msg != null && !msg.isEmpty()) ? msg : "Translation service error. Please try again.";
                                            } else if (exception != null && exception.getMessage() != null) {
                                                errorMsg = "Translation error: " + exception.getMessage();
                                            }

                                            _errorMessage.postValue(errorMsg);
                                            _isLoading.postValue(false);
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        Log.e(TAG, "Text recognition failed", exception);
                        _detectedText.postValue(null);

                        String errorMsg = "Text recognition failed";
                        if (exception != null && exception.getMessage() != null) {
                            errorMsg = "Text recognition failed: " + exception.getMessage();
                        }

                        _errorMessage.postValue(errorMsg);
                        _isLoading.postValue(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during image processing", e);
                String errorMsg = "An unexpected error occurred";
                if (e.getMessage() != null) {
                    errorMsg += ": " + e.getMessage();
                }
                _errorMessage.postValue(errorMsg);
                _isLoading.postValue(false);
            }
        });
    }

    public void summarizeDetectedText(TextSummarizationService.SummaryType summaryType, String targetLanguage) {
        String textToSummarize = _detectedText.getValue();

        if (textToSummarize == null || textToSummarize.trim().isEmpty()) {
            Log.w(TAG, "No text available to summarize");
            _errorMessage.postValue("No text available to summarize");
            return;
        }

        executor.execute(() -> {
            try {
                _isSummarizing.postValue(true);
                _errorMessage.postValue(null);

                Log.d(TAG, "Starting text summarization...");
                summarizationService.summarizeText(textToSummarize, summaryType, targetLanguage,
                        new TextSummarizationService.SummarizationCallback() {
                            @Override
                            public void onSuccess(TextSummarizationService.SummaryResult.Success result) {
                                Log.d(TAG, "Summarization successful");
                                _summaryResult.postValue(result.summary);
                                _isSummarizing.postValue(false);
                            }

                            @Override
                            public void onFailure(TextSummarizationService.SummaryResult.Error error) {
                                Log.e(TAG, "Summarization failed: " + error.message);
                                _errorMessage.postValue(error.message);
                                _isSummarizing.postValue(false);
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Error during summarization", e);
                _errorMessage.postValue("Summarization failed: " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                _isSummarizing.postValue(false);
            }
        });
    }

    public void speakDetectedText(String languageCode) {
        String text = _detectedText.getValue();
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "No detected text to speak");
            _errorMessage.postValue("No detected text to speak");
            return;
        }

        try {
            Log.d(TAG, "Speaking detected text in " + languageCode);
            speechService.speakText(text, languageCode, currentSpeechRate);
        } catch (Exception e) {
            Log.e(TAG, "Error speaking detected text", e);
            _errorMessage.postValue("Failed to speak text");
        }
    }

    public void speakTranslatedText(String languageCode) {
        String text = _translationResult.getValue();
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "No translated text to speak");
            _errorMessage.postValue("No translated text to speak");
            return;
        }

        try {
            Log.d(TAG, "Speaking translated text in " + languageCode);
            speechService.speakText(text, languageCode, currentSpeechRate);
        } catch (Exception e) {
            Log.e(TAG, "Error speaking translated text", e);
            _errorMessage.postValue("Failed to speak translation");
        }
    }

    public void speakSummary(String languageCode) {
        String text = _summaryResult.getValue();
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "No summary to speak");
            _errorMessage.postValue("No summary to speak");
            return;
        }

        try {
            Log.d(TAG, "Speaking summary in " + languageCode);
            speechService.speakText(text, languageCode, currentSpeechRate);
        } catch (Exception e) {
            Log.e(TAG, "Error speaking summary", e);
            _errorMessage.postValue("Failed to speak summary");
        }
    }

    public void setSpeechRate(float rate) {
        currentSpeechRate = Math.max(SpeechService.SPEED_VERY_SLOW, Math.min(rate, SpeechService.SPEED_VERY_FAST));
        speechService.setSpeechRate(currentSpeechRate);
        _speechRate.postValue(currentSpeechRate);
        Log.d(TAG, "Speech rate set to: " + currentSpeechRate);
    }

    public void stopSpeaking() {
        try {
            speechService.stopSpeaking();
            Log.d(TAG, "Speech stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping speech", e);
        }
    }

    public String getSpeechRateText(float rate) {
        if (rate <= SpeechService.SPEED_VERY_SLOW) {
            return "Very Slow";
        } else if (rate <= SpeechService.SPEED_SLOW) {
            return "Slow";
        } else if (rate <= SpeechService.SPEED_NORMAL) {
            return "Normal";
        } else if (rate <= SpeechService.SPEED_FAST) {
            return "Fast";
        } else if (rate <= SpeechService.SPEED_VERY_FAST) {
            return "Very Fast";
        } else {
            return "Custom";
        }
    }

    private boolean isValidBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null");
            return false;
        }
        if (bitmap.isRecycled()) {
            Log.e(TAG, "Bitmap is recycled");
            return false;
        }
        if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            Log.e(TAG, "Bitmap has invalid dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return false;
        }
        if (bitmap.getWidth() > 4096 || bitmap.getHeight() > 4096) {
            Log.e(TAG, "Bitmap too large: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return false;
        }
        return true;
    }

    public void clearResults() {
        _detectedText.setValue(null);
        _translationResult.setValue(null);
        _summaryResult.setValue(null);
        _errorMessage.setValue(null);
        Log.d(TAG, "Results cleared");
    }

    public void clearError() {
        _errorMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel cleared, cleaning up resources");

        // Shutdown executor
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

        // Close services
        try {
            if (textRecognitionService != null) {
                textRecognitionService.close();
            }
            if (translationService != null) {
                translationService.closeTranslators();
            }
            if (summarizationService != null) {
                summarizationService.close();
            }
            if (speechService != null) {
                speechService.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing services", e);
        }
    }

    public static class ImageTranslationViewModelFactory implements ViewModelProvider.Factory {
        private UserRepository userRepository;
        private LanguageRepository languageRepository;
        private Context context;

        public ImageTranslationViewModelFactory(UserRepository userRepository, LanguageRepository languageRepository, Context context) {
            this.userRepository = userRepository;
            this.languageRepository = languageRepository;
            this.context = context;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ImageTranslationViewModel.class)) {
                return (T) new ImageTranslationViewModel(userRepository, languageRepository, context);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}