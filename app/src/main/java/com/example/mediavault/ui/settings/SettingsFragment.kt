package com.example.mediavault.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.mediavault.R

class SettingsFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        sharedPreferences = requireActivity().getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val switchTheme = view.findViewById<SwitchCompat>(R.id.switch_theme)
        val rowBackup = view.findViewById<View>(R.id.row_backup)
        val btnAbout = view.findViewById<View>(R.id.btn_about)

        // Load saved state or use system default
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        switchTheme.isChecked = isDarkMode

        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val currentMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            
            // Only update if the mode is different to prevent infinite cycles or redundant refreshes
            if (AppCompatDelegate.getDefaultNightMode() != currentMode) {
                AppCompatDelegate.setDefaultNightMode(currentMode)
                sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply()
            }
        }

        // Action for Backup
        rowBackup.setOnClickListener {
            val progressDialog = android.app.ProgressDialog(context)
            progressDialog.setTitle("Media Backup")
            progressDialog.setMessage("Backing up your media files... Please wait.")
            progressDialog.setCancelable(false)
            progressDialog.show()

            // Simulate a backup process (3 seconds)
            view.postDelayed({
                progressDialog.dismiss()
                Toast.makeText(context, "Backup completed successfully!", Toast.LENGTH_LONG).show()
            }, 3000)
        }

        // Logic to go to About Page
        btnAbout.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.action_settings_to_about)
        }

        return view
    }
}