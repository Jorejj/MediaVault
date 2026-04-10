package com.example.mediavault;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Toast;

import com.example.mediavault.widget.ToastUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.example.mediavault.utils.ProgressValueUtils;
import java.io.File;
import java.util.List;

public class EditMediaActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";

    private int mediaId;
    private DatabaseHelper dbHelper;

    private TextInputEditText etTitle, etReview, etImage, etJournal, etCreator, etDescription;
    private TextInputLayout tilImage;
    private ImageView ivCover;
    private TextInputEditText etProgress, etTotal;
    private AutoCompleteTextView autoType, autoStatus, autoUnit, autoPriority, autoGenre;
    private RatingBar rbRating;
    private ChipGroup chipGroupMood;
    private SwitchMaterial switchFavorite;
    private MaterialButton btnSave;
    private CollapsingToolbarLayout collapsingToolbar;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null) {
                        etImage.setText(selectedImage.toString());
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_media);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Media not found");
            finish();
            return;
        }

        dbHelper = DatabaseHelper.getInstance(this);
        initViews();
        setupDropdowns();
        loadMediaData();
        setupImagePreviewListener();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Title handled by CollapsingToolbar
        }

        collapsingToolbar = findViewById(R.id.toolbar_layout);

        etTitle = findViewById(R.id.et_edit_title);
        etCreator = findViewById(R.id.et_edit_creator);
        etDescription = findViewById(R.id.et_edit_description);
        etReview = findViewById(R.id.et_edit_review);
        etJournal = findViewById(R.id.et_edit_journal);
        etImage = findViewById(R.id.et_edit_image);
        tilImage = findViewById(R.id.til_edit_image);
        ivCover = findViewById(R.id.iv_edit_cover);
        etProgress = findViewById(R.id.et_edit_progress);
        etTotal = findViewById(R.id.et_edit_total);
        autoType = findViewById(R.id.spinner_edit_type_auto);
        autoStatus = findViewById(R.id.spinner_edit_status_auto);
        autoUnit = findViewById(R.id.spinner_edit_unit_auto);
        autoPriority = findViewById(R.id.spinner_edit_priority_auto);
        autoGenre = findViewById(R.id.auto_edit_genre);
        rbRating = findViewById(R.id.rb_edit_rating);
        chipGroupMood = findViewById(R.id.chip_group_mood);
        switchFavorite = findViewById(R.id.switch_edit_favorite);
        btnSave = findViewById(R.id.btn_edit_save);

        if (tilImage != null) {
            tilImage.setEndIconOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                imagePickerLauncher.launch(intent);
            });
        }

        btnSave.setOnClickListener(v -> saveChanges());

        rbRating.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            // Rating updated by user interaction
        });

        // Update title in real-time
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                collapsingToolbar.setTitle(s);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupDropdowns() {
        String[] types = getResources().getStringArray(R.array.media_types);
        ArrayAdapter<String> adapterType = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        autoType.setAdapter(adapterType);
        autoType.setOnItemClickListener((parent, view, position, id) -> updateGenreDropdown());

        String[] statuses = getResources().getStringArray(R.array.media_statuses);
        ArrayAdapter<String> adapterStatus = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, statuses);
        autoStatus.setAdapter(adapterStatus);

        String[] units = getResources().getStringArray(R.array.capacity_units);
        ArrayAdapter<String> adapterUnit = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, units);
        autoUnit.setAdapter(adapterUnit);
        autoUnit.setOnItemClickListener((parent, view, position, id) ->
                configureProgressInputForUnit(autoUnit.getText().toString().trim()));

        String[] priorities = getResources().getStringArray(R.array.priority_levels);
        ArrayAdapter<String> adapterPriority = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, priorities);
        autoPriority.setAdapter(adapterPriority);
        
        // Initial genre setup
        updateGenreDropdown();
    }
    
    private void updateGenreDropdown() {
        String mediaType = autoType.getText().toString().trim();
        if (mediaType.isEmpty()) {
            mediaType = "Book"; // default
        }
        List<String> genres = GenreManager.getGenreListForMediaType(mediaType);
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genres);
        autoGenre.setAdapter(genreAdapter);
    }

    private void setupImagePreviewListener() {
        etImage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateImageHeader(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateImageHeader(String path) {
        if (path == null || path.isEmpty()) {
            ivCover.setImageResource(R.drawable.mediavault_logo);
            return;
        }

        File file = new File(path);
        if (file.exists()) {
            Glide.with(this)
                    .load(file)
                    .centerCrop()
                    .placeholder(R.drawable.mediavault_logo)
                    .error(R.drawable.mediavault_logo)
                    .into(ivCover);
        } else {
            Glide.with(this)
                    .load(path)
                    .centerCrop()
                    .placeholder(R.drawable.mediavault_logo)
                    .error(R.drawable.mediavault_logo)
                    .into(ivCover);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadMediaData() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            Cursor cursor = dbHelper.getMediaById(mediaId);
            if (cursor != null && cursor.moveToFirst()) {
                // Extract all data on background thread
                final String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                final String genre = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
                final String creator = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATOR));
                final String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPTION));
                final String review = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW));
                final String journal = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_JOURNAL));
                final float progress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                final int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                final float rating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));
                final String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));
                final String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                final String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                final String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                final String priority = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRIORITY));
                final boolean isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_FAVORITE)) == 1;
                final String mood = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MOOD));
                cursor.close();

                // Update UI on main thread
                runOnUiThread(() -> {
                    etTitle.setText(title);
                    collapsingToolbar.setTitle(title);
                    autoGenre.setText(genre, false);
                    etCreator.setText(creator);
                    etDescription.setText(description);
                    etReview.setText(review);
                    etJournal.setText(journal);
                    etProgress.setText(ProgressValueUtils.formatForDisplay(progress, unit));
                    etTotal.setText(String.valueOf(total));
                    rbRating.setRating(rating);
                    etImage.setText(imagePath);
                    updateImageHeader(imagePath);
                    autoType.setText(type, false);
                    autoStatus.setText(status, false);
                    autoUnit.setText(unit, false);
                    configureProgressInputForUnit(unit);
                    autoPriority.setText(priority, false);
                    switchFavorite.setChecked(isFavorite);
                    applyMoodToChips(mood);
                });
            } else {
                if (cursor != null) cursor.close();
            }
        });
    }

    private void saveChanges() {
        String title = etTitle.getText().toString().trim();
        String type = autoType.getText().toString();
        String status = autoStatus.getText().toString();
        String genre = autoGenre.getText().toString().trim();
        String review = etReview.getText().toString().trim();
        String journal = etJournal.getText().toString().trim();
        String progressStr = etProgress.getText().toString().trim();
        String totalStr = etTotal.getText().toString().trim();
        String unit = autoUnit.getText().toString();
        String priority = autoPriority.getText().toString();
        String mood = getSelectedMood();
        String creator = etCreator.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        boolean isFavorite = switchFavorite.isChecked();
        String newImage = etImage.getText().toString().trim();
        float rating = rbRating.getRating();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(totalStr)) {
            ToastUtils.showCustomToast(this, "Title and Total Capacity are required");
            return;
        }

        AppExecutor.getInstance().diskIO().execute(() -> {
            try {
                float progress = Float.parseFloat(progressStr);
                int total = Integer.parseInt(totalStr);
                if (!ProgressValueUtils.isMinutesUnit(unit)
                        && Math.abs(progress - Math.floor(progress)) > 0.0001f) {
                    AppExecutor.getInstance().mainThread().execute(() ->
                            ToastUtils.showCustomToast(EditMediaActivity.this, "Use whole numbers for " + unit));
                    return;
                }

                float normalizedProgress = ProgressValueUtils.normalizeForUnit(progress, unit);
                if (normalizedProgress > total) {
                    AppExecutor.getInstance().mainThread().execute(() ->
                            ToastUtils.showCustomToast(EditMediaActivity.this, "Progress cannot exceed Total Capacity"));
                    return;
                }

                String finalImage = ImageUtils.downloadAndSaveImage(getApplicationContext(), newImage);
                boolean updated = dbHelper.updateMedia(mediaId, title, type, genre, status, normalizedProgress, total, unit, finalImage, rating, review, journal, mood, priority, isFavorite, description, creator);

                AppExecutor.getInstance().mainThread().execute(() -> {
                    if (updated) {
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Changes saved");
                        Intent updateIntent = new Intent(DescriptionActivity.ACTION_MEDIA_UPDATED);
                        updateIntent.setPackage(getPackageName());
                        sendBroadcast(updateIntent);
                        finish();
                    } else {
                        new MaterialAlertDialogBuilder(EditMediaActivity.this)
                                .setTitle("Update Failed")
                                .setMessage("This title already exists in your library. Please use a unique title.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            } catch (NumberFormatException e) {
                AppExecutor.getInstance().mainThread().execute(() ->
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Invalid progress or total value"));
            } catch (Exception e) {
                AppExecutor.getInstance().mainThread().execute(() ->
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Failed to save changes"));
            }
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
        if (checkedId == R.id.chip_mood_excited) return "Excited";
        if (checkedId == R.id.chip_mood_happy) return "Happy";
        if (checkedId == R.id.chip_mood_neutral) return "Neutral";
        if (checkedId == R.id.chip_mood_sad) return "Sad";
        if (checkedId == R.id.chip_mood_mindblown) return "Mind-blown";
        return "";
    }

    private void applyMoodToChips(String mood) {
        if (mood == null) {
            chipGroupMood.clearCheck();
            return;
        }
        switch (mood) {
            case "Excited":
                chipGroupMood.check(R.id.chip_mood_excited);
                break;
            case "Happy":
                chipGroupMood.check(R.id.chip_mood_happy);
                break;
            case "Neutral":
                chipGroupMood.check(R.id.chip_mood_neutral);
                break;
            case "Sad":
                chipGroupMood.check(R.id.chip_mood_sad);
                break;
            case "Mind-blown":
                chipGroupMood.check(R.id.chip_mood_mindblown);
                break;
            default:
                chipGroupMood.clearCheck();
                break;
        }
    }
}
