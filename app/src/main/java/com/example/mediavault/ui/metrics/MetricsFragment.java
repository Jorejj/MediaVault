package com.example.mediavault.ui.metrics;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.Locale;

public class MetricsFragment extends Fragment {

    private BarChart chartMonthlyActivity;
    private PieChart chartVaultComposition;
    private TabLayout tabLayoutMetrics;
    
    private TextView txtCompletedBooks, txtCompletedMovies, txtCompletedAnime, txtCompletedSeries;
    private TextView txtBacklogStatus, txtBacklogPercentage, txtTopGenre, txtAvgRating;
    private ProgressBar progressBacklogHealth;
    
    private DatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_metrics, container, false);
        
        dbHelper = new DatabaseHelper(requireContext());
        
        // Initialize all views from fragment_metrics.xml
        chartMonthlyActivity = view.findViewById(R.id.chart_monthly_activity);
        chartVaultComposition = view.findViewById(R.id.chart_vault_composition);
        tabLayoutMetrics = view.findViewById(R.id.tab_layout_metrics);
        
        txtCompletedBooks = view.findViewById(R.id.txt_completed_books);
        txtCompletedMovies = view.findViewById(R.id.txt_completed_movies);
        txtCompletedAnime = view.findViewById(R.id.txt_completed_anime);
        txtCompletedSeries = view.findViewById(R.id.txt_completed_series);
        
        txtBacklogStatus = view.findViewById(R.id.txt_backlog_status);
        txtBacklogPercentage = view.findViewById(R.id.txt_backlog_percentage);
        progressBacklogHealth = view.findViewById(R.id.progress_backlog_health);
        
        txtTopGenre = view.findViewById(R.id.txt_top_genre);
        txtAvgRating = view.findViewById(R.id.txt_avg_rating);
        
        // Setup components with real database data
        refreshData();
        
        tabLayoutMetrics.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                setupHabitProgressChart(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        return view;
    }

    private void refreshData() {
        setupOverviewData();
        setupVaultCompositionChart();
        setupBacklogHealth();
        setupHabitProgressChart(tabLayoutMetrics.getSelectedTabPosition());
    }

    private void setupOverviewData() {
        // Fetch real counts from Database
        txtCompletedBooks.setText(String.valueOf(dbHelper.getCompletedCountByType("Book")));
        txtCompletedMovies.setText(String.valueOf(dbHelper.getCompletedCountByType("Movie")));
        txtCompletedAnime.setText(String.valueOf(dbHelper.getCompletedCountByType("Anime")));
        txtCompletedSeries.setText(String.valueOf(dbHelper.getCompletedCountByType("Series")));
        
        txtTopGenre.setText(String.format("Top Genre: %s", dbHelper.getTopGenre()));
        txtAvgRating.setText(String.format(Locale.getDefault(), "Avg Rating: %.1f", dbHelper.getAverageRating()));
    }

    private void setupHabitProgressChart(int position) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        String[] labels = new String[]{"Daily", "Weekly", "Monthly", "Yearly"};
        String label = labels[position] + " Progress";

        // Aggregate data for Pages, Hours, and Episodes
        int totalPages = dbHelper.getTotalPagesRead();
        float totalHours = dbHelper.getTotalMinutesWatched() / 60f;
        int totalEpisodes = dbHelper.getTotalEpisodesWatched();

        // Factors for distribution
        float factor = 1f;
        if (position == 0) factor = 1/30f; // Daily approx
        else if (position == 1) factor = 1/4f; // Weekly approx
        else if (position == 3) factor = 12f; // Yearly approx

        entries.add(new BarEntry(0f, (float) totalPages * factor));
        entries.add(new BarEntry(1f, totalHours * factor));
        entries.add(new BarEntry(2f, (float) totalEpisodes * factor));

        BarDataSet dataSet = new BarDataSet(entries, label);
        dataSet.setColors(new int[]{
                ContextCompat.getColor(requireContext(), R.color.accent_blue),
                ContextCompat.getColor(requireContext(), R.color.accent_cyan),
                ContextCompat.getColor(requireContext(), R.color.accent_green)
        });
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(Color.GRAY);
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        chartMonthlyActivity.setData(barData);
        chartMonthlyActivity.getDescription().setEnabled(false);

        int textPrimaryColor = resolveThemeColor(com.example.mediavault.R.attr.colorTextPrimary);

        Legend legend = chartMonthlyActivity.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(textPrimaryColor);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);

        chartMonthlyActivity.setDragEnabled(false);
        chartMonthlyActivity.setScaleEnabled(false);
        chartMonthlyActivity.setPinchZoom(false);
        chartMonthlyActivity.setDoubleTapToZoomEnabled(false);
        
        XAxis xAxis = chartMonthlyActivity.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textPrimaryColor);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Pages", "Hours", "Episodes"}));

        chartMonthlyActivity.getAxisLeft().setDrawGridLines(false);
        chartMonthlyActivity.getAxisRight().setEnabled(false);
        
        chartMonthlyActivity.animateY(1000);
        chartMonthlyActivity.invalidate();
    }

    private void setupVaultCompositionChart() {
        int books = dbHelper.getTotalCountByType("Book");
        int anime = dbHelper.getTotalCountByType("Anime");
        int movies = dbHelper.getTotalCountByType("Movie");
        int series = dbHelper.getTotalCountByType("Series");
        int manga = dbHelper.getTotalCountByType("Manga");

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (books > 0) entries.add(new PieEntry(books, "Books"));
        if (anime > 0) entries.add(new PieEntry(anime, "Anime"));
        if (movies > 0) entries.add(new PieEntry(movies, "Movies"));
        if (series > 0) entries.add(new PieEntry(series, "Series"));
        if (manga > 0) entries.add(new PieEntry(manga, "Manga"));

        if (entries.isEmpty()) {
            chartVaultComposition.setNoDataText("No data available in library");
            chartVaultComposition.setData(null);
            chartVaultComposition.invalidate();
            return;
        }

        int textPrimaryColor = resolveThemeColor(com.example.mediavault.R.attr.colorTextPrimary);

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{
            ContextCompat.getColor(requireContext(), R.color.accent_blue),
            ContextCompat.getColor(requireContext(), R.color.accent_cyan),
            ContextCompat.getColor(requireContext(), R.color.accent_blue_variant),
            ContextCompat.getColor(requireContext(), R.color.vault_accent_blue),
            Color.parseColor("#9C27B0") // Purple for Manga
        });
        dataSet.setSliceSpace(3f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData pieData = new PieData(dataSet);
        chartVaultComposition.setData(pieData);
        chartVaultComposition.getDescription().setEnabled(false);
        chartVaultComposition.setHoleColor(Color.TRANSPARENT);
        chartVaultComposition.setEntryLabelColor(textPrimaryColor);
        chartVaultComposition.setEntryLabelTextSize(11f);
        chartVaultComposition.setCenterTextColor(textPrimaryColor);
        chartVaultComposition.setRotationEnabled(false);

        Legend legend = chartVaultComposition.getLegend();
        legend.setTextColor(textPrimaryColor);

        chartVaultComposition.animateY(1400);
        chartVaultComposition.invalidate();
    }

    private int resolveThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
    
    private void setupBacklogHealth() {
        int completed = dbHelper.getStatusCount("Completed");
        int planning = dbHelper.getStatusCount("Planning");
        int ongoing = dbHelper.getStatusCount("Ongoing");
        int total = completed + planning + ongoing;
        
        int progress = (total > 0) ? (completed * 100 / total) : 0;
        progressBacklogHealth.setProgress(progress);
        txtBacklogPercentage.setText(String.format(Locale.getDefault(), "%d%% Completed", progress));
        
        if (progress >= 70) {
            txtBacklogStatus.setText("Excellent");
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
        } else if (progress >= 40) {
            txtBacklogStatus.setText("Healthy");
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_blue));
        } else {
            txtBacklogStatus.setText("Overwhelming");
            txtBacklogStatus.setTextColor(Color.RED);
        }
    }
}
