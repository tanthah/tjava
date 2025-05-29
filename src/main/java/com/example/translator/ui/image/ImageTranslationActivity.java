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
    }

    private void setupViewModel() {
        TranslatorApplication application = (TranslatorApplication) getApplication();
        ImageTranslationViewModel.ImageTranslationViewModelFactory factory = new ImageTranslationViewModel.ImageTranslationViewModelFactory(
                application.getUserRepository(),
                application.getLanguageRepository(),
                this
        );
        viewModel = new ViewModelProvider(this, factory).get(ImageTranslationViewModel.class);
    }

    private void setupClickListeners() {
        btnSelectImage.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        btnTakePhoto.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                openCamera();
            } else {
                requestCameraPermission();
            }
        });

        btnConfirmCrop.setOnClickListener(v -> confirmCrop());

        btnRetake.setOnClickListener(v -> showImageSelectionMode());

        btnTranslate.setOnClickListener(v -> translateImage());

        // New speech buttons
        btnSpeakDetected.setOnClickListener(v -> {
            String sourceLanguage = getSelectedSourceLanguageCode();
            viewModel.speakDetectedText(sourceLanguage);
        });

        btnSpeakTranslated.setOnClickListener(v -> {
            String targetLanguage = getSelectedTargetLanguageCode();
            viewModel.speakTranslatedText(targetLanguage);
        });

        btnSpeakSummary.setOnClickListener(v -> {
            String targetLanguage = getSelectedTargetLanguageCode();
            viewModel.speakSummary(targetLanguage);
        });

        // Summary button
        btnSummarize.setOnClickListener(v -> showSummarizationDialog());

        // Speech settings button
        btnSpeechSettings.setOnClickListener(v -> showSpeechSettingsDialog());

        setupImageTouchListeners();
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
            List<com.example.translator.data.model.Language> cameraLanguages = languages.stream()
                    .filter(lang -> lang.getSupportsCameraTranslation())
                    .collect(java.util.stream.Collectors.toList());
            setupLanguageSpinners(cameraLanguages);
        });

        viewModel.detectedText.observe(this, text -> {
            String displayText = text != null ? text : "No text detected";
            tvDetectedText.setText(displayText);
            tvDetectedText.setVisibility(text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
            btnSpeakDetected.setVisibility(text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
            btnSummarize.setVisibility(text != null && !text.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.translationResult.observe(this, result -> {
            String displayText = result != null ? result : "Translation will appear here";
            tvTranslatedText.setText(displayText);
            tvTranslatedText.setVisibility(result != null && !result.isEmpty() ? View.VISIBLE : View.GONE);
            btnSpeakTranslated.setVisibility(result != null && !result.isEmpty() ? View.VISIBLE : View.GONE);

            // Show results section when translation is available
            if (result != null && !result.isEmpty()) {
                scrollResults.setVisibility(View.VISIBLE);
            }
        });

        viewModel.summaryResult.observe(this, summary -> {
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
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnTranslate.setEnabled(!isLoading && (croppedBitmap != null || selectedImageBitmap != null));
            btnConfirmCrop.setEnabled(!isLoading);
        });

        viewModel.isSummarizing.observe(this, isSummarizing -> {
            progressSummarization.setVisibility(isSummarizing ? View.VISIBLE : View.GONE);
            btnSummarize.setEnabled(!isSummarizing);
        });

        viewModel.errorMessage.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.speechRate.observe(this, rate -> {
            // Update speech settings UI if needed
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
        if (languages.isEmpty()) return;

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

        if (defaultSourceIndex != -1) spinnerSourceLanguage.setSelection(defaultSourceIndex);
        if (defaultTargetIndex != -1) spinnerTargetLanguage.setSelection(defaultTargetIndex);
    }

    private String getSelectedSourceLanguageCode() {
        try {
            LanguageSpinnerItem item = (LanguageSpinnerItem) spinnerSourceLanguage.getSelectedItem();
            return item != null ? item.language.getLanguageCode() : "en";
        } catch (Exception e) {
            return "en";
        }
    }

    private String getSelectedTargetLanguageCode() {
        try {
            LanguageSpinnerItem item = (LanguageSpinnerItem) spinnerTargetLanguage.getSelectedItem();
            return item != null ? item.language.getLanguageCode() : "vi";
        } catch (Exception e) {
            return "vi";
        }
    }

    private void showImageSelectionMode() {
        layoutImageSelection.setVisibility(View.VISIBLE);
        layoutImagePreview.setVisibility(View.GONE);
        scrollResults.setVisibility(View.GONE);

        // Clear previous data
        selectedImageBitmap = null;
        croppedBitmap = null;
        tvDetectedText.setText("");
        tvTranslatedText.setText("");
        tvSummary.setText("");
        layoutSummary.setVisibility(View.GONE);
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
    }

    private void fitImageToView(Bitmap bitmap) {
        ivSelectedImage.post(() -> {
            float viewWidth = ivSelectedImage.getWidth();
            float viewHeight = ivSelectedImage.getHeight();
            float bitmapWidth = bitmap.getWidth();
            float bitmapHeight = bitmap.getHeight();

            if (viewWidth == 0f || viewHeight == 0f) {
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
        });
    }

    // Continue with remaining methods...
    private void openGallery() {
        try {
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
                RectF cropRect = cropOverlay.getCropRect();
                croppedBitmap = cropBitmap(selectedImageBitmap, cropRect);
                Toast.makeText(this, "Area selected. Tap translate to process.", Toast.LENGTH_SHORT).show();
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

            if (width > 0 && height > 0) {
                return Bitmap.createBitmap(bitmap, x, y, width, height);
            } else {
                return bitmap; // Return original if crop area is invalid
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating cropped bitmap", e);
            return bitmap; // Return original bitmap on error
        }
    }

    private void translateImage() {
        Bitmap bitmapToProcess = croppedBitmap != null ? croppedBitmap : selectedImageBitmap;

        if (bitmapToProcess != null) {
            String sourceLanguage = getSelectedSourceLanguageCode();
            String targetLanguage = getSelectedTargetLanguageCode();

            executor.execute(() -> {
                viewModel.processImage(bitmapToProcess, sourceLanguage, targetLanguage);
            });
        } else {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_GALLERY:
                    if (data != null && data.getData() != null) {
                        Uri uri = data.getData();
                        try {
                            Bitmap bitmap = loadBitmapFromUri(uri);
                            if (bitmap != null) {
                                showImagePreviewMode(bitmap);
                            } else {
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
                                showImagePreviewMode(bitmap);
                            } else {
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
            return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap from URI", e);
            return null;
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private void requestStoragePermission() {
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

        switch (requestCode) {
            case CAMERA_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                }
                break;
            case STORAGE_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery();
                } else {
                    Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        if (executor != null) {
            executor.shutdown();
        }
    }
}