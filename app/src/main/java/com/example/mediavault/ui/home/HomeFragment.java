package com.example.mediavault.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.AddMediaActivity;
import com.example.mediavault.BuildConfig;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.api.search.MovieSearchWaterfall;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.strategies.AniListAnimeStrategy;
import com.example.mediavault.api.search.strategies.GoogleBooksStrategy;
import com.example.mediavault.api.search.strategies.MangaDexStrategy;
import com.example.mediavault.receiver.DailyGoalReminderReceiver;
import com.example.mediavault.utils.ProgressValueUtils;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import com.example.mediavault.utils.AccessibilityServiceHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvViewDetailedStats;
    private com.google.android.material.button.MaterialButton btnAnalyzeHabits;
    private DatabaseHelper dbHelper;
    private androidx.viewpager2.widget.ViewPager2 pagerSpotlight;
    private SpotlightAdapter spotlightAdapter;
    private java.util.List<com.example.mediavault.ui.library.MediaItem> spotlightItems;
    private TextView textHomeRecommendationTitle, textHomeRecommendationSubtitle;
    private RecyclerView recyclerHomeRecommendations;
    private HomeRecommendationAdapter recommendationAdapter;
    private final java.util.List<com.example.mediavault.ui.library.MediaItem> recommendationItems = new java.util.ArrayList<>();
    
    // Service Status Banner
    private View cardServiceStatus;
    private TextView textServiceStatus;
    private View btnEnableService;
    
    // Daily Goals UI
    private TextView tvEditGoals, tvGoalsEmpty, tvGoalsHeader, tvCongratulations;
    private LinearLayout layoutGoalPages, layoutGoalEpisodes, layoutGoalMinutes;
    private TextView tvProgressPages, tvProgressEpisodes, tvProgressMinutes;
    private ProgressBar pbGoalPages, pbGoalEpisodes, pbGoalMinutes;
    private DailyGoalsManager goalsManager;

    // Streak UI
    private TextView tvStreakCount, tvStreakMessage;

    private boolean isUpdateReceiverRegistered = false;
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                updateSpotlightData();
                updateDailyProgress();
                updateStreakUI();
                if (BuildConfig.DEBUG) {
                    ToastUtils.showCustomToast(context, "Home data refreshed");
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        dbHelper = DatabaseHelper.getInstance(requireContext());
        goalsManager = DailyGoalsManager.getInstance(requireContext());
        
        // Service Status Banner
        cardServiceStatus = view.findViewById(R.id.card_service_status);
        textServiceStatus = view.findViewById(R.id.text_service_status);
        btnEnableService = view.findViewById(R.id.btn_enable_service);
        
        textHomeRecommendationTitle = view.findViewById(R.id.text_home_recommendation_title);
        textHomeRecommendationSubtitle = view.findViewById(R.id.text_home_recommendation_subtitle);
        recyclerHomeRecommendations = view.findViewById(R.id.recycler_home_recommendations);
        
        pagerSpotlight = view.findViewById(R.id.pager_spotlight);
        
        // Daily Goals UI
        tvEditGoals = view.findViewById(R.id.tv_edit_goals);
        tvGoalsEmpty = view.findViewById(R.id.tv_goals_empty);
        tvGoalsHeader = view.findViewById(R.id.tv_goals_header);
        tvCongratulations = view.findViewById(R.id.tv_congratulations);
        
        layoutGoalPages = view.findViewById(R.id.layout_goal_pages);
        tvProgressPages = view.findViewById(R.id.tv_progress_pages);
        pbGoalPages = view.findViewById(R.id.pb_goal_pages);
        
        layoutGoalEpisodes = view.findViewById(R.id.layout_goal_episodes);
        tvProgressEpisodes = view.findViewById(R.id.tv_progress_episodes);
        pbGoalEpisodes = view.findViewById(R.id.pb_goal_episodes);
        
        layoutGoalMinutes = view.findViewById(R.id.layout_goal_minutes);
        tvProgressMinutes = view.findViewById(R.id.tv_progress_minutes);
        pbGoalMinutes = view.findViewById(R.id.pb_goal_minutes);

        tvStreakCount = view.findViewById(R.id.tv_streak_count_home);
        tvStreakMessage = view.findViewById(R.id.tv_streak_message);
        
        tvViewDetailedStats = view.findViewById(R.id.tv_view_detailed_stats);
        btnAnalyzeHabits = view.findViewById(R.id.btn_analyze_habits);

        if (recyclerHomeRecommendations != null) {
            recyclerHomeRecommendations.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
            recyclerHomeRecommendations.setHasFixedSize(false);
            recommendationAdapter = new HomeRecommendationAdapter(requireContext(), new HomeRecommendationAdapter.Callback() {
                @Override
                public void onOpen(@NonNull com.example.mediavault.ui.library.MediaItem item) {
                    openRecommendationDetails(item);
                }

                @Override
                public void onAdd(@NonNull com.example.mediavault.ui.library.MediaItem item) {
                    addRecommendationToLibrary(item);
                }
            });
            recyclerHomeRecommendations.setAdapter(recommendationAdapter);
            LinearSnapHelper snapHelper = new LinearSnapHelper();
            snapHelper.attachToRecyclerView(recyclerHomeRecommendations);
        }

        setupNavigation(view);
        setupSpotlightCarousel();
        updateDailyProgress();
        updateStreakUI();
        scheduleDailyReminder();
        setupServiceStatusBanner();
        
        tvEditGoals.setOnClickListener(v -> showEditGoalDialog());
        
        return view;
    }

    private void showEditGoalDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_daily_goals, null);
        
        TextInputEditText etPages = dialogView.findViewById(R.id.et_goal_pages);
        TextInputEditText etEpisodes = dialogView.findViewById(R.id.et_goal_episodes);
        TextInputEditText etMinutes = dialogView.findViewById(R.id.et_goal_minutes);
        
        TextView tvReminderTime = dialogView.findViewById(R.id.tv_reminder_time);
        com.google.android.material.switchmaterial.SwitchMaterial switchReminder = dialogView.findViewById(R.id.switch_reminder);
        com.google.android.material.button.MaterialButton btnTestReminderNow = dialogView.findViewById(R.id.btn_test_reminder_now);
        
        // Pre-fill existing values
        int currentPages = goalsManager.getGoalPages();
        int currentEpisodes = goalsManager.getGoalEpisodes();
        int currentMinutes = goalsManager.getGoalMinutes();
        
        if (currentPages > 0) etPages.setText(String.valueOf(currentPages));
        if (currentEpisodes > 0) etEpisodes.setText(String.valueOf(currentEpisodes));
        if (currentMinutes > 0) etMinutes.setText(String.valueOf(currentMinutes));
        
        // Reminder settings
        final int[] reminderTime = {goalsManager.getReminderHour(), goalsManager.getReminderMinute()};
        boolean isEnabled = goalsManager.isReminderEnabled();
        
        switchReminder.setChecked(isEnabled);
        
        // Use 12-hour format
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, reminderTime[0]);
        calendar.set(Calendar.MINUTE, reminderTime[1]);
        tvReminderTime.setText(sdf.format(calendar.getTime()));

        tvReminderTime.setEnabled(isEnabled);
        tvReminderTime.setAlpha(isEnabled ? 1.0f : 0.5f);
        
        switchReminder.setOnCheckedChangeListener((buttonView, checked) -> {
            tvReminderTime.setEnabled(checked);
            tvReminderTime.setAlpha(checked ? 1.0f : 0.5f);
        });
        
        tvReminderTime.setOnClickListener(v -> {
            if (!switchReminder.isChecked()) return; // Double check

            new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                reminderTime[0] = hourOfDay;
                reminderTime[1] = minute;
                
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                tvReminderTime.setText(sdf.format(cal.getTime()));
                
            }, reminderTime[0], reminderTime[1], false).show(); // false = 12h format
        });

        if (btnTestReminderNow != null) {
            if (!BuildConfig.DEBUG) {
                btnTestReminderNow.setVisibility(View.GONE);
            } else {
                btnTestReminderNow.setOnClickListener(v -> {
                    Intent testIntent = new Intent(com.example.mediavault.receiver.DailyGoalReminderReceiver.ACTION_TEST_REMINDER);
                    testIntent.setClass(requireContext(), DailyGoalReminderReceiver.class);
                    requireContext().sendBroadcast(testIntent);
                    ToastUtils.showCustomToast(requireContext(), "Test reminder sent");
                });
            }
        }
        
        androidx.appcompat.app.AlertDialog alertDialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Daily Goals")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    String pagesStr = etPages.getText() != null ? etPages.getText().toString().trim() : "";
                    String epsStr = etEpisodes.getText() != null ? etEpisodes.getText().toString().trim() : "";
                    String minStr = etMinutes.getText() != null ? etMinutes.getText().toString().trim() : "";

                    int pagesGoal = parseNonNegativeGoal(pagesStr);
                    int episodesGoal = parseNonNegativeGoal(epsStr);
                    int minutesGoal = parseNonNegativeGoal(minStr);
                    if (minutesGoal > 1440) {
                        minutesGoal = 1440;
                        ToastUtils.showCustomToast(requireContext(), "Minutes goal capped at 1440 per day");
                    }

                    goalsManager.setGoalPages(pagesGoal);
                    goalsManager.setGoalEpisodes(episodesGoal);
                    goalsManager.setGoalMinutes(minutesGoal);
                    
                    goalsManager.setReminderEnabled(switchReminder.isChecked());
                    goalsManager.setReminderTime(reminderTime[0], reminderTime[1]);
                    
                    updateDailyProgress();
                    scheduleDailyReminder(); // Reschedule with new settings
                    
                    ToastUtils.showCustomToast(requireContext(), "Daily goals & reminder updated!");
                } catch (NumberFormatException e) {
                    ToastUtils.showCustomToast(requireContext(), "Invalid number format");
                }
            })
            .setNegativeButton("Cancel", null)
            .create();
        
        // Enable keyboard adjustment for soft input
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }
        
        alertDialog.show();
    }

    private void updateDailyProgress() {
        if (dbHelper == null) return;

        // Get today's progress from DB
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DailyProgress progress = dbHelper.getDailyProgress(today);
        
        boolean hasAnyGoal = false;
        boolean allGoalsMet = true;

        // Update Pages Goal
        int goalPages = goalsManager.getGoalPages();
        if (goalPages > 0) {
            layoutGoalPages.setVisibility(View.VISIBLE);
            pbGoalPages.setMax(goalPages);
            int normalizedPages = (int) ProgressValueUtils.normalizeForUnit(progress.pagesRead, "Pages");
            pbGoalPages.setProgress(normalizedPages);
            tvProgressPages.setText(String.format(Locale.getDefault(), "%d/%d", normalizedPages, goalPages));
            hasAnyGoal = true;
            if (normalizedPages < goalPages) allGoalsMet = false;
        } else {
            layoutGoalPages.setVisibility(View.GONE);
        }

        // Update Episodes Goal
        int goalEpisodes = goalsManager.getGoalEpisodes();
        if (goalEpisodes > 0) {
            layoutGoalEpisodes.setVisibility(View.VISIBLE);
            pbGoalEpisodes.setMax(goalEpisodes);
            int normalizedEpisodes = (int) ProgressValueUtils.normalizeForUnit(progress.episodesWatched, "Episodes");
            pbGoalEpisodes.setProgress(normalizedEpisodes);
            tvProgressEpisodes.setText(String.format(Locale.getDefault(), "%d/%d", normalizedEpisodes, goalEpisodes));
            hasAnyGoal = true;
            if (normalizedEpisodes < goalEpisodes) allGoalsMet = false;
        } else {
            layoutGoalEpisodes.setVisibility(View.GONE);
        }

        // Update Minutes Goal
        int goalMinutes = goalsManager.getGoalMinutes();
        if (goalMinutes > 0) {
            layoutGoalMinutes.setVisibility(View.VISIBLE);
            pbGoalMinutes.setMax(goalMinutes);
            int normalizedMinutes = Math.max(0, Math.round(progress.minutesWatched));
            pbGoalMinutes.setProgress(normalizedMinutes);
            tvProgressMinutes.setText(String.format(Locale.getDefault(), "%d/%d", normalizedMinutes, goalMinutes));
            hasAnyGoal = true;
            if (normalizedMinutes < goalMinutes) allGoalsMet = false;
        } else {
            layoutGoalMinutes.setVisibility(View.GONE);
        }
        
        if (hasAnyGoal) {
            tvGoalsEmpty.setVisibility(View.GONE);
            if (allGoalsMet) {
                tvCongratulations.setVisibility(View.VISIBLE);
                tvGoalsHeader.setText(com.example.mediavault.R.string.auto_goal_achieved);
                tvGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
            } else {
                tvCongratulations.setVisibility(View.GONE);
                tvGoalsHeader.setText(com.example.mediavault.R.string.auto_today_s_progress_2);
                tvGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary)); // Fallback if attr not avail
            }
        } else {
            tvGoalsEmpty.setVisibility(View.VISIBLE);
            tvCongratulations.setVisibility(View.GONE);
            tvGoalsHeader.setText(com.example.mediavault.R.string.auto_daily_goals);
        }
    }
    
    private void scheduleDailyReminder() {
        Context context = getContext();
        if (context == null) return;
        DailyGoalReminderReceiver.scheduleNextReminder(context, goalsManager);
    }
    private void setupSpotlightCarousel() {
        spotlightItems = new java.util.ArrayList<>();
        spotlightAdapter = new SpotlightAdapter(requireContext(), spotlightItems);
        pagerSpotlight.setAdapter(spotlightAdapter);
        
        pagerSpotlight.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(0.5f + (1 - absPos) * 0.5f);
            page.setScaleY(0.85f + (1 - absPos) * 0.15f);
        });
        
        updateSpotlightData();
    }

    private void updateSpotlightData() {
        if (dbHelper == null) return;
        
        com.example.mediavault.AppExecutor.getInstance().diskIO().execute(() -> {
            java.util.List<com.example.mediavault.ui.library.MediaItem> loadedItems = new java.util.ArrayList<>();
            java.util.List<com.example.mediavault.ui.library.MediaItem> recommendationCandidates = new java.util.ArrayList<>();
            try {
                Cursor cursor = dbHelper.getOngoingMediaList();
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                    int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                    int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                    int genreIndex = cursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                    int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                    int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                    int prevProgressIndex = cursor.getColumnIndex(DatabaseHelper.COL_PREVIOUS_PROGRESS);
                    int totalIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                    int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                    int imageIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);
                    int ratingIndex = cursor.getColumnIndex(DatabaseHelper.COL_RATING);
                    int favoriteIndex = cursor.getColumnIndex(DatabaseHelper.COL_IS_FAVORITE);
                    int sourceUrlIndex = cursor.getColumnIndex(DatabaseHelper.COL_SOURCE_URL);
                    int contentTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONTENT_TYPE);

                    if (idIndex != -1 && titleIndex != -1 && typeIndex != -1 && statusIndex != -1 && 
                        progressIndex != -1 && totalIndex != -1 && unitIndex != -1) {
                        
                        if (cursor.moveToFirst()) {
                            do {
                                int id = cursor.getInt(idIndex);
                                String title = cursor.getString(titleIndex);
                                String type = cursor.getString(typeIndex);
                                String genre = genreIndex != -1 ? cursor.getString(genreIndex) : "";
                                String status = cursor.getString(statusIndex);
                                float progress = cursor.getFloat(progressIndex);
                                float prevProgress = prevProgressIndex != -1 ? cursor.getFloat(prevProgressIndex) : progress;
                                int total = cursor.getInt(totalIndex);
                                String unit = cursor.getString(unitIndex);
                                String imagePath = (imageIndex != -1) ? cursor.getString(imageIndex) : null;
                                float rating = ratingIndex != -1 ? cursor.getFloat(ratingIndex) : 0f;
                                boolean favorite = favoriteIndex != -1 && cursor.getInt(favoriteIndex) == 1;
                                String sourceUrl = sourceUrlIndex != -1 ? cursor.getString(sourceUrlIndex) : null;
                                String contentType = contentTypeIndex != -1 ? cursor.getString(contentTypeIndex) : null;
                                
                                loadedItems.add(new com.example.mediavault.ui.library.MediaItem(
                                    id, title, type, genre, status, progress, prevProgress, total, unit, imagePath, rating, favorite, sourceUrl, contentType
                                ));
                            } while (cursor.moveToNext());
                        }
                    }
                    cursor.close();
                }

                Cursor allCursor = dbHelper.getAllMedia();
                if (allCursor != null) {
                    int idIndex = allCursor.getColumnIndex(DatabaseHelper.COL_ID);
                    int titleIndex = allCursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                    int typeIndex = allCursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                    int genreIndex = allCursor.getColumnIndex(DatabaseHelper.COL_GENRE);
                    int statusIndex = allCursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                    int progressIndex = allCursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                    int prevProgressIndex = allCursor.getColumnIndex(DatabaseHelper.COL_PREVIOUS_PROGRESS);
                    int totalIndex = allCursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                    int unitIndex = allCursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                    int imageIndex = allCursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);
                    int ratingIndex = allCursor.getColumnIndex(DatabaseHelper.COL_RATING);
                    int favoriteIndex = allCursor.getColumnIndex(DatabaseHelper.COL_IS_FAVORITE);
                    int sourceUrlIndex = allCursor.getColumnIndex(DatabaseHelper.COL_SOURCE_URL);
                    int contentTypeIndex = allCursor.getColumnIndex(DatabaseHelper.COL_CONTENT_TYPE);

                    if (allCursor.moveToFirst()) {
                        do {
                            int id = idIndex != -1 ? allCursor.getInt(idIndex) : -1;
                            if (id <= 0) continue;
                            String title = titleIndex != -1 ? allCursor.getString(titleIndex) : "";
                            String type = typeIndex != -1 ? allCursor.getString(typeIndex) : "";
                            String genre = genreIndex != -1 ? allCursor.getString(genreIndex) : "";
                            String status = statusIndex != -1 ? allCursor.getString(statusIndex) : "";
                            float progress = progressIndex != -1 ? allCursor.getFloat(progressIndex) : 0f;
                            float prevProgress = prevProgressIndex != -1 ? allCursor.getFloat(prevProgressIndex) : progress;
                            int total = totalIndex != -1 ? allCursor.getInt(totalIndex) : 0;
                            String unit = unitIndex != -1 ? allCursor.getString(unitIndex) : "";
                            String imagePath = imageIndex != -1 ? allCursor.getString(imageIndex) : null;
                            float rating = ratingIndex != -1 ? allCursor.getFloat(ratingIndex) : 0f;
                            boolean favorite = favoriteIndex != -1 && allCursor.getInt(favoriteIndex) == 1;
                            String sourceUrl = sourceUrlIndex != -1 ? allCursor.getString(sourceUrlIndex) : null;
                            String contentType = contentTypeIndex != -1 ? allCursor.getString(contentTypeIndex) : null;
                            recommendationCandidates.add(new com.example.mediavault.ui.library.MediaItem(
                                    id, title, type, genre, status, progress, prevProgress, total, unit, imagePath, rating, favorite, sourceUrl, contentType
                            ));
                        } while (allCursor.moveToNext());
                    }
                    allCursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            java.util.List<com.example.mediavault.ui.library.MediaItem> externalRecommendations =
                    fetchApiRecommendations(recommendationCandidates);
            if (!externalRecommendations.isEmpty()) {
                recommendationCandidates.addAll(externalRecommendations);
            }
             
            if (loadedItems.isEmpty()) {
                loadedItems.add(new com.example.mediavault.ui.library.MediaItem(
                    -1, "No Ongoing Media", "Start something!", "", "Planning", 0.0f, 0.0f, 100, "Percent", null, 0f, false, null, null
                ));
            }

            com.example.mediavault.AppExecutor.getInstance().mainThread().execute(() -> {
                spotlightItems.clear();
                spotlightItems.addAll(loadedItems);
                if (spotlightAdapter != null) {
                    spotlightAdapter.notifyDataSetChanged();
                }
                updateRecommendationRail(recommendationCandidates);
            });
        });
    }

    private void updateRecommendationRail(java.util.List<com.example.mediavault.ui.library.MediaItem> items) {
        if (!isAdded() || textHomeRecommendationTitle == null || textHomeRecommendationSubtitle == null) {
            return;
        }

        java.util.List<com.example.mediavault.ui.library.MediaItem> validItems = new java.util.ArrayList<>();
        java.util.Set<String> seenTitles = new java.util.HashSet<>();
        for (com.example.mediavault.ui.library.MediaItem item : items) {
            if (item == null) continue;
            if ("Recently Deleted".equalsIgnoreCase(item.getStatus())) continue;
            String title = item.getTitle() == null ? "" : item.getTitle().trim().toLowerCase(Locale.ROOT);
            if (title.isEmpty()) continue;
            if (!seenTitles.add(title)) continue;
            validItems.add(item);
        }

        if (validItems.isEmpty()) {
            textHomeRecommendationTitle.setText(R.string.auto_home_what_to_watch_next);
            textHomeRecommendationSubtitle.setText(R.string.auto_home_recommendation_empty);
            if (recommendationAdapter != null) {
                recommendationItems.clear();
                recommendationAdapter.replaceItems(recommendationItems);
            }
            return;
        }

        boolean readingFocus = isReadingFocus(validItems);
        if (readingFocus) {
            textHomeRecommendationTitle.setText(R.string.auto_home_what_to_read_next);
            textHomeRecommendationSubtitle.setText(R.string.auto_home_recommendation_subtitle_read);
        } else {
            textHomeRecommendationTitle.setText(R.string.auto_home_what_to_watch_next);
            textHomeRecommendationSubtitle.setText(R.string.auto_home_recommendation_subtitle_watch);
        }

        String focusGenre = extractTopGenre(validItems, readingFocus);
        java.util.List<com.example.mediavault.ui.library.MediaItem> ranked = new java.util.ArrayList<>(validItems);
        ranked.sort((left, right) -> Double.compare(
                recommendationScore(right, readingFocus, focusGenre),
                recommendationScore(left, readingFocus, focusGenre)
        ));
        int limit = Math.min(24, ranked.size());
        recommendationItems.clear();
        recommendationItems.addAll(ranked.subList(0, limit));
        if (recommendationAdapter != null) {
            recommendationAdapter.replaceItems(recommendationItems);
        }
    }

    private void openRecommendationDetails(@NonNull com.example.mediavault.ui.library.MediaItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (item.getId() <= 0) {
            String sourceUrl = item.getSourceUrl();
            if (sourceUrl != null && !sourceUrl.trim().isEmpty()) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl));
                startActivity(browserIntent);
                return;
            }
            addRecommendationToLibrary(item);
            return;
        }
        Intent intent = new Intent(context, DescriptionActivity.class);
        intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, item.getId());
        startActivity(intent);
    }

    private void addRecommendationToLibrary(@NonNull com.example.mediavault.ui.library.MediaItem item) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, AddMediaActivity.class);
        String title = item.getTitle() == null ? "" : item.getTitle().trim();
        if (!title.isEmpty()) {
            intent.putExtra("PREFILL_TITLE", title);
        }
        String typeHint = toTypeHint(item.getType());
        if (!typeHint.isEmpty()) {
            intent.putExtra("PREFILL_TYPE_HINT", typeHint);
        }
        String sourceUrl = item.getSourceUrl();
        if (sourceUrl != null && !sourceUrl.trim().isEmpty()) {
            intent.putExtra("PREFILL_SOURCE_URL", sourceUrl.trim());
        }
        startActivity(intent);
        ToastUtils.showCustomToast(context, "Review and save to add it to your library.");
    }

    private static String toTypeHint(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        if (type.contains("anime")) return "Anime";
        if (type.contains("movie") || type.contains("film")) return "Movie";
        if (type.contains("series") || type.contains("show") || type.contains("tv")) return "Series";
        if (type.contains("manga")) return "Manga";
        if (isReadingTypeStatic(type)) return "Book";
        return "";
    }

    private static boolean isReadingTypeStatic(String type) {
        return type.contains("book")
                || type.contains("novel")
                || type.contains("manga")
                || type.contains("manhwa")
                || type.contains("webtoon")
                || type.contains("comic");
    }

    private static boolean isWatchingTypeStatic(String type) {
        return type.contains("movie")
                || type.contains("anime")
                || type.contains("series")
                || type.contains("show")
                || type.contains("tv")
                || type.contains("documentary");
    }

    private static boolean isReadingType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        return isReadingTypeStatic(type);
    }

    private static boolean isWatchingType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        return isWatchingTypeStatic(type);
    }

    private static boolean isReadingFocus(java.util.List<com.example.mediavault.ui.library.MediaItem> items) {
        int readingWeight = 0;
        int watchingWeight = 0;
        for (com.example.mediavault.ui.library.MediaItem item : items) {
            if (item == null) continue;
            String status = item.getStatus() == null ? "" : item.getStatus().trim().toLowerCase(Locale.ROOT);
            int weight = "ongoing".equals(status) ? 2 : 1;
            if (isReadingType(item.getType())) {
                readingWeight += weight;
            } else if (isWatchingType(item.getType())) {
                watchingWeight += weight;
            }
        }
        return readingWeight > watchingWeight;
    }

    private static String extractTopGenre(java.util.List<com.example.mediavault.ui.library.MediaItem> items, boolean readingFocus) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (com.example.mediavault.ui.library.MediaItem item : items) {
            if (item == null) continue;
            if (readingFocus && !isReadingType(item.getType())) continue;
            if (!readingFocus && !isWatchingType(item.getType())) continue;
            String genre = item.getGenre();
            if (genre == null) continue;
            String[] parts = genre.split("[,/|]");
            if (parts.length == 0) continue;
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        String best = "";
        int max = 0;
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static double recommendationScore(@NonNull com.example.mediavault.ui.library.MediaItem item, boolean readingFocus, String focusGenre) {
        double score = 0d;
        boolean focusType = readingFocus ? isReadingType(item.getType()) : isWatchingType(item.getType());
        score += focusType ? 70d : 15d;

        String status = item.getStatus() == null ? "" : item.getStatus().trim().toLowerCase(Locale.ROOT);
        switch (status) {
            case "ongoing":
                score += 20d;
                break;
            case "planning":
                score += 12d;
                break;
            case "discover":
                score += 14d;
                break;
            case "paused":
                score += 8d;
                break;
            case "completed":
                score -= 12d;
                break;
            case "dropped":
                score -= 20d;
                break;
            default:
                break;
        }

        if (item.isFavorite()) score += 12d;
        score += Math.min(10d, Math.max(0d, item.getRatingValue() * 2d));

        int total = Math.max(0, item.getTotalCount());
        double current = Math.max(0d, item.getCurrentProgress());
        if (total > 0) {
            double ratio = Math.min(1d, current / total);
            score += (1d - ratio) * 15d;
            if (ratio >= 0.95d) score -= 8d;
        }
        if (current > 0d) score += 2d;

        String genre = item.getGenre() == null ? "" : item.getGenre().toLowerCase(Locale.ROOT);
        if (!focusGenre.isEmpty() && genre.contains(focusGenre)) {
            score += 10d;
        }
        if (item.getSourceUrl() != null && !item.getSourceUrl().trim().isEmpty()) {
            score += 3d;
        }
        return score;
    }

    private java.util.List<com.example.mediavault.ui.library.MediaItem> fetchApiRecommendations(
            java.util.List<com.example.mediavault.ui.library.MediaItem> localItems
    ) {
        if (localItems == null || localItems.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        boolean readingFocus = isReadingFocus(localItems);
        String focusGenre = extractTopGenre(localItems, readingFocus);
        java.util.List<String> queries = buildApiQueries(localItems, focusGenre, readingFocus);
        if (queries.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<UniversalMediaResult> apiResults = new java.util.ArrayList<>();
        for (String query : queries) {
            if (readingFocus) {
                collectApiResults(apiResults, new GoogleBooksStrategy(), query, 8);
                collectApiResults(apiResults, new MangaDexStrategy(), query, 8);
            } else {
                collectApiResults(apiResults, new AniListAnimeStrategy(), query, 8);
                if (!BuildConfig.TMDB_API_KEY.isEmpty() || !BuildConfig.OMDB_API_KEY.isEmpty()) {
                    try {
                        java.util.List<UniversalMediaResult> movies =
                                new MovieSearchWaterfall(BuildConfig.TMDB_API_KEY, BuildConfig.OMDB_API_KEY)
                                        .searchMovies(query);
                        appendLimited(apiResults, movies, 8);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return mapApiResultsToMediaItems(apiResults);
    }

    private static java.util.List<String> buildApiQueries(
            java.util.List<com.example.mediavault.ui.library.MediaItem> items,
            String focusGenre,
            boolean readingFocus
    ) {
        java.util.LinkedHashSet<String> queries = new java.util.LinkedHashSet<>();
        for (com.example.mediavault.ui.library.MediaItem item : items) {
            if (item == null || item.getTitle() == null) continue;
            if (readingFocus && !isReadingType(item.getType())) continue;
            if (!readingFocus && !isWatchingType(item.getType())) continue;
            String title = item.getTitle().trim();
            if (!title.isEmpty()) {
                queries.add(title);
            }
            if (queries.size() >= 2) break;
        }
        if (!focusGenre.isEmpty()) {
            queries.add(focusGenre + (readingFocus ? " novel manga" : " movie anime"));
        }
        if (queries.isEmpty()) {
            queries.add(readingFocus ? "popular books" : "popular movies anime");
        }
        return new java.util.ArrayList<>(queries);
    }

    private static void collectApiResults(
            java.util.List<UniversalMediaResult> sink,
            com.example.mediavault.api.search.MediaSearchStrategy strategy,
            String query,
            int limit
    ) {
        try {
            java.util.List<UniversalMediaResult> results = strategy.executeSearch(query);
            appendLimited(sink, results, limit);
        } catch (Exception ignored) {
        }
    }

    private static void appendLimited(
            java.util.List<UniversalMediaResult> sink,
            java.util.List<UniversalMediaResult> source,
            int limit
    ) {
        if (source == null || source.isEmpty()) return;
        int count = 0;
        for (UniversalMediaResult result : source) {
            if (result == null) continue;
            sink.add(result);
            count++;
            if (count >= limit) break;
        }
    }

    private static java.util.List<com.example.mediavault.ui.library.MediaItem> mapApiResultsToMediaItems(
            java.util.List<UniversalMediaResult> apiResults
    ) {
        if (apiResults == null || apiResults.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.ArrayList<com.example.mediavault.ui.library.MediaItem> mapped = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        int syntheticId = -10_000;
        for (UniversalMediaResult result : apiResults) {
            String title = result.getTitle() == null ? "" : result.getTitle().trim();
            if (title.isEmpty()) continue;
            String dedupeKey = title.toLowerCase(Locale.ROOT);
            if (!seen.add(dedupeKey)) continue;

            String type = result.getMediaType() == null ? "" : result.getMediaType().trim();
            String unit;
            if (isReadingTypeStatic(type.toLowerCase(Locale.ROOT))) {
                unit = "Pages";
            } else if (type.toLowerCase(Locale.ROOT).contains("anime")) {
                unit = "Episodes";
            } else if (type.toLowerCase(Locale.ROOT).contains("movie")) {
                unit = "Minutes";
            } else {
                unit = "Items";
            }
            String genre = result.getSourceProvider() == null ? "" : result.getSourceProvider();
            String sourceUrl = result.getSourceUrl() == null ? "" : result.getSourceUrl();
            String imageUrl = result.getImageUrl() == null ? "" : result.getImageUrl();
            mapped.add(new com.example.mediavault.ui.library.MediaItem(
                    syntheticId--,
                    title,
                    type.isEmpty() ? "Media" : type,
                    genre,
                    "Discover",
                    0f,
                    0f,
                    0,
                    unit,
                    imageUrl,
                    0f,
                    false,
                    sourceUrl,
                    "discover"
            ));
            if (mapped.size() >= 24) break;
        }
        return mapped;
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
        updateSpotlightData();
        updateDailyProgress();
        refreshStreakAsync();
        updateServiceStatusBanner();
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

    private void updateStreakUI() {
        if (goalsManager == null) return;
        int streak = goalsManager.getStreakCount();
        if (tvStreakCount != null) tvStreakCount.setText(String.valueOf(streak));

        String message;
        if (streak == 0) {
            message = "Start your journey today!";
        } else if (streak < 3) {
            message = "Great start! Keep the momentum.";
        } else if (streak < 7) {
            message = "You're building a habit. Nice!";
        } else if (streak < 14) {
            message = "Two weeks strong! You're on fire.";
        } else {
            message = "Legendary streak! Don't stop now.";
        }
        if (tvStreakMessage != null) tvStreakMessage.setText(message);
    }

    private void refreshStreakAsync() {
        if (goalsManager == null) return;
        com.example.mediavault.AppExecutor.getInstance().diskIO().execute(() -> {
            goalsManager.calculateCurrentStreakSync();
            com.example.mediavault.AppExecutor.getInstance().mainThread().execute(() -> {
                if (isAdded()) {
                    updateStreakUI();
                }
            });
        });
    }

    private int parseNonNegativeGoal(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        int parsed = Integer.parseInt(raw.trim());
        return Math.max(0, parsed);
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
    
    private void setupServiceStatusBanner() {
        if (btnEnableService == null) return;
        btnEnableService.setOnClickListener(v -> {
            AccessibilityServiceHelper.openAccessibilitySettings(requireContext());
        });
    }
    
    private void updateServiceStatusBanner() {
        if (cardServiceStatus == null) return;
        
        Context context = getContext();
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences("mediavault_prefs", Context.MODE_PRIVATE);
        boolean trackingEnabled = prefs.getBoolean("enable_monitoring", true);
        
        // Only check if tracking is enabled in settings
        if (!trackingEnabled) {
            cardServiceStatus.setVisibility(View.GONE);
            return;
        }
        
        // Check if accessibility service is running
        boolean serviceEnabled = AccessibilityServiceHelper.isServiceEnabled(context);
        
        if (serviceEnabled) {
            cardServiceStatus.setVisibility(View.GONE);
        } else {
            cardServiceStatus.setVisibility(View.VISIBLE);
            if (textServiceStatus != null) {
                textServiceStatus.setText(com.example.mediavault.R.string.auto_external_tracking_is_disabled_enable_accessibili);
            }
        }
    }
}
