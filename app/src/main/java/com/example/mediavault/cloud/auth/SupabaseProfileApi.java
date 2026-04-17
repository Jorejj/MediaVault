package com.example.mediavault.cloud.auth;

import com.google.gson.JsonArray;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseProfileApi {
    @GET("rest/v1/profiles")
    Call<JsonArray> getProfileById(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Query("select") String select,
            @Query("id") String idEq,
            @Query("limit") int limit
    );

    @Headers("Content-Type: application/json")
    @POST("rest/v1/profiles?on_conflict=id")
    Call<JsonArray> upsertProfile(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Header("Prefer") String prefer,
            @Body JsonArray payload
    );
}
