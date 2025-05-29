package com.example.translator.services;

import android.graphics.Bitmap;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;

public class TextRecognitionService {

    private static final String TAG = "TextRecognitionService";
    private static final long RECOGNITION_TIMEOUT = 15000L; // 15 seconds
    private static final int MIN_TEXT_LENGTH = 1;
    private static final int MAX_TEXT_LENGTH = 5000;

    private TextRecognizer textRecognizer;

    public TextRecognitionService() {
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public interface TextRecognitionCallback {
        void onSuccess(String recognizedText);
        void onFailure(Exception exception);
    }

    public void recognizeTextFromBitmap(Bitmap bitmap, TextRecognitionCallback callback) {
        if (!isValidBitmap(bitmap)) {
            Log.w(TAG, "Invalid bitmap provided");
            callback.onFailure(new IllegalArgumentException("Invalid bitmap"));
            return;
        }

        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizeTextFromImage(image, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error creating InputImage from bitmap", e);
            callback.onFailure(e);
        }
    }

    public void recognizeTextFromImage(InputImage inputImage, TextRecognitionCallback callback) {
        try {
            Log.d(TAG, "Starting text recognition...");

            Task<Text> task = textRecognizer.process(inputImage);

            // Add timeout to the task
            Task<Text> timedTask = Tasks.call(() -> {
                try {
                    return Tasks.await(task, RECOGNITION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    throw new RuntimeException("Text recognition timed out", e);
                }
            });

            timedTask.addOnSuccessListener(result -> {
                String recognizedText = result.getText();

                if (recognizedText == null || recognizedText.trim().isEmpty()) {
                    Log.d(TAG, "No text detected in image");
                    callback.onFailure(new RuntimeException("No text detected"));
                    return;
                }

                if (recognizedText.length() < MIN_TEXT_LENGTH) {
                    Log.d(TAG, "Detected text too short: " + recognizedText.length() + " chars");
                    callback.onFailure(new RuntimeException("Detected text too short"));
                    return;
                }

                if (recognizedText.length() > MAX_TEXT_LENGTH) {
                    Log.w(TAG, "Detected text too long, truncating: " + recognizedText.length() + " chars");
                    recognizedText = recognizedText.substring(0, MAX_TEXT_LENGTH);
                }

                Log.d(TAG, "Text recognition successful: " + recognizedText.length() + " chars");
                String cleanedText = cleanupRecognizedText(recognizedText);
                callback.onSuccess(cleanedText);

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
        return bitmap != null &&
                !bitmap.isRecycled() &&
                bitmap.getWidth() > 0 &&
                bitmap.getHeight() > 0 &&
                bitmap.getWidth() <= 4096 &&
                bitmap.getHeight() <= 4096;
    }

    private String cleanupRecognizedText(String text) {
        return text
                .trim()
                .replaceAll("\\s+", " ") // Replace multiple whitespaces with single space
                .replaceAll("[\\r\\n]+", "\n") // Normalize line breaks
                .replaceAll("\\n{3,}", "\n\n"); // Limit consecutive line breaks
    }

    private String handleRecognitionError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }

        if (message.toLowerCase().contains("timeout")) {
            Log.e(TAG, "Text recognition timed out");
            return "Text recognition timed out";
        } else if (message.toLowerCase().contains("memory")) {
            Log.e(TAG, "Memory error during text recognition");
            return "Memory error during text recognition";
        } else if (message.toLowerCase().contains("network")) {
            Log.e(TAG, "Network error during text recognition");
            return "Network error during text recognition";
        } else {
            Log.e(TAG, "Unknown text recognition error: " + message);
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