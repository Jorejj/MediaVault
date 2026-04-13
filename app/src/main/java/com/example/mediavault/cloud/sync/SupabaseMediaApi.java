package com.example.mediavault.cloud.sync;

import com.google.gson.JsonArray;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseMediaApi {
    @Headers("Content-Type: application/json")
    @POST("rest/v1/media_library?on_conflict=user_id,local_media_id")
    Call<Void> upsertMedia(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Header("Prefer") String prefer,
            @Body JsonArray payload
    );

    @DELETE("rest/v1/media_library")
    Call<Void> deleteMediaByLocalId(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Query("user_id") String userIdEq,
            @Query("local_media_id") String localMediaIdEq
    );

    @GET("rest/v1/media_library")
    Call<JsonArray> getMediaByUser(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Query("select") String select,
            @Query("user_id") String userIdEq,
            @Query("order") String order
    );

    @GET("rest/v1/media_library")
    Call<JsonArray> getMediaIdByLocalId(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Query("select") String select,
            @Query("user_id") String userIdEq,
            @Query("local_media_id") String localMediaIdEq,
            @Query("limit") String limit
    );

    @Headers("Content-Type: application/json")
    @POST("rest/v1/user_media_events")
    Call<Void> insertMediaEvents(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Header("Prefer") String prefer,
            @Body JsonArray payload
    );

    @Headers("Content-Type: application/json")
    @POST("rest/v1/progress_log")
    Call<Void> insertProgressLogs(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Header("Prefer") String prefer,
            @Body JsonArray payload
    );

    @Headers("Content-Type: application/json")
    @POST("rest/v1/daily_metrics?on_conflict=user_id,date")
    Call<Void> upsertDailyMetrics(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Header("Prefer") String prefer,
            @Body JsonArray payload
    );
}
