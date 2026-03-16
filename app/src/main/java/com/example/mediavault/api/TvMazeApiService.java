package com.example.mediavault.api;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TvMazeApiService {
    @GET("search/shows")
    Call<List<TvMazeResponse>> searchShows(@Query("q") String query);
}
