package com.example.mediavault.cloud.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.R;
import com.example.mediavault.cloud.CloudConfig;
import com.example.mediavault.utils.ThemeUtils;
import com.example.mediavault.widget.ToastUtils;
import com.google.gson.JsonObject;

public class CloudProfileActivity extends AppCompatActivity {
    private SupabaseAuthRepository authRepository;
    private TextView textEmail;
    private TextView textUserId;
    private TextView textRole;
    private EditText editDisplayName;
    private EditText editAvatarUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_profile);

        authRepository = new SupabaseAuthRepository(this);

        textEmail = findViewById(R.id.text_profile_email);
        textUserId = findViewById(R.id.text_profile_user_id);
        textRole = findViewById(R.id.text_profile_role);
        editDisplayName = findViewById(R.id.edit_profile_display_name);
        editAvatarUrl = findViewById(R.id.edit_profile_avatar_url);

        Button btnSave = findViewById(R.id.btn_profile_save);
        Button btnLogout = findViewById(R.id.btn_profile_logout);
        Button btnBack = findViewById(R.id.btn_profile_back);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveProfile());
        btnLogout.setOnClickListener(v -> authRepository.signOut(new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                ToastUtils.showCustomToast(CloudProfileActivity.this, message);
                if (CloudConfig.isSupabaseEnabled()) {
                    CloudAuthNavigator.openForcedLogin(CloudProfileActivity.this);
                } else {
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(CloudProfileActivity.this, message);
            }
        }));

        loadProfile();
    }

    private void loadProfile() {
        SupabaseSessionManager session = authRepository.getSessionManager();
        if (!session.isLoggedIn()) {
            ToastUtils.showCustomToast(this, "Please login first.");
            finish();
            return;
        }
        textEmail.setText(session.getEmail());
        textUserId.setText(session.getUserId());

        authRepository.fetchProfile(new SupabaseAuthRepository.ProfileCallback() {
            @Override
            public void onSuccess(JsonObject profile) {
                String role = profile.has("role") && !profile.get("role").isJsonNull() ? profile.get("role").getAsString() : "user";
                textRole.setText(role);
                if (profile.has("display_name") && !profile.get("display_name").isJsonNull()) {
                    editDisplayName.setText(profile.get("display_name").getAsString());
                }
                if (profile.has("avatar_url") && !profile.get("avatar_url").isJsonNull()) {
                    editAvatarUrl.setText(profile.get("avatar_url").getAsString());
                }
            }

            @Override
            public void onError(String message) {
                textRole.setText("user");
                ToastUtils.showCustomToast(CloudProfileActivity.this, message);
            }
        });
    }

    private void saveProfile() {
        String displayName = text(editDisplayName);
        String avatarUrl = text(editAvatarUrl);
        authRepository.upsertProfile(displayName, avatarUrl, new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                ToastUtils.showCustomToast(CloudProfileActivity.this, message);
                loadProfile();
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(CloudProfileActivity.this, message);
            }
        });
    }

    private static String text(EditText editText) {
        CharSequence value = editText.getText();
        return value == null ? "" : value.toString().trim();
    }
}
