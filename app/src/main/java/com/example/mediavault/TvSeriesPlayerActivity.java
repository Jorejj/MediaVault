package com.example.mediavault;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mediavault.api.consumet.ResolverFactory;
import com.example.mediavault.api.providers.MediaProvider;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import java.util.Locale;

/**
 * Activity to select and play TV show episodes via Consumet API.
 * Supports season and episode selection with auto-resolution.
 */
public class TvSeriesPlayerActivity extends AppCompatActivity {
    private static final String TAG = "TvSeriesPlayerActivity";
    
    public static final String EXTRA_TV_SHOW_TITLE = "extra_tv_show_title";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_CURRENT_SEASON = "extra_current_season";
    public static final String EXTRA_CURRENT_EPISODE = "extra_current_episode";
    public static final String EXTRA_SOURCE_URL = "extra_source_url";
    
    private EditText etSeasonNumber, etEpisodeNumber;
    private Button btnResolveAndPlay;
    private ProgressBar pbResolving;
    private TextView tvStatus;
    
    private String tvShowTitle;
    private String sourceUrl;
    private int mediaId;
    private int currentSeason;
    private int currentEpisode;
    
    private volatile boolean isDestroyed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_series_player);
        
        tvShowTitle = getIntent().getStringExtra(EXTRA_TV_SHOW_TITLE);
        sourceUrl = normalizeProviderUrl(getIntent().getStringExtra(EXTRA_SOURCE_URL));
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        currentSeason = getIntent().getIntExtra(EXTRA_CURRENT_SEASON, 1);
        currentEpisode = getIntent().getIntExtra(EXTRA_CURRENT_EPISODE, 1);
        
        if (tvShowTitle == null || mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Missing TV show information");
            finish();
            return;
        }
        
        initViews();
    }
    
    private void initViews() {
        TextView tvTitle = findViewById(R.id.tv_tv_series_title);
        tvTitle.setText(tvShowTitle);
        
        etSeasonNumber = findViewById(R.id.et_season_number);
        etSeasonNumber.setText(String.valueOf(currentSeason));
        
        etEpisodeNumber = findViewById(R.id.et_episode_number);
        etEpisodeNumber.setText(String.valueOf(currentEpisode));
        
        btnResolveAndPlay = findViewById(R.id.btn_resolve_and_play_tv);
        pbResolving = findViewById(R.id.pb_resolving_tv);
        tvStatus = findViewById(R.id.tv_status_tv);
        
        btnResolveAndPlay.setOnClickListener(v -> resolveAndPlay());
        
        Button btnCancel = findViewById(R.id.btn_cancel_tv);
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void resolveAndPlay() {
        String seasonText = etSeasonNumber.getText().toString().trim();
        String episodeText = etEpisodeNumber.getText().toString().trim();
        
        if (seasonText.isEmpty() || episodeText.isEmpty()) {
            ToastUtils.showCustomToast(this, "Please enter season and episode numbers");
            return;
        }
        
        int season, episode;
        try {
            season = Integer.parseInt(seasonText);
            episode = Integer.parseInt(episodeText);
            
            if (season < 1 || episode < 1) {
                ToastUtils.showCustomToast(this, "Season and episode must be at least 1");
                return;
            }
        } catch (NumberFormatException e) {
            ToastUtils.showCustomToast(this, "Invalid season/episode numbers");
            return;
        }
        
        // ISSUE #7 FIX: Check network first
        if (!isNetworkAvailable()) {
            ToastUtils.showCustomToast(this, "No internet connection");
            tvStatus.setText(com.example.mediavault.R.string.auto_no_internet_connection);
            tvStatus.setVisibility(View.VISIBLE);
            return;
        }

        chooseProviderThenResolve(season, episode);
    }

    private void chooseProviderThenResolve(int season, int episode) {
        List<String> providerNames = ResolverFactory.getAvailableProvidersForType(MediaProvider.MediaType.TV_SHOW);
        boolean hasSavedSource = sourceUrl != null && !sourceUrl.trim().isEmpty();
        if ((providerNames == null || providerNames.isEmpty()) && !hasSavedSource) {
            ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.TV_SHOW, null);
            executeResolution(season, episode, null);
            return;
        }

        int providerCount = providerNames == null ? 0 : providerNames.size();
        String[] options = new String[providerCount + 1 + (hasSavedSource ? 1 : 0)];
        int index = 0;
        options[index++] = "Auto (recommended)";
        if (hasSavedSource) {
            options[index++] = "Use saved source";
        }
        for (int i = 0; i < providerCount; i++) {
            options[index++] = providerNames.get(i);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose provider")
                .setItems(options, (dialog, which) -> {
                    String choice = options[which];
                    if ("Use saved source".equals(choice)) {
                        String adapted = adaptTvSourceUrl(sourceUrl, season, episode);
                        launchResolvedStream(adapted, season, episode);
                        return;
                    }
                    String selectedProvider = "Auto (recommended)".equals(choice) ? null : choice;
                    ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.TV_SHOW, selectedProvider);
                    executeResolution(season, episode, selectedProvider);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeResolution(int season, int episode, String selectedProvider) {
        // Show loading UI
        btnResolveAndPlay.setEnabled(false);
        pbResolving.setVisibility(View.VISIBLE);
        String providerSuffix = selectedProvider == null ? " (Auto)" : (" (" + selectedProvider + ")");
        tvStatus.setText("Searching for episode..." + providerSuffix);
        tvStatus.setVisibility(View.VISIBLE);

        // Resolve in background
        final int finalSeason = season;
        final int finalEpisode = episode;
        
        AppExecutor.getInstance().networkIO().execute(() -> {
            Log.d(TAG, "Resolving: " + tvShowTitle + " S" + finalSeason + "E" + finalEpisode);
            
            String streamUrl = ResolverFactory.resolveTvShow(tvShowTitle, finalSeason, finalEpisode);
            
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) return; // ISSUE #17 FIX
                
                pbResolving.setVisibility(View.GONE);
                btnResolveAndPlay.setEnabled(true);
                
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    Log.d(TAG, "Resolution successful: " + streamUrl);
                    tvStatus.setText(com.example.mediavault.R.string.auto_launching_player);
                    launchResolvedStream(streamUrl, finalSeason, finalEpisode);
                } else {
                    Log.e(TAG, "Resolution failed");
                    tvStatus.setText(com.example.mediavault.R.string.auto_could_not_find_streaming_link);
                    ToastUtils.showCustomToast(
                        TvSeriesPlayerActivity.this,
                        "Failed to find episode. Check your connection or try a different episode."
                    );
                }
            });
        });
    }

    private void launchResolvedStream(String streamUrl, int season, int episode) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            tvStatus.setText(com.example.mediavault.R.string.auto_could_not_find_streaming_link);
            return;
        }
        if (isDirectPlayableUrl(streamUrl)) {
            Intent playerIntent = new Intent(TvSeriesPlayerActivity.this, PlayerActivity.class);
            playerIntent.putExtra(PlayerActivity.EXTRA_URL, streamUrl);
            playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
            playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_KIND, "Series");
            playerIntent.putExtra(PlayerActivity.EXTRA_TARGET_SEASON, season);
            playerIntent.putExtra(PlayerActivity.EXTRA_TARGET_EPISODE, episode);
            playerIntent.putExtra(PlayerActivity.EXTRA_TARGET_PROGRESS, (float) episode);
            startActivity(playerIntent);
        } else {
            Intent webIntent = new Intent(TvSeriesPlayerActivity.this, EmbeddedWebPlayerActivity.class);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, streamUrl);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, tvShowTitle + " S" + season + "E" + episode);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_FALLBACK_URL, buildExternalFallbackUrl(season, episode));
            startActivity(webIntent);
            ToastUtils.showCustomToast(TvSeriesPlayerActivity.this, "Opened provider fallback in app");
        }
        finish();
    }

    private String adaptTvSourceUrl(String baseUrl, int season, int episode) {
        if (baseUrl == null) return null;
        String trimmed = baseUrl.trim();
        if (trimmed.isEmpty()) return trimmed;
        if (trimmed.matches(".*/\\d+/\\d+(\\?.*)?$")) {
            return trimmed.replaceFirst("/\\d+/\\d+(\\?.*)?$", "/" + season + "/" + episode + "$1");
        }
        return trimmed;
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean isDirectPlayableUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains(".m3u8")
                || lower.matches(".*\\.(mp4|mkv|webm)(\\?.*)?$")
                || lower.startsWith("rtmp://");
    }

    private String buildExternalFallbackUrl(int season, int episode) {
        String encodedTitle = Uri.encode(tvShowTitle == null ? "" : tvShowTitle.trim());
        return "https://xprime.su/search?q=" + encodedTitle + "+season+" + season + "+episode+" + episode;
    }

    private String normalizeProviderUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        normalized = normalized.replace("https://anikai.to", "https://animekai.to");
        normalized = normalized.replace("http://anikai.to", "https://animekai.to");
        return normalized;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }
}
