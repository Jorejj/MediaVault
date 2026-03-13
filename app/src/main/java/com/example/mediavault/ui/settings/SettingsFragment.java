package com.example.mediavault.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsFragment extends Fragment {

    private SharedPreferences sharedPreferences;
    private DatabaseHelper dbHelper;

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
                // Note: Activity will be recreated
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

        // 4. Navigation to About Page
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> {
                Navigation.findNavController(v).navigate(R.id.action_settings_to_about);
            });
        }

        return view;
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

        Context context = getContext();
        if (context == null) return;

        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();

        File file = new File(exportDir, "MediaVault_Export.csv");
        try {
            FileWriter writer = new FileWriter(file);
            // Write Header
            String[] columns = cursor.getColumnNames();
            for (int i = 0; i < columns.length; i++) {
                writer.append(columns[i]);
                if (i < columns.length - 1) writer.append(",");
            }
            writer.append("\n");

            // Write Data
            while (cursor.moveToNext()) {
                for (int i = 0; i < columns.length; i++) {
                    String val = cursor.getString(i);
                    writer.append(val != null ? val.replace(",", ";") : "");
                    if (i < columns.length - 1) writer.append(",");
                }
                writer.append("\n");
            }
            writer.flush();
            writer.close();
            cursor.close();

            // Share the file
            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Data"));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
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
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
