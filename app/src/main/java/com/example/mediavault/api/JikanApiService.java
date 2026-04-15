package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface JikanApiService {

    @GET("anime")
    Call<JikanResponse> getAnime(
            @Query("q") String query,
            @Query("limit") int limit
    );

    @GET("manga")
    Call<JikanResponse> getManga(
            @Query("q") String query,
                @Query("limit") int limit
        );

        @GET("manga")
        Call<JikanResponse> getMangaByType(
                @Query("q") String query,
                @Query("type") String type,
            @Query("limit") int limit
    );
}
