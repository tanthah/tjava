package com.example.translator.services;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TranslationService {

    private static final String TAG = "TranslationService";
    private static final long TRANSLATION_TIMEOUT = 30000L; // 30 seconds
    private static final long LANGUAGE_DETECTION_TIMEOUT = 10000L; // 10 seconds
    private static final int MAX_TEXT_LENGTH = 5000;

    private Context context;
    private LanguageIdentifier languageIdentifier;
    private Map<String, Translator> translators;
    private Set<String> downloadedModels;

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onFailure(Exception exception);
    }

    public interface LanguageDetectionCallback {
        void onSuccess(String detectedLanguage);
        void onFailure(Exception exception);
    }

    public TranslationService(Context context) {
        this.context = context;
        try {
            this.languageIdentifier = LanguageIdentification.getClient();
            this.translators = new HashMap<>();
            this.downloadedModels = new HashSet<>();
            Log.d(TAG, "TranslationService initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TranslationService", e);
        }
    }

    public void detectLanguage(String text, LanguageDetectionCallback callback) {
        if (!isValidInput(text) || callback == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Invalid input text"));
            }
            return;
        }

        try {
            Log.d(TAG, "Detecting language for text: " + text.substring(0, Math.min(50, text.length())) + "...");

            Task<String> task = languageIdentifier.identifyLanguage(text);
            task.addOnSuccessListener(detectedLanguage -> {
                Log.d(TAG, "Language detection result: " + detectedLanguage);
                if ("und".equals(detectedLanguage)) {
                    callback.onSuccess("en"); // Default to English if undetermined
                } else {
                    callback.onSuccess(detectedLanguage);
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Language detection failed", e);
                callback.onFailure(e);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in language detection", e);
            callback.onFailure(e);
        }
    }

    public void translateText(String text, String sourceLanguage, String targetLanguage,
                              TranslationCallback callback) {
        if (!isValidInput(text) || callback == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Invalid input text"));
            }
            return;
        }

        if (sourceLanguage == null || targetLanguage == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Source and target languages cannot be null"));
            }
            return;
        }

        if (sourceLanguage.equals(targetLanguage)) {
            Log.d(TAG, "Source and target languages are the same, returning original text");
            callback.onSuccess(text);
            return;
        }

        Log.d(TAG, "Translating from " + sourceLanguage + " to " + targetLanguage);
        Log.d(TAG, "Text preview: " + text.substring(0, Math.min(100, text.length())) + "...");

        try {
            String translatorKey = sourceLanguage + "_" + targetLanguage;
            Translator translator = getOrCreateTranslator(sourceLanguage, targetLanguage, translatorKey);

            if (translator == null) {
                callback.onFailure(new TranslationException("Failed to create translator"));
                return;
            }

            // Download model if needed and then translate
            downloadModelIfNeeded(translator, translatorKey, new ModelDownloadCallback() {
                @Override
                public void onSuccess() {
                    performTranslation(translator, text, callback);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Model download failed", e);
                    callback.onFailure(new TranslationException("Failed to download translation model: " + e.getMessage(), e));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Translation failed for " + sourceLanguage + " -> " + targetLanguage, e);
            callback.onFailure(new TranslationException("Translation failed: " + e.getMessage(), e));
        }
    }

    private void performTranslation(Translator translator, String text, TranslationCallback callback) {
        try {
            Log.d(TAG, "Performing translation...");
            Task<String> task = translator.translate(text);

            task.addOnSuccessListener(translatedText -> {
                Log.d(TAG, "Translation successful: " + translatedText.substring(0, Math.min(100, translatedText.length())) + "...");
                callback.onSuccess(translatedText);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Translation task failed", e);
                callback.onFailure(new TranslationException("Translation failed: " + e.getMessage(), e));
            });

        } catch (Exception e) {
            Log.e(TAG, "Error performing translation", e);
            callback.onFailure(new TranslationException("Translation error: " + e.getMessage(), e));
        }
    }

    private Translator getOrCreateTranslator(String sourceLanguage, String targetLanguage, String translatorKey) {
        try {
            if (translators.containsKey(translatorKey)) {
                Log.d(TAG, "Using existing translator for " + translatorKey);
                return translators.get(translatorKey);
            }

            Log.d(TAG, "Creating new translator for " + translatorKey);
            Translator translator = createTranslator(sourceLanguage, targetLanguage);
            if (translator != null) {
                translators.put(translatorKey, translator);
                Log.d(TAG, "Successfully created translator for " + translatorKey);
            }
            return translator;
        } catch (Exception e) {
            Log.e(TAG, "Error creating translator", e);
            return null;
        }
    }

    private Translator createTranslator(String sourceLanguage, String targetLanguage) {
        try {
            // Map language codes to ML Kit language codes
            String sourceMLKitLanguage = mapToMLKitLanguage(sourceLanguage);
            String targetMLKitLanguage = mapToMLKitLanguage(targetLanguage);

            if (sourceMLKitLanguage == null || targetMLKitLanguage == null) {
                Log.e(TAG, "Unsupported language: " + sourceLanguage + " -> " + targetLanguage);
                return null;
            }

            Log.d(TAG, "Creating translator: " + sourceMLKitLanguage + " -> " + targetMLKitLanguage);

            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(sourceMLKitLanguage)
                    .setTargetLanguage(targetMLKitLanguage)
                    .build();

            return Translation.getClient(options);
        } catch (Exception e) {
            Log.e(TAG, "Error in createTranslator", e);
            return null;
        }
    }

    private String mapToMLKitLanguage(String languageCode) {
        // Map common language codes to ML Kit language codes
        switch (languageCode.toLowerCase()) {
            case "en": return TranslateLanguage.ENGLISH;
            case "vi": return TranslateLanguage.VIETNAMESE;
            case "es": return TranslateLanguage.SPANISH;
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "it": return TranslateLanguage.ITALIAN;
            case "pt": return TranslateLanguage.PORTUGUESE;
            case "ru": return TranslateLanguage.RUSSIAN;
            case "zh": return TranslateLanguage.CHINESE;
            case "ja": return TranslateLanguage.JAPANESE;
            case "ko": return TranslateLanguage.KOREAN;
            case "th": return TranslateLanguage.THAI;
            case "hi": return TranslateLanguage.HINDI;
            case "ar": return TranslateLanguage.ARABIC;
            case "nl": return TranslateLanguage.DUTCH;
            case "sv": return TranslateLanguage.SWEDISH;
            case "da": return TranslateLanguage.DANISH;
            case "fi": return TranslateLanguage.FINNISH;
            case "pl": return TranslateLanguage.POLISH;
            case "cs": return TranslateLanguage.CZECH;
            case "tr": return TranslateLanguage.TURKISH;
            case "af": return TranslateLanguage.AFRIKAANS;
            default:
                Log.w(TAG, "Unsupported language code: " + languageCode);
                return null;
        }
    }

    private interface ModelDownloadCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    private void downloadModelIfNeeded(Translator translator, String translatorKey, ModelDownloadCallback callback) {
        if (downloadedModels.contains(translatorKey)) {
            Log.d(TAG, "Model already downloaded for " + translatorKey);
            callback.onSuccess();
            return;
        }

        try {
            Log.d(TAG, "Downloading model for " + translatorKey);
            DownloadConditions conditions = new DownloadConditions.Builder()
                    .build(); // Allow download on any network

            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(aVoid -> {
                        downloadedModels.add(translatorKey);
                        Log.d(TAG, "Model downloaded successfully for " + translatorKey);
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Model download failed for " + translatorKey, e);
                        callback.onFailure(e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "Exception during model download for " + translatorKey, e);
            callback.onFailure(e);
        }
    }

    private boolean isValidInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "Input text is null or empty");
            return false;
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            Log.w(TAG, "Input text too long: " + text.length() + " characters");
            return false;
        }

        return true;
    }

    public void closeTranslators() {
        try {
            Log.d(TAG, "Closing translators...");
            for (Map.Entry<String, Translator> entry : translators.entrySet()) {
                try {
                    entry.getValue().close();
                    Log.d(TAG, "Closed translator: " + entry.getKey());
                } catch (Exception e) {
                    Log.w(TAG, "Error closing translator: " + entry.getKey(), e);
                }
            }
            translators.clear();
            downloadedModels.clear();

            if (languageIdentifier != null) {
                languageIdentifier.close();
            }
            Log.d(TAG, "All translators closed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error closing translation service", e);
        }
    }

    // Custom exception classes
    public static class NetworkException extends Exception {
        public NetworkException(String message) {
            super(message);
        }
    }

    public static class TranslationException extends Exception {
        public TranslationException(String message) {
            super(message);
        }

        public TranslationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}