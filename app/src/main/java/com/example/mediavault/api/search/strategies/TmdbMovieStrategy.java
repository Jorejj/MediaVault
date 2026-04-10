package com.example.mediavault.api.search.strategies;

import com.example.mediavault.api.search.MediaSearchStrategy;
import com.example.mediavault.api.search.ProviderHttpException;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.model.TmdbSearchResponse;
import com.example.mediavault.api.search.service.TmdbSearchApiService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;

public class TmdbMovieStrategy implements MediaSearchStrategy {
    private static final String BASE_URL = "https://api.themoviedb.org/3/";
    private static final String PROVIDER = "TMDB";

    private final String apiKey;
    private final TmdbSearchApiService apiService;

    public TmdbMovieStrategy(String apiKey) {
        this(apiKey, SearchRetrofitFactory.create(BASE_URL));
    }

    TmdbMovieStrategy(String apiKey, Retrofit retrofit) {
        this.apiKey = apiKey;
        this.apiService = retrofit.create(TmdbSearchApiService.class);
    }

    @Override
    public List<UniversalMediaResult> executeSearch(String query) throws IOException {
        Response<TmdbSearchResponse> response = apiService
                .searchMovies(apiKey, query, false, "en-US", 1)
                .execute();

        if (!response.isSuccessful()) {
            throw new ProviderHttpException(PROVIDER, response.code(), "TMDB search failed with HTTP " + response.code());
        }

        TmdbSearchResponse body = response.body();
        if (body == null || body.results == null || body.results.isEmpty()) {
            return new ArrayList<>();
        }

        List<UniversalMediaResult> mapped = new ArrayList<>();
        for (TmdbSearchResponse.Result item : body.results) {
            if (item == null || item.title == null || item.title.trim().isEmpty()) {
                continue;
            }
            String poster = item.posterPath == null ? null : "https://image.tmdb.org/t/p/w500" + item.posterPath;
            String sourceUrl = "https://www.themoviedb.org/movie/" + item.id;
            mapped.add(new UniversalMediaResult(
                    String.valueOf(item.id),
                    item.title,
                    "", // Author/Director placeholder
                    "Movie",
                    item.overview,
                    poster,
                    PROVIDER,
                    sourceUrl,
                    extractYear(item.releaseDate)
            ));
        }
        return mapped;
    }

    private int extractYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
