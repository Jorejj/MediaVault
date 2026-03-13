package com.example.mediavault.ui.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
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
import com.example.mediavault.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class SettingsFragment extends Fragment {

    private SharedPreferences sharedPreferences;
    private DatabaseHelper dbHelper;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        sharedPreferences = requireActivity().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        dbHelper = new DatabaseHelper(getContext());

        // UI Components
        SwitchCompat switchTheme = view.findViewById(R.id.switch_theme);
        View rowExportData = view.findViewById(R.id.row_export_data);
        View rowClearDatabase = view.findViewById(R.id.row_clear_database);
        View rowTerms = view.findViewById(R.id.row_terms);
        View rowPrivacy = view.findViewById(R.id.row_privacy);
        View rowBackup = view.findViewById(R.id.row_backup);
        View rowHelp = view.findViewById(R.id.icon_help).getParent() instanceof View ? (View) view.findViewById(R.id.icon_help).getParent() : null;
        View rowShake = view.findViewById(R.id.icon_shake).getParent() instanceof View ? (View) view.findViewById(R.id.icon_shake).getParent() : null;
        View rowVault = view.findViewById(R.id.icon_vault).getParent() instanceof View ? (View) view.findViewById(R.id.icon_vault).getParent() : null;
        RelativeLayout btnAbout = view.findViewById(R.id.btn_about);

        // Theme preference persistence
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        switchTheme.setChecked(isDarkMode);

        // 1. Theme Switcher (Dark Mode)
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int targetMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });

        // 2. Export Data Action
        if (rowExportData != null) {
            rowExportData.setOnClickListener(v -> showExportConfirmationDialog());
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

    private void showSeedDataDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Vault Management")
                .setMessage("Would you like to populate your library with 30 random demo entries? (Duplicate titles will be ignored)")
                .setPositiveButton("Seed Data", (dialog, which) -> {
                    dbHelper.seedDatabase();
                    Toast.makeText(getContext(), "Library seeded with 30 items!", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "Sensitivity set to " + options[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                    Toast.makeText(getContext(), "Backup created successfully", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "Backup failed", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(getContext(), "Restore successful. Please restart the app.", Toast.LENGTH_LONG).show();
                        }
                    } catch (IOException e) {
                        Toast.makeText(getContext(), "Restore failed", Toast.LENGTH_SHORT).show();
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
                .setTitle("Export Data")
                .setMessage("This will create a CSV file with all your media information. Do you want to proceed?")
                .setPositiveButton("Export", (dialog, which) -> exportDatabaseToCSV())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportDatabaseToCSV() {
        Cursor cursor = dbHelper.getAllMedia();
        if (cursor == null || cursor.getCount() == 0) {
            Toast.makeText(getContext(), "No data to export", Toast.LENGTH_SHORT).show();
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
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaVault");

        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            writeCsvToUri(uri);
        } else {
            Toast.makeText(getContext(), "Failed to create file", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCsvToDownloadsLegacy(String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File mediaVaultDir = new File(downloadsDir, "MediaVault");
        if (!mediaVaultDir.exists()) mediaVaultDir.mkdirs();
        
        File file = new File(mediaVaultDir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeCsvToOutputStream(out);
            Toast.makeText(getContext(), "Exported to Downloads/MediaVault", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void writeCsvToUri(Uri uri) {
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            if (out != null) {
                writeCsvToOutputStream(out);
                Toast.makeText(getContext(), "Data exported to Downloads/MediaVault", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "Database cleared successfully", Toast.LENGTH_SHORT).show();
                    // Clear logs as well - DatabaseHelper.clearAllMedia already does this.
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
