package com.example.mediavault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class DescriptionActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String ACTION_MEDIA_UPDATED = "com.example.mediavault.ACTION_MEDIA_UPDATED";

    private int mediaId;
    private DatabaseHelper dbHelper;
    private String mediaTitle;
    
    private ImageView ivCover;
    private TextView tvTitle, tvType, tvGenre, tvStatus, tvProgressText, tvMyReview;
    private ProgressBar pbProgress;
    private RatingBar rbRating;
    private CollapsingToolbarLayout collapsingToolbar;
    private RecyclerView rvReviews;

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_description);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            Toast.makeText(this, "Error: Media not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);
        initViews();
        loadMediaData();
        setupReviews();
    }

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
        tvProgressText = findViewById(R.id.tv_description_progress_text);
        tvMyReview = findViewById(R.id.tv_description_my_review);
        pbProgress = findViewById(R.id.pb_description_progress);
        rbRating = findViewById(R.id.rb_description_rating);
        rvReviews = findViewById(R.id.rv_description_reviews);

        FloatingActionButton fabBack = findViewById(R.id.fab_description_back);
        fabBack.setOnClickListener(v -> finish());

        Button btnEdit = findViewById(R.id.btn_description_edit);
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditMediaActivity.class);
            intent.putExtra(EditMediaActivity.EXTRA_MEDIA_ID, mediaId);
            startActivity(intent);
        });

        Button btnShare = findViewById(R.id.btn_description_share);
        btnShare.setOnClickListener(v -> shareMedia());

        Button btnDelete = findViewById(R.id.btn_description_delete);
        btnDelete.setOnClickListener(v -> showDeleteConfirmation());
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
            Intent intent = new Intent(this, EditMediaActivity.class);
            intent.putExtra(EditMediaActivity.EXTRA_MEDIA_ID, mediaId);
            startActivity(intent);
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

    private void loadMediaData() {
        Cursor cursor = dbHelper.getMediaById(mediaId);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                int reviewIndex = cursor.getColumnIndex(DatabaseHelper.COL_REVIEW);
                int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                int capacityIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                int ratingIndex = cursor.getColumnIndex(DatabaseHelper.COL_RATING);

                mediaTitle = titleIndex != -1 ? cursor.getString(titleIndex) : "Unknown";
                String type = typeIndex != -1 ? cursor.getString(typeIndex) : "N/A";
                String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";
                String status = statusIndex != -1 ? cursor.getString(statusIndex) : "Planning";
                String review = reviewIndex != -1 ? cursor.getString(reviewIndex) : "";
                int progress = progressIndex != -1 ? cursor.getInt(progressIndex) : 0;
                int capacity = capacityIndex != -1 ? cursor.getInt(capacityIndex) : 0;
                String unit = unitIndex != -1 ? cursor.getString(unitIndex) : "";
                float rating = ratingIndex != -1 ? cursor.getFloat(ratingIndex) : 0f;

                tvTitle.setText(mediaTitle);
                collapsingToolbar.setTitle(mediaTitle);
                tvType.setText(type.toUpperCase());
                tvGenre.setText(genre);
                tvStatus.setText(status);
                tvProgressText.setText(progress + " / " + capacity + " " + unit);
                tvMyReview.setText(review != null && !review.isEmpty() ? review : "No personal review yet.");
                pbProgress.setMax(capacity > 0 ? capacity : 100);
                pbProgress.setProgress(progress);
                rbRating.setRating(rating);
            } else {
                Toast.makeText(this, "Error: Media record not found", Toast.LENGTH_SHORT).show();
                finish();
            }
            cursor.close();
        } else {
            Toast.makeText(this, "Error: Could not load media", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to delete this from your library?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (dbHelper.deleteMedia(mediaId)) {
                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent(ACTION_MEDIA_UPDATED));
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updateReceiver, new IntentFilter(ACTION_MEDIA_UPDATED), Context.RECEIVER_NOT_EXPORTED);
        loadMediaData(); // Refresh in case it was edited
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}