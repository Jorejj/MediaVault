package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.AniListSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * AniList GraphQL query endpoint using GET with query/variables.
 */
public interface AniListSearchApiService {
    @GET("/")
    Call<AniListSearchResponse> searchAnime(
            @Query("query") String graphQlQuery,
            @Query("variables") String variablesJson
    );
}
