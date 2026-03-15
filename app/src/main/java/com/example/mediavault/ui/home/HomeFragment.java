package com.example.mediavault.ui.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.ShakeDetector;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.graphics.drawable.Drawable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.mediavault.widget.ToastUtils;

public class HomeFragment extends Fragment {

    private TextView tvWatchTime, tvPagesRead, tvEpisodesWatched, tvOngoingItems, tvAvgRating;
    private TextView tvSpotlightLabel, tvSpotlightTitle, tvSpotlightStatus;
    private ProgressBar progressSpotlight, pbSpotlightLoading;
    private TextView tvViewDetailedStats;
    private MaterialButton btnAnalyzeHabits;
    private DatabaseHelper dbHelper;
    private View spotlightCard;
    private ImageView ivSpotlightBg;
    private View recentItem1, recentItem2, recentItem3, recentItem4;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        dbHelper = new DatabaseHelper(requireContext());
        
        // Initialize Views
        tvWatchTime = view.findViewById(R.id.tv_watch_time);
        tvPagesRead = view.findViewById(R.id.tv_pages_read);
        tvEpisodesWatched = view.findViewById(R.id.tv_episodes_watched);
        tvOngoingItems = view.findViewById(R.id.tv_ongoing_items);
        tvAvgRating = view.findViewById(R.id.tv_avg_rating);
        tvSpotlightLabel = view.findViewById(R.id.tv_spotlight_label);
        tvSpotlightTitle = view.findViewById(R.id.tv_spotlight_title);
        tvSpotlightStatus = view.findViewById(R.id.tv_spotlight_status);
        progressSpotlight = view.findViewById(R.id.progress_spotlight);
        pbSpotlightLoading = view.findViewById(R.id.pb_spotlight_loading);
        spotlightCard = view.findViewById(R.id.card_spotlight);
        ivSpotlightBg = view.findViewById(R.id.iv_spotlight_bg);
        tvViewDetailedStats = view.findViewById(R.id.tv_view_detailed_stats);
        btnAnalyzeHabits = view.findViewById(R.id.btn_analyze_habits);

        // Recent Items
        recentItem1 = view.findViewById(R.id.recent_item_1);
        recentItem2 = view.findViewById(R.id.recent_item_2);
        recentItem3 = view.findViewById(R.id.recent_item_3);
        recentItem4 = view.findViewById(R.id.recent_item_4);
        
