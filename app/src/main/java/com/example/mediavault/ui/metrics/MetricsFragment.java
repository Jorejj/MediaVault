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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.mediavault.AppExecutor;
import com.example.mediavault.BuildConfig;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.receiver.DailyGoalReminderReceiver;
import com.example.mediavault.utils.ProgressValueUtils;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

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
    private TextView txtHomeWatchTime, txtHomePagesRead, txtHomeOngoingItems, txtHomeEpisodesWatched, txtHomeBooksCompleted, txtHomeAvgRating;
    private TextView tvMetricsEditGoals, tvMetricsGoalsHeader, tvMetricsGoalsEmpty, tvMetricsCongratulations;
    private LinearLayout layoutMetricsGoalPages, layoutMetricsGoalEpisodes, layoutMetricsGoalMinutes;
    private TextView tvMetricsProgressPages, tvMetricsProgressEpisodes, tvMetricsProgressMinutes;
    private ProgressBar pbMetricsGoalPages, pbMetricsGoalEpisodes, pbMetricsGoalMinutes;

    private String chartTextPrimaryHex = "#FFFFFF";
    private String chartTextSecondaryHex = "#D0D0D0";
    private String chartGridLineHex = "rgba(255,255,255,0.18)";
    private String chartTooltipBackgroundHex = "rgba(10,10,10,0.95)";
    private String chartTooltipTextHex = "#FFFFFF";
    private String chartAccentHex = "#D32F2F";
    
    private DatabaseHelper dbHelper;
    private DailyGoalsManager goalsManager;
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
        goalsManager = DailyGoalsManager.getInstance(requireContext());
        
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

        txtHomeWatchTime = view.findViewById(R.id.txt_home_watch_time);
        txtHomePagesRead = view.findViewById(R.id.txt_home_pages_read);
        txtHomeOngoingItems = view.findViewById(R.id.txt_home_ongoing_items);
        txtHomeEpisodesWatched = view.findViewById(R.id.txt_home_episodes_watched);
        txtHomeBooksCompleted = view.findViewById(R.id.txt_home_books_completed);
        txtHomeAvgRating = view.findViewById(R.id.txt_home_avg_rating);

        tvMetricsEditGoals = view.findViewById(R.id.tv_metrics_edit_goals);
        tvMetricsGoalsHeader = view.findViewById(R.id.tv_metrics_goals_header);
        tvMetricsGoalsEmpty = view.findViewById(R.id.tv_metrics_goals_empty);
        tvMetricsCongratulations = view.findViewById(R.id.tv_metrics_congratulations);
        layoutMetricsGoalPages = view.findViewById(R.id.layout_metrics_goal_pages);
        layoutMetricsGoalEpisodes = view.findViewById(R.id.layout_metrics_goal_episodes);
        layoutMetricsGoalMinutes = view.findViewById(R.id.layout_metrics_goal_minutes);
        tvMetricsProgressPages = view.findViewById(R.id.tv_metrics_progress_pages);
        tvMetricsProgressEpisodes = view.findViewById(R.id.tv_metrics_progress_episodes);
        tvMetricsProgressMinutes = view.findViewById(R.id.tv_metrics_progress_minutes);
        pbMetricsGoalPages = view.findViewById(R.id.pb_metrics_goal_pages);
        pbMetricsGoalEpisodes = view.findViewById(R.id.pb_metrics_goal_episodes);
        pbMetricsGoalMinutes = view.findViewById(R.id.pb_metrics_goal_minutes);

        if (tvMetricsEditGoals != null) {
            tvMetricsEditGoals.setOnClickListener(v -> showEditGoalDialog());
        }

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

                updateQuickTotalsUI();
                updateDailyGoalsCard();

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

    private void updateQuickTotalsUI() {
        float totalMinutes = dbHelper.getTotalMinutesWatched();
        float totalPages = dbHelper.getTotalPagesRead();
        float totalEpisodes = dbHelper.getTotalEpisodesWatched();
        int totalBooks = dbHelper.getCompletedCountByType("Book");
        int ongoingCount = dbHelper.getStatusCount("Ongoing");
        float avgRating = dbHelper.getAverageRating();

        if (txtHomeWatchTime != null) txtHomeWatchTime.setText(formatDuration((int) totalMinutes));
        if (txtHomePagesRead != null) txtHomePagesRead.setText(String.valueOf((int) ProgressValueUtils.normalizeForUnit(totalPages, "Pages")));
        if (txtHomeOngoingItems != null) txtHomeOngoingItems.setText(String.valueOf(ongoingCount));
        if (txtHomeEpisodesWatched != null) txtHomeEpisodesWatched.setText(String.valueOf((int) ProgressValueUtils.normalizeForUnit(totalEpisodes, "Episodes")));
        if (txtHomeBooksCompleted != null) txtHomeBooksCompleted.setText(String.valueOf(totalBooks));
        if (txtHomeAvgRating != null) txtHomeAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "0 mins";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours == 0) return mins + " mins";
        if (mins == 0) return hours == 1 ? "1 hour" : hours + " hours";
        return (hours == 1 ? "1 hour " : hours + " hours ") + mins + " mins";
    }

    private void updateDailyGoalsCard() {
        if (goalsManager == null || dbHelper == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DailyProgress progress = dbHelper.getDailyProgress(today);

        boolean hasAnyGoal = false;
        boolean allGoalsMet = true;

        int goalPages = goalsManager.getGoalPages();
        if (goalPages > 0) {
            layoutMetricsGoalPages.setVisibility(View.VISIBLE);
            pbMetricsGoalPages.setMax(goalPages);
            int normalizedPages = (int) ProgressValueUtils.normalizeForUnit(progress.pagesRead, "Pages");
            pbMetricsGoalPages.setProgress(normalizedPages);
            tvMetricsProgressPages.setText(String.format(Locale.getDefault(), "%d/%d", normalizedPages, goalPages));
            hasAnyGoal = true;
            if (normalizedPages < goalPages) allGoalsMet = false;
        } else {
            layoutMetricsGoalPages.setVisibility(View.GONE);
        }

        int goalEpisodes = goalsManager.getGoalEpisodes();
        if (goalEpisodes > 0) {
            layoutMetricsGoalEpisodes.setVisibility(View.VISIBLE);
            pbMetricsGoalEpisodes.setMax(goalEpisodes);
            int normalizedEpisodes = (int) ProgressValueUtils.normalizeForUnit(progress.episodesWatched, "Episodes");
            pbMetricsGoalEpisodes.setProgress(normalizedEpisodes);
            tvMetricsProgressEpisodes.setText(String.format(Locale.getDefault(), "%d/%d", normalizedEpisodes, goalEpisodes));
            hasAnyGoal = true;
            if (normalizedEpisodes < goalEpisodes) allGoalsMet = false;
        } else {
            layoutMetricsGoalEpisodes.setVisibility(View.GONE);
        }

        int goalMinutes = goalsManager.getGoalMinutes();
        if (goalMinutes > 0) {
            layoutMetricsGoalMinutes.setVisibility(View.VISIBLE);
            pbMetricsGoalMinutes.setMax(goalMinutes);
            int normalizedMinutes = Math.max(0, Math.round(progress.minutesWatched));
            pbMetricsGoalMinutes.setProgress(normalizedMinutes);
            tvMetricsProgressMinutes.setText(String.format(Locale.getDefault(), "%d/%d", normalizedMinutes, goalMinutes));
            hasAnyGoal = true;
            if (normalizedMinutes < goalMinutes) allGoalsMet = false;
        } else {
            layoutMetricsGoalMinutes.setVisibility(View.GONE);
        }

        if (hasAnyGoal) {
            tvMetricsGoalsEmpty.setVisibility(View.GONE);
            if (allGoalsMet) {
                tvMetricsCongratulations.setVisibility(View.VISIBLE);
                tvMetricsGoalsHeader.setText(com.example.mediavault.R.string.auto_goal_achieved);
                tvMetricsGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
            } else {
                tvMetricsCongratulations.setVisibility(View.GONE);
                tvMetricsGoalsHeader.setText(com.example.mediavault.R.string.auto_today_s_progress_2);
                tvMetricsGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            }
        } else {
            tvMetricsGoalsEmpty.setVisibility(View.VISIBLE);
            tvMetricsCongratulations.setVisibility(View.GONE);
            tvMetricsGoalsHeader.setText(com.example.mediavault.R.string.auto_daily_goals);
        }
    }

    private void showEditGoalDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_daily_goals, null);
        TextInputEditText etPages = dialogView.findViewById(R.id.et_goal_pages);
        TextInputEditText etEpisodes = dialogView.findViewById(R.id.et_goal_episodes);
        TextInputEditText etMinutes = dialogView.findViewById(R.id.et_goal_minutes);
        TextView tvReminderTime = dialogView.findViewById(R.id.tv_reminder_time);
        com.google.android.material.switchmaterial.SwitchMaterial switchReminder = dialogView.findViewById(R.id.switch_reminder);
        com.google.android.material.button.MaterialButton btnTestReminderNow = dialogView.findViewById(R.id.btn_test_reminder_now);

        int currentPages = goalsManager.getGoalPages();
        int currentEpisodes = goalsManager.getGoalEpisodes();
        int currentMinutes = goalsManager.getGoalMinutes();
        if (currentPages > 0) etPages.setText(String.valueOf(currentPages));
        if (currentEpisodes > 0) etEpisodes.setText(String.valueOf(currentEpisodes));
        if (currentMinutes > 0) etMinutes.setText(String.valueOf(currentMinutes));

        final int[] reminderTime = {goalsManager.getReminderHour(), goalsManager.getReminderMinute()};
        switchReminder.setChecked(goalsManager.isReminderEnabled());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, reminderTime[0]);
        calendar.set(Calendar.MINUTE, reminderTime[1]);
        tvReminderTime.setText(sdf.format(calendar.getTime()));
        tvReminderTime.setEnabled(switchReminder.isChecked());
        tvReminderTime.setAlpha(switchReminder.isChecked() ? 1.0f : 0.5f);
        switchReminder.setOnCheckedChangeListener((buttonView, checked) -> {
            tvReminderTime.setEnabled(checked);
            tvReminderTime.setAlpha(checked ? 1.0f : 0.5f);
        });
        tvReminderTime.setOnClickListener(v -> {
            if (!switchReminder.isChecked()) return;
            new android.app.TimePickerDialog(requireContext(), (picker, hourOfDay, minute) -> {
                reminderTime[0] = hourOfDay;
                reminderTime[1] = minute;
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                tvReminderTime.setText(sdf.format(cal.getTime()));
            }, reminderTime[0], reminderTime[1], false).show();
        });
        if (btnTestReminderNow != null) {
            if (!BuildConfig.DEBUG) {
                btnTestReminderNow.setVisibility(View.GONE);
            } else {
                btnTestReminderNow.setOnClickListener(v -> {
                    Intent testIntent = new Intent(DailyGoalReminderReceiver.ACTION_TEST_REMINDER);
                    testIntent.setClass(requireContext(), DailyGoalReminderReceiver.class);
                    requireContext().sendBroadcast(testIntent);
                    ToastUtils.showCustomToast(requireContext(), "Test reminder sent");
                });
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set Daily Goals")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        int pagesGoal = parseNonNegativeGoal(etPages.getText() == null ? "" : etPages.getText().toString());
                        int episodesGoal = parseNonNegativeGoal(etEpisodes.getText() == null ? "" : etEpisodes.getText().toString());
                        int minutesGoal = parseNonNegativeGoal(etMinutes.getText() == null ? "" : etMinutes.getText().toString());
                        if (minutesGoal > 1440) minutesGoal = 1440;

                        goalsManager.setGoalPages(pagesGoal);
                        goalsManager.setGoalEpisodes(episodesGoal);
                        goalsManager.setGoalMinutes(minutesGoal);
                        goalsManager.setReminderEnabled(switchReminder.isChecked());
                        goalsManager.setReminderTime(reminderTime[0], reminderTime[1]);
                        DailyGoalReminderReceiver.scheduleNextReminder(requireContext(), goalsManager);
                        refreshData();
                        ToastUtils.showCustomToast(requireContext(), "Daily goals updated");
                    } catch (NumberFormatException e) {
                        ToastUtils.showCustomToast(requireContext(), "Invalid number format");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int parseNonNegativeGoal(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        return Math.max(0, Integer.parseInt(raw.trim()));
    }
}
