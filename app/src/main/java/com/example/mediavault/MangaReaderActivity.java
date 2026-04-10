package com.example.mediavault;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.api.consumet.ResolverFactory;
import com.example.mediavault.widget.ToastUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen manga reader with vertical scrolling.
 * Auto-resolves chapter pages via Consumet API (MangaDex).
 * Supports:
 * - Vertical scroll reading
 * - Auto-load next chapter on last page
 * - Immersive full-screen mode
 * - Progress auto-save
 */
public class MangaReaderActivity extends AppCompatActivity {
    private static final String TAG = "MangaReaderActivity";
    
    public static final String EXTRA_MANGA_TITLE = "extra_manga_title";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_START_CHAPTER = "extra_start_chapter";
    
    private RecyclerView rvPages;
    private TextView tvChapterInfo;
    private ImageButton btnPrevChapter, btnNextChapter, btnClose;
    private ProgressBar pbLoading;
    private View overlayControls;
    
    private String mangaTitle;
    private int mediaId;
    private int currentChapter;
    private boolean isLoading = false;
    private volatile boolean isDestroyed = false;
    private boolean chapterProgressCommitted = false;
    
    private MangaPageAdapter pageAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable full-screen immersive mode
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        setContentView(R.layout.activity_manga_reader);
        
        mangaTitle = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        currentChapter = getIntent().getIntExtra(EXTRA_START_CHAPTER, 1);
        
        if (mangaTitle == null || mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Missing manga information");
            finish();
            return;
        }
        
        initViews();
        setupImmersiveMode();
        loadChapter(currentChapter);
    }
    
    private void initViews() {
        rvPages = findViewById(R.id.rv_manga_pages);
        tvChapterInfo = findViewById(R.id.tv_chapter_info);
        btnPrevChapter = findViewById(R.id.btn_prev_chapter);
        btnNextChapter = findViewById(R.id.btn_next_chapter);
        btnClose = findViewById(R.id.btn_close_reader);
        pbLoading = findViewById(R.id.pb_loading_chapter);
        overlayControls = findViewById(R.id.overlay_controls);
        
        // Setup RecyclerView
        rvPages.setLayoutManager(new LinearLayoutManager(this));
        pageAdapter = new MangaPageAdapter(this);
        rvPages.setAdapter(pageAdapter);
        
        // Tap to toggle controls
        rvPages.setOnClickListener(v -> toggleControls());
        
        // Navigation buttons
        btnPrevChapter.setOnClickListener(v -> loadChapter(currentChapter - 1));
        btnNextChapter.setOnClickListener(v -> loadChapter(currentChapter + 1));
        btnClose.setOnClickListener(v -> finish());
        
        // Auto-load next chapter when reaching bottom
        rvPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading) {
                    int lastVisible = layoutManager.findLastVisibleItemPosition();
                    int totalItems = layoutManager.getItemCount();
                    
                    if (!chapterProgressCommitted && totalItems > 0 && lastVisible >= totalItems - 2) {
                        chapterProgressCommitted = true;
                        updateProgressInDatabase(currentChapter);
                    }

                    // At last page, reveal controls
                    if (lastVisible >= totalItems - 1 && totalItems > 0) {
                        showControls();
                    }
                }
            }
        });
    }
    
    private void setupImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
    
    private void loadChapter(int chapter) {
        if (chapter < 1 || isLoading) {
            return;
        }
        
        // ISSUE #7 FIX: Check network before loading
        if (!isNetworkAvailable()) {
            ToastUtils.showCustomToast(this, "No internet connection");
            return;
        }
        
        isLoading = true;
        currentChapter = chapter;
        chapterProgressCommitted = false;
        
        // Update UI
        tvChapterInfo.setText("Chapter " + chapter);
        pbLoading.setVisibility(View.VISIBLE);
        rvPages.setVisibility(View.GONE);
        
        // Disable buttons
        btnPrevChapter.setEnabled(false);
        btnNextChapter.setEnabled(false);
        
        // Resolve chapter pages in background
        AppExecutor.getInstance().networkIO().execute(() -> {
            Log.d(TAG, "Resolving: " + mangaTitle + " Chapter " + chapter);
            
            List<String> pageUrls = ResolverFactory.resolveManga(mangaTitle, chapter);
            
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) return; // ISSUE #17 FIX
                
                isLoading = false;
                pbLoading.setVisibility(View.GONE);
                
                if (pageUrls != null && !pageUrls.isEmpty()) {
                    // ISSUE #5 FIX: Filter out null/empty URLs
                    List<String> validUrls = new ArrayList<>();
                    for (String url : pageUrls) {
                        if (url != null && !url.trim().isEmpty()) {
                            validUrls.add(url);
                        }
                    }
                    
                    if (validUrls.isEmpty()) {
                        Log.e(TAG, "All page URLs are invalid for chapter " + chapter);
                        ToastUtils.showCustomToast(
                            MangaReaderActivity.this,
                            "Chapter pages are corrupted or unavailable"
                        );
                        btnPrevChapter.setEnabled(chapter > 1);
                        btnNextChapter.setEnabled(true);
                        return;
                    }
                    
                    Log.d(TAG, "Loaded " + validUrls.size() + " valid pages");
                    
                    // Display pages
                    pageAdapter.setPages(validUrls);
                    rvPages.setVisibility(View.VISIBLE);
                    rvPages.scrollToPosition(0);
                    
                    // Re-enable buttons
                    btnPrevChapter.setEnabled(chapter > 1);
                    btnNextChapter.setEnabled(true);

                } else {
                    Log.e(TAG, "Failed to load chapter " + chapter);
                    ToastUtils.showCustomToast(
                        MangaReaderActivity.this,
                        "Could not load chapter pages. Opening fallback source..."
                    );
                    openExternalChapterFallback(chapter);
                    
                    // Re-enable buttons
                    btnPrevChapter.setEnabled(chapter > 1);
                    btnNextChapter.setEnabled(true);
                }
            });
        });
    }
    
    private void toggleControls() {
        if (overlayControls.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }
    
    private void showControls() {
        overlayControls.setVisibility(View.VISIBLE);
        overlayControls.animate().alpha(1f).setDuration(200).start();
    }
    
    private void hideControls() {
        overlayControls.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> overlayControls.setVisibility(View.GONE))
            .start();
    }
    
    private void updateProgressInDatabase(int chapter) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
            // Update progress (chapter number), keep status and rating unchanged
            dbHelper.updateProgress(mediaId, chapter, null, 0);
        });
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void openExternalChapterFallback(int chapter) {
        try {
            String query = Uri.encode(mangaTitle + " chapter " + chapter);
            String fallbackUrl = "https://comix.to/filter?keyword=" + query;
            String secondaryFallback = "https://mangafire.to/filter?keyword=" + query;
            Intent webIntent = new Intent(this, EmbeddedWebPlayerActivity.class);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_URL, fallbackUrl);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_FALLBACK_URL, secondaryFallback);
            webIntent.putExtra(EmbeddedWebPlayerActivity.EXTRA_TITLE, mangaTitle + " • Chapter " + chapter);
            startActivity(webIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open fallback chapter URL", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveMode();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }
}
