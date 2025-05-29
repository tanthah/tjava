package com.example.translator.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.translator.R;
import com.example.translator.ui.camera.CameraFragment;
import com.example.translator.ui.home.HomeFragment;
import com.example.translator.ui.text.TextTranslationFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBottomNavigation();

        // Load default fragment if not restoring state
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), R.id.nav_home);
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Set default selected item
        bottomNavigation.setSelectedItemId(R.id.nav_home);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                loadFragment(new HomeFragment(), R.id.nav_home);
                return true;
            } else if (itemId == R.id.nav_camera) {
                loadFragment(new CameraFragment(), R.id.nav_camera);
                return true;
            } else if (itemId == R.id.nav_text) {
                loadFragment(new TextTranslationFragment(), R.id.nav_text);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment, int menuItemId) {
        // Avoid unnecessary fragment transactions
        if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) {
            return;
        }

        try {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();

            currentFragment = fragment;

            // Update bottom navigation selection
            if (bottomNavigation.getSelectedItemId() != menuItemId) {
                bottomNavigation.setSelectedItemId(menuItemId);
            }
        } catch (Exception e) {
            // Handle fragment transaction errors
            e.printStackTrace();
        }
    }

    public void switchToTextTranslation(boolean startVoice) {
        TextTranslationFragment textFragment = new TextTranslationFragment();
        if (startVoice) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("start_voice", true);
            textFragment.setArguments(bundle);
        }
        loadFragment(textFragment, R.id.nav_text);
    }

    @Override
    public void onBackPressed() {
        // Handle back navigation properly
        if (bottomNavigation.getSelectedItemId() != R.id.nav_home) {
            loadFragment(new HomeFragment(), R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}