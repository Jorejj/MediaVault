package com.example.mediavault.ui.metrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.mediavault.AppExecutor;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MetricsFragment extends Fragment {
    private static final String TAG = "MetricsFragment";

    private WebView webviewMonthlyActivity;
    private WebView webviewVaultComposition;
    private TabLayout tabLayoutMetrics;
    
    private TextView txtCountCompleted, txtCountOngoing, txtCountPlanning, txtCountDropped;
    private TextView txtBacklogStatus, txtBacklogPercentage, txtTopGenre, txtAvgRating;
    private TextView txtStreakSnapshotSummary;
    private TextView[] streakDayViews;
    private ProgressBar progressBacklogHealth;

    private String chartTextPrimaryHex = "#FFFFFF";
    private String chartTextSecondaryHex = "#D0D0D0";
    private String chartGridLineHex = "rgba(255,255,255,0.18)";
    private String chartTooltipBackgroundHex = "rgba(10,10,10,0.95)";
    private String chartTooltipTextHex = "#FFFFFF";
    private String chartAccentHex = "#D32F2F";
    
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
        
        dbHelper = DatabaseHelper.getInstance(requireContext());
        
        webviewMonthlyActivity = view.findViewById(R.id.webview_monthly_activity);
        webviewVaultComposition = view.findViewById(R.id.webview_vault_composition);
        tabLayoutMetrics = view.findViewById(R.id.tab_layout_metrics);
        
        configureWebView(webviewMonthlyActivity);
        configureWebView(webviewVaultComposition);
        setupChartThemePalette();
        
        // Overview
        txtCountCompleted = view.findViewById(R.id.txt_count_completed);
        txtCountOngoing = view.findViewById(R.id.txt_count_ongoing);
        txtCountPlanning = view.findViewById(R.id.txt_count_planning);
        txtCountDropped = view.findViewById(R.id.txt_count_dropped);
        
        // Backlog Health
        txtBacklogStatus = view.findViewById(R.id.txt_backlog_status);
        txtBacklogPercentage = view.findViewById(R.id.txt_backlog_percentage);
        progressBacklogHealth = view.findViewById(R.id.progress_backlog_health);
        
        // Genre & Rating
        txtTopGenre = view.findViewById(R.id.txt_top_genre);
        txtAvgRating = view.findViewById(R.id.txt_avg_rating);
        txtStreakSnapshotSummary = view.findViewById(R.id.txt_streak_snapshot_summary);
        streakDayViews = new TextView[]{
                view.findViewById(R.id.txt_streak_day_0),
                view.findViewById(R.id.txt_streak_day_1),
                view.findViewById(R.id.txt_streak_day_2),
                view.findViewById(R.id.txt_streak_day_3),
                view.findViewById(R.id.txt_streak_day_4),
                view.findViewById(R.id.txt_streak_day_5),
                view.findViewById(R.id.txt_streak_day_6)
        };

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

    @Override
    public void onDestroyView() {
        if (webviewMonthlyActivity != null) {
            webviewMonthlyActivity.stopLoading();
            webviewMonthlyActivity.loadUrl("about:blank");
            webviewMonthlyActivity.clearHistory();
            webviewMonthlyActivity.removeAllViews();
            webviewMonthlyActivity.destroy();
            webviewMonthlyActivity = null;
        }
        if (webviewVaultComposition != null) {
            webviewVaultComposition.stopLoading();
            webviewVaultComposition.loadUrl("about:blank");
            webviewVaultComposition.clearHistory();
            webviewVaultComposition.removeAllViews();
            webviewVaultComposition.destroy();
            webviewVaultComposition = null;
        }
        super.onDestroyView();
    }

    private void configureWebView(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new WebViewClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
    }

    private void refreshData() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            int completedCount = dbHelper.getStatusCount("Completed");
            int ongoingCount = dbHelper.getStatusCount("Ongoing");
            int planningCount = dbHelper.getStatusCount("Planning");
            int droppedCount = dbHelper.getStatusCount("Dropped");
            String topGenre = dbHelper.getTopGenre();
            float avgRating = dbHelper.getAverageRating();

            int selectedTab = tabLayoutMetrics.getSelectedTabPosition();
            float pages = 0;
            double hours = 0.0;
            float episodes = 0;

            switch (selectedTab) {
                case 0:
                    pages = dbHelper.getDailyPages();
                    hours = dbHelper.getDailyMinutes() / 60.0;
                    episodes = dbHelper.getDailyEpisodes();
                    break;
                case 1:
                    pages = dbHelper.getWeeklyPages();
                    hours = dbHelper.getWeeklyMinutes() / 60.0;
                    episodes = dbHelper.getWeeklyEpisodes();
                    break;
                case 2:
                    pages = dbHelper.getMonthlyPages();
                    hours = dbHelper.getMonthlyMinutes() / 60.0;
                    episodes = dbHelper.getMonthlyEpisodes();
                    break;
                case 3:
                    pages = dbHelper.getTotalPagesRead();
                    hours = dbHelper.getTotalMinutesWatched() / 60.0;
                    episodes = dbHelper.getTotalEpisodesWatched();
                    break;
            }

            Map<String, Integer> genreCounts = dbHelper.getGenreCounts();
            int[] streakHistory = dbHelper.getRecentGoalHistory(7);

            final int finalPages = Math.round(pages);
            final double finalHours = hours;
            final int finalEpisodes = Math.round(episodes);
            final int[] finalStreakHistory = streakHistory;

            AppExecutor.getInstance().mainThread().execute(() -> {
                if (!isAdded()) return;

                txtCountCompleted.setText(String.valueOf(completedCount));
                txtCountOngoing.setText(String.valueOf(ongoingCount));
                txtCountPlanning.setText(String.valueOf(planningCount));
                txtCountDropped.setText(String.valueOf(droppedCount));
                txtTopGenre.setText(String.format("Top Genre: %s", topGenre));
                txtAvgRating.setText(String.format(Locale.getDefault(), "Avg Rating: %.1f", avgRating));

                updateBacklogHealthUI(completedCount, planningCount, ongoingCount);
                renderHabitChart(finalPages, finalHours, finalEpisodes);
                renderCompositionChart(genreCounts);
                renderStreakSnapshot(finalStreakHistory);
            });
        });
    }

    private void renderStreakSnapshot(int[] history) {
        if (history == null || history.length == 0 || streakDayViews == null) {
            return;
        }

        int metCount = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -(history.length - 1));

        for (int i = 0; i < history.length && i < streakDayViews.length; i++) {
            TextView cell = streakDayViews[i];
            if (cell == null) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                continue;
            }

            if (history[i] == 1) {
                metCount++;
            }

            String label = new SimpleDateFormat("E", Locale.getDefault()).format(calendar.getTime());
            String dayInitial = label.isEmpty() ? "-" : label.substring(0, 1).toUpperCase(Locale.getDefault());
            String mark = history[i] == 1 ? "✓" : "·";
            cell.setText(dayInitial + "\n" + mark);
            cell.setTextColor(ContextCompat.getColor(requireContext(),
                    history[i] == 1 ? R.color.accent_green : R.color.text_secondary));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (txtStreakSnapshotSummary != null) {
            txtStreakSnapshotSummary.setText(
                    String.format(Locale.getDefault(), "Last 7 nights: %d / %d goal nights met", metCount, history.length)
            );
        }
    }

    private void updateBacklogHealthUI(int completed, int planning, int ongoing) {
        int total = completed + planning + ongoing;
        int progress = (total > 0) ? (completed * 100 / total) : 0;
        progressBacklogHealth.setProgress(progress);
        txtBacklogPercentage.setText(String.format(Locale.getDefault(), "%d%% Completed", progress));
        
        if (progress >= 70) {
            txtBacklogStatus.setText(com.example.mediavault.R.string.auto_excellent);
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
        } else if (progress >= 40) {
            txtBacklogStatus.setText(com.example.mediavault.R.string.backlog_status_healthy);
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.vault_red_primary));
        } else {
            txtBacklogStatus.setText(com.example.mediavault.R.string.backlog_status_overwhelming);
            txtBacklogStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.vault_red_dark));
        }
    }

    private void renderHabitChart(int pages, double hours, int episodes) {
        double roundedHours = Math.round(hours * 10.0) / 10.0;
        double maxVal = Math.max(pages, Math.max(roundedHours, episodes));
        String maxAttr = maxVal == 0 ? "max: 10," : "";

        String chartConfig = "{" +
                "chart: { type: 'column', backgroundColor: 'transparent', spacing: [10, 10, 16, 10], marginLeft: 48, marginRight: 14, marginBottom: 48, options3d: { enabled: true, alpha: 12, beta: 10, depth: 38, viewDistance: 20, frame: { bottom: { size: 1, color: 'rgba(255,255,255,0.08)' }, back: { size: 1, color: 'rgba(255,255,255,0.05)' }, side: { size: 1, color: 'rgba(255,255,255,0.05)' } } } }," +
                "title: { text: '' }," +
                "credits: { enabled: false }," +
                "legend: { enabled: false }," +
                "xAxis: { categories: ['Pages', 'Hours', 'Episodes'], lineColor: '" + chartTextSecondaryHex + "', tickColor: '" + chartTextSecondaryHex + "', labels: { style: { color: '" + chartTextPrimaryHex + "', fontSize: '12px', fontWeight: '600' } } }," +
                "yAxis: { type: 'linear', min: 0, " + maxAttr + " allowDecimals: true, title: { text: '' }, gridLineColor: '" + chartGridLineHex + "', labels: { style: { color: '" + chartTextSecondaryHex + "', fontSize: '12px' }, formatter: function() { var v = this.value; if (v >= 1000000000) return (v / 1000000000).toFixed(1).replace('.0','') + 'B'; if (v >= 1000000) return (v / 1000000).toFixed(1).replace('.0','') + 'M'; if (v >= 1000) return (v / 1000).toFixed(1).replace('.0','') + 'k'; return v; } } }," +
                "tooltip: { enabled: true, shared: true, useHTML: true, followTouchMove: true, backgroundColor: '" + chartTooltipBackgroundHex + "', borderColor: '" + chartAccentHex + "', style: { color: '" + chartTooltipTextHex + "', fontSize: '13px' }, " +
                "formatter: function() { var key = String(this.points[0].key); var val = this.points[0].y; if (key === 'Hours') { val = Number(val).toFixed(1).replace('.0',''); } else { val = Math.round(val); } return 'Progress: <b>' + val + '</b> ' + key.toLowerCase(); } }," +
                "plotOptions: { column: { depth: 30, borderWidth: 0, borderRadius: 5, pointPadding: 0.14, groupPadding: 0.2, maxPointWidth: 56, stickyTracking: false, dataLabels: { enabled: true, color: '" + (chartTextPrimaryHex.equals("#000000") ? "#000000" : "#FFFFFF") + "', inside: false, style: { textOutline: 'none', fontSize: '11px' }, formatter: function() { var key = String(this.point.category); if (key === 'Hours') { return Number(this.y).toFixed(1).replace('.0',''); } return Math.round(this.y); } } } }," +
                "series: [{ name: 'Progress', data: [" + pages + ", " + roundedHours + ", " + episodes + "], color: '" + chartAccentHex + "' }]," +
                "responsive: { rules: [{ condition: { maxWidth: 360 }, chartOptions: { chart: { marginLeft: 40, marginBottom: 42 }, xAxis: { labels: { style: { fontSize: '11px' } } }, yAxis: { labels: { style: { fontSize: '11px' } } } } }] }" +
                "}";
        
        load3DChart(webviewMonthlyActivity, chartConfig);
    }

    private void renderCompositionChart(Map<String, Integer> genreCounts) {
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
                "chart: { type: 'pie', backgroundColor: 'transparent', spacing: [10, 10, 10, 10], options3d: { enabled: true, alpha: 45, beta: 0 } }," +
                "title: { text: '' }," +
                "credits: { enabled: false }," +
                "tooltip: { enabled: true, followTouchMove: true, pointFormat: '<b>{point.y}</b> entries', backgroundColor: '" + chartTooltipBackgroundHex + "', borderColor: '" + chartAccentHex + "', style: { color: '" + chartTooltipTextHex + "', fontSize: '13px' } }," +
                "legend: { enabled: true, itemStyle: { color: '" + chartTextSecondaryHex + "' } }," +
                "plotOptions: { pie: { showInLegend: true, depth: 40, center: ['50%', '55%'], size: '88%', innerSize: '30%', borderWidth: 0, dataLabels: { enabled: true, distance: -18, style: { color: '" + chartTextPrimaryHex + "', fontSize: '11px', fontWeight: '600', textOutline: '1px contrast' }, formatter: function() { return this.percentage >= 5 ? '<b>' + this.point.name + '</b>: ' + Math.round(this.percentage) + '%' : ''; } } } }," +
                "series: [{ name: 'Genres', colorByPoint: true, data: " + dataJson + " }]," +
                "responsive: { rules: [{ condition: { maxWidth: 360 }, chartOptions: { plotOptions: { pie: { size: '84%', dataLabels: { distance: -14, style: { fontSize: '10px' } } } } } }] }" +
                "}";

        load3DChart(webviewVaultComposition, chartConfig);
    }

    private void load3DChart(WebView webView, String config) {
        String html = "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no'>" +
                "<script src='https://code.highcharts.com/highcharts.js'></script>" +
                "<script src='https://code.highcharts.com/highcharts-3d.js'></script>" +
                "<style>body { margin: 0; padding: 0; background: transparent; overflow: hidden; } #container { width: 100vw; height: 100vh; }</style>" +
                "</head><body><div id='container'></div>" +
                "<script>Highcharts.chart('container', " + config + ");</script>" +
                "</body></html>";
        webView.loadDataWithBaseURL("https://highcharts.com", html, "text/html", "UTF-8", null);
    }

    private String escapeForJs(String input) {
        if (input == null) return "";
        return input.replace("'", "\\'");
    }

    private void setupChartThemePalette() {
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isDark) {
            chartTextPrimaryHex = "#FFFFFF";
            chartTextSecondaryHex = "#A0A0A0";
            chartGridLineHex = "rgba(255,255,255,0.12)";
            chartTooltipBackgroundHex = "rgba(20,20,20,0.9)";
            chartTooltipTextHex = "#FFFFFF";
            chartAccentHex = "#BC4B51";
        } else {
            chartTextPrimaryHex = "#1E293B";
            chartTextSecondaryHex = "#64748B";
            chartGridLineHex = "rgba(0,0,0,0.08)";
            chartTooltipBackgroundHex = "rgba(255,255,255,0.95)";
            chartTooltipTextHex = "#1E293B";
            chartAccentHex = "#BC4B51";
        }
    }

    private void setupHabitProgressChart(int position) {
        refreshData();
    }
}
