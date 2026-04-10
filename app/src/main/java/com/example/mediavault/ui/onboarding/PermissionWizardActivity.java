package com.example.mediavault.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mediavault.MainActivity;
import com.example.mediavault.R;
import com.example.mediavault.utils.AccessibilityServiceHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Full-screen Activity for permission setup wizard.
 * Persists through settings navigation better than Dialog.
 */
public class PermissionWizardActivity extends AppCompatActivity {
    private static final String TAG = "PermissionWizard";
    private static final String PREFS_NAME = "mediavault_prefs";
    private static final String KEY_WIZARD_COMPLETED = "permission_wizard_completed";
    private static final String EXTRA_CURRENT_STEP = "current_step";

    private int currentStep = 0;

    // Views
    private View step1Dot, step2Dot, step3Dot;
    private TextView textStepTitle, textStepDescription, textStatus;
    private ImageView imgStepIcon, imgStatusIcon;
    private Button btnAction, btnBack, btnSkip;

    // Step data
    private final String[] stepTitles = {
            "Step 1: Accessibility Service",
            "Step 2: Overlay Permission",
            "Step 3: Battery Optimization"
    };

    private final String[] stepDescriptions = {
            "Enable the Accessibility Service to allow MediaVault to detect what you're reading in apps like WebNovel, Kotatsu, and others.",
            "Allow MediaVault to display over other apps so the floating assistant can show your reading progress.",
            "Disable battery optimization for MediaVault to ensure the tracking service runs reliably in the background."
    };

    private final int[] stepIcons = {
            R.drawable.ic_accessibility,
            R.drawable.ic_overlay,
            R.drawable.ic_battery
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_wizard);

