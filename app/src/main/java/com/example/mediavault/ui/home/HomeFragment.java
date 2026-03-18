package com.example.mediavault.ui.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.R;
import com.example.mediavault.receiver.DailyGoalReminderReceiver;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

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
    
    // Daily Goals UI
    private TextView tvEditGoals, tvGoalsEmpty, tvGoalsHeader, tvCongratulations;
    private LinearLayout layoutGoalPages, layoutGoalEpisodes, layoutGoalMinutes;
    private TextView tvProgressPages, tvProgressEpisodes, tvProgressMinutes;
    private ProgressBar pbGoalPages, pbGoalEpisodes, pbGoalMinutes;
    private DailyGoalsManager goalsManager;

    private boolean isUpdateReceiverRegistered = false;
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DescriptionActivity.ACTION_MEDIA_UPDATED.equals(intent.getAction())) {
                updateOverviewStats();
                updateSpotlightData();
                updateDailyProgress();
                ToastUtils.showCustomToast(context, "Home data refreshed");
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        dbHelper = new DatabaseHelper(requireContext());
        goalsManager = new DailyGoalsManager(requireContext());
        
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
        
        tvViewDetailedStats = view.findViewById(R.id.tv_view_detailed_stats);
        btnAnalyzeHabits = view.findViewById(R.id.btn_analyze_habits);

        updateOverviewStats();
        setupNavigation(view);
        setupSpotlightCarousel();
        updateDailyProgress();
        scheduleDailyReminder();
        
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
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Daily Goals")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    String pagesStr = etPages.getText() != null ? etPages.getText().toString().trim() : "";
                    String epsStr = etEpisodes.getText() != null ? etEpisodes.getText().toString().trim() : "";
                    String minStr = etMinutes.getText() != null ? etMinutes.getText().toString().trim() : "";
                    
                    goalsManager.setGoalPages(pagesStr.isEmpty() ? 0 : Integer.parseInt(pagesStr));
                    goalsManager.setGoalEpisodes(epsStr.isEmpty() ? 0 : Integer.parseInt(epsStr));
                    goalsManager.setGoalMinutes(minStr.isEmpty() ? 0 : Integer.parseInt(minStr));
                    
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
            .show();
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
            pbGoalPages.setProgress(progress.pagesRead);
            tvProgressPages.setText(progress.pagesRead + "/" + goalPages);
            hasAnyGoal = true;
            if (progress.pagesRead < goalPages) allGoalsMet = false;
        } else {
            layoutGoalPages.setVisibility(View.GONE);
        }

        // Update Episodes Goal
        int goalEpisodes = goalsManager.getGoalEpisodes();
        if (goalEpisodes > 0) {
            layoutGoalEpisodes.setVisibility(View.VISIBLE);
            pbGoalEpisodes.setMax(goalEpisodes);
            pbGoalEpisodes.setProgress(progress.episodesWatched);
            tvProgressEpisodes.setText(progress.episodesWatched + "/" + goalEpisodes);
            hasAnyGoal = true;
            if (progress.episodesWatched < goalEpisodes) allGoalsMet = false;
        } else {
            layoutGoalEpisodes.setVisibility(View.GONE);
        }

        // Update Minutes Goal
        int goalMinutes = goalsManager.getGoalMinutes();
        if (goalMinutes > 0) {
            layoutGoalMinutes.setVisibility(View.VISIBLE);
            pbGoalMinutes.setMax(goalMinutes);
            pbGoalMinutes.setProgress(progress.minutesWatched);
            tvProgressMinutes.setText(progress.minutesWatched + "/" + goalMinutes);
            hasAnyGoal = true;
            if (progress.minutesWatched < goalMinutes) allGoalsMet = false;
        } else {
            layoutGoalMinutes.setVisibility(View.GONE);
        }
        
        if (hasAnyGoal) {
            tvGoalsEmpty.setVisibility(View.GONE);
            if (allGoalsMet) {
                tvCongratulations.setVisibility(View.VISIBLE);
                tvGoalsHeader.setText("Goal Achieved!");
                tvGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green));
            } else {
                tvCongratulations.setVisibility(View.GONE);
                tvGoalsHeader.setText("Today's Progress");
                tvGoalsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary)); // Fallback if attr not avail
            }
        } else {
            tvGoalsEmpty.setVisibility(View.VISIBLE);
            tvCongratulations.setVisibility(View.GONE);
            tvGoalsHeader.setText("Daily Goals");
        }
    }
    
    private void scheduleDailyReminder() {
        Context context = getContext();
        if (context == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager != null) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                return;
            }
        }

        Intent intent = new Intent(context, DailyGoalReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Cancel any existing alarm first
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        if (!goalsManager.isReminderEnabled()) {
            return;
        }

        // Set alarm based on user preference
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, goalsManager.getReminderHour());
        calendar.set(Calendar.MINUTE, goalsManager.getReminderMinute());
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            try {
                // Use setExact for Android 12+ if allowed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    alarmManager.setInexactRepeating(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY,
                            pendingIntent
                    );
                }
            } catch (SecurityException e) {
                // Handle permission denial on newer Android versions if SCHEDULE_EXACT_ALARM is required but not granted
                e.printStackTrace();
            }
        }
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
        if (dbHelper == null || spotlightAdapter == null) return;
        
        spotlightItems.clear();
        try {
            Cursor cursor = dbHelper.getOngoingMediaList();
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_ID);
                int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MEDIA_TYPE);
                int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS);
                int progressIndex = cursor.getColumnIndex(DatabaseHelper.COL_CURRENT_PROGRESS);
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
                            int progress = cursor.getInt(progressIndex);
                            int total = cursor.getInt(totalIndex);
                            String unit = cursor.getString(unitIndex);
                            String imagePath = (imageIndex != -1) ? cursor.getString(imageIndex) : null;
                            
                            spotlightItems.add(new com.example.mediavault.ui.library.MediaItem(
                                id, title, type, "", status, progress, total, unit, imagePath, 0f, false
                            ));
                        } while (cursor.moveToNext());
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback
        }
        
        if (spotlightItems.isEmpty()) {
            spotlightItems.add(new com.example.mediavault.ui.library.MediaItem(
                -1, "No Ongoing Media", "Start something!", "", "Planning", 0, 100, "Percent", null, 0f, false
            ));
        }
        spotlightAdapter.notifyDataSetChanged();
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
        
        try {
            int totalMinutes = dbHelper.getTotalMinutesWatched();
            if (tvWatchTime != null) tvWatchTime.setText(formatDuration(totalMinutes));
            
            int totalPages = dbHelper.getTotalPagesRead();
            if (tvPagesRead != null) tvPagesRead.setText(String.valueOf(totalPages));

            int totalEpisodes = dbHelper.getTotalEpisodesWatched();
            if (tvEpisodesWatched != null) tvEpisodesWatched.setText(String.valueOf(totalEpisodes));

            int totalBooks = dbHelper.getCompletedCountByType("Book");
            if (tvBooksCompleted != null) tvBooksCompleted.setText(String.valueOf(totalBooks));
            
            int ongoingCount = dbHelper.getStatusCount("Ongoing");
            if (tvOngoingItems != null) tvOngoingItems.setText(String.valueOf(ongoingCount));
            
            float avgRating = dbHelper.getAverageRating();
            if (tvAvgRating != null) tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
