package com.example.mediavault;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mediavault.widget.ToastUtils;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.mediavault.utils.ProgressValueUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DescriptionActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String ACTION_MEDIA_UPDATED = "com.example.mediavault.ACTION_MEDIA_UPDATED";

    private int mediaId;
    private DatabaseHelper dbHelper;
    private String mediaTitle;
    
    private ImageView ivCover;
    private TextView tvTitle, tvType, tvGenre, tvStatus, tvPriority, tvMood, tvProgressText, tvMyReview, tvJournal, tvDescription, tvCreator;
    private ProgressBar pbProgress;
    private RatingBar rbRating;
    private CollapsingToolbarLayout collapsingToolbar;
    private RecyclerView rvReviews;
    private boolean isUpdateReceiverRegistered = false;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                loadMediaData();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_description);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Media not found");
            finish();
            return;
        }

        dbHelper = DatabaseHelper.getInstance(this);
        initViews();
        loadMediaData();
        setupReviews();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        collapsingToolbar = findViewById(R.id.toolbar_layout);
        ivCover = findViewById(R.id.iv_description_cover);
        tvTitle = findViewById(R.id.tv_description_title);
        tvType = findViewById(R.id.tv_description_type);
        tvGenre = findViewById(R.id.tv_description_genre);
        tvStatus = findViewById(R.id.tv_description_status);
        tvPriority = findViewById(R.id.tv_description_priority);
        tvMood = findViewById(R.id.tv_description_mood);
        tvCreator = findViewById(R.id.tv_description_creator);
        tvProgressText = findViewById(R.id.tv_description_progress_text);
        tvMyReview = findViewById(R.id.tv_description_my_review);
        tvJournal = findViewById(R.id.tv_description_journal);
        tvDescription = findViewById(R.id.tv_description_text);
        pbProgress = findViewById(R.id.pb_description_progress);
        rbRating = findViewById(R.id.rb_description_rating);
        rvReviews = findViewById(R.id.rv_description_reviews);

        // Make description scrollable inside NestedScrollView
        if (tvDescription != null) {
            tvDescription.setMovementMethod(new ScrollingMovementMethod());
            tvDescription.setOnTouchListener((v, event) -> {
                if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            });
        }

        Button btnEdit = findViewById(R.id.btn_description_edit);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> showEditConfirmationDialog());
        }

        Button btnShare = findViewById(R.id.btn_description_share);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareMedia());
        }

        Button btnDelete = findViewById(R.id.btn_description_delete);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> showDeleteConfirmation());
        }

        Button btnUpdateProgress = findViewById(R.id.btn_description_update_progress);
        if (btnUpdateProgress != null) {
            btnUpdateProgress.setOnClickListener(v -> openProgressUpdateScreen());
        }

        Button btnConsume = findViewById(R.id.btn_description_consume);
        if (btnConsume != null) {
            btnConsume.setOnClickListener(v -> launchConsumer());
        }
    }

    private void launchConsumer() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            String url = null;
            String type = null;
            String title = null;
            float currentProgress = 0;
            int currentSeason = 1;
            int currentEpisode = 1;
            
            try (Cursor cursor = dbHelper.getMediaById(mediaId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    url = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE_URL));
                    type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                    title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                    currentProgress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                    int seasonIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_SEASON);
                    int episodeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_EPISODE);
                    if (seasonIndex >= 0) {
                        currentSeason = Math.max(1, cursor.getInt(seasonIndex));
                    }
                    if (episodeIndex >= 0) {
                        currentEpisode = Math.max(1, cursor.getInt(episodeIndex));
                    }
                }
            }

            String finalUrl = normalizeLegacyProviderUrl(url);
            String finalType = type;
            String finalTitle = title;
            int finalProgress = Math.max(1, (int) currentProgress);
            int finalSeason = Math.max(1, currentSeason);
            int finalEpisode = Math.max(1, currentEpisode);
            
            AppExecutor.getInstance().mainThread().execute(() -> {
                // ISSUE #1 FIX: Validate required data before routing
                if (finalType == null || finalTitle == null) {
                    ToastUtils.showCustomToast(this, "Error: Media information is incomplete");
                    return;
                }
                
                Intent intent;
                
                // Route to appropriate activity based on media type
                if ("Manga".equalsIgnoreCase(finalType)) {
                    // Manga: chapter selector table before opening reader
                    intent = new Intent(this, MangaChapterSelectorActivity.class);
                    intent.putExtra(MangaChapterSelectorActivity.EXTRA_MANGA_TITLE, finalTitle);
                    intent.putExtra(MangaChapterSelectorActivity.EXTRA_MEDIA_ID, mediaId);
                    intent.putExtra(MangaChapterSelectorActivity.EXTRA_CURRENT_CHAPTER, finalProgress);
                    
                } else if ("Book".equalsIgnoreCase(finalType) || "Novel".equalsIgnoreCase(finalType)) {
                    // Books/novels: use text reader flow with chapter-like pagination.
                    launchBookWithProviderChooser(finalTitle, finalUrl, mediaId);
                    return;
                    
                } else if ("Anime".equalsIgnoreCase(finalType)) {
                    // Anime: Episode selector for Consumet resolution
                    intent = new Intent(this, EpisodeSelectorActivity.class);
                    intent.putExtra(EpisodeSelectorActivity.EXTRA_ANIME_TITLE, finalTitle);
                    intent.putExtra(EpisodeSelectorActivity.EXTRA_MEDIA_ID, mediaId);
                    intent.putExtra(EpisodeSelectorActivity.EXTRA_CURRENT_EPISODE, finalProgress);
                    
                } else if ("TV Show".equalsIgnoreCase(finalType) || "Series".equalsIgnoreCase(finalType)) {
                    // TV Shows: Season/Episode selector
                    intent = new Intent(this, TvSeriesPlayerActivity.class);
                    intent.putExtra(TvSeriesPlayerActivity.EXTRA_TV_SHOW_TITLE, finalTitle);
                    intent.putExtra(TvSeriesPlayerActivity.EXTRA_MEDIA_ID, mediaId);
                    intent.putExtra(TvSeriesPlayerActivity.EXTRA_CURRENT_SEASON, finalSeason);
                    intent.putExtra(TvSeriesPlayerActivity.EXTRA_CURRENT_EPISODE, finalEpisode);
                    intent.putExtra(TvSeriesPlayerActivity.EXTRA_SOURCE_URL, finalUrl);
                    
                } else if ("Movie".equalsIgnoreCase(finalType)) {
                    // Movies: always route through provider selector flow.
                    intent = new Intent(this, MoviePlayerActivity.class);
                    intent.putExtra(MoviePlayerActivity.EXTRA_MOVIE_TITLE, finalTitle);
                    intent.putExtra(MoviePlayerActivity.EXTRA_MEDIA_ID, mediaId);
                    intent.putExtra(MoviePlayerActivity.EXTRA_YEAR, 0); // TODO: Extract year from metadata if available
                    intent.putExtra(MoviePlayerActivity.EXTRA_SOURCE_URL, finalUrl);
                    
                } else {
                    // Fallback: Direct playback with sourceUrl if available
                    if (finalUrl == null || finalUrl.isEmpty()) {
                        ToastUtils.showCustomToast(this, "No source URL found for this media.");
                        return;
                    }
                    intent = new Intent(this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_URL, finalUrl);
                    intent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
                }
                
                startActivity(intent);
            });
        });
    }

    private void launchBookWithProviderChooser(String title, String savedSourceUrl, int mediaId) {
        String normalizedSavedSource = normalizeLegacyProviderUrl(savedSourceUrl);
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("Auto Reader (Recommended)");
        if (normalizedSavedSource != null && !normalizedSavedSource.trim().isEmpty()) {
            options.add("Use saved source");
        }
        options.add("OpenChapter");
        options.add("NovelFire");
        options.add("WTR-LAB");

        String[] optionArray = options.toArray(new String[0]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Choose provider")
                .setItems(optionArray, (dialog, which) -> {
                    String selected = optionArray[which];
                    if ("Auto Reader (Recommended)".equals(selected)) {
                        Intent readerIntent = new Intent(this, TextReaderActivity.class);
                        readerIntent.putExtra(TextReaderActivity.EXTRA_URL, normalizedSavedSource);
                        readerIntent.putExtra(TextReaderActivity.EXTRA_MEDIA_ID, mediaId);
                        readerIntent.putExtra(TextReaderActivity.EXTRA_TITLE, title);
                        startActivity(readerIntent);
                        return;
                    }

                    if ("Use saved source".equals(selected)) {
                        Intent webIntent = new Intent(this, EmbeddedWebPlayerActivity.class);
                        webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, normalizedSavedSource);
                        webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, title);
                        startActivity(webIntent);
                        return;
                    }

                    String encodedTitle = android.net.Uri.encode(title == null ? "" : title.trim());
                    String providerUrl;
                    if ("OpenChapter".equals(selected)) {
                        providerUrl = "https://openchapter.io/search?query=" + encodedTitle;
                    } else if ("NovelFire".equals(selected)) {
                        providerUrl = "https://novelfire.net/search?keyword=" + encodedTitle;
                    } else {
                        providerUrl = "https://wtr-lab.com/en/search?keyword=" + encodedTitle;
                    }
                    Intent webIntent = new Intent(this, EmbeddedWebPlayerActivity.class);
                    webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, providerUrl);
                    webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, title + " • " + selected);
                    startActivity(webIntent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String normalizeLegacyProviderUrl(String url) {
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

    private boolean isDirectPlayableUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u8")
                || lower.matches(".*\\.(mp4|mkv|webm)(\\?.*)?$")
                || lower.startsWith("rtmp://");
    }

    private void setupReviews() {
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        List<Review> sampleReviews = new ArrayList<>();
        sampleReviews.add(new Review("Alex Johnson", 4.5f, "An absolute masterpiece. The character development is top-notch and the pacing is perfect."));
        sampleReviews.add(new Review("Maria Garcia", 5.0f, "I've recommended this to all my friends. It's rare to find something so engaging and thought-provoking."));
        sampleReviews.add(new Review("Sam Lee", 3.5f, "Pretty good, but the middle section dragged a bit. Still worth it for the incredible finale."));
        
        rvReviews.setAdapter(new ReviewAdapter(sampleReviews));
    }

    private static class Review {
        String user;
        float rating;
        String comment;
        Review(String user, float rating, String comment) {
            this.user = user;
            this.rating = rating;
            this.comment = comment;
        }
    }

    private static class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {
        private final List<Review> reviews;
        ReviewAdapter(List<Review> reviews) { this.reviews = reviews; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Review r = reviews.get(position);
            holder.user.setText(r.user);
            holder.rating.setRating(r.rating);
            holder.comment.setText(r.comment);
        }

        @Override
        public int getItemCount() { return reviews.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView user, comment;
            RatingBar rating;
            ViewHolder(View v) {
                super(v);
                user = v.findViewById(R.id.tv_review_user);
                comment = v.findViewById(R.id.tv_review_comment);
                rating = v.findViewById(R.id.rb_review_rating);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.description_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            showEditConfirmationDialog();
            return true;
        } else if (id == R.id.action_share) {
            shareMedia();
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteConfirmation();
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareMedia() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Checking out " + mediaTitle + " on MediaVault!");
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share via"));
    }

    private void showEditConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit full details?")
                .setMessage("This opens the full editor for title, cover, notes, and all metadata. Continue?")
                .setPositiveButton("Continue", (dialog, which) -> {
                    Intent intent = new Intent(this, EditMediaActivity.class);
                    intent.putExtra(EditMediaActivity.EXTRA_MEDIA_ID, mediaId);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openProgressUpdateScreen() {
        Intent intent = new Intent(this, ProgressUpdateActivity.class);
        intent.putExtra(ProgressUpdateActivity.EXTRA_MEDIA_ID, mediaId);
        startActivity(intent);
    }

    private void loadMediaData() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            Cursor cursor = dbHelper.getMediaById(mediaId);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                    int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                    int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                    int creatorIndex = cursor.getColumnIndex(DatabaseHelper.COL_CREATOR);
                    int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                    int descriptionIndex = cursor.getColumnIndex(DatabaseHelper.COL_DESCRIPTION);
                    int reviewIndex = cursor.getColumnIndex(DatabaseHelper.COL_REVIEW);
                    int journalIndex = cursor.getColumnIndex(DatabaseHelper.COL_JOURNAL);
                    int moodIndex = cursor.getColumnIndex(DatabaseHelper.COL_MOOD);
                    int priorityIndex = cursor.getColumnIndex(DatabaseHelper.COL_PRIORITY);
                    int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                    int capacityIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                    int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                    int ratingIndex = cursor.getColumnIndex(DatabaseHelper.COL_RATING);
                    int imageIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);

                    // Extract data on background thread
                    final String title = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                    final String type = typeIndex != -1 ? cursor.getString(typeIndex) : "N/A";
                    final String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";
                    final String creator = creatorIndex != -1 ? cursor.getString(creatorIndex) : "";
                    final String status = statusIndex != -1 ? cursor.getString(statusIndex) : "Planning";
                    final String description = descriptionIndex != -1 ? cursor.getString(descriptionIndex) : "";
                    final String review = reviewIndex != -1 ? cursor.getString(reviewIndex) : "";
                    final String journal = journalIndex != -1 ? cursor.getString(journalIndex) : "";
                    final String mood = moodIndex != -1 ? cursor.getString(moodIndex) : "";
                    final String priority = priorityIndex != -1 ? cursor.getString(priorityIndex) : "Medium";
                    final float progress = progressIndex != -1 ? cursor.getFloat(progressIndex) : 0f;
                    final int capacity = capacityIndex != -1 ? cursor.getInt(capacityIndex) : 0;
                    final String unit = unitIndex != -1 ? cursor.getString(unitIndex) : "";
                    final float rating = ratingIndex != -1 ? cursor.getFloat(ratingIndex) : 0f;
                    final String imagePath = imageIndex != -1 ? cursor.getString(imageIndex) : null;
                    cursor.close();

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        mediaTitle = title;
                        tvTitle.setText(mediaTitle);
                        collapsingToolbar.setTitle(mediaTitle);
                        tvType.setText(type.toUpperCase(Locale.getDefault()));
                        tvGenre.setText(genre);
                        
                        if (creator != null && !creator.isEmpty()) {
                            tvCreator.setVisibility(View.VISIBLE);
                            tvCreator.setText("By " + creator);
                        } else {
                            tvCreator.setVisibility(View.GONE);
                        }
                        
                        tvStatus.setText(status);
                        tvPriority.setText(priority);
                        tvMood.setText(formatMood(mood));
                        
                        if (description != null && !description.isEmpty()) {
                            tvDescription.setText(description);
                        }

                        tvProgressText.setText(String.format(
                                Locale.getDefault(),
                                "%s / %d %s",
                                ProgressValueUtils.formatForDisplay(progress, unit),
                                capacity,
                                unit
                        ));
                        tvMyReview.setText(review != null && !review.isEmpty() ? review : "No personal review yet.");
                        tvJournal.setText(journal != null && !journal.isEmpty() ? journal : "No journal memory yet.");
                        pbProgress.setMax(capacity > 0 ? capacity : 100);
                        pbProgress.setProgress((int) progress);
                        rbRating.setRating(rating);

                        if (ivCover != null) {
                            if (imagePath != null && !imagePath.isEmpty()) {
                                File file = new File(imagePath);
                                if (file.exists()) {
                                    Glide.with(DescriptionActivity.this).load(file).centerCrop().into(ivCover);
                                } else {
                                    Glide.with(DescriptionActivity.this).load(imagePath).placeholder(R.drawable.mediavault_logo).error(R.drawable.mediavault_logo).centerCrop().into(ivCover);
                                }
                            } else {
                                ivCover.setImageResource(R.drawable.mediavault_logo);
                            }
                        }
                    });
                } else {
                    cursor.close();
                    runOnUiThread(() -> {
                        ToastUtils.showCustomToast(DescriptionActivity.this, "Error: Media record not found");
                        finish();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    ToastUtils.showCustomToast(DescriptionActivity.this, "Error: Could not load media");
                    finish();
                });
            }
        });
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to delete this from your library?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AppExecutor.getInstance().diskIO().execute(() -> {
                        boolean success = dbHelper.deleteMedia(mediaId);
                        runOnUiThread(() -> {
                            if (success) {
                                ToastUtils.showCustomToast(DescriptionActivity.this, "Deleted successfully");
                                Intent updateIntent = new Intent(ACTION_MEDIA_UPDATED);
                                updateIntent.setPackage(getPackageName());
                                sendBroadcast(updateIntent);
                                finish();
                            } else {
                                ToastUtils.showCustomToast(DescriptionActivity.this, "Failed to delete media");
                            }
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isUpdateReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_UPDATED);
            ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            isUpdateReceiverRegistered = true;
        }
        loadMediaData(); // Refresh in case it was edited
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isUpdateReceiverRegistered) {
            unregisterReceiver(updateReceiver);
            isUpdateReceiverRegistered = false;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private String formatMood(String mood) {
        if (mood == null || mood.isEmpty()) {
            return "Not set";
        }
        switch (mood) {
            case "Excited":
                return "\uD83D\uDE0D Excited";
            case "Happy":
                return "\uD83D\uDE0A Happy";
            case "Neutral":
                return "\uD83D\uDE10 Neutral";
            case "Sad":
                return "\uD83D\uDE22 Sad";
            case "Mind-blown":
                return "\uD83E\uDD2F Mind-blown";
            default:
                return mood;
        }
    }
}
