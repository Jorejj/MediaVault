package com.example.mediavault;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediavault.api.consumet.ResolverFactory;
import com.example.mediavault.api.providers.MediaProvider;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class MangaChapterSelectorActivity extends AppCompatActivity {
    public static final String EXTRA_MANGA_TITLE = "extra_manga_title";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_CURRENT_CHAPTER = "extra_current_chapter";

    private String mangaTitle;
    private int mediaId;
    private int currentChapter;
    private boolean isDestroyed;

    private ProgressBar progressBar;
    private TextView statusView;
    private MangaChapterTableAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manga_chapter_selector);

        mangaTitle = getIntent().getStringExtra(EXTRA_MANGA_TITLE);
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        currentChapter = Math.max(1, getIntent().getIntExtra(EXTRA_CURRENT_CHAPTER, 1));

        if (mangaTitle == null || mediaId == -1) {
            ToastUtils.showCustomToast(this, "Error: Missing manga information");
            finish();
            return;
        }

        initViews();
        loadChapterList();
    }

    private void initViews() {
        TextView titleView = findViewById(R.id.tv_manga_selector_title);
        titleView.setText(mangaTitle);

        progressBar = findViewById(R.id.pb_manga_chapters);
        statusView = findViewById(R.id.tv_manga_chapter_status);

        RecyclerView recyclerView = findViewById(R.id.rv_manga_chapter_table);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MangaChapterTableAdapter(currentChapter, this::openReaderAtChapter);
        recyclerView.setAdapter(adapter);

        Button btnContinue = findViewById(R.id.btn_open_current_chapter);
        btnContinue.setOnClickListener(v -> openReaderAtChapter(currentChapter));

        Button btnCancel = findViewById(R.id.btn_cancel_manga_selector);
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadChapterList() {
        if (!isNetworkAvailable()) {
            statusView.setText(com.example.mediavault.R.string.auto_no_internet_connection_you_can_still_continue_at);
            statusView.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(com.example.mediavault.R.string.auto_fetching_chapter_list);

        AppExecutor.getInstance().networkIO().execute(() -> {
            List<MediaProvider.ChapterInfo> chapters = ResolverFactory.fetchMangaChapters(mangaTitle);
            AppExecutor.getInstance().mainThread().execute(() -> {
                if (isDestroyed) return;
                progressBar.setVisibility(View.GONE);
                if (chapters == null || chapters.isEmpty()) {
                    statusView.setText(com.example.mediavault.R.string.auto_could_not_fetch_chapters_continue_with_manual_ch);
                } else {
                    String provider = ResolverFactory.getLastSuccessfulMangaProvider();
                    statusView.setText("Tap a chapter to start reading" +
                        (provider != null ? " (via " + provider + ")" : ""));
                    adapter.setChapters(chapters);
                }
            });
        });
    }

    private void openReaderAtChapter(int chapter) {
        List<String> providerNames = ResolverFactory.getAvailableProvidersForType(MediaProvider.MediaType.MANGA);
        if (providerNames == null || providerNames.isEmpty()) {
            ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.MANGA, null);
            launchReader(chapter);
            return;
        }

        String[] options = new String[providerNames.size() + 1];
        options[0] = "Auto (recommended)";
        for (int i = 0; i < providerNames.size(); i++) {
            options[i + 1] = providerNames.get(i);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose provider")
                .setItems(options, (dialog, which) -> {
                    String selectedProvider = which == 0 ? null : options[which];
                    ResolverFactory.setPreferredProviderForSession(MediaProvider.MediaType.MANGA, selectedProvider);
                    launchReader(chapter);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchReader(int chapter) {
        Intent intent = new Intent(this, MangaReaderActivity.class);
        intent.putExtra(MangaReaderActivity.EXTRA_MANGA_TITLE, mangaTitle);
        intent.putExtra(MangaReaderActivity.EXTRA_MEDIA_ID, mediaId);
        intent.putExtra(MangaReaderActivity.EXTRA_START_CHAPTER, Math.max(1, chapter));
        startActivity(intent);
        finish();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
    }
}
