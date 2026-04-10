package com.example.mediavault;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper; // Added this import
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

@SuppressWarnings("CustomSplashScreen")

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starting_page);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if onboarding should be shown
            if (OnboardingActivity.shouldShowOnboarding(this)) {
                Intent intent = new Intent(SplashActivity.this, OnboardingActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
            }
            finish();
        }, 3333);
    }

    private void applySavedTheme() {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
    }
}
