package com.example.mediavault.cloud.auth;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface SupabaseAuthApi {
    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=password")
    Call<JsonObject> signInWithPassword(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Body JsonObject body
    );

    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    Call<JsonObject> signUp(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Body JsonObject body
    );

    @Headers("Content-Type: application/json")
    @POST("auth/v1/recover")
    Call<JsonObject> sendPasswordRecovery(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Body JsonObject body
    );

    @Headers("Content-Type: application/json")
    @POST("auth/v1/logout")
    Call<Void> signOut(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization
    );

    @Headers("Content-Type: application/json")
    @PUT("auth/v1/user")
    Call<JsonObject> updateUserPassword(
            @Header("apikey") String apikey,
            @Header("Authorization") String authorization,
            @Body JsonObject body
    );
}
