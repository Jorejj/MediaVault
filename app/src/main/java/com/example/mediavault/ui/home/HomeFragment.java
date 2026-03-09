package com.example.mediavault.ui.home;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvWatchTime, tvPagesRead, tvOngoingItems, tvAvgRating;
    private TextView tvViewDetailedStats;
    private MaterialButton btnAnalyzeHabits;
    private DatabaseHelper dbHelper;
    private View recentItem1, recentItem2, recentItem3, recentItem4;

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

        // Recent Items
        recentItem1 = view.findViewById(R.id.recent_item_1);
        recentItem2 = view.findViewById(R.id.recent_item_2);
        recentItem3 = view.findViewById(R.id.recent_item_3);
        recentItem4 = view.findViewById(R.id.recent_item_4);
        
        updateOverviewStats();
        setupNavigation(view);
        setupRecentActivities();
        
        return view;
    }

    private void setupRecentActivities() {
        Cursor cursor = dbHelper.getAllMedia();
        View[] items = {recentItem1, recentItem2, recentItem3, recentItem4};
        
        // Hide all initially
        for (View item : items) item.setVisibility(View.GONE);

        if (cursor != null) {
            int count = 0;
            if (cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                int totalIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);

                do {
                    if (count >= 4) break;
                    
                    View itemView = items[count];
                    itemView.setVisibility(View.VISIBLE);
                    
                    int id = cursor.getInt(idIndex);
                    String title = cursor.getString(titleIndex);
                    String type = cursor.getString(typeIndex);
                    int progress = cursor.getInt(progressIndex);
                    int total = cursor.getInt(totalIndex);
                    
                    TextView tvTitle = itemView.findViewById(R.id.tv_title);
                    TextView tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
                    TextView tvPercent = itemView.findViewById(R.id.tv_progress_percentage);
                    ProgressBar progressBar = itemView.findViewById(R.id.progress_bar);
                    
                    if (tvTitle != null) tvTitle.setText(title);
                    if (tvSubtitle != null) tvSubtitle.setText(type);
                    if (total > 0) {
                        int percent = (int) ((progress / (float) total) * 100);
                        if (tvPercent != null) tvPercent.setText(percent + "%");
                        if (progressBar != null) {
                            progressBar.setMax(total);
                            progressBar.setProgress(progress);
                        }
                    }

                    itemView.setOnClickListener(v -> {
                        Intent intent = new Intent(requireContext(), DescriptionActivity.class);
                        intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, id);
                        startActivity(intent);
                    });

                    count++;
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }

    private void setupRecentClickListeners() {
        // Method replaced by setupRecentActivities for real data
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
