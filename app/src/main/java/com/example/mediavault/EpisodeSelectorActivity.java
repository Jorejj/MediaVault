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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mediavault.api.consumet.ResolverFactory;
import com.example.mediavault.api.providers.MediaProvider;
import com.example.mediavault.api.providers.ProviderManager;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import java.util.Locale;

/**
 * Activity to resolve and play anime episodes.
 * Demonstrates Consumet API integration for streaming link resolution.
 */
public class EpisodeSelectorActivity extends AppCompatActivity {
    private static final String TAG = "EpisodeSelectorActivity";
    
    public static final String EXTRA_ANIME_TITLE = "extra_anime_title";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_CURRENT_EPISODE = "extra_current_episode";
    
    private EditText etEpisodeNumber;
    private Button btnResolveAndPlay;
    private ProgressBar pbResolving;
    private TextView tvStatus;
    private EpisodeTableAdapter episodeTableAdapter;
    
    private String animeTitle;
    private int mediaId;
    private int currentEpisode;
    
    private volatile boolean isDestroyed = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_episode_selector);
        
        animeTitle = getIntent().getStringExtra(EXTRA_ANIME_TITLE);
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        currentEpisode = getIntent().getIntExtra(EXTRA_CURRENT_EPISODE, 1);
        
        if (animeTitle == null || mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Missing anime information");
            finish();
            return;
        }
        
        initViews();
        loadEpisodeList();
    }
    
    private void initViews() {
        TextView tvTitle = findViewById(R.id.tv_episode_selector_title);
        tvTitle.setText(animeTitle);
        
        etEpisodeNumber = findViewById(R.id.et_episode_number);
        etEpisodeNumber.setText(String.valueOf(currentEpisode));
        
        btnResolveAndPlay = findViewById(R.id.btn_resolve_and_play);
        pbResolving = findViewById(R.id.pb_resolving);
        tvStatus = findViewById(R.id.tv_status);

        RecyclerView rvEpisodeTable = findViewById(R.id.rv_episode_table);
        rvEpisodeTable.setLayoutManager(new LinearLayoutManager(this));
        episodeTableAdapter = new EpisodeTableAdapter(currentEpisode, this::onEpisodeSelected);
        rvEpisodeTable.setAdapter(episodeTableAdapter);
        
        btnResolveAndPlay.setOnClickListener(v -> resolveAndPlay());
        
        Button btnCancel = findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadEpisodeList() {
        if (!isNetworkAvailable()) {
            tvStatus.setText(com.example.mediavault.R.string.auto_no_internet_connection_episode_table_unavailable);
            tvStatus.setVisibility(View.VISIBLE);
            return;
        }

        tvStatus.setText(com.example.mediavault.R.string.auto_fetching_episode_list);
        tvStatus.setVisibility(View.VISIBLE);

        // Set provider status callback for UI updates
        ResolverFactory.setStatusCallback(new ProviderManager.ProviderStatusCallback() {
            @Override
            public void onProviderTrying(String providerName) {
                runOnUiThread(() -> {
                    if (!isDestroyed) {
                        tvStatus.setText("Trying " + providerName + "...");
                    }
                });
            }

            @Override
            public void onProviderSuccess(String providerName) {
                runOnUiThread(() -> {
                    if (!isDestroyed) {
                        tvStatus.setText("Connected via " + providerName);
                    }
                });
            }

            @Override
            public void onProviderFailed(String providerName, String reason) {
                runOnUiThread(() -> {
                    if (!isDestroyed) {
                        tvStatus.setText(providerName + " failed, trying next...");
                    }
                });
            }
        });

        AppExecutor.getInstance().networkIO().execute(() -> {
            List<MediaProvider.EpisodeInfo> episodes = ResolverFactory.fetchAnimeEpisodes(animeTitle);
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) return;
                if (episodes == null || episodes.isEmpty()) {
                    tvStatus.setText(com.example.mediavault.R.string.auto_could_not_fetch_episode_list_you_can_still_enter);
                } else {
                    String provider = ResolverFactory.getLastSuccessfulAnimeProvider();
                    tvStatus.setText("Tap any episode row to watch" + 
                        (provider != null ? " (via " + provider + ")" : ""));
                    episodeTableAdapter.setEpisodes(episodes);
                }
            });
        });
    }

    private void onEpisodeSelected(int episodeNumber) {
        etEpisodeNumber.setText(String.valueOf(episodeNumber));
        resolveAndPlay();
    }
    
    private void resolveAndPlay() {
        String episodeText = etEpisodeNumber.getText().toString().trim();
        if (episodeText.isEmpty()) {
            ToastUtils.showCustomToast(this, "Please enter an episode number");
            return;
        }
        
        int episodeNumber;
        try {
            episodeNumber = Integer.parseInt(episodeText);
            if (episodeNumber < 1) {
                ToastUtils.showCustomToast(this, "Episode number must be at least 1");
                return;
            }
        } catch (NumberFormatException e) {
            ToastUtils.showCustomToast(this, "Invalid episode number");
            return;
        }
        
        // ISSUE #7 FIX: Check network first
        if (!isNetworkAvailable()) {
            ToastUtils.showCustomToast(this, "No internet connection");
            tvStatus.setText(com.example.mediavault.R.string.auto_no_internet_connection);
            tvStatus.setVisibility(View.VISIBLE);
            return;
        }

        chooseProviderThenResolve(episodeNumber);
    }

    private void chooseProviderThenResolve(int episodeNumber) {
        List<String> providerNames = ResolverFactory.getAvailableProvidersForType(MediaProvider.MediaType.ANIME);
        if (providerNames == null || providerNames.isEmpty()) {
            ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.ANIME, null);
            executeResolution(episodeNumber, null);
            return;
        }

        String[] options = new String[providerNames.size() + 1];
        options[0] = "Auto (recommended)";
        for (int i = 0; i < providerNames.size(); i++) {
            options[i + 1] = providerNames.get(i);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose provider")
                .setItems(options, (dialog, which) -> {
                    String selectedProvider = which == 0 ? null : options[which];
                    ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.ANIME, selectedProvider);
                    executeResolution(episodeNumber, selectedProvider);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeResolution(int episodeNumber, String selectedProvider) {
        // Show loading UI
        btnResolveAndPlay.setEnabled(false);
        pbResolving.setVisibility(View.VISIBLE);
        String providerSuffix = selectedProvider == null ? " (Auto)" : (" (" + selectedProvider + ")");
        tvStatus.setText("Searching for streaming link..." + providerSuffix);
        tvStatus.setVisibility(View.VISIBLE);

        // Resolve in background
        final int episode = episodeNumber;
        AppExecutor.getInstance().networkIO().execute(() -> {
            Log.d(TAG, "Resolving: " + animeTitle + " Episode " + episode);
            
            // Use resolveWithProvider to get provider info
            ProviderManager.ProviderResult result = ResolverFactory.resolveAnimeWithProvider(animeTitle, episode);
            String streamUrl = result != null ? result.url : null;
            String providerName = result != null ? result.providerName : null;
            
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) return; // ISSUE #17 FIX
                
                pbResolving.setVisibility(View.GONE);
                btnResolveAndPlay.setEnabled(true);
                
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    Log.d(TAG, "Resolution successful via " + providerName + ": " + streamUrl);
                    tvStatus.setText("Found via " + providerName + "! Launching player...");

                    if (isDirectPlayableUrl(streamUrl)) {
                        // Launch ExoPlayer for direct media sources
                        Intent playerIntent = new Intent(EpisodeSelectorActivity.this, PlayerActivity.class);
                        playerIntent.putExtra(PlayerActivity.EXTRA_URL, streamUrl);
                        playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
                        playerIntent.putExtra(PlayerActivity.EXTRA_MEDIA_KIND, "Anime");
                        playerIntent.putExtra(PlayerActivity.EXTRA_TARGET_PROGRESS, (float) episode);
                        startActivity(playerIntent);
                    } else {
                        Intent webIntent = new Intent(EpisodeSelectorActivity.this, EmbeddedWebPlayerActivity.class);
                        webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, streamUrl);
                        webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, animeTitle + " • Episode " + episode);
                        webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_FALLBACK_URL, buildExternalFallbackUrl(episode));
                        startActivity(webIntent);
                        ToastUtils.showCustomToast(EpisodeSelectorActivity.this, "Opened provider fallback in app");
                    }
                    finish();
                } else {
                    Log.e(TAG, "Resolution failed - all providers exhausted");
                    tvStatus.setText(com.example.mediavault.R.string.auto_all_providers_failed_to_find_streaming_link);
                    ToastUtils.showCustomToast(
                        EpisodeSelectorActivity.this,
                        "Failed to find streaming link from any provider. Try a different episode or check connection."
                    );
                }
            });
        });
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

    private String buildExternalFallbackUrl(int episode) {
        String encodedTitle = Uri.encode(animeTitle == null ? "" : animeTitle.trim());
        return "https://animekai.to/search?keyword=" + encodedTitle + "+episode+" + Math.max(1, episode);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }
}
