package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GoogleBooksApiService {

    @GET("volumes")
    Call<GoogleBooksResponse> getBooks(
            @Query("q") String query,
            @Query("key") String apiKey
    );
}
