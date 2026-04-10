package com.example.mediavault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteConstraintException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mediavault.widget.ToastUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mediavault.api.GoogleBooksApiService;
import com.example.mediavault.api.GoogleBooksResponse;
import com.example.mediavault.api.JikanApiService;
import com.example.mediavault.api.JikanResponse;
import com.example.mediavault.api.MediaMetadataProfile;
import com.example.mediavault.api.MediaSearchAdapter;
import com.example.mediavault.api.MediaSearchResult;
import com.example.mediavault.api.MovieDetailResponse;
import com.example.mediavault.api.OpenLibraryResponse;
import com.example.mediavault.api.OpenLibraryApiService;
import com.example.mediavault.api.TmdbApiService;
import com.example.mediavault.api.TmdbResponse;
import com.example.mediavault.api.TvDetailResponse;
import com.example.mediavault.api.search.model.AniListSearchResponse;
import com.example.mediavault.api.search.service.AniListSearchApiService;
import com.google.gson.Gson;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.Locale;

public class AddMediaActivity extends AppCompatActivity implements MediaSearchAdapter.OnItemClickListener {

    private static final String TAG = "AddMediaDB_Error";
    private static final String ANILIST_LIGHT_NOVEL_QUERY =
            "query ($search: String) { " +
                    "Page(page: 1, perPage: 10) { " +
                    "media(search: $search, type: MANGA, format_in: [NOVEL, ONE_SHOT]) { " +
                    "id title { romaji english native } description(asHtml: false) coverImage { large } startDate { year } chapters " +
                    "} } }";

    private View layoutSearchApi, layoutManualEntry, coordinatorLayout;
    private MaterialButtonToggleGroup toggleGroup;
    
    // Search API Components
    private TextInputEditText etApiSearch;
    private Spinner spinnerApiTarget;
    private RecyclerView rvApiResults;
    private MediaSearchAdapter searchAdapter;
    private ProgressBar pbSearchLoading;
    private TextView tvNoResults;
    private Button btnApiSearchSubmit;
    
    // Manual Entry Components
    private TextInputEditText etManualTitle, etManualImage, etManualAuthor, etManualDescription, etManualReview, etManualJournal;
    private EditText etManualProgress, etManualTotal;
    private Spinner spinnerManualType, spinnerManualStatus, spinnerTotalUnit, spinnerManualPriority, spinnerManualGenre;
    private RatingBar rbManualRating;
    private ChipGroup chipGroupManualMood;
    private SwitchMaterial switchManualFavorite;
    private Button btnManualDone;
    private LinearLayout layoutDurationSlider;
    private Slider sliderManualDuration;
    private TextView tvDurationValue;
    private TextView tvLabelProgress;
    private TextView tvLabelTotal;
    private MaterialCardView cardManualEntry;
    private TextInputLayout tilManualImage;

