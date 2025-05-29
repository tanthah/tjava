package com.example.translator.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.card.MaterialCardView;
import com.example.translator.R;
import com.example.translator.TranslatorApplication;
import com.example.translator.ui.MainActivity;
import com.example.translator.ui.camera.CameraActivity;
import com.example.translator.ui.image.ImageTranslationActivity;
import com.example.translator.ui.settings.SettingsActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private ExecutorService executor;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();
        setupViewModel();
        setupClickListeners(view);
        initializeDefaultPreferences();
    }

    private void setupViewModel() {
        TranslatorApplication application = (TranslatorApplication) requireActivity().getApplication();
        HomeViewModel.HomeViewModelFactory factory = new HomeViewModel.HomeViewModelFactory(
                application.getUserRepository(),
                application.getLanguageRepository()
        );
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupClickListeners(View view) {
        // Camera Translation Card
        MaterialCardView cardCameraTranslation = view.findViewById(R.id.card_camera_translation);
        if (cardCameraTranslation != null) {
            cardCameraTranslation.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(requireContext(), CameraActivity.class));
                } catch (Exception e) {
                    // Handle activity not found or other errors
                    e.printStackTrace();
                }
            });
        }

        // Text Translation Card
        MaterialCardView cardTextTranslation = view.findViewById(R.id.card_text_translation);
        if (cardTextTranslation != null) {
            cardTextTranslation.setOnClickListener(v -> {
                try {
                    MainActivity mainActivity = (MainActivity) requireActivity();
                    mainActivity.switchToTextTranslation(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Image Translation Card
        MaterialCardView cardImageTranslation = view.findViewById(R.id.card_image_translation);
        if (cardImageTranslation != null) {
            cardImageTranslation.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(requireContext(), ImageTranslationActivity.class));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Voice Translation Card
        MaterialCardView cardVoiceTranslation = view.findViewById(R.id.card_voice_translation);
        if (cardVoiceTranslation != null) {
            cardVoiceTranslation.setOnClickListener(v -> {
                try {
                    MainActivity mainActivity = (MainActivity) requireActivity();
                    mainActivity.switchToTextTranslation(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Settings Button
        View btnSettings = view.findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(requireContext(), SettingsActivity.class));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void initializeDefaultPreferences() {
        executor.execute(() -> {
            try {
                viewModel.initializeDefaultPreferences();
            } catch (Exception e) {
                // Handle initialization error silently
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}