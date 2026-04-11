package com.example.mediavault.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.BuildConfig;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.ImageUtils;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MediaSearchManager {
    private static final String TAG = "MediaSearchManager";

    private final Retrofit jikanRetrofit;
    private final Retrofit googleBooksRetrofit;
    private final Retrofit tmdbRetrofit;
    private final Retrofit tvMazeRetrofit;
    private final Retrofit openLibraryRetrofit;

    public MediaSearchManager() {
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

        tvMazeRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.tvmaze.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        openLibraryRetrofit = new Retrofit.Builder()
                .baseUrl("https://openlibrary.org/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private boolean hasTmdbApiKey() {
        return BuildConfig.TMDB_API_KEY != null && !BuildConfig.TMDB_API_KEY.trim().isEmpty();
    }

    private boolean hasGoogleBooksApiKey() {
        return BuildConfig.GOOGLE_BOOKS_API_KEY != null && !BuildConfig.GOOGLE_BOOKS_API_KEY.trim().isEmpty();
    }

    public void searchAndDownloadImage(Context context, int mediaId, String title, String type) {
        if (title == null || title.isEmpty()) return;

        if ("Anime".equalsIgnoreCase(type)) {
            searchAnimeImage(context, mediaId, title);
        } else if ("Manga".equalsIgnoreCase(type)) {
            searchMangaImage(context, mediaId, title);
        } else if ("Book".equalsIgnoreCase(type)) {
            searchBookImage(context, mediaId, title);
        } else if ("Movie".equalsIgnoreCase(type)) {
            searchTmdbImage(context, mediaId, title, true);
        } else if ("Series".equalsIgnoreCase(type)) {
            searchSeriesImage(context, mediaId, title);
        }
    }

    public void enrichMediaMetadata(Context context, int mediaId, String title, String type) {
        if (title == null || title.isEmpty()) return;
        
        // Phase 2: Metadata Enrichment
        if ("Anime".equalsIgnoreCase(type)) {
            enrichAnimeMetadata(context, mediaId, title);
        } else if ("Manga".equalsIgnoreCase(type)) {
            enrichMangaMetadata(context, mediaId, title);
        } else if ("Movie".equalsIgnoreCase(type)) {
            enrichTmdbMetadata(context, mediaId, title, true);
        } else if ("Series".equalsIgnoreCase(type)) {
            enrichTmdbMetadata(context, mediaId, title, false);
        } else if ("Book".equalsIgnoreCase(type)) {
            enrichBookMetadata(context, mediaId, title);
        }
    }

    private void enrichAnimeMetadata(Context context, int mediaId, String title) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getAnime(title, 1).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    JikanResponse.MediaData data = response.body().getData().get(0);
                    String imageUrl = data.getImages() != null && data.getImages().getJpg() != null
                            ? data.getImages().getJpg().getImageUrl()
                            : null;
                    MediaMetadataProfile profile = MediaMetadataProfile.create()
                            .withCanonicalTitle(data.getTitle())
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(data.getTitle()))
                            .addAltTitle(data.getTitleEnglish())
                            .addAltTitle(data.getTitleJapanese())
                            .withProviderId("jikan")
                            .withProviderSlug("jikan")
                            .withMetadataSource("jikan")
                            .withMediaType("Anime")
                            .withStatus(data.getStatus())
                            .withReleaseYear(data.getYear())
                            .withTotalCount(data.getEpisodes())
                            .withUnit("Episodes")
                            .withProviderFeaturesJson("{\"subDubAvailability\":\"unknown\",\"episodeListAvailable\":true}")
                            .addGenres(MediaMetadataProfile.splitCsv(data.getGenresAsString()))
                            .withRating(data.getScore())
                            .withPopularity(data.getPopularity() == null ? null : data.getPopularity().floatValue())
                            .withMetadataConfidence(0.82f)
                            .withMetadataPriority(80)
                            .stampNow();
                    if (data.getMalId() != null) {
                        profile.addExternalId("malId", String.valueOf(data.getMalId()));
                    }
                    updateWithMetadata(context, mediaId, data.getSynopsis(), data.getEpisodes(), data.getGenresAsString(), data.getAuthor(), imageUrl, profile);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Anime enrichment failed", t);
            }
        });
    }

    private void enrichMangaMetadata(Context context, int mediaId, String title) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getManga(title, 1).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    JikanResponse.MediaData data = response.body().getData().get(0);
                    String imageUrl = data.getImages() != null && data.getImages().getJpg() != null
                            ? data.getImages().getJpg().getImageUrl()
                            : null;
                    MediaMetadataProfile profile = MediaMetadataProfile.create()
                            .withCanonicalTitle(data.getTitle())
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(data.getTitle()))
                            .addAltTitle(data.getTitleEnglish())
                            .addAltTitle(data.getTitleJapanese())
                            .withProviderId("jikan")
                            .withProviderSlug("jikan")
                            .withMetadataSource("jikan")
                            .withMediaType("Manga")
                            .withStatus(data.getStatus())
                            .withReleaseYear(data.getYear())
                            .withTotalCount(data.getChapters())
                            .withUnit("Chapters")
                            .withProviderFeaturesJson("{\"scanlatorGroup\":\"unknown\",\"chapterListAvailable\":true}")
                            .addGenres(MediaMetadataProfile.splitCsv(data.getGenresAsString()))
                            .withRating(data.getScore())
                            .withPopularity(data.getPopularity() == null ? null : data.getPopularity().floatValue())
                            .withMetadataConfidence(0.82f)
                            .withMetadataPriority(80)
                            .stampNow();
                    if (data.getMalId() != null) {
                        profile.addExternalId("malId", String.valueOf(data.getMalId()));
                    }
                    updateWithMetadata(context, mediaId, data.getSynopsis(), data.getChapters(), data.getGenresAsString(), data.getAuthor(), imageUrl, profile);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Manga enrichment failed", t);
            }
        });
    }

    private void enrichTmdbMetadata(Context context, int mediaId, String title, boolean isMovie) {
        if (!hasTmdbApiKey()) {
            Log.w(TAG, "TMDB API key missing; skipping TMDB metadata enrichment");
            return;
        }
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        Call<TmdbResponse> call = isMovie
                ? service.searchMovies(BuildConfig.TMDB_API_KEY, title)
                : service.searchTv(BuildConfig.TMDB_API_KEY, title);
        
        call.enqueue(new Callback<TmdbResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmdbResponse> call, @NonNull Response<TmdbResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null && !response.body().getResults().isEmpty()) {
                    TmdbResponse.TmdbItem item = response.body().getResults().get(0);
                    String imageUrl = item.getPosterPath() != null ? "https://image.tmdb.org/t/p/w500" + item.getPosterPath() : null;
                    
                    // TMDB search results don't have episode count for TV, need secondary fetch
                    if (!isMovie) {
                        fetchTvDetailsAndEnrich(context, mediaId, item, imageUrl);
                    } else {
                        MediaMetadataProfile profile = MediaMetadataProfile.create()
                                .withCanonicalTitle(item.getTitle())
                                .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(item.getTitle()))
                                .withProviderId("tmdb")
                                .withProviderSlug("tmdb")
                                .withMetadataSource("tmdb")
                                .withMediaType("Movie")
                                .withLanguage(item.getOriginalLanguage())
                                .withReleaseYear(parseReleaseYear(item.getReleaseDate()))
                                .withTotalCount(1)
                                .withUnit("Minutes")
                                .withProviderFeaturesJson("{\"runtime\":\"unknown\",\"contentRating\":\"unknown\"}")
                                .withRating(item.getVoteAverage())
                                .withPopularity(item.getPopularity())
                                .withMetadataConfidence(0.95f)
                                .withMetadataPriority(100)
                                .stampNow()
                                .addExternalId("tmdbId", String.valueOf(item.getId()));
                        updateWithMetadata(context, mediaId, item.getOverview(), 1, "Movie", "N/A", imageUrl, profile);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<TmdbResponse> call, @NonNull Throwable t) {}
        });
    }

    private void fetchTvDetailsAndEnrich(Context context, int mediaId, TmdbResponse.TmdbItem item, String imageUrl) {
        if (!hasTmdbApiKey()) {
            Log.w(TAG, "TMDB API key missing; skipping TV details enrichment");
            return;
        }
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        service.getTvDetails(item.getId(), BuildConfig.TMDB_API_KEY).enqueue(new Callback<TvDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<TvDetailResponse> call, @NonNull Response<TvDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TvDetailResponse details = response.body();
                    MediaMetadataProfile profile = MediaMetadataProfile.create()
                            .withCanonicalTitle(item.getTitle())
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(item.getTitle()))
                            .withProviderId("tmdb")
                            .withProviderSlug("tmdb")
                            .withMetadataSource("tmdb")
                            .withMediaType("Series")
                            .withLanguage(item.getOriginalLanguage())
                            .withStatus(details.getStatus())
                            .withReleaseYear(parseReleaseYear(item.getReleaseDate()))
                            .withTotalCount(details.getNumberOfEpisodes())
                            .withUnit("Episodes")
                            .withProviderFeaturesJson("{\"seasonStructure\":\"s1e1_default\",\"contentRating\":\"unknown\"}")
                            .addGenres(MediaMetadataProfile.splitCsv(details.getGenresAsString()))
                            .withRating(item.getVoteAverage())
                            .withPopularity(item.getPopularity())
                            .withMetadataConfidence(0.95f)
                            .withMetadataPriority(100)
                            .stampNow()
                            .addExternalId("tmdbId", String.valueOf(item.getId()));
                    if (details.getNumberOfSeasons() != null) {
                        profile.addTag("seasons:" + details.getNumberOfSeasons());
                    }
                    updateWithMetadata(context, mediaId, details.getOverview(), details.getNumberOfEpisodes(), details.getGenresAsString(), details.getCreator(), imageUrl, profile);
                }
            }
            @Override
            public void onFailure(@NonNull Call<TvDetailResponse> call, @NonNull Throwable t) {}
        });
    }

    private void enrichBookMetadata(Context context, int mediaId, String title) {
        if (!hasGoogleBooksApiKey()) {
            Log.w(TAG, "Google Books API key missing; skipping Google Books metadata enrichment");
            return;
        }
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(title, BuildConfig.GOOGLE_BOOKS_API_KEY).enqueue(new Callback<GoogleBooksResponse>() {
            @Override
            public void onResponse(@NonNull Call<GoogleBooksResponse> call, @NonNull Response<GoogleBooksResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getItems() != null && !response.body().getItems().isEmpty()) {
                    GoogleBooksResponse.VolumeInfo info = response.body().getItems().get(0).getVolumeInfo();
                    String imageUrl = null;
                    if (info.getImageLinks() != null && info.getImageLinks().getThumbnail() != null) {
                        imageUrl = info.getImageLinks().getThumbnail().replace("http://", "https://");
                    }
                    MediaMetadataProfile profile = MediaMetadataProfile.create()
                            .withCanonicalTitle(info.getTitle())
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(info.getTitle()))
                            .withProviderId("google-books")
                            .withProviderSlug("google-books")
                            .withCanonicalUrl(info.getCanonicalVolumeLink() != null ? info.getCanonicalVolumeLink() : info.getInfoLink())
                            .withMetadataSource("google_books")
                            .withMediaType("Book")
                            .withLanguage(info.getLanguage())
                            .withReleaseYear(parseReleaseYear(info.getPublishedDate()))
                            .withTotalCount(info.getPageCount())
                            .withUnit("Pages")
                            .withProviderFeaturesJson("{\"translationStatus\":\"unknown\",\"updateFrequency\":\"unknown\"}")
                            .addGenres(info.getCategories())
                            .withMetadataConfidence(0.9f)
                            .withMetadataPriority(90)
                            .stampNow();
                    if (info.getIndustryIdentifiers() != null) {
                        for (GoogleBooksResponse.IndustryIdentifier identifier : info.getIndustryIdentifiers()) {
                            if (identifier != null) {
                                profile.addExternalId(identifier.getType(), identifier.getIdentifier());
                            }
                        }
                    }
                    updateWithMetadata(context, mediaId, info.getDescription(), info.getPageCount(), info.getCategoriesAsString(), info.getAuthorsAsString(), imageUrl, profile);
                }
            }
            @Override
            public void onFailure(@NonNull Call<GoogleBooksResponse> call, @NonNull Throwable t) {}
        });
    }

    private void updateWithMetadata(Context context, int mediaId, String desc, Integer total, String genre, String author, String imageUrl) {
        updateWithMetadata(context, mediaId, desc, total, genre, author, imageUrl, null);
    }

    private void updateWithMetadata(Context context, int mediaId, String desc, Integer total, String genre, String author, String imageUrl, MediaMetadataProfile metadataProfile) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            // We use a partial update here (new method needed or use existing with careful null handling)
            // For now, let's use the full updateMedia but fetch current values first
            Cursor cursor = db.getMediaById(mediaId);
            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS));
                float progress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                float rating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));
                String review = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW));
                String existingSourceUrl = cursor.getColumnIndex(DatabaseHelper.COL_SOURCE_URL) >= 0
                        ? cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE_URL))
                        : null;
                String existingContentType = cursor.getColumnIndex(DatabaseHelper.COL_CONTENT_TYPE) >= 0
                        ? cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CONTENT_TYPE))
                        : null;

                int finalTotal = (total != null && total > 0) ? total : cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                String finalGenre = (genre != null && !genre.isEmpty()) ? genre : cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENRE));
                String finalDesc = (desc != null && !desc.isEmpty()) ? desc : cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPTION));
                
                db.updateMedia(mediaId, title, type, finalGenre, status, progress, finalTotal, unit, null, rating, review, finalDesc, author);
                String resolvedSource = existingSourceUrl;
                if (existingSourceUrl == null || existingSourceUrl.trim().isEmpty()) {
                    String fallbackSource = buildFallbackSourceUrl(title, type);
                    String fallbackContentType = (existingContentType == null || existingContentType.trim().isEmpty()) ? "search" : existingContentType;
                    db.updateSourceAndContent(mediaId, fallbackSource, fallbackContentType);
                    resolvedSource = fallbackSource;
                }
                // Also update image if we got one
                if (imageUrl != null) {
                    downloadAndUpdate(context, mediaId, imageUrl);
                }
                MediaMetadataProfile profileToSave = metadataProfile;
                if (profileToSave == null) {
                    profileToSave = MediaMetadataProfile.create()
                            .withCanonicalTitle(title)
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(title))
                            .withProviderSlug(MediaMetadataProfile.detectProviderSlugFromUrl(resolvedSource))
                            .withCanonicalUrl(resolvedSource)
                            .withMetadataSource("enrichment_fallback")
                            .withMediaType(type)
                            .withTotalCount(finalTotal)
                            .withUnit(unit)
                            .addGenres(MediaMetadataProfile.splitCsv(finalGenre))
                            .withMetadataConfidence(0.55f)
                            .withMetadataPriority(50)
                            .stampNow();
                } else {
                    if (profileToSave.getCanonicalTitle() == null) {
                        profileToSave.withCanonicalTitle(title);
                    }
                    if (profileToSave.getCanonicalUrl() == null) {
                        profileToSave.withCanonicalUrl(resolvedSource);
                    }
                    if (profileToSave.getProviderSlug() == null) {
                        profileToSave.withProviderSlug(MediaMetadataProfile.detectProviderSlugFromUrl(resolvedSource));
                    }
                    if (profileToSave.getMediaType() == null) {
                        profileToSave.withMediaType(type);
                    }
                    if (profileToSave.getTotalCount() == null) {
                        profileToSave.withTotalCount(finalTotal);
                    }
                    if (profileToSave.getUnit() == null) {
                        profileToSave.withUnit(unit);
                    }
                    profileToSave.stampNow();
                }
                String mergeSource = profileToSave.getMetadataSource();
                if (mergeSource == null || mergeSource.trim().isEmpty()) {
                    mergeSource = "api_enrichment";
                }
                db.mergeAndUpsertMetadata(mediaId, profileToSave, mergeSource);
                cursor.close();
            }
        });
    }

    private void searchAnimeImage(Context context, int mediaId, String title) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getAnime(title, 1).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    String imageUrl = null;
                    JikanResponse.MediaData data = response.body().getData().get(0);
                    if (data.getImages() != null && data.getImages().getJpg() != null) {
                        imageUrl = data.getImages().getJpg().getImageUrl();
                    }
                    if (imageUrl != null) {
                        downloadAndUpdate(context, mediaId, imageUrl);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Anime search failed", t);
            }
        });
    }

    private void searchMangaImage(Context context, int mediaId, String title) {
        JikanApiService service = jikanRetrofit.create(JikanApiService.class);
        service.getManga(title, 1).enqueue(new Callback<JikanResponse>() {
            @Override
            public void onResponse(@NonNull Call<JikanResponse> call, @NonNull Response<JikanResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    String imageUrl = null;
                    JikanResponse.MediaData data = response.body().getData().get(0);
                    if (data.getImages() != null && data.getImages().getJpg() != null) {
                        imageUrl = data.getImages().getJpg().getImageUrl();
                    }
                    if (imageUrl != null) {
                        downloadAndUpdate(context, mediaId, imageUrl);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<JikanResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Manga search failed", t);
            }
        });
    }

    private void searchBookImage(Context context, int mediaId, String title) {
        if (!hasGoogleBooksApiKey()) {
            searchOpenLibraryImage(context, mediaId, title);
            return;
        }
        // Try Google Books first
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(title, BuildConfig.GOOGLE_BOOKS_API_KEY).enqueue(new Callback<GoogleBooksResponse>() {
            @Override
            public void onResponse(@NonNull Call<GoogleBooksResponse> call, @NonNull Response<GoogleBooksResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getItems() != null && !response.body().getItems().isEmpty()) {
                    GoogleBooksResponse.VolumeInfo info = response.body().getItems().get(0).getVolumeInfo();
                    String imageUrl = null;
                    if (info.getImageLinks() != null) {
                        imageUrl = info.getImageLinks().getThumbnail();
                        if (imageUrl != null && imageUrl.startsWith("http://")) {
                            imageUrl = imageUrl.replace("http://", "https://");
                        }
                    }
                    if (imageUrl != null) {
                        downloadAndUpdate(context, mediaId, imageUrl);
                    } else {
                        searchOpenLibraryImage(context, mediaId, title);
                    }
                } else {
                    searchOpenLibraryImage(context, mediaId, title);
                }
            }

            @Override
            public void onFailure(@NonNull Call<GoogleBooksResponse> call, @NonNull Throwable t) {
                searchOpenLibraryImage(context, mediaId, title);
            }
        });
    }

    private void searchOpenLibraryImage(Context context, int mediaId, String title) {
        OpenLibraryApiService service = openLibraryRetrofit.create(OpenLibraryApiService.class);
        service.searchBooks(title, 1).enqueue(new Callback<OpenLibraryResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenLibraryResponse> call, @NonNull Response<OpenLibraryResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getDocs() != null && !response.body().getDocs().isEmpty()) {
                    String imageUrl = response.body().getDocs().get(0).getCoverUrl();
                    if (imageUrl != null) {
                        downloadAndUpdate(context, mediaId, imageUrl);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<OpenLibraryResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "OpenLibrary search failed", t);
            }
        });
    }

    private void searchTmdbImage(Context context, int mediaId, String title, boolean isMovie) {
        if (!hasTmdbApiKey()) {
            Log.w(TAG, "TMDB API key missing; skipping TMDB image search");
            return;
        }
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        Call<TmdbResponse> call = isMovie
                ? service.searchMovies(BuildConfig.TMDB_API_KEY, title)
                : service.searchTv(BuildConfig.TMDB_API_KEY, title);
        
        call.enqueue(new Callback<TmdbResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmdbResponse> call, @NonNull Response<TmdbResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null && !response.body().getResults().isEmpty()) {
                    String posterPath = response.body().getResults().get(0).getPosterPath();
                    if (posterPath != null) {
                        String imageUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
                        downloadAndUpdate(context, mediaId, imageUrl);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<TmdbResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "TMDB search failed", t);
            }
        });
    }

    private void searchSeriesImage(Context context, int mediaId, String title) {
        if (!hasTmdbApiKey()) {
            searchTvMazeImage(context, mediaId, title);
            return;
        }
        // Try TMDB first, then TVMaze as fallback
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        service.searchTv(BuildConfig.TMDB_API_KEY, title).enqueue(new Callback<TmdbResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmdbResponse> call, @NonNull Response<TmdbResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null && !response.body().getResults().isEmpty()) {
                    String posterPath = response.body().getResults().get(0).getPosterPath();
                    if (posterPath != null) {
                        String imageUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
                        downloadAndUpdate(context, mediaId, imageUrl);
                    } else {
                        searchTvMazeImage(context, mediaId, title);
                    }
                } else {
                    searchTvMazeImage(context, mediaId, title);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TmdbResponse> call, @NonNull Throwable t) {
                searchTvMazeImage(context, mediaId, title);
            }
        });
    }

    private void searchTvMazeImage(Context context, int mediaId, String title) {
        TvMazeApiService service = tvMazeRetrofit.create(TvMazeApiService.class);
        service.searchShows(title).enqueue(new Callback<List<TvMazeResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<TvMazeResponse>> call, @NonNull Response<List<TvMazeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    TvMazeResponse.TvShow show = response.body().get(0).getShow();
                    if (show != null && show.getImage() != null) {
                        String imageUrl = show.getImage().getOriginal();
                        if (imageUrl != null) {
                            downloadAndUpdate(context, mediaId, imageUrl);
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TvMazeResponse>> call, @NonNull Throwable t) {
                Log.e(TAG, "TvMaze search failed", t);
            }
        });
    }

    public void autoFetchAllMissingImages(Context context) {
        new Thread(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
            Cursor cursor = dbHelper.getAllMedia();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                    String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IMAGE_PATH));

                    if (imagePath == null || imagePath.isEmpty() || imagePath.startsWith("http")) {
                        // For URLs, we just need to download. For null, we search then download.
                        if (imagePath != null && imagePath.startsWith("http")) {
                            downloadAndUpdate(context, id, imagePath);
                        } else {
                            // Clean demo suffix if present for better search
                            String searchTitle = title.replace(" (Demo)", "").trim();
                            searchAndDownloadImage(context, id, searchTitle, type);
                        }
                    }
                }
                cursor.close();
            }
        }).start();
    }

    private void downloadAndUpdate(Context context, int mediaId, String imageUrl) {
        new Thread(() -> {
            String localPath = ImageUtils.downloadAndSaveImage(context, imageUrl);
            if (localPath != null && !localPath.startsWith("http")) {
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
                dbHelper.updateImagePath(mediaId, localPath);
                Log.d(TAG, "Successfully auto-updated image for media ID: " + mediaId);
            }
        }).start();
    }

    private Integer parseReleaseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildFallbackSourceUrl(String title, String type) {
        String safeTitle = Uri.encode(title == null ? "" : title);
        if ("Anime".equalsIgnoreCase(type)) {
            return "https://animekai.to/search?keyword=" + safeTitle;
        }
        if ("Manga".equalsIgnoreCase(type)) {
            return "https://comix.to/filter?keyword=" + safeTitle;
        }
        if ("Movie".equalsIgnoreCase(type)) {
            return "https://nepu.to/search?q=" + safeTitle;
        }
        if ("Series".equalsIgnoreCase(type) || "TV Show".equalsIgnoreCase(type)) {
            return "https://xprime.su/search?q=" + safeTitle + "+episode+1";
        }
        if ("Book".equalsIgnoreCase(type)) {
            return buildBookFallbackSourceUrl(title);
        }
        return "https://www.google.com/search?q=" + safeTitle;
    }

    private String buildBookFallbackSourceUrl(String title) {
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
}
