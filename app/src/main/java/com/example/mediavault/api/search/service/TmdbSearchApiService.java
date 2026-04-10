package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.TmdbSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * TMDB v3 search endpoint.
 */
public interface TmdbSearchApiService {
    @GET("search/movie")
    Call<TmdbSearchResponse> searchMovies(
            @Query("api_key") String apiKey,
            @Query("query") String query,
            @Query("include_adult") boolean includeAdult,
            @Query("language") String language,
            @Query("page") int page
    );
}
