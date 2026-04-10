package com.example.mediavault.api.search.strategies;

import com.example.mediavault.api.search.MediaSearchStrategy;
import com.example.mediavault.api.search.ProviderHttpException;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.model.OmdbSearchResponse;
import com.example.mediavault.api.search.service.OmdbSearchApiService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * OMDb fallback for movie search when TMDB is unavailable/rate-limited.
 */
public class OmdbMovieStrategy implements MediaSearchStrategy {
    private static final String BASE_URL = "https://www.omdbapi.com/";
    private static final String PROVIDER = "OMDb";

    private final String apiKey;
    private final OmdbSearchApiService apiService;

    public OmdbMovieStrategy(String apiKey) {
        this(apiKey, SearchRetrofitFactory.create(BASE_URL));
    }

    OmdbMovieStrategy(String apiKey, Retrofit retrofit) {
        this.apiKey = apiKey;
        this.apiService = retrofit.create(OmdbSearchApiService.class);
    }

    @Override
    public List<UniversalMediaResult> executeSearch(String query) throws IOException {
        Response<OmdbSearchResponse> response = apiService
                .searchMovies(apiKey, query, "movie", 1)
                .execute();

        if (!response.isSuccessful()) {
            throw new ProviderHttpException(PROVIDER, response.code(), "OMDb search failed with HTTP " + response.code());
        }

        OmdbSearchResponse body = response.body();
        if (body == null || body.search == null || body.search.isEmpty()) {
            return new ArrayList<>();
        }

        List<UniversalMediaResult> mapped = new ArrayList<>();
        for (OmdbSearchResponse.OmdbItem item : body.search) {
            if (item == null || item.title == null || item.title.trim().isEmpty()) {
                continue;
            }
            mapped.add(new UniversalMediaResult(
                    item.imdbId,
                    item.title,
                    "", // OMDb search endpoint doesn't return director/author, would need a Get-by-ID call
                    "Movie",
                    null,
                    normalizePoster(item.poster),
                    PROVIDER,
                    item.imdbId == null ? null : "https://www.imdb.com/title/" + item.imdbId,
                    parseYear(item.year)
            ));
        }
        return mapped;
    }

    private int parseYear(String yearValue) {
        if (yearValue == null || yearValue.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(yearValue.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String normalizePoster(String poster) {
        if (poster == null || "N/A".equalsIgnoreCase(poster)) {
            return null;
        }
        return poster;
    }
}
