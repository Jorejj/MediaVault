package com.example.mediavault.api.search.service;

import com.example.mediavault.api.search.model.AniListSearchResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import java.util.Map;

/**
 * AniList GraphQL query endpoint using POST with body payload.
 */
public interface AniListSearchApiService {
    @POST("/")
    Call<AniListSearchResponse> searchAnime(
            @Body Map<String, Object> body
    );
}
