package com.example.mediavault;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.textfield.TextInputEditText;

public class EditMediaActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_ID = "extra_media_id";

    private int mediaId;
    private DatabaseHelper dbHelper;
    private String coverPath;

    private TextInputEditText etTitle, etGenre, etReview;
    private ImageView ivCover;
    private EditText etProgress, etTotal;
    private Spinner spinnerType, spinnerStatus, spinnerUnit;
    private RatingBar rbRating;
    private Button btnSave;
    private CollapsingToolbarLayout collapsingToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_media);

        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        if (mediaId == -1) {
            Toast.makeText(this, "Error: Media not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);
        initViews();
        loadMediaData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        collapsingToolbar = findViewById(R.id.toolbar_layout);
        collapsingToolbar.setTitle("Edit Media");

        etTitle = findViewById(R.id.et_edit_title);
        etGenre = findViewById(R.id.et_edit_genre);
        etReview = findViewById(R.id.et_edit_review);
        ivCover = findViewById(R.id.iv_edit_cover);
        etProgress = findViewById(R.id.et_edit_progress);
        etTotal = findViewById(R.id.et_edit_total);
        spinnerType = findViewById(R.id.spinner_edit_type);
        spinnerStatus = findViewById(R.id.spinner_edit_status);
        spinnerUnit = findViewById(R.id.spinner_edit_unit);
        rbRating = findViewById(R.id.rb_edit_rating);
        btnSave = findViewById(R.id.btn_edit_save);

        btnSave.setOnClickListener(v -> saveChanges());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadMediaData() {
        Cursor cursor = dbHelper.getMediaById(mediaId);
        if (cursor != null && cursor.moveToFirst()) {
            etTitle.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE)));
            etGenre.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE)));
            etReview.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW)));
            etProgress.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROGRESS))));
            etTotal.setText(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAPACITY))));
            rbRating.setRating(cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING)));
            coverPath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COVER));

            setSpinnerToValue(spinnerType, cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TYPE)));
            setSpinnerToValue(spinnerStatus, cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS)));
            setSpinnerToValue(spinnerUnit, cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT)));

            // In a real app, use Glide to load coverPath into ivCover
            
            cursor.close();
        }
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void saveChanges() {
        String title = etTitle.getText().toString().trim();
        String type = spinnerType.getSelectedItem().toString();
        String status = spinnerStatus.getSelectedItem().toString();
        String genre = etGenre.getText().toString().trim();
        String review = etReview.getText().toString().trim();
        String progressStr = etProgress.getText().toString().trim();
        String totalStr = etTotal.getText().toString().trim();
        String unit = spinnerUnit.getSelectedItem().toString();
        float rating = rbRating.getRating();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(totalStr)) {
            Toast.makeText(this, "Title and Total Capacity are required", Toast.LENGTH_SHORT).show();
            return;
        }

        int progress = Integer.parseInt(progressStr);
        int total = Integer.parseInt(totalStr);

        if (progress > total) {
            Toast.makeText(this, "Progress cannot exceed Total Capacity", Toast.LENGTH_SHORT).show();
            return;
        }

        if (dbHelper.updateMedia(mediaId, title, type, genre, status, progress, total, unit, coverPath, rating, review)) {
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(DescriptionActivity.ACTION_MEDIA_UPDATED));
            finish();
        } else {
            Toast.makeText(this, "Error: Could not save changes", Toast.LENGTH_SHORT).show();
        }
    }
}