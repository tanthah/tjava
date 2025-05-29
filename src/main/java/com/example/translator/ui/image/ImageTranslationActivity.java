package com.example.translator.ui.image;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.example.translator.R;
import com.example.translator.TranslatorApplication;
import com.example.translator.ui.camera.CropOverlayView;
import com.example.translator.ui.text.LanguageSpinnerAdapter;
import com.example.translator.ui.text.LanguageSpinnerAdapter.LanguageSpinnerItem;
import com.example.translator.services.SpeechService;
import com.example.translator.services.TextSummarizationService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageTranslationActivity extends AppCompatActivity {

    private static final String TAG = "ImageTranslationActivity";
    private static final int REQUEST_IMAGE_GALLERY = 1001;
    private static final int REQUEST_IMAGE_CAMERA = 1002;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;

    private ImageTranslationViewModel viewModel;
    private ExecutorService executor;

    // Image selection views
    private LinearLayout layoutImageSelection;
    private MaterialButton btnSelectImage;
    private MaterialButton btnTakePhoto;

    // Image preview views
    private LinearLayout layoutImagePreview;
    private ImageView ivSelectedImage;
    private CropOverlayView cropOverlay;
    private MaterialButton btnConfirmCrop;
    private MaterialButton btnRetake;

    // Common views
    private Spinner spinnerSourceLanguage;
    private Spinner spinnerTargetLanguage;
    private TextView tvDetectedText;
    private TextView tvTranslatedText;
    private TextView tvSummary;
    private LinearLayout layoutSummary;
    private MaterialButton btnTranslate;
    private ProgressBar progressBar;
    private ProgressBar progressSummarization;
    private ScrollView scrollResults;

    // New speech and summary controls
    private MaterialButton btnSpeakDetected;
    private MaterialButton btnSpeakTranslated;
    private MaterialButton btnSpeakSummary;
    private MaterialButton btnSummarize;
    private MaterialButton btnSpeechSettings;

    private Bitmap selectedImageBitmap;
    private Bitmap croppedBitmap;

    // Touch handling for zoom and pan
    private ScaleGestureDetector scaleGestureDetector;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;

    // Image transformation matrix
    private Matrix imageMatrix = new Matrix();
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_translation);

        executor = Executors.newFixedThreadPool(4);
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        initializeViews();
        setupViewModel();
        setupClickListeners();
        observeViewModel();

        showImageSelectionMode();

        Log.d(TAG, "ImageTranslationActivity initialized");
    }

    private void initializeViews() {
        // Image selection views
        layoutImageSelection = findViewById(R.id.layout_image_selection);
        btnSelectImage = findViewById(R.id.btn_select_image);
        btnTakePhoto = findViewById(R.id.btn_take_photo);

        // Image preview views
        layoutImagePreview = findViewById(R.id.layout_image_preview);
        ivSelectedImage = findViewById(R.id.iv_selected_image);
        cropOverlay = findViewById(R.id.crop_overlay);
        btnConfirmCrop = findViewById(R.id.btn_confirm_crop);
        btnRetake = findViewById(R.id.btn_retake);

        // Common views
        spinnerSourceLanguage = findViewById(R.id.spinner_source_language);
        spinnerTargetLanguage = findViewById(R.id.spinner_target_language);
        tvDetectedText = findViewById(R.id.tv_detected_text);
        tvTranslatedText = findViewById(R.id.tv_translated_text);
        tvSummary = findViewById(R.id.tv_summary);
        layoutSummary = findViewById(R.id.layout_summary);
        btnTranslate = findViewById(R.id.btn_translate);
        progressBar = findViewById(R.id.progress_bar);
        progressSummarization = findViewById(R.id.progress_summarization);
        scrollResults = findViewById(R.id.scroll_results);

        // New speech and summary controls
        btnSpeakDetected = findViewById(R.id.btn_speak_detected);
        btnSpeakTranslated = findViewById(R.id.btn_speak_translated);
        btnSpeakSummary = findViewById(R.id.btn_speak_summary);
        btnSummarize = findViewById(R.id.btn_summarize);
        btnSpeechSettings = findViewById(R.id.btn_speech_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Image Translation");
        }

        // Set ImageView scaleType to matrix for manual control
        ivSelectedImage.setScaleType(ImageView.ScaleType.MATRIX);

        Log.d(TAG, "Views initialized");
    }

    private void setupViewModel() {
        TranslatorApplication application = (TranslatorApplication) getApplication();
        ImageTranslationViewModel.ImageTranslationViewModelFactory factory =
                new ImageTranslationViewModel.ImageTranslationViewModelFactory(
                        application.getUserRepository(),
                        application.getLanguageRepository(),
                        this
                );
        viewModel = new ViewModelProvider(this, factory).get(ImageTranslationViewModel.class);
        Log.d(TAG, "ViewModel setup completed");
    }

    private void setupClickListeners() {
        btnSelectImage.setOnClickListener(v -> {
            Log.d(TAG, "Select image button clicked");
            if (checkStoragePermission()) {
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        btnTakePhoto.setOnClickListener(v -> {
            Log.d(TAG, "Take photo button clicked");
            if (checkCameraPermission()) {
                openCamera();
            } else {
                requestCameraPermission();
            }
        });

        btnConfirmCrop.setOnClickListener(v -> {
            Log.d(TAG, "Confirm crop button clicked");
            confirmCrop();
        });

        btnRetake.setOnClickListener(v -> {
            Log.d(TAG, "Retake button clicked");
            showImageSelectionMode();
        });

        btnTranslate.setOnClickListener(v -> {
            Log.d(TAG, "Translate button clicked");
            translateImage();
        });

        // New speech buttons
        btnSpeakDetected.setOnClickListener(v -> {
            String sourceLanguage = getSelectedSourceLanguageCode();
            Log.d(TAG, "Speak detected text in " + sourceLanguage);
            viewModel.speakDetectedText(sourceLanguage);
        });

        btnSpeakTranslated.setOnClickListener(v -> {
            String targetLanguage = getSelectedTargetLanguageCode();
            Log.d(TAG, "Speak translated text in " + targetLanguage);
            viewModel.speakTranslatedText(targetLanguage);
        });

        btnSpeakSummary.setOnClickListener(v -> {
            String targetLanguage = getSelectedTargetLanguageCode();
            Log.d(TAG, "Speak summary in " + targetLanguage);
            viewModel.speakSummary(targetLanguage);
        });

        // Summary button
        btnSummarize.setOnClickListener(v -> {
            Log.d(TAG, "Summarize button clicked");
            showSummarizationDialog();
        });

        // Speech settings button
        btnSpeechSettings.setOnClickListener(v -> {
            Log.d(TAG, "Speech settings button clicked");
            showSpeechSettingsDialog();
        });

        setupImageTouchListeners();
        Log.d(TAG, "Click listeners setup completed");
    }

    private void setupImageTouchListeners() {
        ivSelectedImage.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!scaleGestureDetector.isInProgress()) {
                        float deltaX = event.getX() - lastTouchX;
                        float deltaY = event.getY() - lastTouchY;

                        translateX += deltaX;
                        translateY += deltaY;

                        updateImageMatrix();

                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return true;

                default:
                    return false;
            }
        });
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

            updateImageMatrix();
            return true;
        }
    }

    private void updateImageMatrix() {
        imageMatrix.reset();
        imageMatrix.postScale(scaleFactor, scaleFactor);
        imageMatrix.postTranslate(translateX, translateY);
        ivSelectedImage.setImageMatrix(imageMatrix);
    }

    private void observeViewModel() {
        viewModel.supportedLanguages.observe(this, languages -> {
            Log.d(TAG, "Supported languages updated: " + (languages != null ? languages.size() : 0));
            List<com.example.translator.data.model.Language> cameraLanguages = languages.stream()
                    .filter(lang -> lang.getSupportsCameraTranslation() || lang.getSupportsTextTranslation())
                    .collect(java.util.stream.Collectors.toList());
            setupLanguageSpinners(cameraLanguages);
        });

        viewModel.detectedText.observe(this, text -> {
            Log.d(TAG, "Detected text updated: " + (text != null ? text.length() + " chars" : "null"));
            String displayText = (text != null && !text.isEmpty()) ? text : "No text detected";
            tvDetectedText.setText(displayText);
            tvDetectedText.setVisibility(View.VISIBLE);
            btnSpeakDetected.setVisibility((text != null && !text.isEmpty()) ? View.VISIBLE : View.GONE);
            btnSummarize.setVisibility((text != null && !text.isEmpty()) ? View.VISIBLE : View.GONE);
        });

        viewModel.translationResult.observe(this, result -> {
            Log.d(TAG, "Translation result updated: " + (result != null ? result.length() + " chars" : "null"));
            String displayText = (result != null && !result.isEmpty()) ? result : "Translation will appear here";
            tvTranslatedText.setText(displayText);
            tvTranslatedText.setVisibility(View.VISIBLE);
            btnSpeakTranslated.setVisibility((result != null && !result.isEmpty()) ? View.VISIBLE : View.GONE);

            // Show results section when translation is available
            if (result != null && !result.isEmpty()) {
                scrollResults.setVisibility(View.VISIBLE);
            }
        });

        viewModel.summaryResult.observe(this, summary -> {
            Log.d(TAG, "Summary result updated: " + (summary != null ? summary.length() + " chars" : "null"));
            if (summary != null && !summary.isEmpty()) {
                tvSummary.setText(summary);
                layoutSummary.setVisibility(View.VISIBLE);
                btnSpeakSummary.setVisibility(View.VISIBLE);
            } else {
                layoutSummary.setVisibility(View.GONE);
                btnSpeakSummary.setVisibility(View.GONE);
            }
        });

        viewModel.isLoading.observe(this, isLoading -> {
            Log.d(TAG, "Loading state: " + isLoading);
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnTranslate.setEnabled(!isLoading && (croppedBitmap != null || selectedImageBitmap != null));
            btnConfirmCrop.setEnabled(!isLoading);
        });

        viewModel.isSummarizing.observe(this, isSummarizing -> {
            Log.d(TAG, "Summarizing state: " + isSummarizing);
            progressSummarization.setVisibility(isSummarizing ? View.VISIBLE : View.GONE);
            btnSummarize.setEnabled(!isSummarizing);
        });

        viewModel.errorMessage.observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "Error message: " + error);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.speechRate.observe(this, rate -> {
            Log.d(TAG, "Speech rate updated: " + rate);
        });
    }

    private void showSummarizationDialog() {
        String[] options = {
                "Brief Summary (1-2 sentences)",
                "Detailed Summary (3-5 sentences)",
                "Key Points (Bullet format)",
                "Key Phrases"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Summary Type")
                .setItems(options, (dialog, which) -> {
                    TextSummarizationService.SummaryType summaryType;
                    switch (which) {
                        case 0:
                            summaryType = TextSummarizationService.SummaryType.BRIEF;
                            break;
                        case 1:
                            summaryType = TextSummarizationService.SummaryType.DETAILED;
                            break;
                        case 2:
                            summaryType = TextSummarizationService.SummaryType.BULLET_POINTS;
                            break;
                        case 3:
                            summaryType = TextSummarizationService.SummaryType.KEY_PHRASES;
                            break;
                        default:
                            summaryType = TextSummarizationService.SummaryType.BRIEF;
                    }

                    String targetLanguage = getSelectedTargetLanguageCode();
                    Log.d(TAG, "Starting summarization with type: " + summaryType + ", language: " + targetLanguage);
                    viewModel.summarizeDetectedText(summaryType, targetLanguage);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSpeechSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_speech_settings, null);
        Slider speedSlider = dialogView.findViewById(R.id.slider_speech_speed);
        TextView speedText = dialogView.findViewById(R.id.tv_speed_text);

        // Set current speed
        Float currentRate = viewModel.speechRate.getValue();
        float rate = currentRate != null ? currentRate : SpeechService.SPEED_NORMAL;
        speedSlider.setValue(rate);
        speedText.setText("Speed: " + viewModel.getSpeechRateText(rate));

        speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            speedText.setText("Speed: " + viewModel.getSpeechRateText(value));
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Speech Settings")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialog, id) -> {
                    viewModel.setSpeechRate(speedSlider.getValue());
                    Toast.makeText(this, "Speech speed updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupLanguageSpinners(List<com.example.translator.data.model.Language> languages) {
        if (languages == null || languages.isEmpty()) {
            Log.w(TAG, "No languages available for spinners");
            return;
        }

        Log.d(TAG, "Setting up language spinners with " + languages.size() + " languages");
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(this, languages);

        spinnerSourceLanguage.setAdapter(adapter);
        spinnerTargetLanguage.setAdapter(adapter);

        // Set default selections
        int defaultSourceIndex = -1;
        int defaultTargetIndex = -1;

        for (int i = 0; i < languages.size(); i++) {
            if ("en".equals(languages.get(i).getLanguageCode())) {
                defaultSourceIndex = i;
            }
            if ("vi".equals(languages.get(i).getLanguageCode())) {
                defaultTargetIndex = i;
            }
        }

        if (defaultSourceIndex != -1) {
            spinnerSourceLanguage.setSelection(defaultSourceIndex);
            Log.d(TAG, "Source language set to English (index " + defaultSourceIndex + ")");
        }
        if (defaultTargetIndex != -1) {
            spinnerTargetLanguage.setSelection(defaultTargetIndex);
            Log.d(TAG, "Target language set to Vietnamese (index " + defaultTargetIndex + ")");
        }
    }

    private String getSelectedSourceLanguageCode() {
        try {
            LanguageSpinnerItem item = (LanguageSpinnerItem) spinnerSourceLanguage.getSelectedItem();
            String code = item != null ? item.language.getLanguageCode() : "en";
            Log.d(TAG, "Selected source language: " + code);
            return code;
        } catch (Exception e) {
            Log.e(TAG, "Error getting source language code", e);
            return "en";
        }
    }

    private String getSelectedTargetLanguageCode() {
        try {
            LanguageSpinnerItem item = (LanguageSpinnerItem) spinnerTargetLanguage.getSelectedItem();
            String code = item != null ? item.language.getLanguageCode() : "vi";
            Log.d(TAG, "Selected target language: " + code);
            return code;
        } catch (Exception e) {
            Log.e(TAG, "Error getting target language code", e);
            return "vi";
        }
    }

    private void showImageSelectionMode() {
        Log.d(TAG, "Showing image selection mode");
        layoutImageSelection.setVisibility(View.VISIBLE);
        layoutImagePreview.setVisibility(View.GONE);
        scrollResults.setVisibility(View.GONE);

        // Clear previous data
        selectedImageBitmap = null;
        croppedBitmap = null;
        viewModel.clearResults();
        btnTranslate.setEnabled(false);

        // Reset matrix values
        scaleFactor = 1.0f;
        translateX = 0f;
        translateY = 0f;
        imageMatrix.reset();

        // Hide speech buttons
        btnSpeakDetected.setVisibility(View.GONE);
        btnSpeakTranslated.setVisibility(View.GONE);
        btnSpeakSummary.setVisibility(View.GONE);
        btnSummarize.setVisibility(View.GONE);
    }

    private void showImagePreviewMode(Bitmap bitmap) {
        Log.d(TAG, "Showing image preview mode with bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        layoutImageSelection.setVisibility(View.GONE);
        layoutImagePreview.setVisibility(View.VISIBLE);
        scrollResults.setVisibility(View.GONE);

        selectedImageBitmap = bitmap;

        // Set image and fit to ImageView
        ivSelectedImage.setImageBitmap(bitmap);
        fitImageToView(bitmap);

        // Show crop overlay
        cropOverlay.setVisibility(View.VISIBLE);
        btnTranslate.setEnabled(true);

        Log.d(TAG, "Image preview mode setup completed");
    }

    private void fitImageToView(Bitmap bitmap) {
        ivSelectedImage.post(() -> {
            float viewWidth = ivSelectedImage.getWidth();
            float viewHeight = ivSelectedImage.getHeight();
            float bitmapWidth = bitmap.getWidth();
            float bitmapHeight = bitmap.getHeight();

            if (viewWidth == 0f || viewHeight == 0f) {
                Log.w(TAG, "ImageView dimensions not ready, skipping fit");
                return;
            }

            // Calculate scale to fit image in view
            float scaleX = viewWidth / bitmapWidth;
            float scaleY = viewHeight / bitmapHeight;
            scaleFactor = Math.min(scaleX, scaleY);

            // Center the image
            translateX = (viewWidth - bitmapWidth * scaleFactor) / 2;
            translateY = (viewHeight - bitmapHeight * scaleFactor) / 2;

            updateImageMatrix();
            Log.d(TAG, "Image fitted to view with scale: " + scaleFactor);
        });
    }

    private void openGallery() {
        try {
            Log.d(TAG, "Opening gallery");
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivityForResult(intent, REQUEST_IMAGE_GALLERY);
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(this, "Failed to open gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        try {
            Log.d(TAG, "Opening camera");
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAMERA);
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmCrop() {
        if (selectedImageBitmap != null) {
            try {
                Log.d(TAG, "Confirming crop");
                RectF cropRect = cropOverlay.getCropRect();
                croppedBitmap = cropBitmap(selectedImageBitmap, cropRect);
                Toast.makeText(this, "Area selected. Tap translate to process.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Crop confirmed, cropped bitmap: " +
                        (croppedBitmap != null ? croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight() : "null"));
            } catch (Exception e) {
                Log.e(TAG, "Error cropping image", e);
                Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap cropBitmap(Bitmap bitmap, RectF cropRect) {
        try {
            // Get the current image matrix values
            float[] values = new float[9];
            imageMatrix.getValues(values);

            float scaleX = values[Matrix.MSCALE_X];
            float scaleY = values[Matrix.MSCALE_Y];
            float transX = values[Matrix.MTRANS_X];
            float transY = values[Matrix.MTRANS_Y];

            // Convert crop coordinates to bitmap coordinates
            int x = Math.max(0, (int) ((cropRect.left - transX) / scaleX));
            int y = Math.max(0, (int) ((cropRect.top - transY) / scaleY));
            int width = Math.min(bitmap.getWidth() - x, (int) (cropRect.width() / scaleX));
            int height = Math.min(bitmap.getHeight() - y, (int) (cropRect.height() / scaleY));

            Log.d(TAG, "Crop coordinates: x=" + x + ", y=" + y + ", width=" + width + ", height=" + height);

            if (width > 0 && height > 0) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, x, y, width, height);
                Log.d(TAG, "Cropped bitmap created: " + cropped.getWidth() + "x" + cropped.getHeight());
                return cropped;
            } else {
                Log.w(TAG, "Invalid crop area, returning original bitmap");
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating cropped bitmap", e);
            return bitmap;
        }
    }

    private void translateImage() {
        Bitmap bitmapToProcess = croppedBitmap != null ? croppedBitmap : selectedImageBitmap;

        if (bitmapToProcess == null) {
            Log.e(TAG, "No bitmap available for translation");
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        String sourceLanguage = getSelectedSourceLanguageCode();
        String targetLanguage = getSelectedTargetLanguageCode();

        Log.d(TAG, "Starting image translation");
        Log.d(TAG, "Bitmap to process: " + bitmapToProcess.getWidth() + "x" + bitmapToProcess.getHeight());
        Log.d(TAG, "Languages: " + sourceLanguage + " -> " + targetLanguage);

        executor.execute(() -> {
            viewModel.processImage(bitmapToProcess, sourceLanguage, targetLanguage);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_GALLERY:
                    if (data != null && data.getData() != null) {
                        Uri uri = data.getData();
                        Log.d(TAG, "Image selected from gallery: " + uri);
                        try {
                            Bitmap bitmap = loadBitmapFromUri(uri);
                            if (bitmap != null) {
                                Log.d(TAG, "Loaded bitmap from gallery: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                showImagePreviewMode(bitmap);
                            } else {
                                Log.e(TAG, "Failed to load bitmap from URI");
                                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading image from gallery", e);
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;

                case REQUEST_IMAGE_CAMERA:
                    try {
                        Bundle extras = data != null ? data.getExtras() : null;
                        if (extras != null) {
                            Bitmap bitmap = (Bitmap) extras.get("data");
                            if (bitmap != null) {
                                Log.d(TAG, "Captured bitmap from camera: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                showImagePreviewMode(bitmap);
                            } else {
                                Log.e(TAG, "Camera returned null bitmap");
                                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing camera result", e);
                        Toast.makeText(this, "Failed to process camera image", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            Log.d(TAG, "Loading bitmap from URI: " + uri);
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            Log.d(TAG, "Successfully loaded bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "IOException loading bitmap from URI", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap from URI", e);
            return null;
        }
    }

    private boolean checkCameraPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Camera permission check: " + hasPermission);
        return hasPermission;
    }

    private boolean checkStoragePermission() {
        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        Log.d(TAG, "Storage permission check: " + hasPermission);
        return hasPermission;
    }

    private void requestCameraPermission() {
        Log.d(TAG, "Requesting camera permission");
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private void requestStoragePermission() {
        Log.d(TAG, "Requesting storage permission");
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);

        switch (requestCode) {
            case CAMERA_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted");
                    openCamera();
                } else {
                    Log.d(TAG, "Camera permission denied");
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                }
                break;
            case STORAGE_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permission granted");
                    openGallery();
                } else {
                    Log.d(TAG, "Storage permission denied");
                    Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused, stopping speech");
        viewModel.stopSpeaking();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}