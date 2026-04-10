package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.MangaDexSearchResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * MangaDex v5 search endpoint.
 */
public interface MangaDexSearchApiService {
    @GET("manga")
    Call<MangaDexSearchResponse> searchManga(
            @Query("title") String title,
            @Query("limit") int limit,
            @Query("includes[]") List<String> includes,
            @Query("contentRating[]") List<String> contentRatings,
            @Query("order[relevance]") String relevanceOrder
    );
}
