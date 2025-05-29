package com.example.translator.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayList;
import java.util.Locale;

public class SpeechService {

    private static final String TAG = "SpeechService";
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final String TTS_UTTERANCE_ID = "TTS_UTTERANCE";

    // Speed constants
    public static final float SPEED_VERY_SLOW = 0.5f;
    public static final float SPEED_SLOW = 0.75f;
    public static final float SPEED_NORMAL = 1.0f;
    public static final float SPEED_FAST = 1.25f;
    public static final float SPEED_VERY_FAST = 1.5f;

    private Context context;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private boolean isInitialized = false;
    private boolean isSpeaking = false;
    private boolean isListening = false;

    // Speech rate settings
    private float speechRate = SPEED_NORMAL;
    private float speechPitch = 1.0f;

    // Callback interfaces
    public interface InitializationCallback {
        void onComplete(boolean success);
    }

    public interface SpeechRecognitionCallback {
        void onReady();
        void onSpeaking();
        void onRmsChanged(float rmsdB);
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(int errorCode);
    }

    public SpeechService(Context context) {
        this.context = context;
    }

    public void initializeTextToSpeech(InitializationCallback callback) {
        try {
            // Clean up existing instance
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
            }

            textToSpeech = new TextToSpeech(context, status -> {
                isInitialized = status == TextToSpeech.SUCCESS;

                if (isInitialized) {
                    setupTTSListener();
                    // Set default speech rate and pitch
                    textToSpeech.setSpeechRate(speechRate);
                    textToSpeech.setPitch(speechPitch);
                    Log.d(TAG, "TextToSpeech initialized successfully");
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed with status: " + status);
                }

                callback.onComplete(isInitialized);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TextToSpeech", e);
            callback.onComplete(false);
        }
    }

    private void setupTTSListener() {
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true;
                Log.d(TAG, "TTS started speaking");
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;
                Log.d(TAG, "TTS finished speaking");
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false;
                Log.e(TAG, "TTS error occurred");
            }
        });
    }

    public void setSpeechRate(float rate) {
        speechRate = Math.max(0.1f, Math.min(rate, 3.0f));
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(speechRate);
        }
    }

    public void setSpeechPitch(float pitch) {
        speechPitch = Math.max(0.1f, Math.min(pitch, 2.0f));
        if (textToSpeech != null) {
            textToSpeech.setPitch(speechPitch);
        }
    }

    public float getSpeechRate() {
        return speechRate;
    }

    public float getSpeechPitch() {
        return speechPitch;
    }

    public void speakText(String text, String languageCode) {
        speakText(text, languageCode, speechRate);
    }

    public void speakText(String text, String languageCode, float rate) {
        if (!isInitialized) {
            Log.w(TAG, "TextToSpeech not initialized");
            return;
        }

        if (text == null || text.trim().isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            Log.w(TAG, "Invalid text for TTS: length=" + (text != null ? text.length() : 0));
            return;
        }

        try {
            Locale locale = Locale.forLanguageTag(languageCode);
            int result = textToSpeech.setLanguage(locale);

            switch (result) {
                case TextToSpeech.LANG_MISSING_DATA:
                case TextToSpeech.LANG_NOT_SUPPORTED:
                    Log.w(TAG, "Language not supported: " + languageCode + ", using default");
                    textToSpeech.setLanguage(Locale.getDefault());
                    break;
                case TextToSpeech.LANG_AVAILABLE:
                case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                    Log.d(TAG, "Language set successfully: " + languageCode);
                    break;
            }

            // Stop any ongoing speech
            stopSpeaking();

            // Set speech rate for this utterance
            textToSpeech.setSpeechRate(rate);

            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_UTTERANCE_ID);

            int speakResult = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, TTS_UTTERANCE_ID);

            if (speakResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Failed to start TTS with result: " + speakResult);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in speakText", e);
        }
    }

    public void stopSpeaking() {
        try {
            if (isSpeaking && textToSpeech != null) {
                textToSpeech.stop();
                isSpeaking = false;
                Log.d(TAG, "TTS stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping TTS", e);
        }
    }

    public void startSpeechRecognition(String languageCode, SpeechRecognitionCallback callback) {
        try {
            if (isListening) {
                stopSpeechRecognition();
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                callback.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
                return;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            isListening = true;

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Speech recognition ready");
                    callback.onReady();
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Speech recognition started");
                    callback.onSpeaking();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    callback.onRmsChanged(rmsdB);
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> results = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (results != null && !results.isEmpty()) {
                        String result = results.get(0);
                        if (result != null && !result.trim().isEmpty()) {
                            callback.onPartialResult(result);
                        }
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> resultList = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String finalResult = null;

                    if (resultList != null && !resultList.isEmpty()) {
                        finalResult = resultList.get(0);
                    }

                    if (finalResult != null && !finalResult.trim().isEmpty()) {
                        Log.d(TAG, "Speech recognition completed: " + finalResult);
                        callback.onFinalResult(finalResult);
                    } else {
                        Log.w(TAG, "Speech recognition returned empty result");
                        callback.onError(SpeechRecognizer.ERROR_NO_MATCH);
                    }

                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getSpeechErrorMessage(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage + " (code: " + error + ")");
                    callback.onError(error);
                    isListening = false;
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Not needed for this implementation
                }

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Speech recognition ended");
                    isListening = false;
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Handle speech recognition events
                    Log.d(TAG, "Speech recognition event: " + eventType);
                }
            });

            speechRecognizer.startListening(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            callback.onError(SpeechRecognizer.ERROR_CLIENT);
            isListening = false;
        }
    }

    public void stopSpeechRecognition() {
        try {
            if (isListening && speechRecognizer != null) {
                speechRecognizer.stopListening();
                isListening = false;
                Log.d(TAG, "Speech recognition stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping speech recognition", e);
        }
    }

    private String getSpeechErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No recognition result matched";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server sends error status";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error";
        }
    }

    public void release() {
        try {
            // Stop any ongoing operations
            stopSpeaking();
            stopSpeechRecognition();

            // Release TTS
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
                textToSpeech = null;
            }

            // Release speech recognizer
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }

            // Reset flags
            isInitialized = false;
            isSpeaking = false;
            isListening = false;

            Log.d(TAG, "SpeechService resources released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing SpeechService resources", e);
        }
    }
}