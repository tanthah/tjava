package com.example.translator.ui.text;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.translator.R;
import com.example.translator.TranslatorApplication;
import com.example.translator.services.SpeechService;
import com.example.translator.ui.text.LanguageSpinnerAdapter.LanguageSpinnerItem;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextTranslationFragment extends Fragment {

    private TextTranslationViewModel viewModel;
    private SpeechService speechService;
    private ExecutorService executor;

    // Views
    private Spinner spinnerSourceLanguage;
    private Spinner spinnerTargetLanguage;
    private TextInputEditText etSourceText;
    private TextView tvTranslatedText;
    private MaterialButton btnTranslate;
    private MaterialButton btnVoiceInput;
    private MaterialButton btnSpeakSource;
    private MaterialButton btnSpeak;
    private MaterialButton btnCopy;
    private ImageButton btnSwapLanguages;
    private ProgressBar progressBar;

    // Voice recognition
    private boolean isListening = false;
    private SpeechService.SpeechRecognitionCallback currentSpeechCallback;

    private static final int RECORD_AUDIO_PERMISSION_CODE = 101;

    public TextTranslationFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_text_translation, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();
        initializeViews(view);
        setupViewModel();
        setupSpeechService();
        setupClickListeners();
        observeViewModel();

        // Check if voice translation was requested
        Bundle arguments = getArguments();
        if (arguments != null && arguments.getBoolean("start_voice", false)) {
            requestAudioPermissionAndStartRecording();
        }
    }

    private void initializeViews(View view) {
        spinnerSourceLanguage = view.findViewById(R.id.spinner_source_language);
        spinnerTargetLanguage = view.findViewById(R.id.spinner_target_language);
        etSourceText = view.findViewById(R.id.et_source_text);
        tvTranslatedText = view.findViewById(R.id.tv_translated_text);
        btnTranslate = view.findViewById(R.id.btn_translate);
        btnVoiceInput = view.findViewById(R.id.btn_voice_input);
        btnSpeakSource = view.findViewById(R.id.btn_speak_source);
        btnSpeak = view.findViewById(R.id.btn_speak);
        btnCopy = view.findViewById(R.id.btn_copy);
        btnSwapLanguages = view.findViewById(R.id.btn_swap_languages);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void setupViewModel() {
        TranslatorApplication application = (TranslatorApplication) requireActivity().getApplication();
        TextTranslationViewModel.TextTranslationViewModelFactory factory = new TextTranslationViewModel.TextTranslationViewModelFactory(
                application.getUserRepository(),
                application.getLanguageRepository(),
                requireContext()
        );
        viewModel = new ViewModelProvider(this, factory).get(TextTranslationViewModel.class);
    }

    private void setupSpeechService() {
        speechService = new SpeechService(requireContext());
        speechService.initializeTextToSpeech(success -> {
            if (!success) {
                showToast(getString(R.string.tts_not_available));
            }
        });
    }

    private void setupClickListeners() {
        btnTranslate.setOnClickListener(v -> performTranslation());

        btnVoiceInput.setOnClickListener(v -> {
            if (isListening) {
                stopVoiceRecording();
            } else {
                requestAudioPermissionAndStartRecording();
            }
        });

        btnSpeakSource.setOnClickListener(v -> speakSourceText());

        btnSpeak.setOnClickListener(v -> speakTranslation());

        btnCopy.setOnClickListener(v -> copyTranslationToClipboard());

        btnSwapLanguages.setOnClickListener(v -> swapLanguages());
    }

    private void observeViewModel() {
        viewModel.supportedLanguages.observe(getViewLifecycleOwner(), this::setupLanguageSpinners);

        viewModel.translationResult.observe(getViewLifecycleOwner(), result -> {
            String displayText = result != null ? result : getString(R.string.translation_failed);
            tvTranslatedText.setText(displayText);
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnTranslate.setEnabled(!isLoading);
        });

        viewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showToast(error);
                viewModel.clearError();
            }
        });
    }

    private void setupLanguageSpinners(List<com.example.translator.data.model.Language> languages) {
        if (languages == null || languages.isEmpty()) return;

        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(requireContext(), languages);

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

    private void performTranslation() {
        String sourceText = etSourceText.getText() != null ? etSourceText.getText().toString().trim() : "";

        if (sourceText.isEmpty()) {
            showToast(getString(R.string.enter_text_hint));
            return;
        }

        if (sourceText.length() > 5000) {
            showToast("Text too long. Maximum 5000 characters allowed.");
            return;
        }

        String sourceLanguage = getSelectedSourceLanguageCode();
        String targetLanguage = getSelectedTargetLanguageCode();

        if (sourceLanguage.equals(targetLanguage)) {
            tvTranslatedText.setText(sourceText);
            return;
        }

        executor.execute(() -> {
            try {
                viewModel.translateText(sourceText, sourceLanguage, targetLanguage);
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        showToast(getString(R.string.translation_failed))
                );
            }
        });
    }

    private void speakSourceText() {
        String sourceText = etSourceText.getText() != null ? etSourceText.getText().toString().trim() : "";

        if (sourceText.isEmpty()) {
            showToast("No text to speak");
            return;
        }

        String sourceLanguage = getSelectedSourceLanguageCode();
        speechService.speakText(sourceText, sourceLanguage);
    }

    private void requestAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE
            );
        } else {
            startVoiceRecording();
        }
    }

    private void startVoiceRecording() {
        if (isListening) return;

        String sourceLanguage = getSelectedSourceLanguageCode();
        isListening = true;
        btnVoiceInput.setText(getString(R.string.listening));
        btnVoiceInput.setEnabled(false);

        currentSpeechCallback = new SpeechService.SpeechRecognitionCallback() {
            @Override
            public void onReady() {
                // Voice recognition is ready
            }

            @Override
            public void onSpeaking() {
                // User started speaking
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Volume level changed
            }

            @Override
            public void onPartialResult(String text) {
                // Partial result received
            }

            @Override
            public void onFinalResult(String text) {
                requireActivity().runOnUiThread(() -> {
                    etSourceText.setText(text);
                    stopVoiceRecording();

                    // Auto-translate if text is recognized
                    String targetLanguage = getSelectedTargetLanguageCode();
                    if (!text.isEmpty()) {
                        executor.execute(() ->
                                viewModel.translateText(text, sourceLanguage, targetLanguage)
                        );
                    }
                });
            }

            @Override
            public void onError(int errorCode) {
                requireActivity().runOnUiThread(() -> {
                    stopVoiceRecording();
                    showToast(getString(R.string.speech_recognition_failed));
                });
            }
        };

        speechService.startSpeechRecognition(sourceLanguage, currentSpeechCallback);
    }

    private void stopVoiceRecording() {
        speechService.stopSpeechRecognition();
        isListening = false;
        btnVoiceInput.setText(getString(R.string.voice_input));
        btnVoiceInput.setEnabled(true);
    }

    private void speakTranslation() {
        String translatedText = tvTranslatedText.getText() != null ? tvTranslatedText.getText().toString() : "";

        if (translatedText.isEmpty() || translatedText.equals(getString(R.string.translation_result))) {
            showToast("No translation to speak");
            return;
        }

        String targetLanguage = getSelectedTargetLanguageCode();
        speechService.speakText(translatedText, targetLanguage);
    }

    private void copyTranslationToClipboard() {
        String translatedText = tvTranslatedText.getText() != null ? tvTranslatedText.getText().toString() : "";

        if (translatedText.isEmpty() || translatedText.equals(getString(R.string.translation_result))) {
            showToast("No translation to copy");
            return;
        }

        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Translated Text", translatedText);
            clipboard.setPrimaryClip(clip);
            showToast(getString(R.string.text_copied));
        } catch (Exception e) {
            showToast("Failed to copy text");
        }
    }

    private void swapLanguages() {
        int sourcePosition = spinnerSourceLanguage.getSelectedItemPosition();
        int targetPosition = spinnerTargetLanguage.getSelectedItemPosition();

        spinnerSourceLanguage.setSelection(targetPosition);
        spinnerTargetLanguage.setSelection(sourcePosition);

        // Swap text if both fields have content
        String sourceText = etSourceText.getText() != null ? etSourceText.getText().toString() : "";
        String targetText = tvTranslatedText.getText() != null ? tvTranslatedText.getText().toString() : "";

        if (!sourceText.isEmpty() && !targetText.isEmpty() &&
                !targetText.equals(getString(R.string.translation_result))) {
            etSourceText.setText(targetText);
            tvTranslatedText.setText(sourceText);
        }
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

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                showToast(getString(R.string.audio_permission_required));
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopVoiceRecording();
        if (speechService != null) {
            speechService.stopSpeaking();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (speechService != null) {
            speechService.release();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}