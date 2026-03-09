package com.example.mediavault.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvWatchTime, tvPagesRead, tvOngoingItems, tvAvgRating;
    private TextView tvViewDetailedStats;
    private MaterialButton btnAnalyzeHabits;
    private DatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        dbHelper = new DatabaseHelper(requireContext());
        
        // Initialize TextViews
        tvWatchTime = view.findViewById(R.id.tv_watch_time);
        tvPagesRead = view.findViewById(R.id.tv_pages_read);
        tvOngoingItems = view.findViewById(R.id.tv_ongoing_items);
        tvAvgRating = view.findViewById(R.id.tv_avg_rating);
        tvViewDetailedStats = view.findViewById(R.id.tv_view_detailed_stats);
        btnAnalyzeHabits = view.findViewById(R.id.btn_analyze_habits);
        
        updateOverviewStats();
        setupNavigation(view);
        
        return view;
    }

    private void updateOverviewStats() {
        // Watch Time (Minutes to Hours)
        int totalMinutes = dbHelper.getTotalMinutesWatched();
        double hours = totalMinutes / 60.0;
        tvWatchTime.setText(String.format(Locale.getDefault(), "%.1f", hours));
        
        // Pages Read
        int totalPages = dbHelper.getTotalPagesRead();
        tvPagesRead.setText(String.valueOf(totalPages));
        
        // Ongoing Items
        int ongoingCount = dbHelper.getStatusCount("Ongoing");
        tvOngoingItems.setText(String.valueOf(ongoingCount));
        
        // Average Rating
        float avgRating = dbHelper.getAverageRating();
        tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
    }

    private void setupNavigation(View view) {
        // Navigate to Metrics Fragment
        View.OnClickListener toMetrics = v -> {
            // Using setSelectedItemId ensures the BottomNavigationView selection state
            // is updated. This allows the user to click back to "Home" in the nav bar
            // to return here correctly.
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_metrics);
                } else {
                    // Fallback to direct navigation if BottomNav is not found
                    Navigation.findNavController(view).navigate(R.id.nav_metrics);
                }
            }
        };

        tvViewDetailedStats.setOnClickListener(toMetrics);
        btnAnalyzeHabits.setOnClickListener(toMetrics);
    }
}
