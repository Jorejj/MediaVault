package com.example.mediavault;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.receiver.DailyGoalReminderReceiver;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.view.ViewGroup;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;
import android.graphics.drawable.Drawable;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Setup BlurView for Navbar
        setupBlurView();

        // Initialize Navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            FloatingActionButton fab = findViewById(R.id.fab_add);
            View bottomAppBar = findViewById(R.id.bottom_app_bar);

            int navIconSize = (int) getResources().getDimension(R.dimen.bottom_nav_icon_size);
            bottomNav.setItemIconSize(navIconSize);
            bottomNav.setItemHorizontalTranslationEnabled(false);
             
            // Setup Bottom Navigation with NavController
            NavigationUI.setupWithNavController(bottomNav, navController);

            // Setup FAB to navigate to the Add Media destination
            fab.setOnClickListener(v -> {
                navController.navigate(R.id.nav_add_media);
            });

            // Handle FAB and BottomAppBar visibility based on destination
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                
                // Show bottom navigation on main screens
                if (id == R.id.nav_home || id == R.id.nav_library || id == R.id.nav_metrics || id == R.id.nav_settings || id == R.id.nav_shake) {
                    if (bottomAppBar != null) bottomAppBar.setVisibility(View.VISIBLE);
                    fab.show();
                    
                    // Prevent nav_shake from highlighting an unrelated menu item
                    if (id == R.id.nav_shake) {
                        bottomNav.getMenu().setGroupCheckable(0, false, true);
                    } else {
                        bottomNav.getMenu().setGroupCheckable(0, true, true);
                    }
                } else {
                    // Hide bottom bar and FAB on other screens like Add Media, About, etc.
                    fab.hide();
                    if (bottomAppBar != null) bottomAppBar.setVisibility(View.GONE);
                }
            });
        }
        
        // Schedule periodic service status check
        com.example.mediavault.worker.ServiceStatusWorker.schedule(this);
        com.example.mediavault.worker.DailyResetWorker.schedule(this);

        DailyGoalsManager goalsManager = DailyGoalsManager.getInstance(this);
        DailyGoalReminderReceiver.scheduleNextReminder(this, goalsManager);
    }

    private void applySavedTheme() {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
    }

    private void setupBlurView() {
        BlurView blurView = findViewById(R.id.blur_view_nav);
        if (blurView == null) return;
        
        float radius = 20f;
        View decorView = getWindow().getDecorView();
        ViewGroup rootView = decorView.findViewById(android.R.id.content);
        Drawable windowBackground = decorView.getBackground();

        blurView.setupWith(rootView)
                .setFrameClearDrawable(windowBackground)
                .setBlurAlgorithm(new RenderScriptBlur(this))
                .setBlurRadius(radius)
                .setBlurAutoUpdate(true)
                .setHasFixedTransformationMatrix(true);
        
        // Dynamic overlay color from theme for Glassmorphism
        int overlayColor = ContextCompat.getColor(this, R.color.glass_surface_color);
        blurView.setOverlayColor(overlayColor);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
    }

    /**
     * Check if accessibility service is enabled and show dialog if not
     */
    private void checkServiceStatus() {
        com.example.mediavault.utils.AccessibilityServiceHelper.ServiceStatus status = 
            com.example.mediavault.utils.AccessibilityServiceHelper.getDetailedStatus(this);
        
        // Only show permission dialog if wizard has been completed and permissions are missing
        // (Wizard handles first-time setup)
        boolean wizardCompleted = getSharedPreferences("mediavault_prefs", MODE_PRIVATE)
                .getBoolean("permission_wizard_completed", false);
        
        if (!status.fullyOperational && wizardCompleted && shouldShowPermissionPrompt()) {
            showPermissionDialog(status);
        } else if (status.fullyOperational) {
            // Service is working - cancel any old notifications and reset prompt flag
            com.example.mediavault.utils.AccessibilityServiceHelper.cancelServiceDisabledNotification(this);
            // Reset the "don't ask" flag when permissions are granted
            getSharedPreferences("Settings", MODE_PRIVATE)
                .edit()
                .putBoolean("skip_permission_prompt", false)
                .apply();
        }
    }
    
    /**
     * Show a user-friendly dialog explaining missing permissions
     */
    private void showPermissionDialog(com.example.mediavault.utils.AccessibilityServiceHelper.ServiceStatus status) {
        String title;
        String message;
        String buttonText;
        
        if (!status.accessibilityEnabled && !status.overlayEnabled) {
            title = "Setup Required";
            message = "MediaVault needs two permissions to track your reading progress:\n\n" +
                      "1️⃣ Accessibility Service - Detects what you're reading\n" +
                      "2️⃣ Overlay Permission - Shows the floating tracker\n\n" +
                      "Let's enable Accessibility first.";
            buttonText = "Enable Accessibility";
        } else if (!status.accessibilityEnabled) {
            title = "Tracking Disabled";
            message = "The media tracking service was disabled.\n\n" +
                      "This can happen after an app update. " +
                      "Re-enable it to continue tracking your reading progress automatically.";
            buttonText = "Enable Tracking";
        } else {
            title = "Overlay Permission Needed";
            message = "The floating tracker needs permission to appear over other apps.\n\n" +
                      "This lets you see your reading progress and quickly add new media.";
            buttonText = "Grant Permission";
        }
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_vault)
            .setPositiveButton(buttonText, (dialog, which) -> {
                if (!status.accessibilityEnabled) {
                    com.example.mediavault.utils.AccessibilityServiceHelper.openAccessibilitySettings(this);
                } else {
                    com.example.mediavault.utils.AccessibilityServiceHelper.openOverlaySettings(this);
                }
            })
            .setNegativeButton("Later", null)
            .setNeutralButton("Don't Ask Again", (dialog, which) -> {
                // Save preference to not show again
                getSharedPreferences("Settings", MODE_PRIVATE)
                    .edit()
                    .putBoolean("skip_permission_prompt", true)
                    .apply();
            })
            .setCancelable(true)
            .show();
    }
    
    /**
     * Check if user dismissed permission prompt permanently
     */
    private boolean shouldShowPermissionPrompt() {
        return !getSharedPreferences("Settings", MODE_PRIVATE)
            .getBoolean("skip_permission_prompt", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Avoid tearing down overlays during normal tracking lifecycle.
        if (isFinishing()) {
            SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
            boolean trackingEnabled = prefs.getBoolean("external_tracking_enabled", false);
            boolean assistantEnabled = prefs.getBoolean("floating_assistant_enabled", false);
            if (!(trackingEnabled && assistantEnabled)) {
                com.example.mediavault.service.FloatingAssistantManager manager =
                        com.example.mediavault.service.FloatingAssistantManager.getInstance(this);
                manager.destroyAll();
            }
        }
    }
}
