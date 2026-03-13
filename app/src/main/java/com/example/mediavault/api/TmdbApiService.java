package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TmdbApiService {

    @GET("search/movie")
    Call<TmdbResponse> searchMovies(
            @Query("api_key") String apiKey,
            @Query("query") String query
    );

    @GET("search/tv")
    Call<TmdbResponse> searchTv(
            @Query("api_key") String apiKey,
            @Query("query") String query
    );
}