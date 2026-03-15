package com.example.mediavault.api;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;

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
    private static final String TMDB_API_KEY = "b839069f4d8893e2d87c422727980edc";

    private final Retrofit jikanRetrofit;
    private final Retrofit googleBooksRetrofit;
    private final Retrofit tmdbRetrofit;
    private final Retrofit tvMazeRetrofit;
    private final Retrofit openLibraryRetrofit;

    public MediaSearchManager() {
        jikanRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.jikan.moe/v4/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        googleBooksRetrofit = new Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/books/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        tmdbRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        tvMazeRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.tvmaze.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        openLibraryRetrofit = new Retrofit.Builder()
                .baseUrl("https://openlibrary.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
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
        // Try Google Books first
        GoogleBooksApiService service = googleBooksRetrofit.create(GoogleBooksApiService.class);
        service.getBooks(title).enqueue(new Callback<GoogleBooksResponse>() {
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
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        Call<TmdbResponse> call = isMovie ? service.searchMovies(TMDB_API_KEY, title) : service.searchTv(TMDB_API_KEY, title);
        
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
        // Try TMDB first, then TVMaze as fallback
        TmdbApiService service = tmdbRetrofit.create(TmdbApiService.class);
        service.searchTv(TMDB_API_KEY, title).enqueue(new Callback<TmdbResponse>() {
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
            DatabaseHelper dbHelper = new DatabaseHelper(context);
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
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                dbHelper.updateImagePath(mediaId, localPath);
                Log.d(TAG, "Successfully auto-updated image for media ID: " + mediaId);
            }
        }).start();
    }
}
