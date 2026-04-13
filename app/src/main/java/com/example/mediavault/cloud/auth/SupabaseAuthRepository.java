package com.example.mediavault.cloud.auth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.mediavault.cloud.CloudConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseAuthRepository {
    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    public interface ProfileCallback {
        void onSuccess(JsonObject profile);
        void onError(String message);
    }

    private final SupabaseSessionManager sessionManager;

    public SupabaseAuthRepository(Context context) {
        this.sessionManager = new SupabaseSessionManager(context);
    }

    public SupabaseSessionManager getSessionManager() {
        return sessionManager;
    }

    public void signIn(String email, String password, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is disabled. Set SUPABASE_ENABLED=true and configure keys.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        authApi().signInWithPassword(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Login failed (" + response.code() + "). Check email/password.");
                    return;
                }
                sessionManager.saveSession(response.body());
                callback.onSuccess("Logged in successfully.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Login request failed: " + throwable.getMessage());
            }
        });
    }

    public void signUp(String email, String password, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is disabled. Set SUPABASE_ENABLED=true and configure keys.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        authApi().signUp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Sign up failed (" + response.code() + ").");
                    return;
                }
                JsonObject payload = response.body();
                if (payload != null) {
                    sessionManager.saveSession(payload);
                }
                callback.onSuccess("Account created. Check your email if confirmation is required.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Sign up request failed: " + throwable.getMessage());
            }
        });
    }

    public void sendPasswordReset(String email, String redirectTo, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is disabled. Set SUPABASE_ENABLED=true and configure keys.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        if (redirectTo != null && !redirectTo.trim().isEmpty()) {
            body.addProperty("redirect_to", redirectTo);
        }
        authApi().sendPasswordRecovery(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Password reset request failed (" + response.code() + ").");
                    return;
                }
                callback.onSuccess("Password reset email sent.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Password reset request failed: " + throwable.getMessage());
            }
        });
    }

    public void signOutLocal() {
        sessionManager.clearSession();
    }

    public void signOut(AuthCallback callback) {
        String token = sessionManager.getAccessToken();
        if (CloudConfig.isSupabaseEnabled() && token != null && !token.trim().isEmpty()) {
            authApi().signOut(apiKey(), "Bearer " + token).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                    sessionManager.clearSession();
                    if (response.isSuccessful()) {
                        callback.onSuccess("Logged out.");
                    } else {
                        callback.onSuccess("Logged out locally.");
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                    sessionManager.clearSession();
                    callback.onSuccess("Logged out locally.");
                }
            });
            return;
        }
        sessionManager.clearSession();
        callback.onSuccess("Logged out locally.");
    }

    public void fetchProfile(ProfileCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is disabled.");
            return;
        }
        String userId = sessionManager.getUserId();
        String token = sessionManager.getAccessToken();
        if (userId.isEmpty() || token.isEmpty()) {
            callback.onError("No active session.");
            return;
        }
        profileApi().getProfileById(
                apiKey(),
                "Bearer " + token,
                "id,email,display_name,avatar_url,role,preferred_types,preferred_genres,avg_session_minutes,binge_score,completion_rate",
                "eq." + userId,
                1
        ).enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(@NonNull Call<JsonArray> call, @NonNull Response<JsonArray> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Failed to load profile (" + response.code() + ").");
                    return;
                }
                JsonArray arr = response.body();
                if (arr.size() == 0) {
                    callback.onError("Profile not found.");
                    return;
                }
                callback.onSuccess(arr.get(0).getAsJsonObject());
            }

            @Override
            public void onFailure(@NonNull Call<JsonArray> call, @NonNull Throwable throwable) {
                callback.onError("Profile request failed: " + throwable.getMessage());
            }
        });
    }

    public void upsertProfile(String displayName, String avatarUrl, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is disabled.");
            return;
        }
        String userId = sessionManager.getUserId();
        String email = sessionManager.getEmail();
        String token = sessionManager.getAccessToken();
        if (userId.isEmpty() || token.isEmpty()) {
            callback.onError("No active session.");
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("id", userId);
        row.addProperty("email", email);
        row.addProperty("display_name", displayName == null ? "" : displayName.trim());
        row.addProperty("avatar_url", avatarUrl == null ? "" : avatarUrl.trim());
        JsonArray payload = new JsonArray();
        payload.add(row);

        profileApi().upsertProfile(
                apiKey(),
                "Bearer " + token,
                "resolution=merge-duplicates,return=representation",
                payload
        ).enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(@NonNull Call<JsonArray> call, @NonNull Response<JsonArray> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Failed to save profile (" + response.code() + ").");
                    return;
                }
                callback.onSuccess("Profile updated.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonArray> call, @NonNull Throwable throwable) {
                callback.onError("Profile update failed: " + throwable.getMessage());
            }
        });
    }

    private SupabaseAuthApi authApi() {
        return new Retrofit.Builder()
                .baseUrl(normalizedBaseUrl())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseAuthApi.class);
    }

    private SupabaseProfileApi profileApi() {
        return new Retrofit.Builder()
                .baseUrl(normalizedBaseUrl())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseProfileApi.class);
    }

    private static String normalizedBaseUrl() {
        String raw = CloudConfig.getSupabaseUrl();
        return raw.endsWith("/") ? raw : raw + "/";
    }

    private static String apiKey() {
        return CloudConfig.getSupabaseAnonKey();
    }

    private static String bearerAnon() {
        return "Bearer " + CloudConfig.getSupabaseAnonKey();
    }
}
