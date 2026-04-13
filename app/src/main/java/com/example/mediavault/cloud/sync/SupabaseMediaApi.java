package com.example.mediavault.cloud.sync;

import com.google.gson.JsonArray;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
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
}