        // Restore step if returning from settings
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(EXTRA_CURRENT_STEP, 0);
        } else if (getIntent() != null) {
            currentStep = getIntent().getIntExtra(EXTRA_CURRENT_STEP, 0);
        }

        initViews();
        updateUI();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_CURRENT_STEP, currentStep);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if permission was granted while we were in settings
        updateUI();
        
        // Auto-advance if permission granted
        if (checkStepPermission(currentStep)) {
            Log.d(TAG, "Permission granted, auto-advancing from step " + currentStep);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                goToNextStep();
            }, 800);
        }
    }

    private void initViews() {
        step1Dot = findViewById(R.id.step_1_dot);
        step2Dot = findViewById(R.id.step_2_dot);
        step3Dot = findViewById(R.id.step_3_dot);
        textStepTitle = findViewById(R.id.text_step_title);
        textStepDescription = findViewById(R.id.text_step_description);
        textStatus = findViewById(R.id.text_status);
        imgStepIcon = findViewById(R.id.img_step_icon);
        imgStatusIcon = findViewById(R.id.img_status_icon);
        btnAction = findViewById(R.id.btn_action);
        btnBack = findViewById(R.id.btn_back);
        btnSkip = findViewById(R.id.btn_skip);

        btnAction.setOnClickListener(v -> onActionClick());
        btnBack.setOnClickListener(v -> goBack());
        btnSkip.setOnClickListener(v -> skipOrFinish());
    }

    private void updateUI() {
        // Update step indicators
        step1Dot.setAlpha(currentStep >= 0 ? 1f : 0.3f);
        step2Dot.setAlpha(currentStep >= 1 ? 1f : 0.3f);
        step3Dot.setAlpha(currentStep >= 2 ? 1f : 0.3f);

        // Update content
        textStepTitle.setText(stepTitles[currentStep]);
        textStepDescription.setText(stepDescriptions[currentStep]);
        imgStepIcon.setImageResource(stepIcons[currentStep]);

        // Update status
        boolean isEnabled = checkStepPermission(currentStep);
        updateStatusIndicator(isEnabled);

        // Update buttons
        btnBack.setVisibility(currentStep > 0 ? View.VISIBLE : View.GONE);
        
        if (isEnabled) {
            btnAction.setText(currentStep < 2 ? "Continue" : "Finish");
        } else {
            btnAction.setText(com.example.mediavault.R.string.auto_enable_now);
        }

        // Update skip text
        if (currentStep == 2) {
            btnSkip.setText(com.example.mediavault.R.string.auto_skip_and_finish);
        } else {
            btnSkip.setText(com.example.mediavault.R.string.auto_skip_for_now);
        }
    }

    private void updateStatusIndicator(boolean isEnabled) {
        if (isEnabled) {
            imgStatusIcon.setImageResource(R.drawable.ic_check);
            imgStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            textStatus.setText(com.example.mediavault.R.string.auto_enabled);
            textStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            imgStatusIcon.setImageResource(R.drawable.ic_close);
            imgStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.vault_red_primary));
            textStatus.setText(com.example.mediavault.R.string.auto_not_enabled);
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.vault_red_primary));
        }
    }

    private boolean checkStepPermission(int step) {
        switch (step) {
            case 0: // Accessibility
                return AccessibilityServiceHelper.isServiceEnabled(this);
            case 1: // Overlay
                return AccessibilityServiceHelper.isOverlayPermissionGranted(this);
            case 2: // Battery
                return isBatteryOptimizationDisabled(this);
            default:
                return false;
        }
    }

    private boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    private void onActionClick() {
        boolean isEnabled = checkStepPermission(currentStep);
        
        if (isEnabled) {
            // Permission already granted, go to next step
            goToNextStep();
        } else {
            // Open settings for this permission
            openSettingsForStep(currentStep);
        }
    }

    private void openSettingsForStep(int step) {
        Intent intent;
        
        switch (step) {
            case 0: // Accessibility
                openAccessibilitySettingsWithGuidance();
                break;
                
            case 1: // Overlay
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open overlay settings", e);
                    }
                }
                break;
                
            case 2: // Battery
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean opened = openBatteryOptimizationSettings();
                    if (!opened) {
                        showBatteryManualInstructions();
                    }
                }
                break;
        }
    }

    private void openAccessibilitySettingsWithGuidance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Enable Accessibility")
                    .setMessage("Android 14+ may block Accessibility for restricted apps.\n\n1) Open App info for MediaVault.\n2) Tap the menu and enable 'Allow restricted settings'.\n3) Return and enable MediaVault in Accessibility.")
                    .setPositiveButton("Open Accessibility", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open accessibility settings", e);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Log.e(TAG, "Failed to open accessibility settings", e);
        }
    }

    private boolean openBatteryOptimizationSettings() {
        // 1) Most compatible path (used in SettingsFragment)
        Intent[] candidates = new Intent[] {
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_SETTINGS),
                // OEM fallbacks
                new Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                new Intent().setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                new Intent().setClassName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
                new Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                new Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
        };

        for (Intent candidate : candidates) {
            try {
                if (candidate.resolveActivity(getPackageManager()) != null) {
                    startActivity(candidate);
                    Log.i(TAG, "Opened battery settings via: " + candidate);
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Battery settings intent failed: " + candidate, e);
            }
        }

        Log.e(TAG, "No battery settings activity available");
        return false;
    }

    private void showBatteryManualInstructions() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Manual Step Required")
                .setMessage("Open your phone Settings > Apps > MediaVault > Battery, then disable battery restrictions/optimization for MediaVault.")
                .setPositiveButton("Open App Settings", (d, w) -> {
                    Intent appSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    startActivity(appSettings);
                })
                .setNegativeButton("Got it", null)
                .show();
    }

    private void goToNextStep() {
        if (currentStep < 2) {
            currentStep++;
            updateUI();
        } else {
            finishWizard();
        }
    }

    private void goBack() {
        if (currentStep > 0) {
            currentStep--;
            updateUI();
        }
    }

    private void skipOrFinish() {
        if (currentStep < 2) {
            goToNextStep();
        } else {
            finishWizard();
        }
    }

    private void finishWizard() {
        // Mark wizard as completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_WIZARD_COMPLETED, true).apply();

        // Navigate to MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public static boolean shouldShowWizard(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean wizardCompleted = prefs.getBoolean(KEY_WIZARD_COMPLETED, false);
        
        SharedPreferences settingsPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean trackingEnabled = settingsPrefs.getBoolean("external_tracking_enabled", false);
        
        return !wizardCompleted && trackingEnabled;
    }
}