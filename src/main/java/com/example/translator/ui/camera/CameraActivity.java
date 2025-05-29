package com.example.translator.ui.camera;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.translator.R;

public class CameraActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.camera_container, new CameraFragment())
                    .commit();
        }
    }
}