package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.R;
import com.example.mediavault.utils.ThemeUtils;
import com.example.mediavault.widget.ToastUtils;

public class SupabaseRegisterActivity extends AppCompatActivity {
    private static final long EMAIL_COOLDOWN_MS = 60_000L;
    private static final String PREFS_NAME = "supabase_register_pending";
    private static final String KEY_PENDING_EMAIL = "pending_email";
    private static final String KEY_PENDING_PASSWORD = "pending_password";

    private SupabaseAuthRepository authRepository;
    private EditText editEmail;
    private EditText editPassword;
    private EditText editVerificationCode;
    private TextView textTitle;
    private TextView textSubtitle;
    private TextView textStatus;
    private TextView textVerifyEmailTarget;
    private View registerPanel;
    private View verifyPanel;
    private Button buttonRegister;
    private Button buttonVerifyCode;
    private TextView textBackToLoginRegister;
    private TextView textBackToLoginVerify;
    private String pendingVerificationEmail = "";
    private String pendingPassword = "";
    private boolean forceLogin;
    private long nextRegisterEmailRequestAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supabase_register);

        authRepository = new SupabaseAuthRepository(this);
        forceLogin = getIntent().getBooleanExtra(SupabaseAuthActivity.EXTRA_FORCE_LOGIN, false);

        textTitle = findViewById(R.id.text_register_title);
        textSubtitle = findViewById(R.id.text_register_subtitle);
        textStatus = findViewById(R.id.text_register_status);
        textVerifyEmailTarget = findViewById(R.id.text_verify_email_target);
        registerPanel = findViewById(R.id.layout_register_panel);
        verifyPanel = findViewById(R.id.layout_verify_panel);
        editEmail = findViewById(R.id.edit_register_email);
        editPassword = findViewById(R.id.edit_register_password);
        editVerificationCode = findViewById(R.id.edit_register_verification_code);
        buttonRegister = findViewById(R.id.btn_register_submit);
        buttonVerifyCode = findViewById(R.id.btn_register_verify_code);
        textBackToLoginRegister = findViewById(R.id.text_back_to_login_link_register);
        textBackToLoginVerify = findViewById(R.id.text_back_to_login_link_verify);

        buttonRegister.setOnClickListener(v -> attemptRegister());
        buttonVerifyCode.setOnClickListener(v -> attemptVerifyCode());
        textBackToLoginRegister.setOnClickListener(v -> finish());
        textBackToLoginVerify.setOnClickListener(v -> finish());

        loadPendingRegistration();
        applySupabaseAvailabilityState();
        showRegisterPanel();
        handleRegisterLinkIfPresent();
    }

    private void applySupabaseAvailabilityState() {
        boolean enabled = com.example.mediavault.cloud.CloudConfig.isSupabaseEnabled();
        if (!enabled) {
            textStatus.setText("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
        } else {
            textStatus.setText(getString(R.string.auto_create_account_to_get_started));
        }
    }

    private void attemptRegister() {
        long now = System.currentTimeMillis();
        if (now < nextRegisterEmailRequestAt) {
            long seconds = Math.max(1L, (nextRegisterEmailRequestAt - now + 999L) / 1000L);
            ToastUtils.showCustomToast(this, "Please wait " + seconds + "s before requesting another code.");
            return;
        }
        String email = readEmail();
        String password = readPassword();
        if (email.isEmpty() || password.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email and password are required.");
            return;
        }
        if (password.length() < 6) {
            ToastUtils.showCustomToast(this, "Password must be at least 6 characters.");
            return;
        }
        pendingVerificationEmail = email;
        pendingPassword = password;
        savePendingRegistration();
        nextRegisterEmailRequestAt = now + EMAIL_COOLDOWN_MS;
        buttonRegister.setEnabled(false);
        buttonRegister.postDelayed(() -> buttonRegister.setEnabled(true), EMAIL_COOLDOWN_MS);
        authRepository.requestRegisterOtp(email, new RegisterOtpCallback());
    }

    private void attemptVerifyCode() {
        String code = readVerificationCode();
        if (pendingVerificationEmail.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email is required.");
            return;
        }
        if (code.length() != 6) {
            ToastUtils.showCustomToast(this, "Enter the 6-digit verification code from your email.");
            return;
        }
        authRepository.verifyRegisterCodeAndActivate(
                pendingVerificationEmail,
                code,
                pendingPassword,
                new VerifyCallback()
        );
    }

    private String readEmail() {
        CharSequence text = editEmail.getText();
        return text == null ? "" : text.toString().trim();
    }

    private String readPassword() {
        CharSequence text = editPassword.getText();
        return text == null ? "" : text.toString().trim();
    }

    private String readVerificationCode() {
        CharSequence text = editVerificationCode.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void showRegisterPanel() {
        registerPanel.setVisibility(View.VISIBLE);
        verifyPanel.setVisibility(View.GONE);
        textTitle.setText(R.string.auto_create_account);
        textSubtitle.setText(R.string.auto_register_subtitle);
    }

    private void showVerifyPanel() {
        registerPanel.setVisibility(View.GONE);
        verifyPanel.setVisibility(View.VISIBLE);
        textTitle.setText(R.string.auto_verify_your_email);
        textSubtitle.setText("Tap the confirmation link in your email or enter the 6-digit code.");
        textStatus.setText("Verification email sent. Check your inbox.");
        textVerifyEmailTarget.setText(getString(R.string.auto_code_sent_to_format, pendingVerificationEmail));
        editVerificationCode.setText("");
    }

    private void handleRegisterLinkIfPresent() {
        Uri data = getIntent() == null ? null : getIntent().getData();
        String tokenHash = getParam(data, "token_hash");
        if (TextUtils.isEmpty(tokenHash)) {
            return;
        }
        if (TextUtils.isEmpty(pendingPassword)) {
            textStatus.setText("Open register first and set your password before confirming by email link.");
            showVerifyPanel();
            return;
        }
        String type = getParam(data, "type");
        showVerifyPanel();
        textStatus.setText("Verifying your email...");
        authRepository.verifyRegisterLinkAndActivate(tokenHash, type, pendingPassword, new VerifyCallback());
    }

    private static String getParam(Uri data, String key) {
        if (data == null) {
            return "";
        }
        String query = data.getQueryParameter(key);
        if (!TextUtils.isEmpty(query)) {
            return query;
        }
        String fragment = data.getFragment();
        if (TextUtils.isEmpty(fragment)) {
            return "";
        }
        String[] pairs = fragment.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return kv[1];
            }
        }
        return "";
    }

    private void savePendingRegistration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PENDING_EMAIL, pendingVerificationEmail)
                .putString(KEY_PENDING_PASSWORD, pendingPassword)
                .apply();
    }

    private void loadPendingRegistration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        pendingVerificationEmail = prefs.getString(KEY_PENDING_EMAIL, "");
        pendingPassword = prefs.getString(KEY_PENDING_PASSWORD, "");
    }

    private void clearPendingRegistration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private void openSuccessPage() {
        Intent intent = new Intent(this, SupabaseRegisterSuccessActivity.class);
        intent.putExtra(SupabaseAuthActivity.EXTRA_FORCE_LOGIN, forceLogin);
        startActivity(intent);
        finish();
    }

    private class RegisterOtpCallback implements SupabaseAuthRepository.AuthCallback {
        @Override
        public void onSuccess(String message) {
            authRepository.signOutLocal();
            showVerifyPanel();
            ToastUtils.showCustomToast(SupabaseRegisterActivity.this, message);
        }

        @Override
        public void onError(String message) {
            ToastUtils.showCustomToast(SupabaseRegisterActivity.this, message);
        }
    }

    private class VerifyCallback implements SupabaseAuthRepository.AuthCallback {
        @Override
        public void onSuccess(String message) {
            authRepository.signOutLocal();
            ToastUtils.showCustomToast(SupabaseRegisterActivity.this, message);
            clearPendingRegistration();
            openSuccessPage();
        }

        @Override
        public void onError(String message) {
            ToastUtils.showCustomToast(SupabaseRegisterActivity.this, message);
        }
    }

    @Override
    public void onBackPressed() {
        if (verifyPanel.getVisibility() == View.VISIBLE) {
            showRegisterPanel();
            return;
        }
        super.onBackPressed();
    }
}
