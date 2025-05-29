package com.example.translator.ui.settings;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.translator.R;
import com.example.translator.TranslatorApplication;
import com.example.translator.data.model.UserPreferences;
import com.example.translator.ui.text.LanguageSpinnerAdapter;
import com.example.translator.ui.text.LanguageSpinnerAdapter.LanguageSpinnerItem;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private SettingsViewModel viewModel;
    private ExecutorService executor;

    private Spinner spinnerDefaultSourceLanguage;
    private Spinner spinnerDefaultTargetLanguage;
    private Spinner spinnerTheme;
    private Spinner spinnerFontSize;
    private Switch switchAutoDetectLanguage;
    private Switch switchTtsEnabled;
    private Switch switchCameraAutoTranslate;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        executor = Executors.newSingleThreadExecutor();
        setupActionBar();
        initializeViews();
        setupViewModel();
        setupClickListeners();
        observeViewModel();
    }

    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

    private void initializeViews() {
        spinnerDefaultSourceLanguage = findViewById(R.id.spinner_default_source_language);
        spinnerDefaultTargetLanguage = findViewById(R.id.spinner_default_target_language);
        spinnerTheme = findViewById(R.id.spinner_theme);
        spinnerFontSize = findViewById(R.id.spinner_font_size);
        switchAutoDetectLanguage = findViewById(R.id.switch_auto_detect_language);
        switchTtsEnabled = findViewById(R.id.switch_tts_enabled);
        switchCameraAutoTranslate = findViewById(R.id.switch_camera_auto_translate);
        btnSave = findViewById(R.id.btn_save);
    }

    private void setupViewModel() {
        TranslatorApplication application = (TranslatorApplication) getApplication();
        SettingsViewModel.SettingsViewModelFactory factory = new SettingsViewModel.SettingsViewModelFactory(
                application.getUserRepository(),
                application.getLanguageRepository()
        );
        viewModel = new ViewModelProvider(this, factory).get(SettingsViewModel.class);
    }

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void observeViewModel() {
        viewModel.supportedLanguages.observe(this, this::setupLanguageSpinners);

        viewModel.userPreferences.observe(this, preferences -> {
            if (preferences != null) {
                loadSettings(preferences);
            }
        });
    }

    private void setupLanguageSpinners(List<com.example.translator.data.model.Language> languages) {
        if (languages == null || languages.isEmpty()) return;

        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(this, languages);

        spinnerDefaultSourceLanguage.setAdapter(adapter);
        spinnerDefaultTargetLanguage.setAdapter(adapter);

        // Setup theme spinner
        String[] themeOptions = {"Light", "Dark", "System"};
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeOptions);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);

        // Setup font size spinner
        String[] fontSizeOptions = {"Small", "Medium", "Large"};
        ArrayAdapter<String> fontSizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fontSizeOptions);
        fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFontSize.setAdapter(fontSizeAdapter);
    }

    private void loadSettings(UserPreferences preferences) {
        // Set language selections
        if (spinnerDefaultSourceLanguage.getAdapter() instanceof LanguageSpinnerAdapter) {
            LanguageSpinnerAdapter sourceAdapter = (LanguageSpinnerAdapter) spinnerDefaultSourceLanguage.getAdapter();
            for (int i = 0; i < sourceAdapter.getCount(); i++) {
                LanguageSpinnerItem item = sourceAdapter.getItem(i);
                if (item.language.getLanguageCode().equals(preferences.getDefaultSourceLanguage())) {
                    spinnerDefaultSourceLanguage.setSelection(i);
                    break;
                }
            }
        }

        if (spinnerDefaultTargetLanguage.getAdapter() instanceof LanguageSpinnerAdapter) {
            LanguageSpinnerAdapter targetAdapter = (LanguageSpinnerAdapter) spinnerDefaultTargetLanguage.getAdapter();
            for (int i = 0; i < targetAdapter.getCount(); i++) {
                LanguageSpinnerItem item = targetAdapter.getItem(i);
                if (item.language.getLanguageCode().equals(preferences.getDefaultTargetLanguage())) {
                    spinnerDefaultTargetLanguage.setSelection(i);
                    break;
                }
            }
        }

        // Set theme selection
        int themeIndex;
        switch (preferences.getTheme()) {
            case "light":
                themeIndex = 0;
                break;
            case "dark":
                themeIndex = 1;
                break;
            case "system":
                themeIndex = 2;
                break;
            default:
                themeIndex = 0;
        }
        spinnerTheme.setSelection(themeIndex);

        // Set font size selection
        int fontSizeIndex;
        switch (preferences.getFontSize()) {
            case "small":
                fontSizeIndex = 0;
                break;
            case "medium":
                fontSizeIndex = 1;
                break;
            case "large":
                fontSizeIndex = 2;
                break;
            default:
                fontSizeIndex = 1;
        }
        spinnerFontSize.setSelection(fontSizeIndex);

        // Set switches
        switchAutoDetectLanguage.setChecked(preferences.isAutoDetectLanguage());
        switchTtsEnabled.setChecked(preferences.isTtsEnabled());
        switchCameraAutoTranslate.setChecked(preferences.isCameraAutoTranslate());
    }

    private void saveSettings() {
        try {
            LanguageSpinnerItem sourceItem = (LanguageSpinnerItem) spinnerDefaultSourceLanguage.getSelectedItem();
            LanguageSpinnerItem targetItem = (LanguageSpinnerItem) spinnerDefaultTargetLanguage.getSelectedItem();

            String sourceLanguage = sourceItem != null ? sourceItem.language.getLanguageCode() : "en";
            String targetLanguage = targetItem != null ? targetItem.language.getLanguageCode() : "vi";

            String theme;
            switch (spinnerTheme.getSelectedItemPosition()) {
                case 0:
                    theme = "light";
                    break;
                case 1:
                    theme = "dark";
                    break;
                case 2:
                    theme = "system";
                    break;
                default:
                    theme = "light";
            }

            String fontSize;
            switch (spinnerFontSize.getSelectedItemPosition()) {
                case 0:
                    fontSize = "small";
                    break;
                case 1:
                    fontSize = "medium";
                    break;
                case 2:
                    fontSize = "large";
                    break;
                default:
                    fontSize = "medium";
            }

            UserPreferences preferences = new UserPreferences(
                    sourceLanguage,
                    targetLanguage,
                    theme,
                    switchAutoDetectLanguage.isChecked(),
                    switchTtsEnabled.isChecked(),
                    switchCameraAutoTranslate.isChecked(),
                    fontSize
            );

            executor.execute(() -> {
                viewModel.updateUserPreferences(preferences);
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error saving settings", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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