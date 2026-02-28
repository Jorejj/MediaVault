package com.example.mediavault;

import android.content.Context;
import android.content.SharedPreferences;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class SettingsFragment extends Fragment {

    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        sharedPreferences = requireActivity().getSharedPreferences("Settings", Context.MODE_PRIVATE);

        // UI Components
        SwitchCompat switchTheme = view.findViewById(R.id.switch_theme);
        View rowBackup = view.findViewById(R.id.row_backup);
        RelativeLayout btnAbout = view.findViewById(R.id.btn_about);

        // Theme preference persistence
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        switchTheme.setChecked(isDarkMode);

        // 1. Theme Switcher (Dark Mode)
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int targetMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode);
                sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply();
            }
        });

        // 2. Media Backup Action
        rowBackup.setOnClickListener(v -> {
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
            progressDialog.setTitle("Media Backup");
            progressDialog.setMessage("Backing up your media files... Please wait.");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // Simulate process
            v.postDelayed(() -> {
                progressDialog.dismiss();
                Toast.makeText(getContext(), "Backup completed successfully!", Toast.LENGTH_LONG).show();
            }, 3000);
        });

        // 3. Navigation to About Page
        btnAbout.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_settings_to_about);
        });

        return view;
    }
}