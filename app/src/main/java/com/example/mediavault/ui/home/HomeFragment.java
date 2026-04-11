package com.example.mediavault.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
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

import com.example.mediavault.BuildConfig;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
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

    private TextView tvWatchTime, tvPagesRead, tvEpisodesWatched, tvBooksCompleted, tvOngoingItems, tvAvgRating;
    private TextView tvViewDetailedStats;
    private com.google.android.material.button.MaterialButton btnAnalyzeHabits;
    private DatabaseHelper dbHelper;
    private androidx.viewpager2.widget.ViewPager2 pagerSpotlight;
    private SpotlightAdapter spotlightAdapter;
    private java.util.List<com.example.mediavault.ui.library.MediaItem> spotlightItems;
    
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
                updateOverviewStats();
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
        
        // Initialize Views
        tvWatchTime = view.findViewById(R.id.tv_watch_time);
        tvPagesRead = view.findViewById(R.id.tv_pages_read);
        tvEpisodesWatched = view.findViewById(R.id.tv_episodes_watched);
        tvBooksCompleted = view.findViewById(R.id.tv_books_completed);
        tvOngoingItems = view.findViewById(R.id.tv_ongoing_items);
        tvAvgRating = view.findViewById(R.id.tv_avg_rating);
        
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

        updateOverviewStats();
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
            try {
                Cursor cursor = dbHelper.getOngoingMediaList();
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                    int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                    int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                    int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                    int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
                    int prevProgressIndex = cursor.getColumnIndex(DatabaseHelper.COL_PREVIOUS_PROGRESS);
                    int totalIndex = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_COUNT);
                    int unitIndex = cursor.getColumnIndex(DatabaseHelper.COL_UNIT);
                    int imageIndex = cursor.getColumnIndex(DatabaseHelper.COL_IMAGE_PATH);

                    if (idIndex != -1 && titleIndex != -1 && typeIndex != -1 && statusIndex != -1 && 
                        progressIndex != -1 && totalIndex != -1 && unitIndex != -1) {
                        
                        if (cursor.moveToFirst()) {
                            do {
                                int id = cursor.getInt(idIndex);
                                String title = cursor.getString(titleIndex);
                                String type = cursor.getString(typeIndex);
                                String status = cursor.getString(statusIndex);
                                float progress = cursor.getFloat(progressIndex);
                                float prevProgress = prevProgressIndex != -1 ? cursor.getFloat(prevProgressIndex) : progress;
                                int total = cursor.getInt(totalIndex);
                                String unit = cursor.getString(unitIndex);
                                String imagePath = (imageIndex != -1) ? cursor.getString(imageIndex) : null;
                                
                                loadedItems.add(new com.example.mediavault.ui.library.MediaItem(
                                    id, title, type, "", status, progress, prevProgress, total, unit, imagePath, 0f, false, null, null
                                ));
                            } while (cursor.moveToNext());
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            });
        });
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
        updateOverviewStats();
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

    private void updateOverviewStats() {
        if (dbHelper == null) return;
        com.example.mediavault.AppExecutor.getInstance().diskIO().execute(() -> {
            try {
                float totalMinutes = dbHelper.getTotalMinutesWatched();
                float totalPages = dbHelper.getTotalPagesRead();
                float totalEpisodes = dbHelper.getTotalEpisodesWatched();
                int totalBooks = dbHelper.getCompletedCountByType("Book");
                int ongoingCount = dbHelper.getStatusCount("Ongoing");
                float avgRating = dbHelper.getAverageRating();

                com.example.mediavault.AppExecutor.getInstance().mainThread().execute(() -> {
                    if (!isAdded()) return;
                    if (tvWatchTime != null) tvWatchTime.setText(formatDuration((int) totalMinutes));
                    if (tvPagesRead != null) tvPagesRead.setText(String.valueOf((int) ProgressValueUtils.normalizeForUnit(totalPages, "Pages")));
                    if (tvEpisodesWatched != null) tvEpisodesWatched.setText(String.valueOf((int) ProgressValueUtils.normalizeForUnit(totalEpisodes, "Episodes")));
                    if (tvBooksCompleted != null) tvBooksCompleted.setText(String.valueOf(totalBooks));
                    if (tvOngoingItems != null) tvOngoingItems.setText(String.valueOf(ongoingCount));
                    if (tvAvgRating != null) tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
