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
                _errorMessage.postValue("Text-to-speech not available");
            }
        });
        _speechRate.setValue(currentSpeechRate);
    }

    public void processImage(Bitmap bitmap, String sourceLanguage, String targetLanguage) {
        executor.execute(() -> {
            try {
                _isLoading.postValue(true);
                _errorMessage.postValue(null);

                Log.d(TAG, "Starting image processing...");

                // Step 1: Validate inputs
                if (!isValidBitmap(bitmap)) {
                    _errorMessage.postValue("Invalid image. Please select a different image.");
                    return;
                }

                if (sourceLanguage.isEmpty() || targetLanguage.isEmpty()) {
                    _errorMessage.postValue("Please select source and target languages.");
                    return;
                }

                // Step 2: Recognize text from image
                Log.d(TAG, "Recognizing text from image...");
                textRecognitionService.recognizeTextFromBitmap(bitmap, new TextRecognitionService.TextRecognitionCallback() {
                    @Override
                    public void onSuccess(String recognizedText) {
                        if (recognizedText == null || recognizedText.isEmpty()) {
                            Log.w(TAG, "No text detected in image");
                            _detectedText.postValue(null);
                            _errorMessage.postValue("No text detected in the selected area. Try selecting a different area or image with clearer text.");
                            _isLoading.postValue(false);
                            return;
                        }

                        Log.d(TAG, "Text recognized: " + recognizedText.length() + " characters");
                        _detectedText.postValue(recognizedText);

                        // Step 3: Translate the recognized text if languages are different
                        if (sourceLanguage.equals(targetLanguage)) {
                            Log.d(TAG, "Source and target languages are the same, skipping translation");
                            _translationResult.postValue(recognizedText);
                            _isLoading.postValue(false);
                        } else {
                            Log.d(TAG, "Translating text from " + sourceLanguage + " to " + targetLanguage + "...");
                            translationService.translateText(recognizedText, sourceLanguage, targetLanguage,
                                    new TranslationService.TranslationCallback() {
                                        @Override
                                        public void onSuccess(String translatedText) {
                                            if (translatedText == null || translatedText.isEmpty()) {
                                                Log.w(TAG, "Translation failed or returned empty result");
                                                _translationResult.postValue(null);
                                                _errorMessage.postValue("Translation failed. Please check your internet connection and try again.");
                                            } else {
                                                Log.d(TAG, "Translation successful: " + translatedText.length() + " characters");
                                                _translationResult.postValue(translatedText);
                                            }
                                            _isLoading.postValue(false);
                                        }

                                        @Override
                                        public void onFailure(Exception exception) {
                                            Log.e(TAG, "Translation error", exception);
                                            if (exception instanceof TranslationService.NetworkException) {
                                                _errorMessage.postValue("No internet connection. Please check your network and try again.");
                                            } else if (exception instanceof TranslationService.TranslationException) {
                                                _errorMessage.postValue(exception.getMessage() != null ? exception.getMessage() : "Translation service error. Please try again.");
                                            } else {
                                                _errorMessage.postValue("An unexpected error occurred: " + (exception.getMessage() != null ? exception.getMessage() : "Unknown error"));
                                            }
                                            _isLoading.postValue(false);
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        Log.e(TAG, "Text recognition error", exception);
                        _detectedText.postValue(null);
                        _errorMessage.postValue("Text recognition failed: " + exception.getMessage());
                        _isLoading.postValue(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during processing", e);
                _errorMessage.postValue("An unexpected error occurred: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                _isLoading.postValue(false);
            }
        });
    }

    public void summarizeDetectedText(TextSummarizationService.SummaryType summaryType, String targetLanguage) {
        String textToSummarize = _detectedText.getValue();

        if (textToSummarize == null || textToSummarize.isEmpty()) {
            _errorMessage.postValue("No text available to summarize");
            return;
        }

        executor.execute(() -> {
            try {
                _isSummarizing.postValue(true);
                _errorMessage.postValue(null);

                Log.d(TAG, "Summarizing text...");
                summarizationService.summarizeText(textToSummarize, summaryType, targetLanguage,
                        new TextSummarizationService.SummarizationCallback() {
                            @Override
                            public void onSuccess(TextSummarizationService.SummaryResult.Success result) {
                                _summaryResult.postValue(result.summary);
                                Log.d(TAG, "Summarization successful");
                                _isSummarizing.postValue(false);
                            }

                            @Override
                            public void onFailure(TextSummarizationService.SummaryResult.Error error) {
                                _errorMessage.postValue(error.message);
                                Log.e(TAG, "Summarization failed: " + error.message);
                                _isSummarizing.postValue(false);
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Error during summarization", e);
                _errorMessage.postValue("Summarization failed: " + e.getMessage());
                _isSummarizing.postValue(false);
            }
        });
    }

    public void speakDetectedText(String languageCode) {
        String text = _detectedText.getValue();
        if (text == null || text.isEmpty()) {
            _errorMessage.postValue("No detected text to speak");
            return;
        }

        try {
            speechService.speakText(text, languageCode, currentSpeechRate);
        } catch (Exception e) {
            Log.e(TAG, "Error speaking detected text", e);
            _errorMessage.postValue("Failed to speak text");
        }
    }

    public void speakTranslatedText(String languageCode) {
        String text = _translationResult.getValue();
        if (text == null || text.isEmpty()) {
            _errorMessage.postValue("No translated text to speak");
            return;
        }

        try {
            speechService.speakText(text, languageCode, currentSpeechRate);
        } catch (Exception e) {
            Log.e(TAG, "Error speaking translated text", e);
            _errorMessage.postValue("Failed to speak translation");
        }
    }

    public void speakSummary(String languageCode) {
        String text = _summaryResult.getValue();
        if (text == null || text.isEmpty()) {
            _errorMessage.postValue("No summary to speak");
            return;
        }

        try {
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
    }

    public void stopSpeaking() {
        speechService.stopSpeaking();
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
        return bitmap != null &&
                !bitmap.isRecycled() &&
                bitmap.getWidth() > 0 &&
                bitmap.getHeight() > 0 &&
                bitmap.getWidth() <= 4096 &&
                bitmap.getHeight() <= 4096;
    }

    public void clearResults() {
        _detectedText.setValue(null);
        _translationResult.setValue(null);
        _summaryResult.setValue(null);
        _errorMessage.setValue(null);
    }

    public void clearError() {
        _errorMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        Log.d(TAG, "ViewModel cleared, cleaning up resources");

        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
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