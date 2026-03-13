package com.example.mediavault.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
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

    @GET("movie/{movie_id}?append_to_response=credits")
    Call<MovieDetailResponse> getMovieDetails(
            @Path("movie_id") int movieId,
            @Query("api_key") String apiKey
    );

    @GET("tv/{tv_id}")
    Call<TvDetailResponse> getTvDetails(
            @Path("tv_id") int tvId,
            @Query("api_key") String apiKey
    );
}
