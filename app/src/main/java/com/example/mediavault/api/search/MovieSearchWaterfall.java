package com.example.mediavault.api.search;

import com.example.mediavault.api.search.strategies.OmdbMovieStrategy;
import com.example.mediavault.api.search.strategies.TmdbMovieStrategy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Movie search chain configured as TMDB -> OMDb fallback.
 */
public class MovieSearchWaterfall {
    private final ProviderWaterfall providerWaterfall = new ProviderWaterfall();
    private final List<MediaSearchStrategy> movieStrategies;

    public MovieSearchWaterfall(String tmdbApiKey, String omdbApiKey) {
        this.movieStrategies = Arrays.asList(
                new TmdbMovieStrategy(tmdbApiKey),
                new OmdbMovieStrategy(omdbApiKey)
        );
    }

    public List<UniversalMediaResult> searchMovies(String query) throws IOException {
        return providerWaterfall.searchWithFallback(movieStrategies, query);
    }
}
