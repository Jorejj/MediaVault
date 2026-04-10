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
 * Activity to resolve and play movies via Consumet API.
 * Automatically resolves streaming links and launches PlayerActivity.
 */
public class MoviePlayerActivity extends AppCompatActivity {
    private static final String TAG = "MoviePlayerActivity";
    
    public static final String EXTRA_MOVIE_TITLE = "extra_movie_title";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_YEAR = "extra_year";
    public static final String EXTRA_SOURCE_URL = "extra_source_url";
    
    private ProgressBar pbResolving;
    private TextView tvStatus;
    private Button btnRetry;
    
    private String movieTitle;
    private String sourceUrl;
    private int mediaId;
    private int year;
    
    // ISSUE #17 FIX: Track activity lifecycle
    private volatile boolean isDestroyed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_player);
        
        movieTitle = getIntent().getStringExtra(EXTRA_MOVIE_TITLE);
        sourceUrl = normalizeProviderUrl(getIntent().getStringExtra(EXTRA_SOURCE_URL));
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        year = getIntent().getIntExtra(EXTRA_YEAR, 0);
        
        if (movieTitle == null || mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Missing movie information");
            finish();
            return;
        }
        
        initViews();
        chooseProviderThenResolve();
    }
    
    private void initViews() {
        TextView tvTitle = findViewById(R.id.tv_movie_title);
        tvTitle.setText(movieTitle);
        
        pbResolving = findViewById(R.id.pb_resolving_movie);
        tvStatus = findViewById(R.id.tv_status_movie);
        btnRetry = findViewById(R.id.btn_retry_movie);
        
        btnRetry.setOnClickListener(v -> chooseProviderThenResolve());
        
        Button btnCancel = findViewById(R.id.btn_cancel_movie);
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void chooseProviderThenResolve() {
        List<String> providerNames = ResolverFactory.getAvailableProvidersForType(MediaProvider.MediaType.MOVIE);
        boolean hasSavedSource = sourceUrl != null && !sourceUrl.trim().isEmpty();
        if ((providerNames == null || providerNames.isEmpty()) && !hasSavedSource) {
            ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.MOVIE, null);
            resolveAndPlay(null);
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
                        launchResolvedStream(sourceUrl);
                        return;
                    }
                    String selectedProvider = "Auto (recommended)".equals(choice) ? null : choice;
                    ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.MOVIE, selectedProvider);
                    resolveAndPlay(selectedProvider);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resolveAndPlay(String selectedProvider) {
        // ISSUE #7 FIX: Check network connectivity first
        if (!isNetworkAvailable()) {
            ToastUtils.showCustomToast(this, "No internet connection. Please check your network.");
            tvStatus.setText(com.example.mediavault.R.string.auto_no_internet_connection);
            tvStatus.setVisibility(View.VISIBLE);
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }
        
        // Show loading UI
        pbResolving.setVisibility(View.VISIBLE);
        String providerSuffix = selectedProvider == null ? " (Auto)" : (" (" + selectedProvider + ")");
        tvStatus.setText("Finding streaming link..." + providerSuffix);
        tvStatus.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);
        
        // Resolve in background
        AppExecutor.getInstance().networkIO().execute(() -> {
            Log.d(TAG, "Resolving movie: " + movieTitle + (year > 0 ? " (" + year + ")" : ""));
            
            String streamUrl = ResolverFactory.resolveMovie(movieTitle, year);
            
            AppExecutor.getInstance().mainThread().execute(() -> {
                // ISSUE #17 FIX: Don't update UI if activity destroyed
                if (isDestroyed) return;
                
                pbResolving.setVisibility(View.GONE);
                
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    Log.d(TAG, "Resolution successful: " + streamUrl);
                    tvStatus.setText(com.example.mediavault.R.string.auto_launching_player);
                    launchResolvedStream(streamUrl);
                } else {
                    Log.e(TAG, "Resolution failed");
                    tvStatus.setText(com.example.mediavault.R.string.auto_could_not_find_streaming_link);
                    btnRetry.setVisibility(View.VISIBLE);
                    ToastUtils.showCustomToast(
                        MoviePlayerActivity.this,
                        "Failed to find streaming link. Check your connection."
                    );
                }
            });
        });
    }

    private void launchResolvedStream(String streamUrl) {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            tvStatus.setText(com.example.mediavault.R.string.auto_could_not_find_streaming_link);
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }
        if (isDirectPlayableUrl(streamUrl)) {
            Intent playerIntent = new Intent(MoviePlayerActivity.this, PlayerActivity.class);
            playerIntent.putExtra(PlayerActivity.EXTRA_URL, streamUrl);
            playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
            playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_KIND, "Movie");
            playerIntent.putExtra(PlayerActivity.EXTRA_TARGET_PROGRESS, 1f);
            startActivity(playerIntent);
        } else {
            Intent webIntent = new Intent(MoviePlayerActivity.this, EmbeddedWebPlayerActivity.class);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, streamUrl);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, movieTitle);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_FALLBACK_URL, buildExternalFallbackUrl());
            startActivity(webIntent);
            ToastUtils.showCustomToast(MoviePlayerActivity.this, "Opened provider fallback in app");
        }
        finish();
    }
    
    // ISSUE #7 FIX: Network connectivity helper
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

    private String buildExternalFallbackUrl() {
        String encodedTitle = Uri.encode(movieTitle == null ? "" : movieTitle.trim());
        return "https://nepu.to/search?q=" + encodedTitle + (year > 0 ? "+" + year : "");
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
        // ISSUE #17 FIX: Mark as destroyed to prevent UI updates
        isDestroyed = true;
    }
}
