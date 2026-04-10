package com.example.mediavault;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mediavault.ui.library.MediaAdapter;
import com.example.mediavault.ui.library.MediaItem;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class SeriesDetailsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private MediaItem seriesItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_details);

        seriesItem = (MediaItem) getIntent().getSerializableExtra("media_item");
        if (seriesItem == null) {
            finish();
            return;
        }

        setupToolbar();
        setupHeader();
        setupRecyclerView();
        setupResumeButton();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupHeader() {
        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.toolbar_layout);
        collapsingToolbar.setTitle(seriesItem.getTitle());
        Typeface titleTypeface = ResourcesCompat.getFont(this, R.font.sf_pro_bold);
        if (titleTypeface != null) {
            collapsingToolbar.setExpandedTitleTypeface(titleTypeface);
            collapsingToolbar.setCollapsedTitleTypeface(titleTypeface);
        }

        ImageView headerBanner = findViewById(R.id.img_header_banner);
        if (seriesItem.getCoverPath() != null && !seriesItem.getCoverPath().isEmpty()) {
            ImageUtils.loadImage(seriesItem.getCoverPath(), headerBanner);
        } else {
            headerBanner.setImageResource(R.drawable.ambient_bg);
        }
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_series_content);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Use AppExecutor to fetch content from the database
        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            List<MediaItem> contentList = dbHelper.getContentForSeries(seriesItem.getId());
            
            runOnUiThread(() -> {
                adapter = new MediaAdapter(contentList, item -> {
                    // Navigate to appropriate player/reader based on type
                });
                recyclerView.setAdapter(adapter);
            });
        });
    }

    private void setupResumeButton() {
        MaterialButton btnResume = findViewById(R.id.btn_series_resume);
        btnResume.setOnClickListener(v -> {
            // Logic to find the last-watched episode and resume
        });
    }
}