        updateOverviewStats();
        setupNavigation(view);
        updateBacklogSpotlight();
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateOverviewStats();
        setupRecentActivities();
        updateBacklogSpotlight();
    }

    private void setupRecentActivities() {
        Cursor cursor = dbHelper.getAllMedia();
        View[] items = {recentItem1, recentItem2, recentItem3, recentItem4};
        
        // Hide all initially
        for (View item : items) item.setVisibility(View.GONE);

        if (cursor != null) {
            int count = 0;
            if (cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                int totalIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                int imageIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);
                int updatedIndex = cursor.getColumnIndex(DatabaseHelper.COL_LAST_UPDATED);

                do {
                    if (count >= 4) break;
                    
                    View itemView = items[count];
                    itemView.setVisibility(View.VISIBLE);
                    
                    int id = cursor.getInt(idIndex);
                    String title = cursor.getString(titleIndex);
                    String type = cursor.getString(typeIndex);
                    int progress = cursor.getInt(progressIndex);
                    int total = cursor.getInt(totalIndex);
                    String imagePath = cursor.getString(imageIndex);
                    String lastUpdated = cursor.getString(updatedIndex);
                    
                    TextView tvTitle = itemView.findViewById(R.id.tv_title);
                    TextView tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
                    TextView tvPercent = itemView.findViewById(R.id.tv_progress_percentage);
                    TextView tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
                    ProgressBar progressBar = itemView.findViewById(R.id.progress_bar);
                    ImageView ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
                    
                    if (tvTitle != null) tvTitle.setText(title);
                    if (tvSubtitle != null) tvSubtitle.setText(type);
                    if (tvTimestamp != null) tvTimestamp.setText(getTimeAgo(lastUpdated));
                    
                    if (total > 0) {
                        int percent = (int) ((progress / (float) total) * 100);
                        if (tvPercent != null) tvPercent.setText(percent + "%");
                        if (progressBar != null) {
                            progressBar.setMax(total);
                            progressBar.setProgress(progress);
                        }
                    }

                    if (ivThumbnail != null) {
                        if (imagePath != null && !imagePath.isEmpty()) {
                            File file = new File(imagePath);
                            if (file.exists()) {
                                Glide.with(this).load(file).centerCrop().into(ivThumbnail);
                            } else {
                                Glide.with(this).load(imagePath).placeholder(R.drawable.mediavault_logo).error(R.drawable.mediavault_logo).centerCrop().into(ivThumbnail);
                            }
                        } else {
                            ivThumbnail.setImageResource(R.drawable.mediavault_logo);
                        }
                    }

                    itemView.setOnClickListener(v -> {
                        Intent intent = new Intent(requireContext(), DescriptionActivity.class);
                        intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, id);
                        startActivity(intent);
                    });

                    count++;
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }

    private String getTimeAgo(String dateString) {
        if (dateString == null) return "just now";
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        try {
            Date date = sdf.parse(dateString);
            if (date == null) return "just now";
            
            long time = date.getTime();
            long now = System.currentTimeMillis();
            long diff = now - time;
            
            if (diff < 60000) return "just now";
            if (diff < 3600000) return (diff / 60000) + "m ago";
            if (diff < 86400000) return (diff / 3600000) + "h ago";
            if (diff < 604800000) return (diff / 86400000) + "d ago";
            
            SimpleDateFormat displaySdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return displaySdf.format(date);
            
        } catch (ParseException e) {
            return "just now";
        }
    }

    private void updateOverviewStats() {
        int totalMinutes = dbHelper.getTotalMinutesWatched();
        tvWatchTime.setText(formatDuration(totalMinutes));
        
        // Pages Read
        int totalPages = dbHelper.getTotalPagesRead();
        tvPagesRead.setText(String.valueOf(totalPages));

        // Episodes Watched
        int totalEpisodes = dbHelper.getTotalEpisodesWatched();
        tvEpisodesWatched.setText(String.valueOf(totalEpisodes));
        
        // Ongoing Items
        int ongoingCount = dbHelper.getStatusCount("Ongoing");
        tvOngoingItems.setText(String.valueOf(ongoingCount));
        
        // Average Rating
        float avgRating = dbHelper.getAverageRating();
        tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "0 mins";
        }

        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hours == 0) {
            return mins + " mins";
        }
        if (mins == 0) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return (hours == 1 ? "1 hour " : hours + " hours ") + mins + " mins";
    }

    private void updateBacklogSpotlight() {
        Cursor cursor = dbHelper.getBacklogSpotlightMedia();
        if (cursor == null) {
            return;
        }

        try {
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                int progress = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));

                tvSpotlightLabel.setText("Currently " + status);
                tvSpotlightTitle.setText(title);
                tvSpotlightStatus.setText(status + " • " + progress + "/" + total + " " + unit);
                progressSpotlight.setMax(Math.max(total, 1));
                progressSpotlight.setProgress(Math.min(progress, total));
                
                if (imagePath != null && !imagePath.isEmpty()) {
                    if (pbSpotlightLoading != null) pbSpotlightLoading.setVisibility(View.VISIBLE);
                    
                    File file = new File(imagePath);
                    Object loadSource = file.exists() ? file : imagePath;

                    Glide.with(this)
                            .load(loadSource)
                            .centerCrop()
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    if (pbSpotlightLoading != null) pbSpotlightLoading.setVisibility(View.GONE);
                                    ivSpotlightBg.setImageResource(R.drawable.cinematic_bg);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    if (pbSpotlightLoading != null) pbSpotlightLoading.setVisibility(View.GONE);
                                    return false;
                                }
                            })
                            .into(ivSpotlightBg);
                } else {
                    if (pbSpotlightLoading != null) pbSpotlightLoading.setVisibility(View.GONE);
                    ivSpotlightBg.setImageResource(R.drawable.cinematic_bg);
                }

                spotlightCard.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), DescriptionActivity.class);
                    intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, id);
                    startActivity(intent);
                });
            } else {
                if (pbSpotlightLoading != null) pbSpotlightLoading.setVisibility(View.GONE);
                tvSpotlightLabel.setText("Currently Watching / Reading");
                tvSpotlightTitle.setText("Backlog Spotlight");
                tvSpotlightStatus.setText("Plan something new");
                progressSpotlight.setMax(100);
                progressSpotlight.setProgress(0);
                if (ivSpotlightBg != null) ivSpotlightBg.setImageResource(R.drawable.cinematic_bg);
                spotlightCard.setOnClickListener(null);
            }
        } finally {
            cursor.close();
        }
    }

    private void setupNavigation(View view) {
        View.OnClickListener toMetrics = v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_metrics);
                } else {
                    Navigation.findNavController(view).navigate(R.id.nav_metrics);
                }
            }
        };

        tvViewDetailedStats.setOnClickListener(toMetrics);
        btnAnalyzeHabits.setOnClickListener(toMetrics);

        View cardShake = view.findViewById(R.id.card_shake);
        if (cardShake != null) {
            cardShake.setOnClickListener(v -> {
                Navigation.findNavController(v).navigate(R.id.action_home_to_shake);
            });
        }
    }
}
