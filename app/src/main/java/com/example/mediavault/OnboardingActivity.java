package com.example.mediavault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.ui.dialogs.PermissionWizardDialog;

/**
 * First-run onboarding activity that explains external tracking
 * and guides users through permissions.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "mediavault_prefs";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";

    private int currentPage = 0;
    private static final int TOTAL_PAGES = 4;

    // Page views
    private View pageWelcome, pageHowItWorks, pagePermissions, pageComplete;
    private View dot1, dot2, dot3, dot4;
    private Button btnSkip, btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if onboarding already completed
        if (isOnboardingComplete()) {
            proceedToMain();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        // Initialize views
        pageWelcome = findViewById(R.id.page_welcome);
        pageHowItWorks = findViewById(R.id.page_how_it_works);
        pagePermissions = findViewById(R.id.page_permissions);
        pageComplete = findViewById(R.id.page_complete);

        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        dot3 = findViewById(R.id.dot_3);
        dot4 = findViewById(R.id.dot_4);

        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);

        btnSkip.setOnClickListener(v -> skipOnboarding());
        btnNext.setOnClickListener(v -> nextPage());

        updateUI();
    }

    private void updateUI() {
        // Hide all pages
        pageWelcome.setVisibility(View.GONE);
        pageHowItWorks.setVisibility(View.GONE);
        pagePermissions.setVisibility(View.GONE);
        pageComplete.setVisibility(View.GONE);

        // Show current page
        switch (currentPage) {
            case 0:
                pageWelcome.setVisibility(View.VISIBLE);
                break;
            case 1:
                pageHowItWorks.setVisibility(View.VISIBLE);
                break;
            case 2:
                pagePermissions.setVisibility(View.VISIBLE);
                break;
            case 3:
                pageComplete.setVisibility(View.VISIBLE);
                btnNext.setText(com.example.mediavault.R.string.auto_get_started);
                break;
        }

        // Update dot indicators
        dot1.setAlpha(currentPage == 0 ? 1f : 0.3f);
        dot2.setAlpha(currentPage == 1 ? 1f : 0.3f);
        dot3.setAlpha(currentPage == 2 ? 1f : 0.3f);
        dot4.setAlpha(currentPage == 3 ? 1f : 0.3f);

        // Update button text
        if (currentPage < TOTAL_PAGES - 1) {
            btnNext.setText(com.example.mediavault.R.string.auto_next);
        }
    }

    private void nextPage() {
        if (currentPage < TOTAL_PAGES - 1) {
            currentPage++;
            updateUI();
        } else {
            // Last page - show permission wizard and finish
            finishOnboarding();
        }
    }

    private void skipOnboarding() {
        markOnboardingComplete();
        proceedToMain();
    }

    private void finishOnboarding() {
        markOnboardingComplete();
        
        // Launch permission wizard activity (persists through settings navigation)
        Intent intent = new Intent(this, com.example.mediavault.ui.onboarding.PermissionWizardActivity.class);
        startActivity(intent);
        finish(); // Close onboarding so user can't go back
    }

    private void markOnboardingComplete() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply();
    }

    private boolean isOnboardingComplete() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Check if onboarding should be shown
     */
    public static boolean shouldShowOnboarding(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    /**
     * Reset onboarding (for testing)
     */
    public static void resetOnboarding(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_ONBOARDING_COMPLETE).apply();
    }
}