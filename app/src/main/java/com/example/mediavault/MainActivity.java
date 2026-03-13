package com.example.mediavault;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            FloatingActionButton fab = findViewById(R.id.fab_add);
            View bottomAppBar = findViewById(R.id.bottom_app_bar);
            
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
                } else {
                    // Hide bottom bar and FAB on other screens like Add Media, About, etc.
                    fab.hide();
                    if (bottomAppBar != null) bottomAppBar.setVisibility(View.GONE);
                }
            });
        }
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}
