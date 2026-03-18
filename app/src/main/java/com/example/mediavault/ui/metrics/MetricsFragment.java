package com.example.mediavault.ui.metrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
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
import androidx.core.graphics.ColorUtils;
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
    
    private TextView txtCountCompleted, txtCountOngoing, txtCountPlanning, txtCountDropped;
    private TextView txtBacklogStatus, txtBacklogPercentage, txtTopGenre, txtAvgRating;
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
        
        dbHelper = new DatabaseHelper(requireContext());
        
        webviewMonthlyActivity = view.findViewById(R.id.webview_monthly_activity);
        webviewVaultComposition = view.findViewById(R.id.webview_vault_composition);
        tabLayoutMetrics = view.findViewById(R.id.tab_layout_metrics);
        
        configureWebView(webviewMonthlyActivity);
        configureWebView(webviewVaultComposition);
        setupChartThemePalette();
        
        txtCountCompleted = view.findViewById(R.id.txt_count_completed);
        txtCountOngoing = view.findViewById(R.id.txt_count_ongoing);
        txtCountPlanning = view.findViewById(R.id.txt_count_planning);
        txtCountDropped = view.findViewById(R.id.txt_count_dropped);
        
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
        webView.getSettings().setDomStorageEnabled(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new WebViewClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
    }

    private void refreshData() {
        setupOverviewData();
        setupVaultCompositionChart();
        setupBacklogHealth();
        setupHabitProgressChart(tabLayoutMetrics.getSelectedTabPosition());
    }

    private void setupOverviewData() {
        txtCountCompleted.setText(String.valueOf(dbHelper.getStatusCount("Completed")));
        txtCountOngoing.setText(String.valueOf(dbHelper.getStatusCount("Ongoing")));
        txtCountPlanning.setText(String.valueOf(dbHelper.getStatusCount("Planning")));
        txtCountDropped.setText(String.valueOf(dbHelper.getStatusCount("Dropped")));
        
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
        
        // Use raw values for linear scale
        int pagesVal = pages;
        int hoursVal = roundedHours;
        int episodesVal = episodes;
        
        // Calculate max value to determine scale
        int maxVal = Math.max(pagesVal, Math.max(hoursVal, episodesVal));
        String maxAttr = maxVal == 0 ? "max: 10," : "";

        String chartConfig = "{" +
                "chart: { type: 'column', backgroundColor: 'transparent', spacing: [10, 10, 16, 10], marginLeft: 48, marginRight: 14, marginBottom: 48, options3d: { enabled: true, alpha: 12, beta: 10, depth: 38, viewDistance: 20, frame: { bottom: { size: 1, color: 'rgba(255,255,255,0.08)' }, back: { size: 1, color: 'rgba(255,255,255,0.05)' }, side: { size: 1, color: 'rgba(255,255,255,0.05)' } } } }," +
                "title: { text: '' }," +
                "credits: { enabled: false }," +
                "legend: { enabled: false }," +
                "xAxis: { categories: ['Pages', 'Hours', 'Episodes'], lineColor: '" + chartTextSecondaryHex + "', tickColor: '" + chartTextSecondaryHex + "', labels: { style: { color: '" + chartTextPrimaryHex + "', fontSize: '12px', fontWeight: '600' } } }," +
                "yAxis: { type: 'linear', min: 0, " + maxAttr + " allowDecimals: false, title: { text: '' }, gridLineColor: '" + chartGridLineHex + "', labels: { style: { color: '" + chartTextSecondaryHex + "', fontSize: '12px' }, formatter: function() { var v = this.value; if (v >= 1000000000) return (v / 1000000000).toFixed(0).replace('.0','') + 'B'; if (v >= 1000000) return (v / 1000000).toFixed(0).replace('.0','') + 'M'; if (v >= 1000) return (v / 1000).toFixed(0).replace('.0','') + 'k'; return v; } } }," +
                "tooltip: { enabled: true, shared: true, useHTML: true, followTouchMove: true, backgroundColor: '" + chartTooltipBackgroundHex + "', borderColor: '" + chartAccentHex + "', style: { color: '" + chartTooltipTextHex + "', fontSize: '13px' }, " +
                "formatter: function() { var val = this.points[0].y; return 'Progress: <b>' + val + '</b> ' + String(this.points[0].key).toLowerCase(); } }," +
                "plotOptions: { column: { depth: 30, borderWidth: 0, borderRadius: 5, pointPadding: 0.14, groupPadding: 0.2, maxPointWidth: 56, stickyTracking: false, dataLabels: { enabled: true, color: '" + (chartTextPrimaryHex.equals("#000000") ? "#000000" : "#FFFFFF") + "', inside: false, style: { textOutline: 'none', fontSize: '11px' }, formatter: function() { return this.y; } } } }," +
                "series: [{ name: 'Progress', data: [" + pagesVal + ", " + hoursVal + ", " + episodesVal + "], color: '" + chartAccentHex + "' }]," +
                "responsive: { rules: [{ condition: { maxWidth: 360 }, chartOptions: { chart: { marginLeft: 40, marginBottom: 42 }, xAxis: { labels: { style: { fontSize: '11px' } } }, yAxis: { labels: { style: { fontSize: '11px' } } } } }] }" +
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

    private void load3DChart(WebView webView, String chartConfig) {
        String html = "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                "<style>" +
                "html, body, #container { width:100%; height:100%; margin:0; padding:0; background:transparent; overflow:hidden; touch-action: manipulation; }" +
                "body { display:flex; align-items:center; justify-content:center; -webkit-tap-highlight-color: transparent; }" +
                "#fallback { display:none; color:" + chartTextSecondaryHex + "; font-size:14px; text-align:center; padding:16px; }" +
                "</style>" +
                "<script src='https://code.highcharts.com/highcharts.js'></script>" +
                "<script src='https://code.highcharts.com/highcharts-3d.js'></script>" +
                "</head>" +
                "<body>" +
                "<div id='container'></div>" +
                "<div id='fallback'>Unable to render chart on this device.</div>" +
                "<script>" +
                "try {" +
                "Highcharts.setOptions({ lang: { thousandsSep: ',' } });" +
                "var chart = Highcharts.chart('container', " + chartConfig + ");" +
                "if (!chart) { throw new Error('Chart creation returned null'); }" +
                "window.addEventListener('resize', function() { if (chart) { chart.reflow(); } });" +
                "} catch(e) { console.error('Chart error:', e); }" +
                "if (!document.querySelector('#container svg')) { document.getElementById('container').style.display='none'; document.getElementById('fallback').style.display='block'; }" +
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

    private void setupChartThemePalette() {
        int textPrimary = resolveThemeColor(R.attr.colorTextPrimary);
        int accent = ContextCompat.getColor(requireContext(), R.color.vault_red_primary);

        boolean isLightMode = ColorUtils.calculateLuminance(textPrimary) < 0.5d;

        if (isLightMode) {
            chartTextPrimaryHex = "#1A1A1D"; // Dark Charcoal
            chartTextSecondaryHex = "#4A4A4A"; // Darker Grey
            chartTooltipTextHex = "#FFFFFF";
            chartTooltipBackgroundHex = "rgba(26,26,29,0.95)"; // Dark tooltip
            chartGridLineHex = "rgba(0,0,0,0.12)";
        } else {
            chartTextPrimaryHex = "#E0E0E0"; // Off-white
            chartTextSecondaryHex = "#B0B0B0"; // Light Grey
            chartTooltipTextHex = "#E0E0E0";
            chartTooltipBackgroundHex = "rgba(20,20,20,0.92)"; // Dark tooltip
            chartGridLineHex = "rgba(255,255,255,0.15)";
        }

        chartAccentHex = colorToHex(accent);
    }

    private String colorToHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
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
