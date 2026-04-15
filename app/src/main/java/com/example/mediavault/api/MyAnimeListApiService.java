package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface MyAnimeListApiService {
    @GET("anime")
    Call<MyAnimeListResponse> searchAnime(
            @Header("X-MAL-CLIENT-ID") String clientId,
            @Query("q") String query,
            @Query("limit") int limit,
            @Query("fields") String fields
    );

    @GET("manga")
    Call<MyAnimeListResponse> searchManga(
            @Header("X-MAL-CLIENT-ID") String clientId,
            @Query("q") String query,
            @Query("limit") int limit,
            @Query("fields") String fields
    );
}
