package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
    private static final long EMAIL_COOLDOWN_MS = 60_000L;

    private SupabaseAuthRepository authRepository;
    private EditText editEmail;
    private EditText editPassword;
    private TextView textSessionStatus;
    private TextView textRegisterLink;
    private View dividerAuthActions;
    private Button buttonLogin;
    private Button buttonForgot;
    private Button buttonLogout;
    private Button buttonClose;
    private boolean forceLogin;
    private long nextForgotRequestAt = 0L;

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
        textRegisterLink = findViewById(R.id.text_register_link);
        dividerAuthActions = findViewById(R.id.divider_auth_actions);
        buttonLogin = findViewById(R.id.btn_cloud_login);
        buttonForgot = findViewById(R.id.btn_cloud_forgot);
        buttonLogout = findViewById(R.id.btn_cloud_logout);
        buttonClose = findViewById(R.id.btn_cloud_close);

        if (forceLogin) {
            buttonClose.setText(R.string.auto_exit);
            buttonClose.setOnClickListener(v -> finishAffinity());
        } else {
            buttonClose.setOnClickListener(v -> finish());
        }

        buttonLogin.setOnClickListener(v -> attemptLogin());
        buttonForgot.setOnClickListener(v -> attemptForgotPassword());
        buttonLogout.setOnClickListener(v -> attemptLogout());
        textRegisterLink.setOnClickListener(v -> openRegisterPage());

        applySupabaseAvailabilityState();
        refreshSessionStatus();
    }

    private void applySupabaseAvailabilityState() {
        boolean enabled = CloudConfig.isSupabaseEnabled();
        buttonLogout.setEnabled(true);
        if (!enabled) {
            textSessionStatus.setText("Supabase is currently disabled. Configure SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_ENABLED=true in local.properties.");
        } else if (forceLogin) {
            textSessionStatus.setText("Login is required before entering the app.");
        }
    }

    private void refreshSessionStatus() {
        SupabaseSessionManager session = authRepository.getSessionManager();
        boolean loggedIn = session.isLoggedIn();
        if (loggedIn) {
            textSessionStatus.setText("Logged in as: " + session.getEmail() + "\nUser ID: " + session.getUserId());
        } else if (CloudConfig.isSupabaseEnabled()) {
            textSessionStatus.setText("Not logged in.");
        }
        buttonLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        boolean showClose = loggedIn && !forceLogin;
        buttonClose.setVisibility(showClose ? View.VISIBLE : View.GONE);
        dividerAuthActions.setVisibility((loggedIn || showClose) ? View.VISIBLE : View.GONE);
    }

    private void attemptLogin() {
        String email = readEmail();
        String password = readPassword();
        if (email.isEmpty() || password.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email and password are required.");
            return;
        }
        authRepository.signIn(email, password, new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                refreshSessionStatus();
                SupabaseMediaSyncManager.bootstrapCloudPrimaryAsync(SupabaseAuthActivity.this);
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
        });
    }

    private void attemptForgotPassword() {
        long now = System.currentTimeMillis();
        if (now < nextForgotRequestAt) {
            long seconds = Math.max(1L, (nextForgotRequestAt - now + 999L) / 1000L);
            ToastUtils.showCustomToast(this, "Please wait " + seconds + "s before requesting another reset email.");
            return;
        }
        String email = readEmail();
        if (email.isEmpty()) {
            ToastUtils.showCustomToast(this, "Email is required.");
            return;
        }
        nextForgotRequestAt = now + EMAIL_COOLDOWN_MS;
        buttonForgot.setEnabled(false);
        buttonForgot.postDelayed(() -> buttonForgot.setEnabled(true), EMAIL_COOLDOWN_MS);
        authRepository.sendPasswordReset(email, "mediavault://auth/reset", new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
            }
        });
    }

    private void attemptLogout() {
        authRepository.signOut(new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                refreshSessionStatus();
                ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
                if (!forceLogin) {
                    Intent intent = new Intent(SupabaseAuthActivity.this, SupabaseAuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(SupabaseAuthActivity.this, message);
            }
        });
    }

    private void openRegisterPage() {
        Intent intent = new Intent(this, SupabaseRegisterActivity.class);
        intent.putExtra(EXTRA_FORCE_LOGIN, forceLogin);
        startActivity(intent);
    }

    private String readEmail() {
        CharSequence text = editEmail.getText();
        return text == null ? "" : text.toString().trim();
    }

    private String readPassword() {
        CharSequence text = editPassword.getText();
        return text == null ? "" : text.toString().trim();
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
