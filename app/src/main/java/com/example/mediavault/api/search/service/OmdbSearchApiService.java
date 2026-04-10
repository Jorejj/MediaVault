package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.OmdbSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * OMDb search endpoint used as movie fallback when TMDB fails.
 */
public interface OmdbSearchApiService {
    @GET("/")
    Call<OmdbSearchResponse> searchMovies(
            @Query("apikey") String apiKey,
            @Query("s") String query,
            @Query("type") String type,
            @Query("page") int page
    );
}
