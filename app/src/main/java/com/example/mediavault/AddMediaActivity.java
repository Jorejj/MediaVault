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
import com.example.mediavault.api.MediaSearchAdapter;
import com.example.mediavault.api.MediaSearchResult;
import com.example.mediavault.api.MovieDetailResponse;
import com.example.mediavault.api.TmdbApiService;
import com.example.mediavault.api.TmdbResponse;
import com.example.mediavault.api.TvDetailResponse;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AddMediaActivity extends AppCompatActivity implements MediaSearchAdapter.OnItemClickListener {

    private static final String TAG = "AddMediaDB_Error";
    private static final String TMDB_API_KEY = "b839069f4d8893e2d87c422727980edc";

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
    private Retrofit jikanRetrofit, googleBooksRetrofit, tmdbRetrofit;
    private boolean isNetworkReceiverRegistered = false;

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

        dbHelper = new DatabaseHelper(this);
        networkReceiver = new NetworkReceiver();

        initRetrofit();
        initViews();
        setupToggle();
        setupSearch();
        setupManualEntry();
        setupOnBackPressed();
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
        
        pbSearchLoading.setVisibility(View.VISIBLE);
        rvApiResults.setVisibility(View.GONE);
        tvNoResults.setVisibility(View.GONE);
        btnApiSearchSubmit.setEnabled(false);
        searchAdapter.setResults(new ArrayList<>());

        if (target.contains("Anime")) {
            searchAnime(query);
        } else if (target.contains("Manga")) {
            searchManga(query);
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
            tvNoResults.setText("Unsupported target");
        }
    }

    private void searchAnime(String query) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getAnime(query, 10).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                handleJikanResponse(response, "Anime");
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
            }
        });
    }

    private void searchManga(String query) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getManga(query, 10).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                handleJikanResponse(response, "Manga");
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
            }
        });
    }

    private void handleJikanResponse(Response<JikanResponse> response, String type) {
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

                    results.add(new MediaSearchResult(
                            data.getTitle(),
                            type,
                            data.getDisplayGenres(), 
                            data.getCreator(), 
                            data.getSynopsis(),
                            imageUrl,
                            type.equals("Anime") ? data.getEpisodes() : data.getChapters(),
                            type.equals("Anime") ? "Episodes" : "Chapters"
                    ));
                }
                searchAdapter.setResults(results);
                showSuccessSnackbar("Found " + results.size() + " results.");
            } else {
                tvNoResults.setVisibility(View.VISIBLE);
            }
        } else {
            handleSearchError("API Error: " + response.code());
        }
    }

    private void searchBooks(String query) {
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(query).enqueue(new Callback<GoogleBooksResponse>() {
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

                            results.add(new MediaSearchResult(
                                    info.getTitle(),
                                    "Book",
                                    genres,
                                    authors, 
                                    info.getDescription(),
                                    imageUrl,
                                    info.getPageCount(),
                                    "Pages"
                            ));
                        }
                        searchAdapter.setResults(results);
                        showSuccessSnackbar("Found " + results.size() + " results.");
                    } else {
                        tvNoResults.setVisibility(View.VISIBLE);
                    }
                } else {
                    handleSearchError("API Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<GoogleBooksResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
            }
        });
    }

    private void searchTmdb(String query, String type) {
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        Call<TmdbResponse> call;
        if (type.equals("Movie")) {
            call = service.searchMovies(TMDB_API_KEY, query);
        } else {
            call = service.searchTv(TMDB_API_KEY, query);
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
                                    searchAdapter.setResults(results);
                                    showSuccessSnackbar("Found " + results.size() + " results.");
                                }
                            });
                        }
                    } else {
                        pbSearchLoading.setVisibility(View.GONE);
                        btnApiSearchSubmit.setEnabled(true);
                        rvApiResults.setVisibility(View.VISIBLE);
                        tvNoResults.setVisibility(View.VISIBLE);
                    }
                } else {
                    handleSearchError("API Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<TmdbResponse> call, @NonNull Throwable t) {
                handleSearchError("Network timeout or error");
            }
        });
    }

    private void fetchTmdbDetails(TmdbApiService service, TmdbResponse.TmdbItem item, String type, List<MediaSearchResult> results, Runnable onComplete) {
        if (type.equals("Movie")) {
            service.getMovieDetails(item.getId(), TMDB_API_KEY).enqueue(new Callback<MovieDetailResponse>() {
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
            service.getTvDetails(item.getId(), TMDB_API_KEY).enqueue(new Callback<TvDetailResponse>() {
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
        results.add(new MediaSearchResult(
                item.getTitle(),
                type.equals("Movie") ? "Movie" : "Series",
                genres,
                creator, 
                item.getOverview(),
                imageUrl,
                capacity,
                unit
        ));
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

    @Override
    public void onItemClick(MediaSearchResult result) {
        etManualTitle.setText(result.getTitle());
        setSpinnerToValue(spinnerManualType, result.getType());
        spinnerManualType.setEnabled(result.getType() == null || result.getType().isEmpty());
        updateManualCapacityUI();

        etManualTotal.setText(result.getCapacity() != null ? String.valueOf(result.getCapacity()) : "");
        etManualTotal.setEnabled(result.getCapacity() == null);

        setSpinnerToValue(spinnerTotalUnit, result.getUnit());
        spinnerTotalUnit.setEnabled(result.getUnit() == null || result.getUnit().isEmpty() || result.getUnit().equals("Unknown"));

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
            tvLabelProgress.setText("Pages Read");
            tvLabelTotal.setText("Total Pages");
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.bookly_blue));
            }
            etManualTotal.setHint("Total Pages");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if ("Movie".equalsIgnoreCase(type)) {
            layoutDurationSlider.setVisibility(View.VISIBLE);
            setSpinnerToValue(spinnerTotalUnit, "Minutes");
            tvLabelProgress.setText("Watched");
            tvLabelTotal.setText("Duration");
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
            tvLabelProgress.setText("Progress");
            tvLabelTotal.setText("Episodes");
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
            tvLabelProgress.setText("Progress");
            tvLabelTotal.setText("Chapters");
            if (cardManualEntry != null) {
                cardManualEntry.setStrokeColor(ContextCompat.getColor(this, R.color.crunchy_orange));
            }
            etManualTotal.setHint("Total Chapters");
            etManualTotal.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else {
            layoutDurationSlider.setVisibility(View.GONE);
            tvLabelProgress.setText("Progress");
            tvLabelTotal.setText("Total");
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
        String description = Objects.requireNonNull(etManualDescription.getText()).toString().trim();
        String review = Objects.requireNonNull(etManualReview.getText()).toString().trim();
        String journal = Objects.requireNonNull(etManualJournal.getText()).toString().trim();
        String mood = getSelectedManualMood();
        String priority = spinnerManualPriority.getSelectedItem() != null
                ? spinnerManualPriority.getSelectedItem().toString()
                : "Medium";
        boolean isFavorite = switchManualFavorite.isChecked();
        float rating = rbManualRating.getRating();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(capacityStr)) {
            etManualTitle.setError("Required field");
            return;
        }

        int progress = 0;
        int total;
        try {
            if (!TextUtils.isEmpty(progressStr)) {
                progress = Integer.parseInt(progressStr);
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
        if (progress > total) {
            Snackbar.make(coordinatorLayout, "Progress cannot exceed Total Capacity", Snackbar.LENGTH_LONG).show();
            return;
        }

        if (progress == total) {
            status = "Completed";
        }

        final int finalProgress = progress;
        final String finalStatus = status;
        final float finalRating = rating;
        final String finalReview = review;
        final String finalJournal = journal;
        final String finalMood = mood;
        final String finalPriority = priority;
        final boolean finalIsFavorite = isFavorite;

        btnManualDone.setEnabled(false);

        new Thread(() -> {
            String finalImageUrl = ImageUtils.downloadAndSaveImage(AddMediaActivity.this, imageUrl);
            runOnUiThread(() -> {
                try {
                    long result = dbHelper.addMedia(title, type, genre, creator, total, unit, null, finalImageUrl, description);
                    
                    if (result != -1) {
                        dbHelper.updateProgress((int) result, finalProgress, finalStatus, finalRating);
                        dbHelper.updateMediaMetadata((int) result, finalReview, finalJournal, finalMood, finalPriority, finalIsFavorite);
                        
                        Snackbar snackbar = Snackbar.make(coordinatorLayout, "Successfully added to MediaVault!", Snackbar.LENGTH_SHORT);
                        snackbar.getView().setBackgroundColor(ContextCompat.getColor(AddMediaActivity.this, android.R.color.holo_green_dark));
                        snackbar.addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                finish();
                            }
                        });
                        snackbar.show();
                        
                    } else {
                        btnManualDone.setEnabled(true);
                        Log.e(TAG, "Insertion failed (likely duplicate) for title: " + title);
                        showDuplicateEntryDialog();
                    }
                } catch (SQLiteConstraintException e) {
                    btnManualDone.setEnabled(true);
                    Log.e(TAG, "Constraint violation: " + e.getMessage());
                    showDuplicateEntryDialog();
                } catch (Exception e) {
                    btnManualDone.setEnabled(true);
                    Log.e(TAG, "Unexpected DB error: " + e.getMessage());
                    ToastUtils.showCustomToast(AddMediaActivity.this, "A database error occurred.");
                }
            });
        }).start();
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
