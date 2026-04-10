package com.example.mediavault;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.example.mediavault.ui.library.MediaItem;
import com.example.mediavault.widget.ToastUtils;

public class PlayerActivity extends AppCompatActivity {
    private static final String TAG = "PlayerActivity";
    private static final String KEY_POSITION = "playback_position";

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_MEDIA_ITEM = "media_item";
    public static final String EXTRA_MEDIA_KIND = "extra_media_kind";
    public static final String EXTRA_TARGET_PROGRESS = "extra_target_progress";
    public static final String EXTRA_TARGET_SEASON = "extra_target_season";
    public static final String EXTRA_TARGET_EPISODE = "extra_target_episode";

    private ExoPlayer player;
    private PlayerView playerView;
    private MediaItem mediaItem;
    private String contentUrl;
    private int mediaId;
    private String explicitMediaKind;
    private float targetProgress;
    private int targetSeason;
    private int targetEpisode;
    private boolean goalIncremented = false;
    private long savedPosition = 0;
    private TextView statusText;
    private Button retryButton;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            checkPlaybackProgress();
            progressHandler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable display cutout support for immersive player
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        
        setContentView(R.layout.activity_player);

        if (savedInstanceState != null) {
            savedPosition = savedInstanceState.getLong(KEY_POSITION, 0);
        }

        mediaItem = (MediaItem) getIntent().getSerializableExtra(EXTRA_MEDIA_ITEM);
        contentUrl = getIntent().getStringExtra(EXTRA_URL);
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        explicitMediaKind = getIntent().getStringExtra(EXTRA_MEDIA_KIND);
        targetProgress = getIntent().getFloatExtra(EXTRA_TARGET_PROGRESS, 0f);
        targetSeason = Math.max(1, getIntent().getIntExtra(EXTRA_TARGET_SEASON, 1));
        targetEpisode = Math.max(1, getIntent().getIntExtra(EXTRA_TARGET_EPISODE, 1));

        // ISSUE #10 FIX: Validate URL before proceeding
        if (contentUrl != null && contentUrl.trim().isEmpty()) {
            contentUrl = null; // Treat empty as null
        }

        if (mediaItem == null && mediaId != -1) {
            loadMediaItem(mediaId);
        } else if (contentUrl == null && mediaItem == null) {
            // No content to play
            ToastUtils.showCustomToast(this, "No playable content found");
            finish();
            return;
        }

        playerView = findViewById(R.id.player_view);
        setupUI();
    }

    private void loadMediaItem(int id) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
            try (android.database.Cursor cursor = dbHelper.getMediaById(id)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return;
                }

                final MediaItem item = new MediaItem(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING))
                );
                item.setSourceUrl(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE_URL)));

                AppExecutor.getInstance().mainThread().execute(() -> {
                    mediaItem = item;
                    if (contentUrl == null) contentUrl = mediaItem.getSourceUrl();
                    setupUI();
                    initializePlayer();
                });
            }
        });
    }

    private void setupUI() {
        TextView titleText = findViewById(R.id.text_player_title);
        statusText = findViewById(R.id.text_player_status);
        retryButton = findViewById(R.id.btn_player_retry);
        if (mediaItem != null) {
            titleText.setText(mediaItem.getTitle());
        }

        ImageButton backButton = findViewById(R.id.btn_player_back);
        backButton.setOnClickListener(v -> finish());
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                if (player != null) {
                    player.seekTo(0L);
                    player.prepare();
                    player.play();
                    retryButton.setVisibility(View.GONE);
                    setStatusMessage("Retrying playback...");
                }
            });
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private final android.media.AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (player != null && player.isPlaying()) {
                player.pause();
            }
        }
    };

    private void requestAudioFocus() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(focusChangeListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer() {
        // ISSUE #10 FIX: Additional safety check before playing
        if (contentUrl == null || contentUrl.trim().isEmpty()) {
            ToastUtils.showCustomToast(this, "Invalid media URL");
            finish();
            return;
        }
        
        requestAudioFocus();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        setStatusMessage("Buffering...");
        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    onVideoCompleted();
                } else if (playbackState == Player.STATE_READY && player.getPlayWhenReady()) {
                    progressHandler.post(progressRunnable);
                    setStatusMessage(null);
                } else if (playbackState == Player.STATE_BUFFERING) {
                    setStatusMessage("Buffering...");
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Playback error", error);
                setStatusMessage("Playback failed. Tap retry.");
                if (retryButton != null) {
                    retryButton.setVisibility(View.VISIBLE);
                }
                ToastUtils.showCustomToast(PlayerActivity.this, "Playback error");
            }
        });

        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(contentUrl));
        if (savedPosition > 0) player.seekTo(savedPosition);
        player.prepare();
        player.play();
    }

    private void checkPlaybackProgress() {
        if (player == null || goalIncremented) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration > 0 && (float) position / duration >= 0.9f) {
            onVideoCompleted();
        }
    }

    private void onVideoCompleted() {
        if (goalIncremented) return;
        goalIncremented = true;
        progressHandler.removeCallbacks(progressRunnable);

        AppExecutor.getInstance().diskIO().execute(() -> {
            if (mediaItem == null) {
                return;
            }
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            float currentRating = dbHelper.getMediaRating(mediaItem.getId());
            String effectiveType = explicitMediaKind != null && !explicitMediaKind.trim().isEmpty()
                    ? explicitMediaKind
                    : mediaItem.getType();

            if ("Movie".equalsIgnoreCase(effectiveType)) {
                float completedProgress = Math.max(1f, (float) mediaItem.getTotalCount());
                dbHelper.updateProgress(mediaItem.getId(), completedProgress, "Completed", currentRating);
                return;
            }

            if ("Series".equalsIgnoreCase(effectiveType) || "TV Show".equalsIgnoreCase(effectiveType)) {
                int episode = Math.max(1, targetEpisode);
                int season = Math.max(1, targetSeason);
                dbHelper.updateSeriesProgress(mediaItem.getId(), season, episode, null, currentRating);
                DailyGoalsManager.getInstance(this).incrementAnimeProgressSync();
                return;
            }

            if ("Anime".equalsIgnoreCase(effectiveType)) {
                float resolvedTarget = targetProgress > 0f ? targetProgress : (mediaItem.getCurrentProgress() + 1f);
                float nextProgress = Math.max(mediaItem.getCurrentProgress(), resolvedTarget);
                float capped = Math.min(nextProgress, mediaItem.getTotalCount());
                String status = capped >= mediaItem.getTotalCount() ? "Completed" : "Ongoing";
                dbHelper.updateProgress(mediaItem.getId(), capped, status, currentRating);
                DailyGoalsManager.getInstance(this).incrementAnimeProgressSync();
                return;
            }

            float nextProgress = Math.min(mediaItem.getCurrentProgress() + 1f, mediaItem.getTotalCount());
            String status = nextProgress >= mediaItem.getTotalCount() ? "Completed" : "Ongoing";
            dbHelper.updateProgress(mediaItem.getId(), nextProgress, status, currentRating);
        });
    }

    private void setStatusMessage(String message) {
        if (statusText == null) {
            return;
        }
        if (message == null || message.trim().isEmpty()) {
            statusText.setVisibility(View.GONE);
            statusText.setText("");
            return;
        }
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (player != null) {
            outState.putLong(KEY_POSITION, player.getCurrentPosition());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Strict Focus Fix: Release audio/video immediately for overlays or calls
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        progressHandler.removeCallbacks(progressRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
