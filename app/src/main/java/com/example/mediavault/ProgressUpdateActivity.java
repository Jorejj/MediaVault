package com.example.mediavault;

import android.content.Intent;
import android.database.Cursor;
import android.text.InputType;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.utils.ProgressValueUtils;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class ProgressUpdateActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";

    private int mediaId;
    private DatabaseHelper dbHelper;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextInputEditText etProgress;
    private AutoCompleteTextView autoStatus;
    private ChipGroup chipGroupMood;
    private MaterialButton btnSave;

    private int totalCount = 0;
    private String unit = "";
    private float rating = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_update);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Media not found");
            finish();
            return;
        }

        dbHelper = DatabaseHelper.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_progress_update);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tv_progress_update_title);
        tvSubtitle = findViewById(R.id.tv_progress_update_subtitle);
        etProgress = findViewById(R.id.et_progress_update_progress);
        autoStatus = findViewById(R.id.auto_progress_update_status);
        chipGroupMood = findViewById(R.id.chip_group_progress_update_mood);
        btnSave = findViewById(R.id.btn_progress_update_save);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.media_statuses)
        );
        autoStatus.setAdapter(statusAdapter);

        loadData();
        btnSave.setOnClickListener(v -> saveProgressUpdate());
    }

    private void loadData() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            try (Cursor cursor = dbHelper.getMediaById(mediaId)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    AppExecutor.getInstance().mainThread().execute(() -> {
                        ToastUtils.showCustomToast(this, "Could not load media");
                        finish();
                    });
                    return;
                }

                final String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                final float progress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                final String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                final String mood = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MOOD));
                totalCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                rating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));

                AppExecutor.getInstance().mainThread().execute(() -> {
                    tvTitle.setText(title);
                    tvSubtitle.setText("Progress out of " + totalCount + " " + unit);
                    etProgress.setText(ProgressValueUtils.formatForDisplay(progress, unit));
                    configureProgressInputForUnit(unit);
                    autoStatus.setText(status, false);
                    applyMoodToChips(mood);
                });
            }
        });
    }

    private void saveProgressUpdate() {
        final String progressText = etProgress.getText() != null ? etProgress.getText().toString().trim() : "";
        final String status = autoStatus.getText() != null ? autoStatus.getText().toString().trim() : "";
        final String mood = getSelectedMood();

        if (TextUtils.isEmpty(progressText) || TextUtils.isEmpty(status)) {
            ToastUtils.showCustomToast(this, "Progress and status are required");
            return;
        }

        final float parsedProgress;
        try {
            parsedProgress = Float.parseFloat(progressText);
        } catch (NumberFormatException e) {
            ToastUtils.showCustomToast(this, "Invalid progress value");
            return;
        }

        if (!ProgressValueUtils.isMinutesUnit(unit)
                && Math.abs(parsedProgress - Math.floor(parsedProgress)) > 0.0001f) {
            ToastUtils.showCustomToast(this, "Use whole numbers for " + unit);
            return;
        }

        float normalizedProgress = ProgressValueUtils.normalizeForUnit(parsedProgress, unit);
        if (normalizedProgress < 0 || normalizedProgress > totalCount) {
            ToastUtils.showCustomToast(this, "Progress must be between 0 and " + totalCount);
            return;
        }

        AppExecutor.getInstance().diskIO().execute(() -> {
            boolean progressOk = dbHelper.updateProgressStatusMood(mediaId, normalizedProgress, status, rating, mood);

            AppExecutor.getInstance().mainThread().execute(() -> {
                if (progressOk) {
                    Intent updateIntent = new Intent(DescriptionActivity.ACTION_MEDIA_UPDATED);
                    updateIntent.setPackage(getPackageName());
                    sendBroadcast(updateIntent);
                    ToastUtils.showCustomToast(this, "Progress updated");
                    finish();
                } else {
                    ToastUtils.showCustomToast(this, "Failed to update progress");
                }
            });
        });
    }

    private void configureProgressInputForUnit(String progressUnit) {
        if (ProgressValueUtils.isMinutesUnit(progressUnit)) {
            etProgress.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            etProgress.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    }

    private String getSelectedMood() {
        int checkedId = chipGroupMood.getCheckedChipId();
        if (checkedId == R.id.chip_progress_mood_excited) return "Excited";
        if (checkedId == R.id.chip_progress_mood_happy) return "Happy";
        if (checkedId == R.id.chip_progress_mood_neutral) return "Neutral";
        if (checkedId == R.id.chip_progress_mood_sad) return "Sad";
        if (checkedId == R.id.chip_progress_mood_mindblown) return "Mind-blown";
        return "";
    }

    private void applyMoodToChips(String mood) {
        if (mood == null) {
            chipGroupMood.clearCheck();
            return;
        }
        switch (mood) {
            case "Excited":
                chipGroupMood.check(R.id.chip_progress_mood_excited);
                break;
            case "Happy":
                chipGroupMood.check(R.id.chip_progress_mood_happy);
                break;
            case "Neutral":
                chipGroupMood.check(R.id.chip_progress_mood_neutral);
                break;
            case "Sad":
                chipGroupMood.check(R.id.chip_progress_mood_sad);
                break;
            case "Mind-blown":
                chipGroupMood.check(R.id.chip_progress_mood_mindblown);
                break;
            default:
                chipGroupMood.clearCheck();
                break;
        }
    }
}
