package com.example.mediavault.ui.dialogs;

import android.app.Dialog;
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
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.mediavault.R;
import com.example.mediavault.utils.AccessibilityServiceHelper;

/**
 * Step-by-step permission wizard dialog that guides users through enabling:
 * 1. Accessibility Service
 * 2. Overlay Permission
 * 3. Battery Optimization Exception
 */
public class PermissionWizardDialog extends Dialog {
    private static final String TAG = "PermissionWizardDialog";

    private static final String PREFS_NAME = "mediavault_prefs";
    private static final String KEY_WIZARD_COMPLETED = "permission_wizard_completed";

    public interface OnWizardCompleteListener {
        void onWizardComplete(boolean allPermissionsGranted);
    }

    private int currentStep = 0;
    private OnWizardCompleteListener listener;

    // Views
    private View step1Dot, step2Dot, step3Dot;
    private TextView textStepTitle, textStepDescription, textStatus;
    private ImageView imgStepIcon, imgStatusIcon;
    private Button btnAction, btnBack, btnSkip;
    private View layoutStatus;

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

    public PermissionWizardDialog(@NonNull Context context) {
        super(context, R.style.Theme_MediaVault_Dialog_Transparent);
    }

    public void setOnWizardCompleteListener(OnWizardCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.dialog_permission_wizard);

            setCancelable(false);

            // Initialize views with null checks
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
            layoutStatus = findViewById(R.id.layout_status);

            // Verify critical views loaded
            if (btnAction == null || textStepTitle == null || imgStepIcon == null) {
                throw new IllegalStateException("Failed to load dialog layout resources");
            }

            btnAction.setOnClickListener(v -> onActionClick());
            btnBack.setOnClickListener(v -> goBack());
            btnSkip.setOnClickListener(v -> skipOrFinish());

            updateUI();
        } catch (Exception e) {
            // Log and dismiss on error to prevent crash
            android.util.Log.e("PermissionWizard", "Failed to create dialog", e);
            dismiss();
            // Notify listener that wizard failed
            if (listener != null) {
                listener.onWizardComplete(false);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Refresh status when dialog becomes visible (user may have returned from settings)
        updateUI();
        
        // Auto-advance if current step permission is now granted
        if (checkStepPermission(currentStep)) {
            Log.d(TAG, "Step " + currentStep + " permission granted, auto-advancing");
            // Post delayed to allow UI to update first
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isShowing()) return;
                goToNextStep();
            }, 500);
        }
    }

    private void updateUI() {
        try {
            // Null checks for safety
            if (step1Dot == null || step2Dot == null || step3Dot == null) return;
            if (textStepTitle == null || textStepDescription == null || imgStepIcon == null) return;
            
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
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    private void updateStatusIndicator(boolean isEnabled) {
        if (isEnabled) {
            imgStatusIcon.setImageResource(R.drawable.ic_check);
            imgStatusIcon.setColorFilter(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
            textStatus.setText(com.example.mediavault.R.string.auto_enabled);
            textStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
        } else {
            imgStatusIcon.setImageResource(R.drawable.ic_close);
            imgStatusIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.vault_red_primary));
            textStatus.setText(com.example.mediavault.R.string.auto_not_enabled);
            textStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.vault_red_primary));
        }
    }

    private boolean checkStepPermission(int step) {
        Context context = getContext();
        switch (step) {
            case 0: // Accessibility
                return AccessibilityServiceHelper.isServiceEnabled(context);
            case 1: // Overlay
                return AccessibilityServiceHelper.isOverlayPermissionGranted(context);
            case 2: // Battery
                return isBatteryOptimizationDisabled(context);
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
        Context context = getContext();
        Intent intent;
        
        switch (step) {
            case 0: // Accessibility
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                // Don't dismiss - let onStart() auto-advance when user returns
                break;
                
            case 1: // Overlay
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    // Don't dismiss - let onStart() auto-advance when user returns
                }
                break;
                
            case 2: // Battery
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    // Don't dismiss - let onStart() auto-advance when user returns
                }
                break;
        }
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
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_WIZARD_COMPLETED, true).apply();

        // Check final status
        boolean allGranted = checkStepPermission(0) && checkStepPermission(1) && checkStepPermission(2);

        if (listener != null) {
            listener.onWizardComplete(allGranted);
        }

        dismiss();
    }

    /**
     * Check if wizard should be shown (first run with tracking enabled)
     */
    public static boolean shouldShowWizard(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean wizardCompleted = prefs.getBoolean(KEY_WIZARD_COMPLETED, false);
        
        // Show wizard if not completed and user has enabled tracking
        SharedPreferences settingsPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean trackingEnabled = settingsPrefs.getBoolean("external_tracking_enabled", false);
        
        return !wizardCompleted && trackingEnabled;
    }

    /**
     * Reset wizard (for testing or re-running setup)
     */
    public static void resetWizard(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_WIZARD_COMPLETED).apply();
    }

    /**
     * Check if any permissions are missing
     */
    public static boolean hasAllPermissions(Context context) {
        boolean accessibility = AccessibilityServiceHelper.isServiceEnabled(context);
        boolean overlay = AccessibilityServiceHelper.isOverlayPermissionGranted(context);
        boolean battery = false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            battery = pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } else {
            battery = true;
        }
        
        return accessibility && overlay && battery;
    }
}