    private DatabaseHelper dbHelper;
    private NetworkReceiver networkReceiver;
    private Retrofit jikanRetrofit, googleBooksRetrofit, tmdbRetrofit, openLibraryRetrofit, aniListRetrofit;
    private final Gson gson = new Gson();
    private boolean isNetworkReceiverRegistered = false;
    private String selectedSourceUrl;
    private String selectedContentType;
    private String selectedTmdbId;
    private int selectedReleaseYear;
    private String selectedResultTitle;
    private MediaMetadataProfile selectedMetadataProfile;
    private boolean launchedFromTracker;
    private String trackerDetectedPackage;
    private float trackerDetectedProgress;
    private String trackerDetectedAuthor;
    private int trackerDetectedTotalCount;
    private String trackerDetectedDescription;
    private boolean trackerForceManual;
    private String trackerPrefillSourceUrl;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null && etManualImage != null) {
                        etManualImage.setText(selectedImage.toString());
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.example.mediavault.utils.ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_media);

        dbHelper = DatabaseHelper.getInstance(this);
        networkReceiver = new NetworkReceiver();

        initRetrofit();
        initViews();
        setupToggle();
        setupSearch();
        setupManualEntry();
        setupOnBackPressed();
        
        // Handle intent from external tracker
        handleTrackerIntent();
    }
    
    private void handleTrackerIntent() {
        Intent intent = getIntent();
        boolean webNovelTracker = false;
        if (intent != null) {
            launchedFromTracker = intent.getBooleanExtra("FROM_TRACKER", false);
            trackerDetectedPackage = intent.getStringExtra("TRACKER_PACKAGE");
            trackerDetectedProgress = intent.getFloatExtra("PREFILL_PROGRESS", 0f);
            trackerDetectedAuthor = intent.getStringExtra("PREFILL_AUTHOR");
            trackerDetectedTotalCount = intent.getIntExtra("PREFILL_TOTAL_COUNT", 0);
            trackerDetectedDescription = intent.getStringExtra("PREFILL_DESCRIPTION");
            trackerPrefillSourceUrl = intent.getStringExtra("PREFILL_SOURCE_URL");
            String prefillTypeHint = intent.getStringExtra("PREFILL_TYPE_HINT");
            String inferredTrackerType = !TextUtils.isEmpty(prefillTypeHint)
                    ? prefillTypeHint
                    : inferTrackerMediaType(trackerDetectedPackage);
            if (launchedFromTracker && !TextUtils.isEmpty(inferredTrackerType)) {
                applyTrackerTypeDefaults(inferredTrackerType);
            }
            if (!TextUtils.isEmpty(trackerDetectedAuthor) && TextUtils.isEmpty(etManualAuthor.getText())) {
                etManualAuthor.setText(trackerDetectedAuthor.trim());
            }
            if (trackerDetectedTotalCount > 0 && TextUtils.isEmpty(etManualTotal.getText())) {
                etManualTotal.setText(String.valueOf(trackerDetectedTotalCount));
            }
            if (trackerDetectedProgress > 0f && TextUtils.isEmpty(etManualProgress.getText())) {
                etManualProgress.setText(trimmedProgressText(trackerDetectedProgress));
            }
            webNovelTracker = launchedFromTracker && isWebNovelPackage(trackerDetectedPackage);
            trackerForceManual = intent.getBooleanExtra("PREFILL_FORCE_MANUAL", false) || webNovelTracker;
            if (webNovelTracker) {
                setSpinnerToContains(spinnerApiTarget, "Light/Web");
                setSpinnerToContains(spinnerManualType, "Book");
                setSpinnerToContains(spinnerTotalUnit, "Chapter");
                setSpinnerToContains(spinnerManualStatus, "Progress");
            }
            if (!TextUtils.isEmpty(trackerDetectedDescription) && TextUtils.isEmpty(etManualDescription.getText())) {
                etManualDescription.setText(trackerDetectedDescription.trim());
            }
        }
        if (intent != null && intent.hasExtra("PREFILL_TITLE")) {
            String prefillTitle = intent.getStringExtra("PREFILL_TITLE");
            float prefillProgress = intent.getFloatExtra("PREFILL_PROGRESS", 0f);
             
            if (prefillTitle != null && !prefillTitle.isEmpty()) {
                etApiSearch.setText(prefillTitle);
                etManualTitle.setText(prefillTitle);

                if (trackerForceManual) {
                    toggleGroup.check(R.id.btn_mode_manual);
                    selectedSourceUrl = !TextUtils.isEmpty(trackerPrefillSourceUrl)
                            ? trackerPrefillSourceUrl.trim()
                            : buildTrackerSourceUrl(prefillTitle, webNovelTracker);
                    selectedContentType = "web-reader";
                } else {
                    etApiSearch.post(this::performApiSearch);
                }
                if (prefillProgress > 0f) {
                    etManualProgress.setText(trimmedProgressText(prefillProgress));
                }
                Toast.makeText(
                        this,
                        trackerForceManual
                                ? "Detected: " + prefillTitle + " (manual add pre-filled)"
                                : "Detected: " + prefillTitle + " (Ch. " + (int) prefillProgress + ")",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void initRetrofit() {
        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        jikanRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.jikan.moe/v4/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        googleBooksRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/books/v1/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        tmdbRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        openLibraryRetrofit = new Retrofit.Builder()
                .baseUrl("https://openlibrary.org/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        aniListRetrofit = new Retrofit.Builder()
                .baseUrl("https://graphql.anilist.co/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private void initViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        layoutSearchApi = findViewById(R.id.layout_search_api);
        layoutManualEntry = findViewById(R.id.layout_manual_entry);
        toggleGroup = findViewById(R.id.toggle_group);

        // Search API
        etApiSearch = findViewById(R.id.et_api_search);
        spinnerApiTarget = findViewById(R.id.spinner_api_target);
        rvApiResults = findViewById(R.id.rv_api_results);
        pbSearchLoading = findViewById(R.id.pb_search_loading);
        tvNoResults = findViewById(R.id.tv_no_results);
        btnApiSearchSubmit = findViewById(R.id.btn_api_search_submit);
        
        // Manual Entry
        etManualTitle = findViewById(R.id.et_manual_title);
        spinnerManualType = findViewById(R.id.spinner_manual_type);
        spinnerManualStatus = findViewById(R.id.spinner_manual_status);
        etManualProgress = findViewById(R.id.et_manual_progress);
        etManualProgress.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etManualTotal = findViewById(R.id.et_manual_total);
        spinnerTotalUnit = findViewById(R.id.spinner_total_unit);
        spinnerManualGenre = findViewById(R.id.spinner_manual_genre);
        rbManualRating = findViewById(R.id.rb_manual_rating);
        etManualReview = findViewById(R.id.et_manual_review);
        etManualJournal = findViewById(R.id.et_manual_journal);
        chipGroupManualMood = findViewById(R.id.chip_group_manual_mood);
        spinnerManualPriority = findViewById(R.id.spinner_manual_priority);
        switchManualFavorite = findViewById(R.id.switch_manual_favorite);
        etManualImage = findViewById(R.id.et_manual_image);
        etManualAuthor = findViewById(R.id.et_manual_author);
        etManualDescription = findViewById(R.id.et_manual_description);
        btnManualDone = findViewById(R.id.btn_manual_done);
        layoutDurationSlider = findViewById(R.id.layout_duration_slider);
        sliderManualDuration = findViewById(R.id.slider_manual_duration);
        tvDurationValue = findViewById(R.id.tv_duration_value);
        tvLabelProgress = findViewById(R.id.label_progress);
        tvLabelTotal = findViewById(R.id.label_total);
        cardManualEntry = findViewById(R.id.card_manual_entry);
        tilManualImage = findViewById(R.id.til_manual_image);

        if (tilManualImage != null) {
            tilManualImage.setEndIconOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                imagePickerLauncher.launch(intent);
            });
        }
    }

    private void setupToggle() {
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_mode_search) {
                    layoutSearchApi.setVisibility(View.VISIBLE);
                    layoutManualEntry.setVisibility(View.GONE);
                } else if (checkedId == R.id.btn_mode_manual) {
                    layoutSearchApi.setVisibility(View.GONE);
                    layoutManualEntry.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setupSearch() {
        searchAdapter = new MediaSearchAdapter(this);
        rvApiResults.setLayoutManager(new LinearLayoutManager(this));
        rvApiResults.setAdapter(searchAdapter);

        etApiSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performApiSearch();
                return true;
            }
            return false;
        });

        btnApiSearchSubmit.setOnClickListener(v -> performApiSearch());
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!TextUtils.isEmpty(etManualTitle.getText())) {
                    showDiscardChangesDialog();
                } else {
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });
    }

    private void showDiscardChangesDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved data. Are you sure you want to go back?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Keep Editing", null)
                .show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void performApiSearch() {
        if (!isNetworkAvailable()) {
            ToastUtils.showCustomToast(this, "No internet connection. Please use Manual Entry.");
            return;
        }

        String query = Objects.requireNonNull(etApiSearch.getText()).toString().trim();
        if (TextUtils.isEmpty(query)) return;

        String target = spinnerApiTarget.getSelectedItem().toString();
        selectedSourceUrl = null;
        selectedContentType = null;
        selectedTmdbId = null;
        selectedReleaseYear = 0;
        selectedResultTitle = null;
        selectedMetadataProfile = null;
        
        pbSearchLoading.setVisibility(View.VISIBLE);
        rvApiResults.setVisibility(View.GONE);
        tvNoResults.setVisibility(View.GONE);
        btnApiSearchSubmit.setEnabled(false);
        searchAdapter.setResults(new ArrayList<>());

        if (target.contains("Anime")) {
            searchAnime(query);
        } else if (target.contains("Manga")) {
            searchManga(query);
        } else if (target.contains("Light/Web") || target.contains("Novel")) {
            searchLightNovels(query);
        } else if (target.contains("Books")) {
            searchBooks(query);
        } else if (target.contains("Movie")) {
            searchTmdb(query, "Movie");
        } else if (target.contains("Series")) {
            searchTmdb(query, "Series");
        } else {
            pbSearchLoading.setVisibility(View.GONE);
            rvApiResults.setVisibility(View.VISIBLE);
            btnApiSearchSubmit.setEnabled(true);
            tvNoResults.setVisibility(View.VISIBLE);
            tvNoResults.setText(com.example.mediavault.R.string.auto_unsupported_target);
        }
    }

    private void searchAnime(String query) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getAnime(query, 10).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                handleJikanResponse(response, "Anime", query);
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
                showProviderCoverageFallback(query, "Anime");
            }
        });
    }

    private void searchManga(String query) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getManga(query, 10).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                handleJikanResponse(response, "Manga", query);
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
                showProviderCoverageFallback(query, "Manga");
            }
        });
    }

    private void searchLightNovels(String query) {
        List<MediaSearchResult> aggregatedResults = new ArrayList<>();
        Set<String> dedupeKeys = new HashSet<>();
        final int[] pendingSources = {4};

        Runnable onSourceComplete = () -> {
            pendingSources[0]--;
            if (pendingSources[0] == 0) {
                pbSearchLoading.setVisibility(View.GONE);
                btnApiSearchSubmit.setEnabled(true);
                rvApiResults.setVisibility(View.VISIBLE);
                if (aggregatedResults.isEmpty()) {
                    showProviderCoverageFallback(query, "Book");
                    return;
                }
                List<MediaSearchResult> mergedResults = mergeApiAndProviderResults(aggregatedResults, query, "Book");
                searchAdapter.setResults(mergedResults);
                showSuccessSnackbar(buildCoverageMessage(aggregatedResults.size(), mergedResults.size() - aggregatedResults.size()));
            }
        };

        searchAniListLightNovels(query, aggregatedResults, dedupeKeys, onSourceComplete);
        searchJikanLightNovels(query, aggregatedResults, dedupeKeys, onSourceComplete);
        searchGoogleBooksLightNovels(query, aggregatedResults, dedupeKeys, onSourceComplete);
        searchOpenLibraryLightNovels(query, aggregatedResults, dedupeKeys, onSourceComplete);
    }

    private void searchAniListLightNovels(
            String query,
            List<MediaSearchResult> out,
            Set<String> dedupeKeys,
            Runnable onComplete
    ) {
        AniListSearchApiService service = aniListRetrofit.create(AniListSearchApiService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("query", ANILIST_LIGHT_NOVEL_QUERY);
        Map<String, String> variables = new HashMap<>();
        variables.put("search", query);
        body.put("variables", variables);

        service.searchAnime(body).enqueue(new Callback<AniListSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<AniListSearchResponse> call, @NonNull Response<AniListSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null
                        && response.body().data.page != null
                        && response.body().data.page.media != null) {
                    for (AniListSearchResponse.Media media : response.body().data.page.media) {
                        if (media == null || media.title == null) {
                            continue;
                        }
                        String title = firstNonBlank(media.title.english, media.title.romaji, media.title.nativeTitle);
                        if (TextUtils.isEmpty(title)) {
                            continue;
                        }
                        String sourceUrl = "https://anilist.co/manga/" + media.id;
                        MediaSearchResult result = new MediaSearchResult(
                                title,
                                "Book",
                                "Light Novel",
                                "AniList",
                                sanitizeDescription(media.description),
                                media.coverImage != null ? media.coverImage.large : null,
                                media.chapters,
                                "Chapters",
                                sourceUrl,
                                "search",
                                null,
                                media.startDate != null ? media.startDate.year : 0
                        );
                        result.setMetadataProfile(buildAniListLightNovelMetadataProfile(media, sourceUrl));
                        addUniqueSearchResult(out, dedupeKeys, result);
                    }
                }
                onComplete.run();
            }

            @Override
            public void onFailure(@NonNull Call<AniListSearchResponse> call, @NonNull Throwable t) {
                onComplete.run();
            }
        });
    }

    private void searchJikanLightNovels(
            String query,
            List<MediaSearchResult> out,
            Set<String> dedupeKeys,
            Runnable onComplete
    ) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getMangaByType(query, "lightnovel", 10).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null
                        && !response.body().getData().isEmpty()) {
                    for (JikanResponse.MediaData data : response.body().getData()) {
                        if (data == null || TextUtils.isEmpty(data.getTitle())) {
                            continue;
                        }
                        String imageUrl = null;
                        if (data.getImages() != null && data.getImages().getJpg() != null) {
                            imageUrl = data.getImages().getJpg().getImageUrl();
                        }
                        String sourceUrl = data.getMalId() != null
                                ? "https://myanimelist.net/manga/" + data.getMalId()
                                : "https://myanimelist.net/manga.php?q=" + Uri.encode(data.getTitle());
                        MediaSearchResult result = new MediaSearchResult(
                                data.getTitle(),
                                "Book",
                                data.getDisplayGenres(),
                                data.getCreator(),
                                data.getSynopsis(),
                                imageUrl,
                                data.getChapters(),
                                "Chapters",
                                sourceUrl,
                                "search",
                                null,
                                data.getYear() != null ? data.getYear() : 0
                        );
                        result.setMetadataProfile(buildJikanLightNovelMetadataProfile(data, sourceUrl));
                        addUniqueSearchResult(out, dedupeKeys, result);
                    }
                }
                onComplete.run();
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                onComplete.run();
            }
        });
    }

    private void searchGoogleBooksLightNovels(
            String query,
            List<MediaSearchResult> out,
            Set<String> dedupeKeys,
            Runnable onComplete
    ) {
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(query + " light novel", BuildConfig.GOOGLE_BOOKS_API_KEY).enqueue(new Callback<GoogleBooksResponse>() {
            @Override
            public void onResponse(@NonNull Call<GoogleBooksResponse> call, @NonNull Response<GoogleBooksResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getItems() != null
                        && !response.body().getItems().isEmpty()) {
                    for (GoogleBooksResponse.BookItem item : response.body().getItems()) {
                        GoogleBooksResponse.VolumeInfo info = item.getVolumeInfo();
                        if (info == null || TextUtils.isEmpty(info.getTitle())) {
                            continue;
                        }
                        String imageUrl = null;
                        if (info.getImageLinks() != null) {
                            imageUrl = info.getImageLinks().getThumbnail();
                            if (imageUrl != null && imageUrl.startsWith("http://")) {
                                imageUrl = imageUrl.replace("http://", "https://");
                            }
                        }
                        String authors = "";
                        if (info.getAuthors() != null) {
                            authors = String.join(", ", info.getAuthors());
                        }
                        String genres = "";
                        if (info.getCategories() != null) {
                            genres = String.join(", ", info.getCategories());
                        }
                        String sourceUrl = buildBookSourceUrl(info.getTitle());
                        MediaSearchResult result = new MediaSearchResult(
                                info.getTitle(),
                                "Book",
                                genres,
                                authors,
                                info.getDescription(),
                                imageUrl,
                                info.getPageCount(),
                                "Pages",
                                sourceUrl,
                                "reader",
                                null,
                                parseReleaseYear(info.getPublishedDate())
                        );
                        result.setMetadataProfile(buildGoogleBooksMetadataProfile(info, sourceUrl));
                        addUniqueSearchResult(out, dedupeKeys, result);
                    }
                }
                onComplete.run();
            }

            @Override
            public void onFailure(@NonNull Call<GoogleBooksResponse> call, @NonNull Throwable t) {
                onComplete.run();
            }
        });
    }

    private void searchOpenLibraryLightNovels(
            String query,
            List<MediaSearchResult> out,
            Set<String> dedupeKeys,
            Runnable onComplete
    ) {
        OpenLibraryApiService service = openLibraryRetrofit.create(OpenLibraryApiService.class);
        service.searchBooks(query, 10).enqueue(new Callback<OpenLibraryResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenLibraryResponse> call, @NonNull Response<OpenLibraryResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getDocs() != null
                        && !response.body().getDocs().isEmpty()) {
                    for (OpenLibraryResponse.Doc doc : response.body().getDocs()) {
                        if (doc == null || TextUtils.isEmpty(doc.getTitle())) {
                            continue;
                        }
                        String authors = "";
                        if (doc.getAuthorName() != null && !doc.getAuthorName().isEmpty()) {
                            authors = String.join(", ", doc.getAuthorName());
                        }
                        String genres = "";
                        if (doc.getSubject() != null && !doc.getSubject().isEmpty()) {
                            genres = String.join(", ", doc.getSubject().subList(0, Math.min(3, doc.getSubject().size())));
                        }
                        String sourceUrl = !TextUtils.isEmpty(doc.getWorkUrl())
                                ? doc.getWorkUrl()
                                : "https://openlibrary.org/search?title=" + Uri.encode(doc.getTitle());
                        MediaSearchResult result = new MediaSearchResult(
                                doc.getTitle(),
                                "Book",
                                genres,
                                authors,
                                "OpenLibrary metadata result",
                                doc.getCoverUrl(),
                                doc.getPageCount(),
                                "Pages",
                                sourceUrl,
                                "reader",
                                null,
                                doc.getFirstPublishYear() != null ? doc.getFirstPublishYear() : 0
                        );
                        result.setMetadataProfile(buildOpenLibraryMetadataProfile(doc, sourceUrl, authors));
                        addUniqueSearchResult(out, dedupeKeys, result);
                    }
                }
                onComplete.run();
            }

            @Override
            public void onFailure(@NonNull Call<OpenLibraryResponse> call, @NonNull Throwable t) {
                onComplete.run();
            }
        });
    }

    private void addUniqueSearchResult(List<MediaSearchResult> out, Set<String> dedupeKeys, MediaSearchResult result) {
        if (result == null || TextUtils.isEmpty(result.getTitle())) {
            return;
        }
        String dedupeKey = buildResultDedupKey(result.getTitle(), result.getAuthor());
        if (dedupeKeys.add(dedupeKey)) {
            out.add(result);
        }
    }

    private String buildResultDedupKey(String title, String author) {
        String normalizedTitle = MediaMetadataProfile.normalizeTitle(title);
        String normalizedAuthor = author == null ? "" : author.trim().toLowerCase(Locale.ROOT);
        return normalizedTitle + "|" + normalizedAuthor;
    }

    private String sanitizeDescription(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private void handleJikanResponse(Response<JikanResponse> response, String type, String query) {
        pbSearchLoading.setVisibility(View.GONE);
        btnApiSearchSubmit.setEnabled(true);
        rvApiResults.setVisibility(View.VISIBLE);

        if (response.isSuccessful() && response.body() != null) {
            List<MediaSearchResult> results = new ArrayList<>();
            if (response.body().getData() != null && !response.body().getData().isEmpty()) {
                for (JikanResponse.MediaData data : response.body().getData()) {
                    String imageUrl = null;
                    if (data.getImages() != null && data.getImages().getJpg() != null) {
                        imageUrl = data.getImages().getJpg().getImageUrl();
                    }

                    String sourceUrl = type.equals("Anime")
                            ? "https://animekai.to/search?keyword=" + Uri.encode(data.getTitle())
                            : "https://comix.to/filter?keyword=" + Uri.encode(data.getTitle());
                    MediaSearchResult result = new MediaSearchResult(
                            data.getTitle(),
                            type,
                            data.getDisplayGenres(), 
                            data.getCreator(), 
                            data.getSynopsis(),
                            imageUrl,
                            type.equals("Anime") ? data.getEpisodes() : data.getChapters(),
                            type.equals("Anime") ? "Episodes" : "Chapters",
                            sourceUrl,
                            "search",
                            null,
                            0
                    );
                    result.setMetadataProfile(buildJikanMetadataProfile(data, type, sourceUrl));
                    results.add(result);
                }
                List<MediaSearchResult> mergedResults = mergeApiAndProviderResults(results, query, type);
                searchAdapter.setResults(mergedResults);
                showSuccessSnackbar(buildCoverageMessage(results.size(), mergedResults.size() - results.size()));
            } else {
                showProviderCoverageFallback(query, type);
            }
        } else {
            handleSearchError("API Error: " + response.code());
            showProviderCoverageFallback(query, type);
        }
    }

    private void searchBooks(String query) {
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(query, BuildConfig.GOOGLE_BOOKS_API_KEY).enqueue(new Callback<GoogleBooksResponse>() {
            @Override
            public void onResponse(@NonNull Call<GoogleBooksResponse> call, @NonNull Response<GoogleBooksResponse> response) {
                pbSearchLoading.setVisibility(View.GONE);
                btnApiSearchSubmit.setEnabled(true);
                rvApiResults.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null) {
                    List<MediaSearchResult> results = new ArrayList<>();
                    if (response.body().getItems() != null && !response.body().getItems().isEmpty()) {
                        for (GoogleBooksResponse.BookItem item : response.body().getItems()) {
                            GoogleBooksResponse.VolumeInfo info = item.getVolumeInfo();
                            String imageUrl = null;
                            if (info.getImageLinks() != null) {
                                imageUrl = info.getImageLinks().getThumbnail();
                                if (imageUrl != null && imageUrl.startsWith("http://")) {
                                    imageUrl = imageUrl.replace("http://", "https://");
                                }
                            }

                            String authors = "";
                            if (info.getAuthors() != null) {
                                authors = String.join(", ", info.getAuthors());
                            }

                            String genres = "";
                            if (info.getCategories() != null) {
                                genres = String.join(", ", info.getCategories());
                            }

                            MediaSearchResult result = new MediaSearchResult(
                                    info.getTitle(),
                                    "Book",
                                    genres,
                                    authors, 
                                    info.getDescription(),
                                    imageUrl,
                                    info.getPageCount(),
                                    "Pages",
                                    buildBookSourceUrl(info.getTitle()),
                                    "reader",
                                    null,
                                    0
                            );
                            result.setMetadataProfile(buildGoogleBooksMetadataProfile(info, result.getSourceUrl()));
                            results.add(result);
                        }
                        List<MediaSearchResult> mergedResults = mergeApiAndProviderResults(results, query, "Book");
                        searchAdapter.setResults(mergedResults);
                        showSuccessSnackbar(buildCoverageMessage(results.size(), mergedResults.size() - results.size()));
                    } else {
                        showProviderCoverageFallback(query, "Book");
                    }
                } else {
                    handleSearchError("API Error: " + response.code());
                    showProviderCoverageFallback(query, "Book");
                }
            }

            @Override
            public void onFailure(@NonNull Call<GoogleBooksResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
                showProviderCoverageFallback(query, "Book");
            }
        });
    }

    private void searchTmdb(String query, String type) {
        if (TextUtils.isEmpty(BuildConfig.TMDB_API_KEY)) {
            pbSearchLoading.setVisibility(View.GONE);
            btnApiSearchSubmit.setEnabled(true);
            rvApiResults.setVisibility(View.VISIBLE);
            showProviderCoverageFallback(query, type.equals("Movie") ? "Movie" : "Series");
            return;
        }
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        Call<TmdbResponse> call;
        if (type.equals("Movie")) {
            call = service.searchMovies(BuildConfig.TMDB_API_KEY, query);
        } else {
            call = service.searchTv(BuildConfig.TMDB_API_KEY, query);
        }

        call.enqueue(new Callback<TmdbResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmdbResponse> call, @NonNull Response<TmdbResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MediaSearchResult> results = new ArrayList<>();
                    List<TmdbResponse.TmdbItem> items = response.body().getResults();
                    if (items != null && !items.isEmpty()) {
                        final int[] pending = {items.size()};
                        for (TmdbResponse.TmdbItem item : items) {
                            fetchTmdbDetails(service, item, type, results, () -> {
                                pending[0]--;
                                if (pending[0] == 0) {
                                    pbSearchLoading.setVisibility(View.GONE);
                                    btnApiSearchSubmit.setEnabled(true);
                                    rvApiResults.setVisibility(View.VISIBLE);
                                    String mediaType = "Movie".equals(type) ? "Movie" : "Series";
                                    List<MediaSearchResult> mergedResults = mergeApiAndProviderResults(results, query, mediaType);
                                    searchAdapter.setResults(mergedResults);
                                    showSuccessSnackbar(buildCoverageMessage(results.size(), mergedResults.size() - results.size()));
                                }
                            });
                        }
                    } else {
                        pbSearchLoading.setVisibility(View.GONE);
                        btnApiSearchSubmit.setEnabled(true);
                        rvApiResults.setVisibility(View.VISIBLE);
                        showProviderCoverageFallback(query, type.equals("Movie") ? "Movie" : "Series");
                    }
                } else {
                    handleSearchError("API Error: " + response.code());
                    showProviderCoverageFallback(query, type.equals("Movie") ? "Movie" : "Series");
                }
            }

            @Override
            public void onFailure(@NonNull Call<TmdbResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
                showProviderCoverageFallback(query, type.equals("Movie") ? "Movie" : "Series");
            }
        });
    }

    private void fetchTmdbDetails(TmdbApiService service, TmdbResponse.TmdbItem item, String type, List<MediaSearchResult> results, Runnable onComplete) {
        if (type.equals("Movie")) {
            service.getMovieDetails(item.getId(), BuildConfig.TMDB_API_KEY).enqueue(new Callback<MovieDetailResponse>() {
                @Override
                public void onResponse(Call<MovieDetailResponse> call, Response<MovieDetailResponse> response) {
                    Integer runtime = null;
                    String genres = "";
                    String director = "";
                    if (response.isSuccessful() && response.body() != null) {
                        runtime = response.body().getRuntime();
                        if (response.body().getGenres() != null) {
                            genres = response.body().getGenres().stream().map(MovieDetailResponse.Genre::getName).collect(Collectors.joining(", "));
                        }
                        director = response.body().getDirector();
                    }
                    addTmdbResult(item, type, genres, director, runtime, "Minutes", results);
                    onComplete.run();
                }

                @Override
                public void onFailure(Call<MovieDetailResponse> call, Throwable t) {
                    addTmdbResult(item, type, "", "", null, "Minutes", results);
                    onComplete.run();
                }
            });
        } else {
            service.getTvDetails(item.getId(), BuildConfig.TMDB_API_KEY).enqueue(new Callback<TvDetailResponse>() {
                @Override
                public void onResponse(Call<TvDetailResponse> call, Response<TvDetailResponse> response) {
                    Integer episodes = null;
                    String genres = "";
                    String creator = "";
                    if (response.isSuccessful() && response.body() != null) {
                        episodes = response.body().getNumberOfEpisodes();
                        if (response.body().getGenres() != null) {
                            genres = response.body().getGenres().stream().map(TvDetailResponse.Genre::getName).collect(Collectors.joining(", "));
                        }
                        creator = response.body().getDisplayCreators();
                    }
                    addTmdbResult(item, type, genres, creator, episodes, "Episodes", results);
                    onComplete.run();
                }

                @Override
                public void onFailure(Call<TvDetailResponse> call, Throwable t) {
                    addTmdbResult(item, type, "", "", null, "Episodes", results);
                    onComplete.run();
                }
            });
        }
    }

    private void addTmdbResult(TmdbResponse.TmdbItem item, String type, String genres, String creator, Integer capacity, String unit, List<MediaSearchResult> results) {
        String imageUrl = null;
        if (item.getPosterPath() != null) {
            imageUrl = "https://image.tmdb.org/t/p/w500" + item.getPosterPath();
        }
        String tmdbId = String.valueOf(item.getId());
        int releaseYear = parseReleaseYear(item.getReleaseDate());
        String sourceUrl = "Movie".equals(type)
                ? "https://vidsrc.to/embed/movie/" + tmdbId
                : "https://vidsrc.to/embed/tv/" + tmdbId + "/1/1";
        MediaSearchResult result = new MediaSearchResult(
                item.getTitle(),
                type.equals("Movie") ? "Movie" : "Series",
                genres,
                creator, 
                item.getOverview(),
                imageUrl,
                capacity,
                unit,
                sourceUrl,
                "embed",
                tmdbId,
                releaseYear
        );
        result.setMetadataProfile(buildTmdbMetadataProfile(item, type, genres, capacity, unit, sourceUrl, tmdbId, releaseYear));
        results.add(result);
    }

    private void handleSearchError(String message) {
        pbSearchLoading.setVisibility(View.GONE);
        btnApiSearchSubmit.setEnabled(true);
        rvApiResults.setVisibility(View.VISIBLE);
        
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "Network Error: " + message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        snackbar.show();
    }

    private void showSuccessSnackbar(String message) {
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
    }

    private void showProviderCoverageFallback(String query, String mediaType) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            tvNoResults.setText(com.example.mediavault.R.string.no_results_found);
            return;
        }

        List<MediaSearchResult> fallbackResults = buildProviderFallbackResults(safeQuery, mediaType);
        if (fallbackResults.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            tvNoResults.setText(com.example.mediavault.R.string.no_results_found);
            return;
        }

        pbSearchLoading.setVisibility(View.GONE);
        btnApiSearchSubmit.setEnabled(true);
        rvApiResults.setVisibility(View.VISIBLE);
        tvNoResults.setVisibility(View.GONE);
        searchAdapter.setResults(fallbackResults);
        Snackbar.make(
                coordinatorLayout,
                "API had limited coverage. Using provider fallback links.",
                Snackbar.LENGTH_LONG
        ).show();
    }

    private List<MediaSearchResult> mergeApiAndProviderResults(List<MediaSearchResult> apiResults, String query, String mediaType) {
        List<MediaSearchResult> merged = new ArrayList<>();
        if (apiResults != null) {
            merged.addAll(apiResults);
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) {
            return merged;
        }

        List<MediaSearchResult> providerResults = buildProviderFallbackResults(safeQuery, mediaType);
        if (providerResults.isEmpty()) {
            return merged;
        }

        Set<String> seenProviderKeys = new HashSet<>();
        for (MediaSearchResult result : merged) {
            String providerKey = extractProviderKey(result);
            if (!providerKey.isEmpty()) {
                seenProviderKeys.add(providerKey);
            }
        }

        for (MediaSearchResult providerResult : providerResults) {
            String providerKey = extractProviderKey(providerResult);
            if (providerKey.isEmpty() || seenProviderKeys.add(providerKey)) {
                merged.add(providerResult);
            }
        }
        return merged;
    }

    private String extractProviderKey(MediaSearchResult result) {
        if (result == null || TextUtils.isEmpty(result.getSourceUrl())) {
            return "";
        }
        String slug = MediaMetadataProfile.detectProviderSlugFromUrl(result.getSourceUrl());
        if (!TextUtils.isEmpty(slug)) {
            return slug.toLowerCase(Locale.ROOT);
        }
        String host = Uri.parse(result.getSourceUrl()).getHost();
        if (TextUtils.isEmpty(host)) {
            return "";
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("www.")) {
            normalizedHost = normalizedHost.substring(4);
        }
        return normalizedHost;
    }

    private String buildCoverageMessage(int apiCount, int providerCount) {
        if (providerCount > 0) {
            return "Found " + apiCount + " API results + " + providerCount + " provider links.";
        }
        return "Found " + apiCount + " results.";
    }

    private List<MediaSearchResult> buildProviderFallbackResults(String query, String mediaType) {
        List<MediaSearchResult> results = new ArrayList<>();
        String type = mediaType == null ? "" : mediaType.trim();

        if ("Anime".equalsIgnoreCase(type)) {
            addProviderFallbackResult(results, query, "Anime", "AnimeKai", "https://animekai.to/search?keyword=%s", "Episodes");
            addProviderFallbackResult(results, query, "Anime", "AniwatchTV", "https://aniwatchtv.to/search?keyword=%s", "Episodes");
            addProviderFallbackResult(results, query, "Anime", "AnimePahe", "https://animepahe.pw/anime?q=%s", "Episodes");
            addProviderFallbackResult(results, query, "Anime", "BiliBili", "https://www.bilibili.tv/en/search-result?q=%s", "Episodes");
            return results;
        }
        if ("Manga".equalsIgnoreCase(type)) {
            addProviderFallbackResult(results, query, "Manga", "Comix", "https://comix.to/filter?keyword=%s", "Chapters");
            addProviderFallbackResult(results, query, "Manga", "MangaFire", "https://mangafire.to/filter?keyword=%s", "Chapters");
            addProviderFallbackResult(results, query, "Manga", "WeebCentral", "https://weebcentral.com/search?q=%s", "Chapters");
            return results;
        }
        if ("Movie".equalsIgnoreCase(type)) {
            addProviderFallbackResult(results, query, "Movie", "Nepu", "https://nepu.to/search?q=%s", "Minutes");
            addProviderFallbackResult(results, query, "Movie", "Xprime", "https://xprime.su/search?q=%s", "Minutes");
            addProviderFallbackResult(results, query, "Movie", "Cineby", "https://www.cineby.sc/search?q=%s", "Minutes");
            return results;
        }
        if ("Series".equalsIgnoreCase(type) || "TV Show".equalsIgnoreCase(type)) {
            addProviderFallbackResult(results, query, "Series", "Nepu", "https://nepu.to/search?q=%s", "Episodes");
            addProviderFallbackResult(results, query, "Series", "Xprime", "https://xprime.su/search?q=%s+episode+1", "Episodes");
            addProviderFallbackResult(results, query, "Series", "Cineby", "https://www.cineby.sc/search?q=%s", "Episodes");
            return results;
        }
        if ("Book".equalsIgnoreCase(type) || "Novel".equalsIgnoreCase(type)) {
            addProviderFallbackResult(results, query, "Book", "OpenChapter", "https://openchapter.io/?s=%s", "Chapters");
            addProviderFallbackResult(results, query, "Book", "NovelFire", "https://novelfire.net/search?keyword=%s", "Chapters");
            addProviderFallbackResult(results, query, "Book", "WTR-LAB", "https://wtr-lab.com/en?search=%s", "Chapters");
            return results;
        }
        return results;
    }

    private void addProviderFallbackResult(
            List<MediaSearchResult> out,
            String query,
            String type,
            String providerLabel,
            String urlTemplate,
            String unit
    ) {
        String sourceUrl = String.format(urlTemplate, Uri.encode(query));
        String description = "Provider fallback link (" + providerLabel + "). API metadata was unavailable for this title.";
        MediaSearchResult result = new MediaSearchResult(
                query,
                type,
                "Unknown",
                providerLabel,
                description,
                null,
                null,
                unit,
                sourceUrl,
                "search",
                null,
                0
        );
        result.setMetadataProfile(buildManualMetadataProfile(
                query,
                type,
                "Unknown",
                providerLabel,
                0,
                unit,
                sourceUrl,
                null,
                0,
                "provider_fallback"
        ));
        out.add(result);
    }

    @Override
    public void onItemClick(MediaSearchResult result) {
        etManualTitle.setText(result.getTitle());
        selectedSourceUrl = result.getSourceUrl();
        selectedContentType = result.getContentType();
        selectedTmdbId = result.getTmdbId();
        selectedReleaseYear = result.getReleaseYear();
        selectedResultTitle = result.getTitle();
        selectedMetadataProfile = result.getMetadataProfile();
        
        // 1. Set Type (this triggers default capacity UI)
        setSpinnerToValue(spinnerManualType, result.getType());
        spinnerManualType.setEnabled(launchedFromTracker || result.getType() == null || result.getType().isEmpty());
        
        // 2. Refresh UI based on type defaults
        updateManualCapacityUI();

        // 3. OVERRIDE with specific API data if available
        if (result.getCapacity() != null && result.getCapacity() > 0) {
            etManualTotal.setText(String.valueOf(result.getCapacity()));
            etManualTotal.setEnabled(false); // Lock if we have certain API data
        } else {
            etManualTotal.setText("");
            etManualTotal.setEnabled(true);
        }

        if (result.getUnit() != null && !result.getUnit().isEmpty() && !result.getUnit().equalsIgnoreCase("Unknown")) {
            setSpinnerToValue(spinnerTotalUnit, result.getUnit());
            spinnerTotalUnit.setEnabled(false);
        } else {
            spinnerTotalUnit.setEnabled(true);
        }

        etManualImage.setText(result.getImageUrl());
        if (tilManualImage != null) {
            tilManualImage.setVisibility(View.VISIBLE);
        }

        etManualDescription.setText(result.getDescription());
        updateGenreSpinner();
        String selectedGenre = result.getGenre();
        if (!TextUtils.isEmpty(selectedGenre)) {
            String primaryGenre = selectedGenre.contains(",")
                    ? selectedGenre.split(",")[0].trim()
                    : selectedGenre.trim();
            setSpinnerToValue(spinnerManualGenre, primaryGenre);
        }
        etManualAuthor.setText(result.getAuthor());
        toggleGroup.check(R.id.btn_mode_manual);

        String apiName = spinnerApiTarget.getSelectedItem().toString();
        ToastUtils.showCustomToast(this, "Auto-filled data from " + apiName + ". Please review.");
    }
    private void setSpinnerToValue(Spinner spinner, String value) {
        if (value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setSpinnerToContains(Spinner spinner, String token) {
        if (spinner == null || token == null) return;
        String needle = token.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = spinner.getItemAtPosition(i).toString();
            if (item != null && item.toLowerCase(Locale.ROOT).contains(needle)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String trimmedProgressText(float progress) {
        if (progress <= 0f) {
            return "";
        }
        if (Math.abs(progress - Math.round(progress)) < 0.0001f) {
            return String.valueOf(Math.round(progress));
        }
        return String.valueOf(progress);
    }

    private boolean isWebNovelPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("webnovel")
                || normalized.contains("qidian")
                || normalized.contains("novel");
    }

    private boolean isMangaTrackerPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("kotatsu")
                || normalized.contains("mihon")
                || normalized.contains("tachiyomi")
                || normalized.contains("mangaplus")
                || normalized.contains("webtoon")
                || normalized.contains("manga")
                || normalized.contains("manhwa")
                || normalized.contains("manhua");
    }

    private boolean isAnimeTrackerPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("crunchyroll")
                || normalized.contains("aniwatch")
                || normalized.contains("anime")
                || normalized.contains("bilibili");
    }

    private String inferTrackerMediaType(String packageName) {
        if (isWebNovelPackage(packageName)) {
            return "Book";
        }
        if (isMangaTrackerPackage(packageName)) {
            return "Manga";
        }
        if (isAnimeTrackerPackage(packageName)) {
            return "Anime";
        }
        return "";
    }

    private void applyTrackerTypeDefaults(String inferredType) {
        if ("Manga".equalsIgnoreCase(inferredType)) {
            setSpinnerToContains(spinnerApiTarget, "Manga");
            setSpinnerToContains(spinnerManualType, "Manga");
            setSpinnerToContains(spinnerTotalUnit, "Chapter");
            setSpinnerToContains(spinnerManualStatus, "Progress");
            return;
        }
        if ("Anime".equalsIgnoreCase(inferredType)) {
            setSpinnerToContains(spinnerApiTarget, "Anime");
            setSpinnerToContains(spinnerManualType, "Anime");
            setSpinnerToContains(spinnerTotalUnit, "Episode");
            setSpinnerToContains(spinnerManualStatus, "Progress");
            return;
        }
        if ("Book".equalsIgnoreCase(inferredType)) {
            setSpinnerToContains(spinnerApiTarget, "Light/Web");
            setSpinnerToContains(spinnerManualType, "Book");
            setSpinnerToContains(spinnerTotalUnit, "Chapter");
            setSpinnerToContains(spinnerManualStatus, "Progress");
        }
    }

    private String buildTrackerSourceUrl(String title, boolean webNovelTracker) {
        if (webNovelTracker) {
            return "https://www.webnovel.com/search?keywords=" + Uri.encode(title == null ? "" : title.trim());
        }
        return buildBookSourceUrl(title);
    }

    private void setupManualEntry() {
        spinnerManualType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateManualCapacityUI();
                updateGenreSpinner();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateManualCapacityUI();
                updateGenreSpinner();
            }
        });

        sliderManualDuration.addOnChangeListener((slider, value, fromUser) -> {
            int minutes = (int) value;
            tvDurationValue.setText(minutes + " min");
            etManualTotal.setText(String.valueOf(minutes));
        });

        updateManualCapacityUI();
        btnManualDone.setOnClickListener(v -> saveToDatabase());
    }

    private void updateGenreSpinner() {
        String mediaType = spinnerManualType.getSelectedItem() != null ? spinnerManualType.getSelectedItem().toString() : "Book";
        List<String> genres = GenreManager.getGenreListForMediaType(mediaType);
        
        // Create adapter for genre spinner
        android.widget.ArrayAdapter<String> genreAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                genres
        );
        genreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerManualGenre.setAdapter(genreAdapter);
    }

    private void updateManualCapacityUI() {
        String type = spinnerManualType.getSelectedItem() != null ? spinnerManualType.getSelectedItem().toString() : "";
        if ("Book".equalsIgnoreCase(type)) {
            layoutDurationSlider.setVisibility(View.GONE);
            setSpinnerToValue(spinnerTotalUnit, "Pages");
            tvLabelProgress.setText(com.example.mediavault.R.string.pages_read);
            tvLabelTotal.setText(com.example.mediavault.R.string.auto_total_pages);
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.bookly_blue));
            }
            etManualTotal.setHint("Total Pages");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if ("Movie".equalsIgnoreCase(type)) {
            layoutDurationSlider.setVisibility(View.VISIBLE);
            setSpinnerToValue(spinnerTotalUnit, "Minutes");
            tvLabelProgress.setText(com.example.mediavault.R.string.auto_watched);
            tvLabelTotal.setText(com.example.mediavault.R.string.auto_duration);
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.netflix_red));
            }
            int minutes = (int) sliderManualDuration.getValue();
            tvDurationValue.setText(minutes + " min");
            etManualTotal.setText(String.valueOf(minutes));
            etManualTotal.setHint("Duration (minutes)");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if ("Anime".equalsIgnoreCase(type) || "Series".equalsIgnoreCase(type)) {
            layoutDurationSlider.setVisibility(View.GONE);
            setSpinnerToValue(spinnerTotalUnit, "Episodes");
            tvLabelProgress.setText(com.example.mediavault.R.string.progress);
            tvLabelTotal.setText(com.example.mediavault.R.string.auto_episodes);
            if (cardManualEntry != null) {
                int accent = "Series".equalsIgnoreCase(type)
                        ? ContextCompat.getColor(this, R.color.netflix_red)
                        : ContextCompat.getColor(this, R.color.crunchy_orange);
                cardManualEntry.setStrokeColor(accent);
            }
            etManualTotal.setHint("Total Episodes");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if ("Manga".equalsIgnoreCase(type)) {
            layoutDurationSlider.setVisibility(View.GONE);
            setSpinnerToValue(spinnerTotalUnit, "Chapters");
            tvLabelProgress.setText(com.example.mediavault.R.string.progress);
            tvLabelTotal.setText(com.example.mediavault.R.string.auto_chapters);
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.crunchy_orange));
            }
            etManualTotal.setHint("Total Chapters");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else {
            layoutDurationSlider.setVisibility(View.GONE);
            tvLabelProgress.setText(com.example.mediavault.R.string.progress);
            tvLabelTotal.setText(com.example.mediavault.R.string.total);
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.glass_border));
            }
            etManualTotal.setHint("Total");
        }
    }

    private void saveToDatabase() {
        String title = Objects.requireNonNull(etManualTitle.getText()).toString().trim();
        String type = spinnerManualType.getSelectedItem().toString();
        String status = spinnerManualStatus.getSelectedItem().toString();
        String genre = spinnerManualGenre.getSelectedItem() != null ? spinnerManualGenre.getSelectedItem().toString() : "";
        String creator = Objects.requireNonNull(etManualAuthor.getText()).toString().trim();
        String progressStr = etManualProgress.getText().toString().trim();
        String capacityStr = etManualTotal.getText().toString().trim();
        String unit = spinnerTotalUnit.getSelectedItem().toString();
        String imageUrl = Objects.requireNonNull(etManualImage.getText()).toString().trim();
        String descriptionInput = Objects.requireNonNull(etManualDescription.getText()).toString().trim();
        if (TextUtils.isEmpty(descriptionInput) && !TextUtils.isEmpty(trackerDetectedDescription)) {
            descriptionInput = trackerDetectedDescription.trim();
        }
        String review = Objects.requireNonNull(etManualReview.getText()).toString().trim();
        String journal = Objects.requireNonNull(etManualJournal.getText()).toString().trim();
        String mood = getSelectedManualMood();
        String priority = spinnerManualPriority.getSelectedItem() != null
                ? spinnerManualPriority.getSelectedItem().toString()
                : "Medium";
        boolean isFavorite = switchManualFavorite.isChecked();
        float rating = rbManualRating.getRating();

        etManualTitle.setError(null);
        etManualTotal.setError(null);
        boolean hasRequiredFieldError = false;
        if (TextUtils.isEmpty(title)) {
            etManualTitle.setError("Required field");
            hasRequiredFieldError = true;
        }
        if (TextUtils.isEmpty(capacityStr)) {
            etManualTotal.setError("Required field");
            hasRequiredFieldError = true;
        }
        if (hasRequiredFieldError) {
            ToastUtils.showCustomToast(this, "Please enter required fields before adding.");
            return;
        }

        float progress = 0;
        int total;
        try {
            if (!TextUtils.isEmpty(progressStr)) {
                progress = Float.parseFloat(progressStr);
            }
            total = Integer.parseInt(capacityStr);
        } catch (NumberFormatException e) {
            ToastUtils.showCustomToast(this, "Invalid numeric input");
            return;
        }

        if (total <= 0) {
            etManualTotal.setError("Total must be greater than 0");
            return;
        }
        if (progress > (float) total) {
            Snackbar.make(coordinatorLayout, "Progress cannot exceed Total Capacity", Snackbar.LENGTH_LONG).show();
            return;
        }

        if (progress >= (float) total) {
            status = "Completed";
        }

        final float finalProgress = progress;
        final String finalStatus = status;
        final float finalRating = rating;
        final String finalReview = review;
        final String finalJournal = journal;
        final String finalMood = mood;
        final String finalPriority = priority;
        final boolean finalIsFavorite = isFavorite;
        final String finalDescription = descriptionInput;
        final String selectedUrlAtSave = selectedSourceUrl;
        final String selectedContentTypeAtSave = selectedContentType;
        final String selectedTmdbAtSave = selectedTmdbId;
        final int selectedYearAtSave = selectedReleaseYear;
        final String selectedTitleAtSave = selectedResultTitle;
        final MediaMetadataProfile selectedMetadataAtSave = selectedMetadataProfile;

        btnManualDone.setEnabled(false);

        AppExecutor.getInstance().diskIO().execute(() -> {
            try {
                String finalImageUrl = ImageUtils.downloadAndSaveImage(getApplicationContext(), imageUrl);
                String sourceUrl = (selectedTitleAtSave != null && selectedTitleAtSave.equalsIgnoreCase(title))
                        ? selectedUrlAtSave
                        : null;
                String contentType = (selectedTitleAtSave != null && selectedTitleAtSave.equalsIgnoreCase(title))
                        ? selectedContentTypeAtSave
                        : null;
                String tmdbForSave = (selectedTitleAtSave != null && selectedTitleAtSave.equalsIgnoreCase(title))
                        ? selectedTmdbAtSave
                        : null;
                int yearForSave = (selectedTitleAtSave != null && selectedTitleAtSave.equalsIgnoreCase(title))
                        ? selectedYearAtSave
                        : 0;
                if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                    sourceUrl = buildFallbackSourceUrl(title, type, tmdbForSave, yearForSave);
                }
                if (contentType == null || contentType.trim().isEmpty()) {
                    contentType = "search";
                }

                long result = dbHelper.addMedia(title, type, genre, creator, total, unit, null, finalImageUrl, finalDescription, sourceUrl, contentType);

                if (result != -1) {
                    dbHelper.updateProgress((int) result, finalProgress, finalStatus, finalRating);
                    if ("Series".equalsIgnoreCase(type) || "TV Show".equalsIgnoreCase(type)) {
                        dbHelper.updateSeriesProgress((int) result, 1, Math.max(1, (int) finalProgress), finalStatus, finalRating);
                    }
                    dbHelper.updateMediaMetadata((int) result, finalReview, finalJournal, finalMood, finalPriority, finalIsFavorite);
                    MediaMetadataProfile metadataProfile = null;
                    if (selectedMetadataAtSave != null
                            && selectedTitleAtSave != null
                            && selectedTitleAtSave.equalsIgnoreCase(title)) {
                        metadataProfile = selectedMetadataAtSave.stampNow();
                    } else {
                        metadataProfile = buildManualMetadataProfile(
                                title,
                                type,
                                genre,
                                creator,
                                total,
                                unit,
                                sourceUrl,
                                selectedTmdbAtSave,
                                yearForSave,
                                launchedFromTracker ? "accessibility" : "manual_input"
                        );
                    }
                    if (metadataProfile != null) {
                        if (launchedFromTracker) {
                            metadataProfile.addTag("tracker:accessibility");
                            if (trackerDetectedPackage != null && !trackerDetectedPackage.trim().isEmpty()) {
                                metadataProfile.addTag("package:" + trackerDetectedPackage.trim().toLowerCase(Locale.ROOT));
                                if ((metadataProfile.getProviderId() == null || metadataProfile.getProviderId().trim().isEmpty())
                                        && isWebNovelPackage(trackerDetectedPackage)) {
                                    metadataProfile.withProviderId(trackerDetectedPackage.trim().toLowerCase(Locale.ROOT));
                                }
                            }
                            if (!TextUtils.isEmpty(trackerDetectedAuthor)) {
                                metadataProfile.addTag("author:" + trackerDetectedAuthor.trim());
                            }
                            if (trackerDetectedTotalCount > 0
                                    && (metadataProfile.getTotalCount() == null || metadataProfile.getTotalCount() <= 0)) {
                                metadataProfile.withTotalCount(trackerDetectedTotalCount);
                                if (metadataProfile.getUnit() == null || metadataProfile.getUnit().trim().isEmpty()) {
                                    metadataProfile.withUnit("Chapters");
                                }
                            }
                            if (isWebNovelPackage(trackerDetectedPackage)) {
                                if (metadataProfile.getProviderSlug() == null || metadataProfile.getProviderSlug().trim().isEmpty()) {
                                    metadataProfile.withProviderSlug("webnovel");
                                }
                                metadataProfile.addTag("source:webnovel");
                            }
                            if (metadataProfile.getMetadataSource() == null || metadataProfile.getMetadataSource().trim().isEmpty()) {
                                metadataProfile.withMetadataSource("accessibility");
                            }
                            metadataProfile.withMetadataConfidence(
                                    metadataProfile.getMetadataConfidence() != null
                                            ? Math.max(0.35f, metadataProfile.getMetadataConfidence())
                                            : 0.35f
                            );
                        }
                        dbHelper.mergeAndUpsertMetadata((int) result, metadataProfile, metadataProfile.getMetadataSource());
                    }

                    AppExecutor.getInstance().mainThread().execute(() -> {
                        // Successfully added - redirect immediately
                        Toast.makeText(AddMediaActivity.this, "✓ Successfully added to MediaVault!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } else {
                    AppExecutor.getInstance().mainThread().execute(() -> {
                        btnManualDone.setEnabled(true);
                        Log.e(TAG, "Insertion failed (likely duplicate) for title: " + title);
                        showDuplicateEntryDialog();
                    });
                }
            } catch (SQLiteConstraintException e) {
                AppExecutor.getInstance().mainThread().execute(() -> {
                    btnManualDone.setEnabled(true);
                    Log.e(TAG, "Constraint violation: " + e.getMessage());
                    showDuplicateEntryDialog();
                });
            } catch (Exception e) {
                AppExecutor.getInstance().mainThread().execute(() -> {
                    btnManualDone.setEnabled(true);
                    Log.e(TAG, "Unexpected DB error: " + e.getMessage());
                    ToastUtils.showCustomToast(AddMediaActivity.this, "A database error occurred.");
                });
            }
        });
    }

    private void showDuplicateEntryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Duplicate Entry")
                .setMessage("This title already exists in your library. Please use a different title or update the existing one.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String getSelectedManualMood() {
        int checkedId = chipGroupManualMood.getCheckedChipId();
        if (checkedId == R.id.chip_manual_mood_excited) return "Excited";
        if (checkedId == R.id.chip_manual_mood_happy) return "Happy";
        if (checkedId == R.id.chip_manual_mood_neutral) return "Neutral";
        if (checkedId == R.id.chip_manual_mood_sad) return "Sad";
        if (checkedId == R.id.chip_manual_mood_mindblown) return "Mind-blown";
        return "";
    }

    private MediaMetadataProfile buildAniListLightNovelMetadataProfile(AniListSearchResponse.Media data, String canonicalUrl) {
        if (data == null || data.title == null) return null;
        String canonicalTitle = firstNonBlank(data.title.english, data.title.romaji, data.title.nativeTitle);
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(canonicalTitle)
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(canonicalTitle))
                .addAltTitle(data.title.romaji)
                .addAltTitle(data.title.english)
                .addAltTitle(data.title.nativeTitle)
                .withProviderId("anilist")
                .withProviderSlug("anilist")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("anilist_lightnovel")
                .withMediaType("Book")
                .withSubType("Light Novel")
                .withReleaseYear(MediaMetadataProfile.safeYear(data.startDate != null ? data.startDate.year : 0))
                .withUnit("Chapters")
                .withMetadataConfidence(0.86f)
                .withMetadataPriority(88)
                .stampNow();
        if (data.id > 0) {
            profile.addExternalId("anilistId", String.valueOf(data.id));
        }
        profile.addTag("source:anilist");
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildJikanLightNovelMetadataProfile(JikanResponse.MediaData data, String canonicalUrl) {
        if (data == null) return null;
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(data.getTitle())
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(data.getTitle()))
                .addAltTitle(data.getTitleEnglish())
                .addAltTitle(data.getTitleJapanese())
                .withProviderId("jikan")
                .withProviderSlug("jikan")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("jikan_lightnovel")
                .withMediaType("Book")
                .withSubType("Light Novel")
                .withStatus(data.getStatus())
                .withReleaseYear(data.getYear())
                .withTotalCount(data.getChapters())
                .withUnit("Chapters")
                .addGenres(MediaMetadataProfile.splitCsv(data.getDisplayGenres()))
                .withRating(data.getScore())
                .withPopularity(data.getPopularity() == null ? null : data.getPopularity().floatValue())
                .withMetadataConfidence(0.8f)
                .withMetadataPriority(78)
                .stampNow();
        if (data.getMalId() != null) {
            profile.addExternalId("malId", String.valueOf(data.getMalId()));
        }
        profile.addTag("source:jikan");
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildOpenLibraryMetadataProfile(OpenLibraryResponse.Doc doc, String canonicalUrl, String author) {
        if (doc == null) return null;
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(doc.getTitle())
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(doc.getTitle()))
                .withProviderId("openlibrary")
                .withProviderSlug("openlibrary")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("openlibrary")
                .withMediaType("Book")
                .withReleaseYear(MediaMetadataProfile.safeYear(doc.getFirstPublishYear() != null ? doc.getFirstPublishYear() : 0))
                .withUnit("Pages")
                .withMetadataConfidence(0.72f)
                .withMetadataPriority(70)
                .stampNow();
        if (!TextUtils.isEmpty(author)) {
            profile.addTag(author);
        }
        if (doc.getSubject() != null) {
            profile.addGenres(doc.getSubject());
        }
        if (!TextUtils.isEmpty(doc.getKey())) {
            profile.addExternalId("openlibraryKey", doc.getKey());
        }
        profile.addTag("source:openlibrary");
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildJikanMetadataProfile(JikanResponse.MediaData data, String type, String canonicalUrl) {
        if (data == null) return null;
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(data.getTitle())
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(data.getTitle()))
                .addAltTitle(data.getTitleEnglish())
                .addAltTitle(data.getTitleJapanese())
                .withProviderId("jikan")
                .withProviderSlug("jikan")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("jikan")
                .withMediaType(type)
                .withStatus(data.getStatus())
                .withReleaseYear(data.getYear())
                .withTotalCount("Anime".equalsIgnoreCase(type) ? data.getEpisodes() : data.getChapters())
                .withUnit("Anime".equalsIgnoreCase(type) ? "Episodes" : "Chapters")
                .withProviderFeaturesJson("Anime".equalsIgnoreCase(type)
                        ? "{\"subDubAvailability\":\"unknown\",\"episodeListAvailable\":true}"
                        : "{\"scanlatorGroup\":\"unknown\",\"chapterListAvailable\":true}")
                .addGenres(MediaMetadataProfile.splitCsv(data.getDisplayGenres()))
                .withRating(data.getScore())
                .withPopularity(data.getPopularity() == null ? null : data.getPopularity().floatValue())
                .withMetadataConfidence(0.82f)
                .withMetadataPriority(80)
                .stampNow();
        if (data.getMalId() != null) {
            profile.addExternalId("malId", String.valueOf(data.getMalId()));
        }
        profile.addTag("source:jikan");
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildGoogleBooksMetadataProfile(GoogleBooksResponse.VolumeInfo info, String canonicalUrl) {
        if (info == null) return null;
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(info.getTitle())
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(info.getTitle()))
                .withProviderId("google-books")
                .withProviderSlug("google-books")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("google_books")
                .withMediaType("Book")
                .withLanguage(info.getLanguage())
                .withReleaseYear(MediaMetadataProfile.safeYear(parseReleaseYear(info.getPublishedDate())))
                .withTotalCount(info.getPageCount())
                .withUnit("Pages")
                .withProviderFeaturesJson("{\"translationStatus\":\"unknown\",\"updateFrequency\":\"unknown\"}")
                .addGenres(info.getCategories())
                .withMetadataConfidence(0.9f)
                .withMetadataPriority(90)
                .stampNow();
        if (info.getAuthors() != null) {
            profile.addTags(info.getAuthors());
        }
        if (info.getIndustryIdentifiers() != null) {
            for (GoogleBooksResponse.IndustryIdentifier identifier : info.getIndustryIdentifiers()) {
                if (identifier != null && identifier.getType() != null && identifier.getIdentifier() != null) {
                    profile.addExternalId(identifier.getType(), identifier.getIdentifier());
                }
            }
        }
        if (info.getCanonicalVolumeLink() != null) {
            profile.withCanonicalUrl(info.getCanonicalVolumeLink());
        } else if (info.getInfoLink() != null) {
            profile.withCanonicalUrl(info.getInfoLink());
        }
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildTmdbMetadataProfile(
            TmdbResponse.TmdbItem item,
            String type,
            String genres,
            Integer totalCount,
            String unit,
            String canonicalUrl,
            String tmdbId,
            int releaseYear
    ) {
        if (item == null) return null;
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(item.getTitle())
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(item.getTitle()))
                .withProviderId("tmdb")
                .withProviderSlug("tmdb")
                .withCanonicalUrl(canonicalUrl)
                .withMetadataSource("tmdb")
                .withMediaType(type.equals("Movie") ? "Movie" : "Series")
                .withLanguage(item.getOriginalLanguage())
                .withReleaseYear(MediaMetadataProfile.safeYear(releaseYear))
                .withTotalCount(totalCount)
                .withUnit(unit)
                .withProviderFeaturesJson(type.equals("Movie")
                        ? "{\"runtime\":\"unknown\",\"contentRating\":\"unknown\"}"
                        : "{\"seasonStructure\":\"s1e1_default\",\"contentRating\":\"unknown\"}")
                .addGenres(MediaMetadataProfile.splitCsv(genres))
                .withRating(item.getVoteAverage())
                .withPopularity(item.getPopularity())
                .withMetadataConfidence(0.95f)
                .withMetadataPriority(100)
                .stampNow();
        if (tmdbId != null && !tmdbId.trim().isEmpty()) {
            profile.addExternalId("tmdbId", tmdbId);
        }
        profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(canonicalUrl));
        return profile;
    }

    private MediaMetadataProfile buildManualMetadataProfile(
            String title,
            String type,
            String genre,
            String creator,
            int totalCount,
            String unit,
            String sourceUrl,
            String tmdbId,
            int releaseYear,
            String metadataSource
    ) {
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(title)
                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(title))
                .withProviderSlug(MediaMetadataProfile.detectProviderSlugFromUrl(sourceUrl))
                .withCanonicalUrl(sourceUrl)
                .withMetadataSource(metadataSource)
                .withMediaType(type)
                .withReleaseYear(MediaMetadataProfile.safeYear(releaseYear))
                .withTotalCount(totalCount > 0 ? totalCount : null)
                .withUnit(unit)
                .addGenres(MediaMetadataProfile.splitCsv(genre))
                .addTag(creator)
                .withMetadataConfidence(0.6f)
                .withMetadataPriority(40)
                .stampNow();
        if (tmdbId != null && !tmdbId.trim().isEmpty()) {
            profile.addExternalId("tmdbId", tmdbId);
        }
        if (sourceUrl != null) {
            profile.addTag("provider:" + MediaMetadataProfile.detectProviderSlugFromUrl(sourceUrl));
        }
        return profile;
    }

    private int parseReleaseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String buildFallbackSourceUrl(String title, String type, String tmdbId, int year) {
        String safeTitle = Uri.encode(title == null ? "" : title.trim());
        if ("Movie".equalsIgnoreCase(type)) {
            if (tmdbId != null && !tmdbId.trim().isEmpty()) {
                return "https://vidsrc.to/embed/movie/" + tmdbId.trim();
            }
            return "https://nepu.to/search?q=" + safeTitle + (year > 0 ? "+" + year : "");
        }
        if ("Series".equalsIgnoreCase(type) || "TV Show".equalsIgnoreCase(type)) {
            if (tmdbId != null && !tmdbId.trim().isEmpty()) {
                return "https://vidsrc.to/embed/tv/" + tmdbId.trim() + "/1/1";
            }
            return "https://xprime.su/search?q=" + safeTitle + "+episode+1";
        }
        if ("Anime".equalsIgnoreCase(type)) {
            return "https://animekai.to/search?keyword=" + safeTitle;
        }
        if ("Manga".equalsIgnoreCase(type)) {
            return "https://comix.to/filter?keyword=" + safeTitle;
        }
        if ("Book".equalsIgnoreCase(type)) {
            return buildBookSourceUrl(title);
        }
        return "https://www.google.com/search?q=" + safeTitle;
    }

    private String buildBookSourceUrl(String title) {
        String normalized = title == null ? "" : title.trim();
        String safeTitle = Uri.encode(normalized);
        int sourceIndex = (normalized.hashCode() & Integer.MAX_VALUE) % 3;
        if (sourceIndex == 0) {
            return "https://openchapter.io/?s=" + safeTitle;
        }
        if (sourceIndex == 1) {
            return "https://novelfire.net/search?keyword=" + safeTitle;
        }
        return "https://wtr-lab.com/en?search=" + safeTitle;
    }

    @Override
    protected void onResume() {
        super.onResume();
       if (!isNetworkReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(networkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(networkReceiver, filter);
            }
            isNetworkReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isNetworkReceiverRegistered) {
            unregisterReceiver(networkReceiver);
            isNetworkReceiverRegistered = false;
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            etApiSearch.setEnabled(isConnected);
            btnApiSearchSubmit.setEnabled(isConnected);
        }
    }
}
