package com.example.mediavault.ui.metrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.tabs.TabLayout;
import java.util.Locale;
import java.util.Map;

public class MetricsFragment extends Fragment {

    private WebView webviewMonthlyActivity;
    private WebView webviewVaultComposition;
    private TabLayout tabLayoutMetrics;
    
    private TextView txtCompletedBooks, txtCompletedMovies, txtCompletedAnime, txtCompletedSeries;
    private TextView txtBacklogStatus, txtBacklogPercentage, txtTopGenre, txtAvgRating;
    private ProgressBar progressBacklogHealth;
    
    private DatabaseHelper dbHelper;
    private boolean isUpdateReceiverRegistered = false;
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                refreshData();
                ToastUtils.showCustomToast(context, "Metrics refreshed");
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_metrics, container, false);
        
        dbHelper = new DatabaseHelper(requireContext());
        
        webviewMonthlyActivity = view.findViewById(R.id.webview_monthly_activity);
        webviewVaultComposition = view.findViewById(R.id.webview_vault_composition);
        tabLayoutMetrics = view.findViewById(R.id.tab_layout_metrics);
        
        configureWebView(webviewMonthlyActivity);
        configureWebView(webviewVaultComposition);
        
        txtCompletedBooks = view.findViewById(R.id.txt_completed_books);
        txtCompletedMovies = view.findViewById(R.id.txt_completed_movies);
        txtCompletedAnime = view.findViewById(R.id.txt_completed_anime);
        txtCompletedSeries = view.findViewById(R.id.txt_completed_series);
        
        txtBacklogStatus = view.findViewById(R.id.txt_backlog_status);
        txtBacklogPercentage = view.findViewById(R.id.txt_backlog_percentage);
        progressBacklogHealth = view.findViewById(R.id.progress_backlog_health);
        
        txtTopGenre = view.findViewById(R.id.txt_top_genre);
        txtAvgRating = view.findViewById(R.id.txt_avg_rating);
        
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

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (!isUpdateReceiverRegistered && context != null) {
            IntentFilter filter = new IntentFilter(DescriptionActivity.ACTION_MEDIA_UPDATED);
            ContextCompat.registerReceiver(context, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            isUpdateReceiverRegistered = true;
        }
        refreshData();
    }

    @Override
    public void onPause() {
        Context context = getContext();
        if (isUpdateReceiverRegistered && context != null) {
            context.unregisterReceiver(updateReceiver);
            isUpdateReceiverRegistered = false;
        }
        super.onPause();
    }

    private void configureWebView(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
    }

    private void refreshData() {
        setupOverviewData();
        setupVaultCompositionChart();
        setupBacklogHealth();
        setupHabitProgressChart(tabLayoutMetrics.getSelectedTabPosition());
    }

    private void setupOverviewData() {
        txtCompletedBooks.setText(String.valueOf(dbHelper.getCompletedCountByType("Book")));
        txtCompletedMovies.setText(String.valueOf(dbHelper.getCompletedCountByType("Movie")));
        txtCompletedAnime.setText(String.valueOf(dbHelper.getCompletedCountByType("Anime")));
        txtCompletedSeries.setText(String.valueOf(dbHelper.getCompletedCountByType("Series")));
        
        txtTopGenre.setText(String.format("Top Genre: %s", dbHelper.getTopGenre()));
        txtAvgRating.setText(String.format(Locale.getDefault(), "Avg Rating: %.1f", dbHelper.getAverageRating()));
    }

    private void setupHabitProgressChart(int position) {
        int pages = 0;
        double hours = 0.0;
        int episodes = 0;
        int safePosition = position < 0 ? 0 : position;

        switch (safePosition) {
            case 0: // Daily
                pages = dbHelper.getDailyPages();
                hours = dbHelper.getDailyMinutes() / 60.0;
                episodes = dbHelper.getDailyEpisodes();
                break;
            case 1: // Weekly
                pages = dbHelper.getWeeklyPages();
                hours = dbHelper.getWeeklyMinutes() / 60.0;
                episodes = dbHelper.getWeeklyEpisodes();
                break;
            case 2: // Monthly
                pages = dbHelper.getMonthlyPages();
                hours = dbHelper.getMonthlyMinutes() / 60.0;
                episodes = dbHelper.getMonthlyEpisodes();
                break;
            case 3: // All-Time
                pages = dbHelper.getTotalPagesRead();
                hours = dbHelper.getTotalMinutesWatched() / 60.0;
                episodes = dbHelper.getTotalEpisodesWatched();
                break;
        }

        int roundedHours = (int) Math.round(hours);

        String chartConfig = "{" +
                "chart: { type: 'column', backgroundColor: 'transparent', spacing: [8, 8, 12, 8], marginLeft: 42, marginRight: 12, marginBottom: 44, options3d: { enabled: true, alpha: 12, beta: 10, depth: 38, viewDistance: 20, frame: { bottom: { size: 1, color: 'rgba(255,255,255,0.08)' }, back: { size: 1, color: 'rgba(255,255,255,0.05)' }, side: { size: 1, color: 'rgba(255,255,255,0.05)' } } } }," +
                "title: { text: '' }," +
                "credits: { enabled: false }," +
                "legend: { enabled: false }," +
                "xAxis: { categories: ['Pages', 'Hours', 'Episodes'], lineColor: '#555', tickColor: '#555', labels: { style: { color: '#E0E0E0', fontSize: '10px' } } }," +
                "yAxis: { min: 0, title: { text: '' }, allowDecimals: false, gridLineColor: 'rgba(255,255,255,0.18)', labels: { style: { color: '#CFCFCF', fontSize: '10px' } } }," +
                "tooltip: { shared: false, backgroundColor: 'rgba(10,10,10,0.92)', borderColor: '#D32F2F', style: { color: '#FFFFFF' }, " +
                "formatter: function() { return this.series.name + ': <b>' + this.y + '</b> ' + this.x.toLowerCase(); } }," +
                "plotOptions: { column: { depth: 24, borderWidth: 0, borderRadius: 4, pointPadding: 0.16, groupPadding: 0.22, maxPointWidth: 54 } }," +
                "series: [{ name: 'Progress', data: [" + pages + ", " + roundedHours + ", " + episodes + "], color: '#D32F2F' }]," +
                "responsive: { rules: [{ condition: { maxWidth: 360 }, chartOptions: { chart: { marginLeft: 36, marginBottom: 38 }, xAxis: { labels: { style: { fontSize: '9px' } } }, yAxis: { labels: { style: { fontSize: '9px' } } } } }] }" +
                "}";
        
        load3DChart(webviewMonthlyActivity, chartConfig);
    }

    private void setupVaultCompositionChart() {
        Map<String, Integer> genreCounts = dbHelper.getGenreCounts();
        int totalItems = 0;
        for (int count : genreCounts.values()) totalItems += count;

        StringBuilder dataJson = new StringBuilder("[");
        int otherCount = 0;
        
        for (Map.Entry<String, Integer> entry : genreCounts.entrySet()) {
            if (totalItems > 0 && (entry.getValue() * 100.0 / totalItems) < 5.0) {
                otherCount += entry.getValue();
            } else if (entry.getValue() > 0) {
                dataJson.append("['").append(escapeForJs(entry.getKey())).append("', ").append(entry.getValue()).append("],");
            }
        }
        
        if (otherCount > 0) {
            dataJson.append("['Other', ").append(otherCount).append("],");
        }
        
        if (dataJson.length() > 1) {
            dataJson.setLength(dataJson.length() - 1);
        }
        dataJson.append("]");

        String chartConfig = "{" +
                "chart: { type: 'pie', backgroundColor: 'transparent', spacing: [8, 8, 8, 8], options3d: { enabled: true, alpha: 42, beta: 0 } }," +
                "title: { text: '' }," +
                "credits: { enabled: false }," +
                "tooltip: { pointFormat: '<b>{point.y}</b> entries', backgroundColor: 'rgba(10,10,10,0.92)', borderColor: '#D32F2F', style: { color: '#FFFFFF' } }," +
                "legend: { enabled: false }," +
                "plotOptions: { pie: { depth: 36, center: ['50%', '56%'], size: '88%', innerSize: '30%', borderWidth: 0, dataLabels: { enabled: true, distance: -18, style: { color: '#FFFFFF', fontSize: '9px', fontWeight: '600', textOutline: 'none' }, formatter: function() { return this.percentage >= 6 ? this.point.name : ''; } } } }," +
                "series: [{ name: 'Genres', colorByPoint: true, data: " + dataJson + " }]," +
                "responsive: { rules: [{ condition: { maxWidth: 360 }, chartOptions: { plotOptions: { pie: { size: '84%', dataLabels: { distance: -14, style: { fontSize: '8px' } } } } } }] }" +
                "}";

        load3DChart(webviewVaultComposition, chartConfig);
    }

    private void load3DChart(WebView webView, String chartConfig) {
        String html = "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "html, body, #container { width:100%; height:100%; margin:0; padding:0; background:transparent; overflow:hidden; }" +
                "body { display:flex; align-items:center; justify-content:center; }" +
                "</style>" +
                "<script src='https://code.highcharts.com/highcharts.js'></script>" +
                "<script src='https://code.highcharts.com/highcharts-3d.js'></script>" +
                "<script src='https://code.highcharts.com/themes/dark-unica.js'></script>" +
                "</head>" +
                "<body>" +
                "<div id='container'></div>" +
                "<script>" +
                "try {" +
                "const chart = Highcharts.chart('container', " + chartConfig + ");" +
                "window.addEventListener('resize', function() { if (chart) { chart.reflow(); } });" +
                "} catch(e) { console.error('Chart error:', e); }" +
                "</script>" +
                "</body>" +
                "</html>";
        
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private String escapeForJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
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
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.id.img_logo != 0 ? R.color.vault_red_primary : android.R.color.holo_blue_light));
        } else {
            txtBacklogStatus.setText("Overwhelming");
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.vault_red_dark));
        }
    }
}
