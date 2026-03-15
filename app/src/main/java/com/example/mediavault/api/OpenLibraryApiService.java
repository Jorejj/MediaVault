package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenLibraryApiService {
    @GET("search.json")
    Call<OpenLibraryResponse> searchBooks(@Query("title") String title, @Query("limit") int limit);
}
