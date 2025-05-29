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
        this.languageIdentifier = LanguageIdentification.getClient();
        this.translators = new HashMap<>();
        this.downloadedModels = new HashSet<>();
    }

    public void detectLanguage(String text, LanguageDetectionCallback callback) {
        if (!isValidInput(text)) {
            callback.onFailure(new IllegalArgumentException("Invalid input text"));
            return;
        }

        if (!isNetworkAvailable()) {
            callback.onFailure(new NetworkException("No internet connection available"));
            return;
        }

        try {
            Task<String> task = languageIdentifier.identifyLanguage(text);

            // Add timeout to the task
            Task<String> timedTask = Tasks.call(() -> {
                try {
                    return Tasks.await(task, LANGUAGE_DETECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    throw new RuntimeException("Language detection timed out", e);
                }
            });

            timedTask.addOnSuccessListener(detectedLanguage -> {
                if ("und".equals(detectedLanguage)) {
                    callback.onSuccess(null);
                } else {
                    callback.onSuccess(detectedLanguage);
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Language detection failed", e);
                callback.onFailure(handleTranslationException(e));
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in language detection", e);
            callback.onFailure(e);
        }
    }

    public void translateText(String text, String sourceLanguage, String targetLanguage,
                              TranslationCallback callback) {
        if (!isValidInput(text)) {
            callback.onFailure(new IllegalArgumentException("Invalid input text"));
            return;
        }

        if (!isNetworkAvailable()) {
            callback.onFailure(new NetworkException("No internet connection available"));
            return;
        }

        if (sourceLanguage.equals(targetLanguage)) {
            callback.onSuccess(text); // No translation needed
            return;
        }

        try {
            String translatorKey = sourceLanguage + "_" + targetLanguage;
            Translator translator = getOrCreateTranslator(sourceLanguage, targetLanguage, translatorKey);

            // Ensure model is downloaded
            downloadModelIfNeeded(translator, translatorKey, new ModelDownloadCallback() {
                @Override
                public void onSuccess() {
                    performTranslation(translator, text, callback);
                }

                @Override
                public void onFailure(Exception e) {
                    callback.onFailure(new TranslationException("Failed to download translation model", e));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Translation failed for " + sourceLanguage + " -> " + targetLanguage, e);
            callback.onFailure(handleTranslationException(e));
        }
    }

    private void performTranslation(Translator translator, String text, TranslationCallback callback) {
        try {
            Task<String> task = translator.translate(text);

            // Add timeout to the task
            Task<String> timedTask = Tasks.call(() -> {
                try {
                    return Tasks.await(task, TRANSLATION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    throw new RuntimeException("Translation timed out", e);
                }
            });

            timedTask.addOnSuccessListener(callback::onSuccess)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Translation task failed", e);
                        callback.onFailure(handleTranslationException(e));
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error performing translation", e);
            callback.onFailure(e);
        }
    }

    private Translator getOrCreateTranslator(String sourceLanguage, String targetLanguage, String translatorKey) {
        if (translators.containsKey(translatorKey)) {
            return translators.get(translatorKey);
        }

        Translator translator = createTranslator(sourceLanguage, targetLanguage);
        translators.put(translatorKey, translator);
        return translator;
    }

    private Translator createTranslator(String sourceLanguage, String targetLanguage) {
        String sourceTranslateLanguage = TranslateLanguage.fromLanguageTag(sourceLanguage);
        if (sourceTranslateLanguage == null) {
            sourceTranslateLanguage = TranslateLanguage.ENGLISH;
        }

        String targetTranslateLanguage = TranslateLanguage.fromLanguageTag(targetLanguage);
        if (targetTranslateLanguage == null) {
            targetTranslateLanguage = TranslateLanguage.VIETNAMESE;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceTranslateLanguage)
                .setTargetLanguage(targetTranslateLanguage)
                .build();

        return Translation.getClient(options);
    }

    private interface ModelDownloadCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    private void downloadModelIfNeeded(Translator translator, String translatorKey, ModelDownloadCallback callback) {
        if (downloadedModels.contains(translatorKey)) {
            callback.onSuccess();
            return;
        }

        try {
            DownloadConditions conditions = new DownloadConditions.Builder()
                    .requireWifi()
                    .build();

            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(aVoid -> {
                        downloadedModels.add(translatorKey);
                        Log.d(TAG, "Translation model downloaded for " + translatorKey);
                        callback.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to download translation model for " + translatorKey, e);

                        // Try without WiFi requirement
                        DownloadConditions fallbackConditions = new DownloadConditions.Builder().build();
                        translator.downloadModelIfNeeded(fallbackConditions)
                                .addOnSuccessListener(aVoid -> {
                                    downloadedModels.add(translatorKey);
                                    Log.d(TAG, "Translation model downloaded (fallback) for " + translatorKey);
                                    callback.onSuccess();
                                })
                                .addOnFailureListener(fallbackException -> {
                                    Log.e(TAG, "Failed to download translation model (fallback) for " + translatorKey, fallbackException);
                                    callback.onFailure(fallbackException);
                                });
                    });

        } catch (Exception e) {
            Log.e(TAG, "Exception during model download", e);
            callback.onFailure(e);
        }
    }

    private boolean isValidInput(String text) {
        return text != null &&
                !text.trim().isEmpty() &&
                text.length() <= MAX_TEXT_LENGTH &&
                !containsSuspiciousContent(text);
    }

    private boolean containsSuspiciousContent(String text) {
        String[] suspiciousPatterns = {
                "<script", "javascript:", "data:", "vbscript:"
        };

        String lowerText = text.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerText.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        android.net.Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        if (networkCapabilities == null) {
            return false;
        }

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private Exception handleTranslationException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }

        if (message.toLowerCase().contains("network") || message.toLowerCase().contains("internet")) {
            return new NetworkException("Network error: " + message);
        } else if (message.toLowerCase().contains("timeout")) {
            return new TranslationException("Translation timed out. Please try again.");
        } else if (message.toLowerCase().contains("model")) {
            return new TranslationException("Translation model not available. Please check your connection.");
        } else {
            return new TranslationException("Translation failed: " + (message.isEmpty() ? "Unknown error" : message));
        }
    }

    public void closeTranslators() {
        try {
            for (Translator translator : translators.values()) {
                try {
                    translator.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing translator", e);
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