package com.example.translator.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.translator.data.model.Language;
import com.example.translator.data.model.UserPreferences;
import com.example.translator.data.repository.LanguageRepository;
import com.example.translator.data.repository.UserRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeViewModel extends ViewModel {

    private UserRepository userRepository;
    private LanguageRepository languageRepository;
    private ExecutorService executor;

    public final LiveData<UserPreferences> userPreferences;
    public final LiveData<List<Language>> supportedLanguages;

    public HomeViewModel(UserRepository userRepository, LanguageRepository languageRepository) {
        this.userRepository = userRepository;
        this.languageRepository = languageRepository;
        this.executor = Executors.newSingleThreadExecutor();

        this.userPreferences = userRepository.getUserPreferences();
        this.supportedLanguages = languageRepository.getAllSupportedLanguages();
    }

    public void initializeDefaultPreferences() {
        executor.execute(() -> {
            userRepository.initializeDefaultPreferences();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executor != null) {
            executor.shutdown();
        }
    }

    public static class HomeViewModelFactory implements ViewModelProvider.Factory {
        private UserRepository userRepository;
        private LanguageRepository languageRepository;

        public HomeViewModelFactory(UserRepository userRepository, LanguageRepository languageRepository) {
            this.userRepository = userRepository;
            this.languageRepository = languageRepository;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(HomeViewModel.class)) {
                return (T) new HomeViewModel(userRepository, languageRepository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}