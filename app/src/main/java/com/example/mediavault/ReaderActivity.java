package com.example.mediavault;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.example.mediavault.ui.library.MediaItem;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReaderActivity extends AppCompatActivity {
    private static final String TAG = "ReaderActivity";

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_MEDIA_ITEM = "media_item";

    private ViewPager2 viewPager;
    private MediaItem mediaItem;
    private String contentUrl;
    private TextView progressText;
    private TextView timeText;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHideHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable display cutout support for immersive reader
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        
        setContentView(R.layout.activity_reader);

        mediaItem = (MediaItem) getIntent().getSerializableExtra(EXTRA_MEDIA_ITEM);
        contentUrl = getIntent().getStringExtra(EXTRA_URL);
        int mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);

        if (mediaItem == null && mediaId != -1) {
            loadMediaItem(mediaId);
        }

        viewPager = findViewById(R.id.reader_viewpager);
        progressText = findViewById(R.id.text_reader_progress);
        timeText = findViewById(R.id.text_reader_time);

        setupUI();
        startClock();
    }

    private void loadMediaItem(int id) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
            try (android.database.Cursor cursor = dbHelper.getMediaById(id)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return;
                }

                final MediaItem item = new MediaItem(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING))
                );
                item.setSourceUrl(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE_URL)));

                runOnUiThread(() -> {
                    mediaItem = item;
                    if (contentUrl == null) contentUrl = mediaItem.getSourceUrl();
                    loadPages();
                });
            }
        });
    }

    private void setupUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setOffscreenPageLimit(1); // Keep 1 page to minimize memory pressure

        if (contentUrl != null || mediaItem != null) {
            loadPages();
        }
    }

    private void loadPages() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            List<String> pages = new ArrayList<>();
            // QA Stress Test: Simulate 60+ pages from the local source
            if (contentUrl != null && contentUrl.startsWith("/")) {
                File dir = new File(contentUrl);
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles((parent, name) -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        return lower.endsWith(".jpg") || lower.endsWith(".png");
                    });
                    if (files != null) {
                        Arrays.sort(files, (a, b) -> naturalCompare(a.getName(), b.getName()));
                        for (File f : files) {
                            pages.add(f.getAbsolutePath());
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                ReaderAdapter adapter = new ReaderAdapter(this, pages);
                viewPager.setAdapter(adapter);
                
                viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        progressText.setText(String.format(Locale.getDefault(), "Page %d / %d", position + 1, pages.size()));
                        resetUIHideTimer();
                        
                        // Increment goal if 90% through
                        if (pages.size() > 0 && (float) position / pages.size() >= 0.9f) {
                            AppExecutor.getInstance().diskIO().execute(() -> 
                                DailyGoalsManager.getInstance(ReaderActivity.this).incrementMangaProgressSync());
                        }
                    }
                });
            });
        });
    }

    private void resetUIHideTimer() {
        uiHideHandler.removeCallbacksAndMessages(null);
        progressText.setAlpha(1f);
        timeText.setAlpha(1f);
        uiHideHandler.postDelayed(() -> {
            progressText.animate().alpha(0f).setDuration(500);
            timeText.animate().alpha(0f).setDuration(500);
        }, 8000);
    }

    private void startClock() {
        timeHandler.post(new Runnable() {
            @Override
            public void run() {
                String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                timeText.setText(currentTime);
                timeHandler.postDelayed(this, 30000);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear caches on pause to keep memory overhead low
        com.bumptech.glide.Glide.get(this).clearMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacksAndMessages(null);
        uiHideHandler.removeCallbacksAndMessages(null);
    }

    private static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        int aLen = a.length();
        int bLen = b.length();

        while (i < aLen && j < bLen) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);

            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                long numA = 0;
                while (i < aLen && Character.isDigit(a.charAt(i))) {
                    numA = (numA * 10) + (a.charAt(i) - '0');
                    i++;
                }

                long numB = 0;
                while (j < bLen && Character.isDigit(b.charAt(j))) {
                    numB = (numB * 10) + (b.charAt(j) - '0');
                    j++;
                }

                int numberCompare = Long.compare(numA, numB);
                if (numberCompare != 0) {
                    return numberCompare;
                }
                continue;
            }

            int charCompare = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (charCompare != 0) {
                return charCompare;
            }

            i++;
            j++;
        }

        return Integer.compare(aLen, bLen);
    }
}
