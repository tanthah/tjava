package com.example.translator.services;

import android.graphics.Bitmap;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class TextRecognitionService {

    private static final String TAG = "TextRecognitionService";
    private static final int MIN_TEXT_LENGTH = 1;
    private static final int MAX_TEXT_LENGTH = 5000;

    private TextRecognizer textRecognizer;

    public TextRecognitionService() {
        try {
            // Use Latin script recognizer which works better for most languages
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Log.d(TAG, "TextRecognitionService initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TextRecognitionService", e);
        }
    }

    public interface TextRecognitionCallback {
        void onSuccess(String recognizedText);
        void onFailure(Exception exception);
    }

    public void recognizeTextFromBitmap(Bitmap bitmap, TextRecognitionCallback callback) {
        if (callback == null) {
            Log.e(TAG, "Callback is null");
            return;
        }

        if (!isValidBitmap(bitmap)) {
            Log.w(TAG, "Invalid bitmap provided");
            callback.onFailure(new IllegalArgumentException("Invalid bitmap"));
            return;
        }

        try {
            Log.d(TAG, "Creating InputImage from bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizeTextFromImage(image, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error creating InputImage from bitmap", e);
            callback.onFailure(e);
        }
    }

    public void recognizeTextFromImage(InputImage inputImage, TextRecognitionCallback callback) {
        if (callback == null) {
            Log.e(TAG, "Callback is null");
            return;
        }

        if (inputImage == null) {
            Log.e(TAG, "InputImage is null");
            callback.onFailure(new IllegalArgumentException("InputImage is null"));
            return;
        }

        try {
            Log.d(TAG, "Starting text recognition process...");

            Task<Text> task = textRecognizer.process(inputImage);

            task.addOnSuccessListener(result -> {
                try {
                    String recognizedText = result.getText();
                    Log.d(TAG, "Text recognition completed");
                    Log.d(TAG, "Raw recognized text: '" + recognizedText + "'");

                    if (recognizedText == null || recognizedText.trim().isEmpty()) {
                        Log.d(TAG, "No text detected in image");
                        callback.onFailure(new RuntimeException("No text detected"));
                        return;
                    }

                    String cleanedText = cleanupRecognizedText(recognizedText);
                    Log.d(TAG, "Cleaned text: '" + cleanedText + "'");

                    if (cleanedText.length() < MIN_TEXT_LENGTH) {
                        Log.d(TAG, "Detected text too short: " + cleanedText.length() + " chars");
                        callback.onFailure(new RuntimeException("Detected text too short"));
                        return;
                    }

                    if (cleanedText.length() > MAX_TEXT_LENGTH) {
                        Log.w(TAG, "Detected text too long, truncating: " + cleanedText.length() + " chars");
                        cleanedText = cleanedText.substring(0, MAX_TEXT_LENGTH);
                    }

                    Log.d(TAG, "Text recognition successful: " + cleanedText.length() + " chars");
                    callback.onSuccess(cleanedText);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing recognition result", e);
                    callback.onFailure(e);
                }

            }).addOnFailureListener(e -> {
                Log.e(TAG, "Text recognition failed", e);
                String errorMessage = handleRecognitionError(e);
                callback.onFailure(new RuntimeException(errorMessage, e));
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in text recognition", e);
            callback.onFailure(e);
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
            Log.w(TAG, "Bitmap very large: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            // Don't reject, but log warning
        }

        Log.d(TAG, "Bitmap is valid: " + bitmap.getWidth() + "x" + bitmap.getHeight() +
                ", Config: " + bitmap.getConfig() + ", Bytes: " + bitmap.getByteCount());
        return true;
    }

    private String cleanupRecognizedText(String text) {
        if (text == null) return "";

        return text
                .trim()
                .replaceAll("\\s+", " ") // Replace multiple whitespaces with single space
                .replaceAll("[\\r\\n]+", "\n") // Normalize line breaks
                .replaceAll("\\n{3,}", "\n\n"); // Limit consecutive line breaks
    }

    private String handleRecognitionError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = exception.getClass().getSimpleName();
        }

        Log.e(TAG, "Recognition error details: " + message);

        if (message.toLowerCase().contains("timeout")) {
            return "Text recognition timed out";
        } else if (message.toLowerCase().contains("memory")) {
            return "Memory error during text recognition";
        } else if (message.toLowerCase().contains("network")) {
            return "Network error during text recognition";
        } else if (message.toLowerCase().contains("service")) {
            return "Text recognition service unavailable";
        } else {
            return "Text recognition failed: " + message;
        }
    }

    public void close() {
        try {
            if (textRecognizer != null) {
                textRecognizer.close();
                Log.d(TAG, "TextRecognitionService closed successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing TextRecognitionService", e);
        }
    }
}