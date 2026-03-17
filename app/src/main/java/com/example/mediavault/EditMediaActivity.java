package com.example.mediavault;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
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
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.util.List;

public class EditMediaActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";

    private int mediaId;
    private DatabaseHelper dbHelper;

    private TextInputEditText etTitle, etReview, etImage, etJournal;
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_media);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Media not found");
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);
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
        Cursor cursor = dbHelper.getMediaById(mediaId);
        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            etTitle.setText(title);
            collapsingToolbar.setTitle(title);

            String genre = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
            autoGenre.setText(genre, false);
            etReview.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW)));
            etJournal.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_JOURNAL)));
            etProgress.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS))));
            etTotal.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT))));
            rbRating.setRating(cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING)));
            
            String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));
            etImage.setText(imagePath);
            updateImageHeader(imagePath);

            autoType.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE)), false);
            autoStatus.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS)), false);
            autoUnit.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT)), false);
            autoPriority.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRIORITY)), false);
            switchFavorite.setChecked(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_FAVORITE)) == 1);
            applyMoodToChips(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MOOD)));
            
            cursor.close();
        }
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
        boolean isFavorite = switchFavorite.isChecked();
        String newImage = etImage.getText().toString().trim();
        float rating = rbRating.getRating();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(totalStr)) {
            ToastUtils.showCustomToast(this, "Title and Total Capacity are required");
            return;
        }

        new Thread(() -> {
            String finalImage = ImageUtils.downloadAndSaveImage(EditMediaActivity.this, newImage);
            runOnUiThread(() -> {
                try {
                    int progress = Integer.parseInt(progressStr);
                    int total = Integer.parseInt(totalStr);

                    if (progress > total) {
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Progress cannot exceed Total Capacity");
                        return;
                    }

                    if (dbHelper.updateMedia(mediaId, title, type, genre, status, progress, total, unit, finalImage, rating, review, journal, mood, priority, isFavorite)) {
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Changes saved");
                        Intent updateIntent = new Intent(DescriptionActivity.ACTION_MEDIA_UPDATED);
                        updateIntent.setPackage(getPackageName());
                        sendBroadcast(updateIntent);
                        finish();
                    } else {
                        ToastUtils.showCustomToast(EditMediaActivity.this, "Error: Could not save changes");
                    }
                } catch (NumberFormatException e) {
                    ToastUtils.showCustomToast(EditMediaActivity.this, "Invalid progress or total value");
                }
            });
        }).start();
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
