package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.GoogleBooksSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Google Books volumes search endpoint.
 */
public interface GoogleBooksSearchApiService {
    @GET("volumes")
    Call<GoogleBooksSearchResponse> searchBooks(
            @Query("q") String query,
            @Query("maxResults") int maxResults,
            @Query("printType") String printType,
            @Query("orderBy") String orderBy,
            @Query("langRestrict") String language,
            @Query("key") String apiKey
    );
}
