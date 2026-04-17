package com.example.mediavault.cloud.auth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.mediavault.cloud.CloudConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseAuthRepository {
    private static final String DEBUG_USERNAME = "user123";
    private static final String DEBUG_PASSWORD = "123456";

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
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedPassword = password == null ? "" : password.trim();
        if (com.example.mediavault.BuildConfig.DEBUG && DEBUG_USERNAME.equalsIgnoreCase(normalizedEmail) && DEBUG_PASSWORD.equals(normalizedPassword)) {
            sessionManager.saveDebugSession(DEBUG_USERNAME);
            callback.onSuccess("Logged in with debug account.");
            return;
        }
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", normalizedEmail);
        body.addProperty("password", normalizedPassword);
        authApi().signInWithPassword(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Login failed (" + response.code() + "). Check email/password.");
                    return;
                }
                sessionManager.saveSession(response.body());
                ensureProfileExists("Logged in successfully.", callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Login request failed: " + throwable.getMessage());
            }
        });
    }

    public void signUp(String email, String password, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("email_redirect_to", "mediavault://auth/register-confirm");
        authApi().signUp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError(authError("Sign up", response));
                    return;
                }
                sendSignupOtp(email, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Sign up request failed: " + throwable.getMessage());
            }
        });
    }

    public void requestRegisterOtp(String email, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        JsonObject otpBody = new JsonObject();
        otpBody.addProperty("email", email == null ? "" : email.trim());
        otpBody.addProperty("create_user", true);
        otpBody.addProperty("email_redirect_to", "mediavault://auth/register-confirm");
        authApi().sendEmailOtp(apiKey(), bearerAnon(), otpBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                sessionManager.clearSession();
                if (!response.isSuccessful()) {
                    callback.onError(authError("Verification code request", response));
                    return;
                }
                callback.onSuccess("Verification code sent. Check your email.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                sessionManager.clearSession();
                callback.onError("Could not send verification code: " + throwable.getMessage());
            }
        });
    }

    public void verifyRegisterCodeAndActivate(String email, String code, String password, AuthCallback callback) {
        verifyRegisterCodeWithType(email, code, password, "email", true, callback);
    }

    public void verifyRegisterLinkAndActivate(String tokenHash, String type, String password, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        String normalizedHash = tokenHash == null ? "" : tokenHash.trim();
        if (normalizedHash.isEmpty()) {
            callback.onError("Missing verification token.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("token_hash", normalizedHash);
        body.addProperty("type", normalizeVerifyType(type));
        authApi().verifyEmailOtp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Verification failed (" + response.code() + ").");
                    return;
                }
                sessionManager.saveSession(response.body());
                if (!sessionManager.isLoggedIn()) {
                    callback.onError("Verification succeeded but no session was returned.");
                    return;
                }
                updatePasswordForCurrentSession(password, new AuthCallback() {
                    @Override
                    public void onSuccess(String message) {
                        ensureProfileExists("Registration complete.", callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Verification request failed: " + throwable.getMessage());
            }
        });
    }

    private void verifyRegisterCodeWithType(String email, String code, String password, String type, boolean allowFallback, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("type", type);
        body.addProperty("email", email);
        body.addProperty("token", code);
        authApi().verifyEmailOtp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (allowFallback) {
                        verifyRegisterCodeWithType(email, code, password, "signup", false, callback);
                        return;
                    }
                    callback.onError("Verification failed (" + response.code() + "). Check the code and try again.");
                    return;
                }
                sessionManager.saveSession(response.body());
                if (!sessionManager.isLoggedIn()) {
                    callback.onError("Verification succeeded but no session was returned.");
                    return;
                }
                updatePasswordForCurrentSession(password, new AuthCallback() {
                    @Override
                    public void onSuccess(String message) {
                        ensureProfileExists("Registration complete.", callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Verification request failed: " + throwable.getMessage());
            }
        });
    }

    public void verifySignUpCode(String email, String code, AuthCallback callback) {
        verifySignUpCodeWithType(email, code, "signup", true, callback);
    }

    private void verifySignUpCodeWithType(String email, String code, String type, boolean allowFallback, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("type", type);
        body.addProperty("email", email);
        body.addProperty("token", code);
        authApi().verifyEmailOtp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (allowFallback) {
                        verifySignUpCodeWithType(email, code, "email", false, callback);
                        return;
                    }
                    callback.onError("Verification failed (" + response.code() + "). Check the code and try again.");
                    return;
                }
                sessionManager.saveSession(response.body());
                if (!sessionManager.isLoggedIn()) {
                    callback.onError("Verification succeeded but no session was returned.");
                    return;
                }
                ensureProfileExists("Email verified. Account is now active.", callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Verification request failed: " + throwable.getMessage());
            }
        });
    }

    public void sendPasswordResetOtp(String email, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("email", email == null ? "" : email.trim());
        body.addProperty("type", "recovery");
        body.addProperty("create_user", false);
        authApi().sendEmailOtp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError(authError("Reset code request", response));
                    return;
                }
                callback.onSuccess("Reset code sent. Check your email.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Could not send reset code: " + throwable.getMessage());
            }
        });
    }

    public void verifyRecoveryCode(String email, String code, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("type", "recovery");
        body.addProperty("email", email);
        body.addProperty("token", code);
        authApi().verifyEmailOtp(apiKey(), bearerAnon(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Invalid or expired code.");
                    return;
                }
                sessionManager.saveSession(response.body());
                callback.onSuccess("Code verified.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Verification failed: " + throwable.getMessage());
            }
        });
    }

    public void sendPasswordReset(String email, String redirectTo, AuthCallback callback) {
        sendPasswordResetOtp(email, callback);
    }

    public void resetPasswordWithAccessToken(String accessToken, String newPassword, AuthCallback callback) {
        if (!CloudConfig.isSupabaseEnabled()) {
            callback.onError("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
            return;
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("Missing recovery access token.");
            return;
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            callback.onError("Password must be at least 6 characters.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("password", newPassword.trim());
        authApi().updateUserPassword(
                apiKey(),
                "Bearer " + accessToken.trim(),
                body
        ).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Reset failed (" + response.code() + ").");
                    return;
                }
                callback.onSuccess("Password updated. Please log in.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Reset failed: " + throwable.getMessage());
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

    private static String normalizeVerifyType(String type) {
        if (type == null) {
            return "signup";
        }
        String normalized = type.trim().toLowerCase();
        if ("email".equals(normalized) || "signup".equals(normalized)) {
            return normalized;
        }
        return "signup";
    }

    private static String authError(String action, Response<?> response) {
        if (response != null && response.code() == 429) {
            String retryAfter = response.headers().get("Retry-After");
            if (retryAfter != null && !retryAfter.trim().isEmpty()) {
                return action + " rate limit exceeded. Wait " + retryAfter.trim() + " seconds, then try again.";
            }
            return action + " rate limit exceeded. Wait about 60 seconds, then try again.";
        }
        return action + " failed (" + (response == null ? "?" : response.code()) + "): " + extractErrorBody(response);
    }

    private static String extractErrorBody(Response<?> response) {
        if (response == null || response.errorBody() == null) {
            return "unknown error";
        }
        try {
            String raw = response.errorBody().string();
            if (raw == null || raw.trim().isEmpty()) {
                return "unknown error";
            }
            return raw.trim();
        } catch (IOException e) {
            return "unknown error";
        }
    }

    private void sendSignupOtp(String email, AuthCallback callback) {
        JsonObject otpBody = new JsonObject();
        otpBody.addProperty("email", email == null ? "" : email.trim());
        otpBody.addProperty("create_user", false);
        authApi().sendEmailOtp(apiKey(), bearerAnon(), otpBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError(authError("Verification code request", response));
                    return;
                }
                callback.onSuccess("Verification code sent. Check your email.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Could not send verification code: " + throwable.getMessage());
            }
        });
    }

    private void updatePasswordForCurrentSession(String password, AuthCallback callback) {
        String token = sessionManager.getAccessToken();
        if (token == null || token.trim().isEmpty()) {
            callback.onError("Missing session token after verification.");
            return;
        }
        String normalizedPassword = password == null ? "" : password.trim();
        if (normalizedPassword.length() < 6) {
            callback.onError("Password must be at least 6 characters.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("password", normalizedPassword);
        authApi().updateUserPassword(apiKey(), "Bearer " + token.trim(), body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Failed to finalize account password (" + response.code() + ").");
                    return;
                }
                callback.onSuccess("Password set.");
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable throwable) {
                callback.onError("Failed to finalize account password: " + throwable.getMessage());
            }
        });
    }

    private void ensureProfileExists(String successMessage, AuthCallback callback) {
        String userId = sessionManager.getUserId();
        String email = sessionManager.getEmail();
        if (userId.isEmpty() || email.isEmpty()) {
            callback.onSuccess(successMessage);
            return;
        }
        String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        upsertProfile(displayName, "", new AuthCallback() {
            @Override
            public void onSuccess(String message) {
                callback.onSuccess(successMessage);
            }

            @Override
            public void onError(String message) {
                callback.onSuccess(successMessage + " (profile sync pending)");
            }
        });
    }
}