package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.MainActivity;
import com.example.mediavault.R;
import com.example.mediavault.cloud.CloudConfig;
import com.example.mediavault.cloud.sync.SupabaseMediaSyncManager;
import com.example.mediavault.utils.ThemeUtils;
import com.example.mediavault.widget.ToastUtils;

public class SupabaseAuthActivity extends AppCompatActivity {
    public static final String EXTRA_FORCE_LOGIN = "extra_force_login";

    private SupabaseAuthRepository authRepository;
    private EditText editEmail;
    private EditText editPassword;
    private TextView textSessionStatus;
    private Button buttonLogin;
    private Button buttonSignup;
    private Button buttonForgot;
    private Button buttonLogout;
    private boolean forceLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supabase_auth);

        authRepository = new SupabaseAuthRepository(this);
        forceLogin = getIntent().getBooleanExtra(EXTRA_FORCE_LOGIN, false);

        editEmail = findViewById(R.id.edit_cloud_email);
        editPassword = findViewById(R.id.edit_cloud_password);
        textSessionStatus = findViewById(R.id.text_cloud_session_status);
        buttonLogin = findViewById(R.id.btn_cloud_login);
        buttonSignup = findViewById(R.id.btn_cloud_signup);
        buttonForgot = findViewById(R.id.btn_cloud_forgot);
        buttonLogout = findViewById(R.id.btn_cloud_logout);

        Button buttonClose = findViewById(R.id.btn_cloud_close);
        if (forceLogin) {
            buttonClose.setText(R.string.auto_exit);
            buttonClose.setOnClickListener(v -> finishAffinity());
        } else {
            buttonClose.setOnClickListener(v -> finish());
        }

        buttonLogin.setOnClickListener(v -> attemptLogin());
        buttonSignup.setOnClickListener(v -> attemptSignup());
        buttonForgot.setOnClickListener(v -> attemptForgotPassword());
        buttonLogout.setOnClickListener(v -> authRepository.signOut(new UiAuthCallback()));

        applySupabaseAvailabilityState();
        refreshSessionStatus();
    }

    private void applySupabaseAvailabilityState() {
        boolean enabled = CloudConfig.isSupabaseEnabled();
        buttonLogin.setEnabled(enabled);
        buttonSignup.setEnabled(enabled);
        buttonForgot.setEnabled(enabled);
        buttonLogout.setEnabled(true);
        if (!enabled) {
            textSessionStatus.setText("Supabase disabled. Configure local.properties and set SUPABASE_ENABLED=true.");
        } else if (forceLogin) {
            textSessionStatus.setText("Login is required before entering the app.");
        }
    }

    private void refreshSessionStatus() {
        SupabaseSessionManager session = authRepository.getSessionManager();
        if (session.isLoggedIn()) {
            textSessionStatus.setText("Logged in as: " + session.getEmail() + "\nUser ID: " + session.getUserId());
        } else {
            if (CloudConfig.isSupabaseEnabled()) {
                textSessionStatus.setText("Not logged in.");
            }
        }
    }

    private void attemptLogin() {
        String email = readEmail();
        String password = readPassword();
        if (email.isEmpty() || password.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email and password are required.");
            return;
        }
        authRepository.signIn(email, password, new UiAuthCallback());
    }

    private void attemptSignup() {
        String email = readEmail();
        String password = readPassword();
        if (email.isEmpty() || password.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email and password are required.");
            return;
        }
        authRepository.signUp(email, password, new UiAuthCallback());
    }

    private void attemptForgotPassword() {
        String email = readEmail();
        if (email.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email is required.");
            return;
        }
        authRepository.sendPasswordReset(email, "", new UiAuthCallback());
    }

    private String readEmail() {
        CharSequence text = editEmail.getText();
        return text == null ? "" : text.toString().trim();
    }

    private String readPassword() {
        CharSequence text = editPassword.getText();
        return text == null ? "" : text.toString().trim();
    }

    private class UiAuthCallback implements SupabaseAuthRepository.AuthCallback {
        @Override
        public void onSuccess(String message) {
            refreshSessionStatus();
            SupabaseMediaSyncManager.syncAllFromLocalAsync(SupabaseAuthActivity.this);
            ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
            if (forceLogin) {
                Intent intent = new Intent(SupabaseAuthActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        }

        @Override
        public void onError(String message) {
            ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
        }
    }

    @Override
    public void onBackPressed() {
        if (forceLogin) {
            finishAffinity();
            return;
        }
        super.onBackPressed();
    }
}
