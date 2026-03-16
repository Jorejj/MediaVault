package com.example.mediavault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper; // Added this import
import androidx.appcompat.app.AppCompatActivity;
@SuppressWarnings("CustomSplashScreen")

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starting_page);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 3333);
    }
}
