package com.example.mediavault.ui.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.R;
import com.example.mediavault.receiver.DailyGoalReminderReceiver;
import com.example.mediavault.service.MediaMonitorService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.example.mediavault.widget.ToastUtils;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private static final String MONITORED_APPS_PREFS = "monitored_apps_prefs";
    private static final String KEY_OPEN_ADD_DIALOG_ONCE = "open_add_dialog_once";

    private SharedPreferences sharedPreferences;
    private DatabaseHelper dbHelper;
    private TextView textBatteryStatus;
    private SwitchCompat switchMonitoring;
    private SwitchCompat switchAssistant;
    
    // Status indicators
    private View indicatorAccessibility;
    private View indicatorOverlay;
    private TextView textAccessibilityStatus;
    private TextView textOverlayStatus;
    private TextView textReminderAlertStatus;

    private final ActivityResultLauncher<String> requestBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> {
                if (uri != null) performBackup(uri);
            }
    );

    private final ActivityResultLauncher<String[]> requestRestoreLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) performRestore(uri);
            }
    );

    private final ActivityResultLauncher<String> requestJsonExportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) exportVaultJson(uri);
            }
    );

    private final ActivityResultLauncher<String[]> requestJsonImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importVaultJson(uri);
            }
    );

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher = registerForActivityResult(new ScanContract(),
            (ScanIntentResult result) -> {
                if (result != null && result.getContents() != null && !result.getContents().isEmpty()) {
                    importVaultFromQrPayload(result.getContents());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        sharedPreferences = requireActivity().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        dbHelper = DatabaseHelper.getInstance(getContext());

        // UI Components
        SwitchCompat switchTheme = view.findViewById(R.id.switch_theme);
        switchMonitoring = view.findViewById(R.id.switch_monitoring);
        switchAssistant = view.findViewById(R.id.switch_assistant);
        SwitchCompat switchAnchor = view.findViewById(R.id.switch_persistent_anchor);
        View rowMonitoring = view.findViewById(R.id.row_monitoring);
        View rowAssistant = view.findViewById(R.id.row_assistant);
        View rowMonitoredApps = view.findViewById(R.id.row_monitored_apps);
        View rowBatteryOptimization = view.findViewById(R.id.row_battery_optimization);
        View rowReminderAlerts = view.findViewById(R.id.row_reminder_alerts);
        TextView textBatteryStatus = view.findViewById(R.id.text_battery_status);
        textReminderAlertStatus = view.findViewById(R.id.text_reminder_alert_status);
        View rowExportData = view.findViewById(R.id.row_export_data);
        View rowQrVault = view.findViewById(R.id.row_qr_vault);
        View rowClearDatabase = view.findViewById(R.id.row_clear_database);
        View rowTerms = view.findViewById(R.id.row_terms);
        View rowPrivacy = view.findViewById(R.id.row_privacy);
        View rowBackup = view.findViewById(R.id.row_backup);
        View rowHelp = view.findViewById(R.id.icon_help).getParent() instanceof View ? (View) view.findViewById(R.id.icon_help).getParent() : null;
        View rowShake = view.findViewById(R.id.icon_shake).getParent() instanceof View ? (View) view.findViewById(R.id.icon_shake).getParent() : null;
        View rowVault = view.findViewById(R.id.icon_vault).getParent() instanceof View ? (View) view.findViewById(R.id.icon_vault).getParent() : null;
        RelativeLayout btnAbout = view.findViewById(R.id.btn_about);
        
        // Status indicators
        indicatorAccessibility = view.findViewById(R.id.indicator_accessibility);
        indicatorOverlay = view.findViewById(R.id.indicator_overlay);
        textAccessibilityStatus = view.findViewById(R.id.text_accessibility_status);
        textOverlayStatus = view.findViewById(R.id.text_overlay_status);

        this.textBatteryStatus = textBatteryStatus;

        // Check the current actual theme to set the initial switch state
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        switchTheme.setChecked(isDarkMode);

        // Load Automation Settings
        switchMonitoring.setChecked(sharedPreferences.getBoolean("external_tracking_enabled", false));
        switchAssistant.setChecked(sharedPreferences.getBoolean("floating_assistant_enabled", false));
        switchAnchor.setChecked(sharedPreferences.getBoolean("persistent_anchor_enabled", false));

        // 1. Theme Switcher (Dark Mode)
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int targetMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                // Apply and persist the theme
                sharedPreferences.edit().putInt("theme_mode", targetMode).apply();
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });

        // Automation Switches
        switchMonitoring.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPreferences.edit().putBoolean("external_tracking_enabled", isChecked).apply();
            notifyWidgetSettingsChanged();
            if (isChecked && !isMediaMonitorServiceEnabled()) {
                openAccessibilitySettingsWithGuidance();
            }
        });

        switchAssistant.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPreferences.edit().putBoolean("floating_assistant_enabled", isChecked).apply();
            notifyWidgetSettingsChanged();
            if (isChecked) {
                requestOverlayPermissionIfNeeded();
            }
        });

        switchAnchor.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPreferences.edit().putBoolean("persistent_anchor_enabled", isChecked).apply();
            notifyWidgetSettingsChanged();
            if (isChecked) {
                requestOverlayPermissionIfNeeded();
            }
            // Update the assistant manager state
            com.example.mediavault.service.FloatingAssistantManager.getInstance(requireContext())
                    .ensureAnchorVisible();
        });

        if (rowMonitoring != null) {
            rowMonitoring.setOnClickListener(v -> {
                if (!isMediaMonitorServiceEnabled()) {
                    openAccessibilitySettingsWithGuidance();
                }
            });
        }

        if (rowAssistant != null) {
            rowAssistant.setOnClickListener(v -> requestOverlayPermissionIfNeeded());
        }

        if (rowMonitoredApps != null) {
            rowMonitoredApps.setOnClickListener(v -> {
                Navigation.findNavController(v).navigate(R.id.action_settings_to_monitored_apps);
            });
            rowMonitoredApps.setOnLongClickListener(v -> {
                requireContext()
                        .getSharedPreferences(MONITORED_APPS_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_OPEN_ADD_DIALOG_ONCE, true)
                        .apply();
                Navigation.findNavController(v).navigate(R.id.action_settings_to_monitored_apps);
                ToastUtils.showCustomToast(requireContext(), "Choose app to add to monitored list");
                return true;
            });
        }

        if (rowBatteryOptimization != null) {
            rowBatteryOptimization.setOnClickListener(v -> {
                Intent intent = new Intent();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                } else {
                    intent.setAction(Settings.ACTION_SETTINGS);
                }
                startActivity(intent);
            });
            updateBatteryStatus(textBatteryStatus);
        }

        if (rowReminderAlerts != null) {
            rowReminderAlerts.setOnClickListener(v -> showReminderAlertSettingsDialog());
        }
        updateReminderAlertSummary();

        // 2. Export Data Action
        if (rowExportData != null) {
            rowExportData.setOnClickListener(v -> showExportConfirmationDialog());
        }

        if (rowQrVault != null) {
            rowQrVault.setOnClickListener(v -> showQrVaultDialog());
        }

        // 3. Clear Database Action
        if (rowClearDatabase != null) {
            rowClearDatabase.setOnClickListener(v -> showClearDatabaseDialog());
        }

        // 4. Terms of Use
        if (rowTerms != null) {
            rowTerms.setOnClickListener(v -> showTermsOfUseDialog());
        }

        // 5. Privacy Policy
        if (rowPrivacy != null) {
            rowPrivacy.setOnClickListener(v -> showPrivacyPolicyDialog());
        }

        // 6. Backup & Restore
        if (rowBackup != null) {
            rowBackup.setOnClickListener(v -> showBackupRestoreDialog());
        }

        // 7. Help
        if (rowHelp != null) {
            rowHelp.setOnClickListener(v -> showHelpDialog());
        }

        // 9. Shake Sensitivity
        if (rowShake != null) {
            rowShake.setOnClickListener(v -> showShakeSensitivityDialog());
        }

        // 10. Vault Management (Seed Data)
        if (rowVault != null) {
            rowVault.setOnClickListener(v -> showSeedDataDialog());
        }

        // 8. Navigation to About Page
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> {
                Navigation.findNavController(v).navigate(R.id.action_settings_to_about);
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (textBatteryStatus != null) {
            updateBatteryStatus(textBatteryStatus);
        }
        
        // Refresh switch states based on actual service status
        refreshServiceStatus();
        
        // Update permission indicators
        updatePermissionIndicators();
        updateReminderAlertSummary();
    }
    
    /**
     * Update the visual permission status indicators (green/red dots)
     */
    private void updatePermissionIndicators() {
        boolean accessibilityEnabled = com.example.mediavault.utils.AccessibilityServiceHelper.isServiceEnabled(requireContext());
        boolean overlayEnabled = com.example.mediavault.utils.AccessibilityServiceHelper.isOverlayPermissionGranted(requireContext());
        
        // Update Accessibility indicator
        if (indicatorAccessibility != null) {
            int colorRes = accessibilityEnabled ? android.R.color.holo_green_dark : R.color.vault_red_primary;
            indicatorAccessibility.getBackground().setTint(
                    androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));
        }
        if (textAccessibilityStatus != null) {
            textAccessibilityStatus.setText(accessibilityEnabled 
                    ? "✓ Service active" 
                    : "Tap to enable accessibility service");
        }
        
        // Update Overlay indicator
        if (indicatorOverlay != null) {
            int colorRes = overlayEnabled ? android.R.color.holo_green_dark : R.color.vault_red_primary;
            indicatorOverlay.getBackground().setTint(
                    androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));
        }
        if (textOverlayStatus != null) {
            textOverlayStatus.setText(overlayEnabled 
                    ? "✓ Permission granted" 
                    : "Tap to enable overlay permission");
        }
    }
    
    /**
     * Refresh switches to reflect actual service/permission states.
     * This handles cases where permissions were revoked externally (e.g., after app update).
     */
    private void refreshServiceStatus() {
        if (switchMonitoring == null || switchAssistant == null) return;
        
        boolean accessibilityEnabled = com.example.mediavault.utils.AccessibilityServiceHelper.isServiceEnabled(requireContext());
        boolean overlayEnabled = com.example.mediavault.utils.AccessibilityServiceHelper.isOverlayPermissionGranted(requireContext());
        
        // Update switch states without triggering listeners
        switchMonitoring.setOnCheckedChangeListener(null);
        switchAssistant.setOnCheckedChangeListener(null);
        
        // If accessibility is disabled, uncheck the monitoring switch
        if (!accessibilityEnabled && switchMonitoring.isChecked()) {
            switchMonitoring.setChecked(false);
            sharedPreferences.edit().putBoolean("external_tracking_enabled", false).apply();
            notifyWidgetSettingsChanged();
        }
        
        // If overlay is disabled, uncheck the assistant switch
        if (!overlayEnabled && switchAssistant.isChecked()) {
            switchAssistant.setChecked(false);
            sharedPreferences.edit().putBoolean("floating_assistant_enabled", false).apply();
            notifyWidgetSettingsChanged();
        }
        
        // Re-attach listeners
        switchMonitoring.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPreferences.edit().putBoolean("external_tracking_enabled", isChecked).apply();
            notifyWidgetSettingsChanged();
            if (isChecked && !isMediaMonitorServiceEnabled()) {
                openAccessibilitySettingsWithGuidance();
            }
        });
        
        switchAssistant.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPreferences.edit().putBoolean("floating_assistant_enabled", isChecked).apply();
            notifyWidgetSettingsChanged();
            if (isChecked) {
                requestOverlayPermissionIfNeeded();
            }
        });

        // Re-sync service/widget state after returning from Android settings screens.
        notifyWidgetSettingsChanged();
    }

    private void notifyWidgetSettingsChanged() {
        Intent intent = new Intent(MediaMonitorService.ACTION_WIDGET_SETTINGS_CHANGED);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
    }

    private void requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(requireContext())) {
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName())
        );
        startActivity(intent);
    }

    private boolean isMediaMonitorServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.trim().isEmpty()) {
            return false;
        }
        String targetService = new ComponentName(requireContext(), MediaMonitorService.class).flattenToString();
        return enabledServices.contains(targetService);
    }

    private void openAccessibilitySettingsWithGuidance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Enable External Tracking")
                    .setMessage("Android 14+ may block Accessibility for restricted apps.\n\n1) Open App info for MediaVault.\n2) Tap the menu and enable 'Allow restricted settings'.\n3) Return and enable MediaVault in Accessibility.")
                    .setPositiveButton("Open Accessibility", (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void updateBatteryStatus(TextView statusView) {
        if (statusView == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
                statusView.setText(com.example.mediavault.R.string.auto_unrestricted);
                statusView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                statusView.setText(com.example.mediavault.R.string.auto_optimized);
                statusView.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
        } else {
            statusView.setText("N/A");
        }
    }

    private void showSeedDataDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Vault Management")
                .setMessage("Would you like to populate your library with 30 random demo entries? (Duplicate titles will be ignored)")
                .setPositiveButton("Seed Data", (dialog, which) -> {
                    dbHelper.seedDatabase();
                    ToastUtils.showCustomToast(getContext(), "Library seeded with 30 items!");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShakeSensitivityDialog() {
        String[] options = {"High (Easy)", "Medium (Normal)", "Low (Hard)"};
        float[] values = {1.5f, 2.7f, 4.0f};
        
        float currentVal = sharedPreferences.getFloat("shake_sensitivity", 2.7f);
        int currentSelection = 1; // Default to Medium
        if (currentVal <= 1.5f) currentSelection = 0;
        else if (currentVal >= 4.0f) currentSelection = 2;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Shake Sensitivity")
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    sharedPreferences.edit().putFloat("shake_sensitivity", values[which]).apply();
                    ToastUtils.showCustomToast(getContext(), "Sensitivity set to " + options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showReminderAlertSettingsDialog() {
        DailyGoalsManager manager = DailyGoalsManager.getInstance(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reminder_alerts, null);

        com.google.android.material.switchmaterial.SwitchMaterial switchSound =
                dialogView.findViewById(R.id.switch_reminder_sound);
        com.google.android.material.switchmaterial.SwitchMaterial switchVibration =
                dialogView.findViewById(R.id.switch_reminder_vibration);
        Spinner spinnerIntensity = dialogView.findViewById(R.id.spinner_reminder_intensity);

        String[] options = {"Low", "Medium", "High"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                options
        );
        spinnerIntensity.setAdapter(adapter);

        switchSound.setChecked(manager.isReminderSoundEnabled());
        switchVibration.setChecked(manager.isReminderVibrationEnabled());

        String intensity = manager.getReminderVibrationIntensity();
        int selection = 1;
        if ("low".equals(intensity)) {
            selection = 0;
        } else if ("high".equals(intensity)) {
            selection = 2;
        }
        spinnerIntensity.setSelection(selection);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reminder Alerts")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    manager.setReminderSoundEnabled(switchSound.isChecked());
                    manager.setReminderVibrationEnabled(switchVibration.isChecked());
                    String selectedIntensity = options[spinnerIntensity.getSelectedItemPosition()].toLowerCase(Locale.ROOT);
                    manager.setReminderVibrationIntensity(selectedIntensity);
                    DailyGoalReminderReceiver.refreshNotificationChannel(requireContext(), manager);
                    DailyGoalReminderReceiver.scheduleNextReminder(requireContext(), manager);
                    updateReminderAlertSummary();
                    ToastUtils.showCustomToast(getContext(), "Reminder alert settings updated");
                })
                .setNeutralButton("System Channel", (dialog, which) -> openReminderChannelSettings(manager))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openReminderChannelSettings(DailyGoalsManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, DailyGoalReminderReceiver.getChannelId(manager));
            startActivity(intent);
        } else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        }
    }

    private void updateReminderAlertSummary() {
        if (textReminderAlertStatus == null || getContext() == null) return;
        DailyGoalsManager manager = DailyGoalsManager.getInstance(requireContext());
        String status = manager.isReminderSoundEnabled() ? "S" : "-";
        status += "/";
        status += manager.isReminderVibrationEnabled() ? manager.getReminderVibrationIntensity().substring(0, 1).toUpperCase(Locale.ROOT) : "-";
        textReminderAlertStatus.setText(status);
    }

    private void showBackupRestoreDialog() {
        String[] options = {"Create Backup", "Restore from Backup"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Backup & Restore")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestBackupLauncher.launch("MediaVault_Backup_" + System.currentTimeMillis() + ".db");
                    } else {
                        requestRestoreLauncher.launch(new String[]{"application/octet-stream", "application/x-sqlite3"});
                    }
                })
                .show();
    }

    private void performBackup(Uri uri) {
        try {
            File currentDb = requireContext().getDatabasePath("MediaVault.db");
            if (currentDb.exists()) {
                try (InputStream in = new FileInputStream(currentDb);
                     OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    ToastUtils.showCustomToast(getContext(), "Backup created successfully");
                }
            }
        } catch (IOException e) {
            ToastUtils.showCustomToast(getContext(), "Backup failed");
        }
    }

    private void performRestore(Uri uri) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore Database")
                .setMessage("This will overwrite your current library. This cannot be undone. Proceed?")
                .setPositiveButton("Restore", (dialog, which) -> {
                    try {
                        File currentDb = requireContext().getDatabasePath("MediaVault.db");
                        // Close database connection if open - but we can't easily here. 
                        // Typically, a restart is best after overwriting the file.
                        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                             OutputStream out = new FileOutputStream(currentDb)) {
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            ToastUtils.showCustomToast(getContext(), "Restore successful. Please restart the app.");
                        }
                    } catch (IOException e) {
                        ToastUtils.showCustomToast(getContext(), "Restore failed");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelpDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Help & FAQ")
                .setMessage("• How to add media? Tap the '+' button on the Home screen.\n\n" +
                        "• How to track progress? Click on any media item and update the progress value.\n\n" +
                        "• Exporting data? Use 'Export Data' to save your library as a CSV file.\n\n" +
                        "• Dark Mode? Toggle it at the top of this screen.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showTermsOfUseDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Terms of Use")
                .setMessage("1. Acceptance: By using MediaVault, you agree to these terms.\n\n" +
                        "2. Usage: This app is for personal media tracking only.\n\n" +
                        "3. Data: Your data is stored locally on your device. We are not responsible for data loss.\n\n" +
                        "4. Limitations: The app is provided 'as is' without warranties.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showPrivacyPolicyDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Privacy Policy")
                .setMessage("1. Data Collection: MediaVault does not collect personal information. All media data is stored locally.\n\n" +
                        "2. Permissions: We require storage access only for exporting your data.\n\n" +
                        "3. Third Parties: No data is shared with third parties.\n\n" +
                        "4. Security: Your data stays on your device.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showExportConfirmationDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Vault Export / Import")
                .setItems(new String[]{"Export CSV", "Export Vault JSON", "Import Vault JSON"}, (dialog, which) -> {
                    if (which == 0) {
                        exportDatabaseToCSV();
                    } else if (which == 1) {
                        requestJsonExportLauncher.launch("MediaVault_Export_" + System.currentTimeMillis() + ".json");
                    } else {
                        requestJsonImportLauncher.launch(new String[]{"application/json", "text/plain"});
                    }
                })
                .show();
    }

    private void showQrVaultDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("QR Vault")
                .setItems(new String[]{"Generate Top 10 Favorites QR", "Scan QR and Import"}, (dialog, which) -> {
                    if (which == 0) {
                        showTopTenQrDialog();
                    } else {
                        ScanOptions options = new ScanOptions();
                        options.setPrompt("Scan a MediaVault QR");
                        options.setBeepEnabled(true);
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                        options.setOrientationLocked(false);
                        qrScanLauncher.launch(options);
                    }
                })
                .show();
    }

    private void showTopTenQrDialog() {
        String payload = buildTopTenPayload();
        if (payload == null) {
            ToastUtils.showCustomToast(getContext(), "Add favorites first to generate QR");
            return;
        }
        Bitmap qrBitmap = createQrBitmap(payload, 860, 860);
        if (qrBitmap == null) {
            ToastUtils.showCustomToast(getContext(), "Unable to generate QR");
            return;
        }

        View qrView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qr_vault, null);
        ImageView image = qrView.findViewById(R.id.img_qr_code);
        image.setImageBitmap(qrBitmap);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Top 10 Favorites")
                .setView(qrView)
                .setPositiveButton("Done", null)
                .show();
    }

    private String buildTopTenPayload() {
        Cursor cursor = dbHelper.getTopFavoritesForQr(10);
        if (cursor == null) {
            return null;
        }
        JSONArray items = new JSONArray();
        try {
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("title", cursor.getString(0));
                item.put("type", cursor.getString(1));
                item.put("genre", cursor.getString(2));
                item.put("total", cursor.getInt(3));
                item.put("unit", cursor.getString(4));
                item.put("progress", cursor.getDouble(5));
                item.put("status", cursor.getString(6));
                item.put("priority", cursor.getString(7));
                item.put("rating", cursor.getDouble(8));
                items.put(item);
            }
        } catch (JSONException e) {
            cursor.close();
            return null;
        }
        cursor.close();

        if (items.length() == 0) {
            return null;
        }

        JSONObject root = new JSONObject();
        try {
            root.put("schema", "mediavault-qr-v1");
            root.put("items", items);
        } catch (JSONException e) {
            return null;
        }
        return root.toString();
    }

    private Bitmap createQrBitmap(String content, int width, int height) {
        try {
            EnumMap<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return bmp;
        } catch (WriterException e) {
            return null;
        }
    }

    private void importVaultFromQrPayload(String rawJson) {
        try {
            JSONObject root = new JSONObject(rawJson);
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) {
                ToastUtils.showCustomToast(getContext(), "QR has no media items");
                return;
            }

            int imported = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                long id = dbHelper.addImportedBacklogItem(
                        item.optString("title", "Untitled"),
                        item.optString("type", "Series"),
                        item.optString("genre", ""),
                        item.optInt("total", 1),
                        item.optString("unit", "Episodes"),
                        item.optString("priority", "Medium")
                );
                if (id != -1) {
                    imported++;
                }
            }
            ToastUtils.showCustomToast(getContext(), "Imported " + imported + " items from QR");
        } catch (JSONException e) {
            ToastUtils.showCustomToast(getContext(), "Invalid QR payload");
        }
    }

    private void exportVaultJson(Uri uri) {
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri);
             Cursor cursor = dbHelper.getAllMedia()) {
            if (out == null || cursor == null) {
                ToastUtils.showCustomToast(getContext(), "Export failed");
                return;
            }

            JSONArray items = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                String genre = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
                String creator = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATOR));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                String review = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW));
                String journal = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_JOURNAL));
                String mood = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MOOD));
                String priority = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRIORITY));
                String image = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));
                String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPTION));

                item.put("title", title != null ? title : "Untitled");
                item.put("type", type != null ? type : "Series");
                item.put("genre", genre != null ? genre : "");
                item.put("creator", creator != null ? creator : "");
                item.put("total", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT)));
                item.put("unit", unit != null ? unit : "Episodes");
                item.put("progress", cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS)));
                item.put("status", status != null ? status : "Planning");
                item.put("rating", cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING)));
                item.put("review", review != null ? review : "");
                item.put("journal", journal != null ? journal : "");
                item.put("mood", mood != null ? mood : "");
                item.put("priority", priority != null ? priority : "Medium");
                item.put("favorite", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_FAVORITE)) == 1);
                item.put("image", image != null ? image : "");
                item.put("description", description != null ? description : "");
                items.put(item);
            }

            JSONObject root = new JSONObject();
            root.put("schema", "mediavault-json-v1");
            root.put("items", items);
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            ToastUtils.showCustomToast(getContext(), "Vault JSON exported");
        } catch (Exception e) {
            ToastUtils.showCustomToast(getContext(), "JSON export failed");
        }
    }

    private void importVaultJson(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                ToastUtils.showCustomToast(getContext(), "Unable to read JSON file");
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int len;
            while ((len = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            JSONObject root = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                ToastUtils.showCustomToast(getContext(), "Invalid JSON backup");
                return;
            }

            int imported = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                long id = dbHelper.addMedia(
                        item.optString("title", "Untitled"),
                        item.optString("type", "Series"),
                        item.optString("genre", ""),
                        item.optString("creator", ""),
                        Math.max(item.optInt("total", 1), 1),
                        item.optString("unit", "Episodes"),
                        null,
                        item.optString("image", ""),
                        item.optString("description", "")
                );
                if (id != -1) {
                    float importedProgress = (float) Math.max(item.optDouble("progress", 0.0), 0.0);
                    dbHelper.updateMedia((int) id,
                            item.optString("title", "Untitled"),
                            item.optString("type", "Series"),
                            item.optString("genre", ""),
                            item.optString("status", "Planning"),
                            importedProgress,
                            Math.max(item.optInt("total", 1), 1),
                            item.optString("unit", "Episodes"),
                            item.optString("image", ""),
                            (float) item.optDouble("rating", 0.0),
                            item.optString("review", ""),
                            item.optString("journal", ""),
                            item.optString("mood", ""),
                            item.optString("priority", "Medium"),
                            item.optBoolean("favorite", false),
                            item.optString("description", ""),
                            item.optString("creator", ""));
                    imported++;
                }
            }
            ToastUtils.showCustomToast(getContext(), "Imported " + imported + " items from JSON");
        } catch (Exception e) {
            ToastUtils.showCustomToast(getContext(), "JSON import failed");
        }
    }

    private void exportDatabaseToCSV() {
        Cursor cursor = dbHelper.getAllMedia();
        if (cursor == null || cursor.getCount() == 0) {
            ToastUtils.showCustomToast(getContext(), "No data to export");
            if (cursor != null) cursor.close();
            return;
        }
        cursor.close();

        String fileName = "MediaVault_Export_" + System.currentTimeMillis() + ".csv";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveCsvToDownloadsApi29(fileName);
        } else {
            saveCsvToDownloadsLegacy(fileName);
        }
    }

    private void saveCsvToDownloadsApi29(String fileName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            saveCsvToDownloadsLegacy(fileName);
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaVault");

        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            writeCsvToUri(uri);
        } else {
            ToastUtils.showCustomToast(getContext(), "Failed to create file");
        }
    }

    private void saveCsvToDownloadsLegacy(String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File mediaVaultDir = new File(downloadsDir, "MediaVault");
        if (!mediaVaultDir.exists()) {
            boolean created = mediaVaultDir.mkdirs();
            if (!created) Log.e(TAG, "Failed to create directory: " + mediaVaultDir.getAbsolutePath());
        }
        
        File file = new File(mediaVaultDir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeCsvToOutputStream(out);
            ToastUtils.showCustomToast(getContext(), "Exported to Downloads/MediaVault");
        } catch (IOException e) {
            Log.e(TAG, "Export failed", e);
            ToastUtils.showCustomToast(getContext(), "Export failed");
        }
    }

    private void writeCsvToUri(Uri uri) {
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            if (out != null) {
                writeCsvToOutputStream(out);
                ToastUtils.showCustomToast(getContext(), "Data exported to Downloads/MediaVault");
            }
        } catch (IOException e) {
            Log.e(TAG, "Export to URI failed", e);
            ToastUtils.showCustomToast(getContext(), "Export failed");
        }
    }

    private void writeCsvToOutputStream(OutputStream out) throws IOException {
        try (Cursor cursor = dbHelper.getAllMedia();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            if (cursor == null || cursor.getCount() == 0) return;

            String[] columns = cursor.getColumnNames();
            for (int i = 0; i < columns.length; i++) {
                writer.write("\"" + columns[i] + "\"");
                if (i < columns.length - 1) writer.write(",");
            }
            writer.newLine();

            while (cursor.moveToNext()) {
                for (int i = 0; i < columns.length; i++) {
                    String val = cursor.getString(i);
                    if (val == null) {
                        writer.write("\"\"");
                    } else {
                        writer.write("\"" + val.replace("\"", "\"\"") + "\"");
                    }
                    if (i < columns.length - 1) writer.write(",");
                }
                writer.newLine();
            }
            writer.flush();
        }
    }

    private void showClearDatabaseDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Database")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage("Are you sure you want to delete ALL your media entries? This action is permanent and cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    dbHelper.clearAllMedia();
                    ToastUtils.showCustomToast(getContext(), "Database cleared successfully");
                    // Clear logs as well - DatabaseHelper.clearAllMedia already does this.
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
