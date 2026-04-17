package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.R;
import com.example.mediavault.utils.ThemeUtils;
import com.example.mediavault.widget.ToastUtils;

public class SupabasePasswordResetActivity extends AppCompatActivity {
    public static final String EXTRA_EMAIL = "extra_email";

    private SupabaseAuthRepository authRepository;
    private TextView textTitle;
    private TextView textStatus;
    private LinearLayout layoutStepVerify;
    private LinearLayout layoutStepReset;
    
    private EditText editCode;
    private EditText editNewPassword;
    private EditText editConfirmPassword;
    
    private Button btnVerifyCode;
    private Button btnResetSubmit;
    private Button btnBack;

    private String userEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supabase_password_reset);

        authRepository = new SupabaseAuthRepository(this);
        userEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        if (userEmail == null) userEmail = "";

        textTitle = findViewById(R.id.text_reset_title);
        textStatus = findViewById(R.id.text_reset_status);
        layoutStepVerify = findViewById(R.id.layout_step_verify);
        layoutStepReset = findViewById(R.id.layout_step_reset);
        
        editCode = findViewById(R.id.edit_reset_code);
        editNewPassword = findViewById(R.id.edit_reset_password);
        editConfirmPassword = findViewById(R.id.edit_reset_password_confirm);
        
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        btnResetSubmit = findViewById(R.id.btn_reset_submit);
        btnBack = findViewById(R.id.btn_reset_back_to_login);

        textStatus.setText("Enter the 6-digit code sent to " + userEmail);

        btnVerifyCode.setOnClickListener(v -> verifyCode());
        btnResetSubmit.setOnClickListener(v -> submitReset());
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, SupabaseAuthActivity.class));
            finish();
        });
    }

    private void verifyCode() {
        String code = text(editCode);
        if (code.length() != 6) {
            ToastUtils.showCustomToast(this, "Please enter the 6-digit code.");
            return;
        }

        authRepository.verifyRecoveryCode(userEmail, code, new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                showResetStep();
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(SupabasePasswordResetActivity.this, message);
            }
        });
    }

    private void showResetStep() {
        textTitle.setText("Set New Password");
        textStatus.setText("Choose a strong password for your account.");
        layoutStepVerify.setVisibility(View.GONE);
        layoutStepReset.setVisibility(View.VISIBLE);
        
        // Update back button constraints if needed, or just let it stay at the bottom
    }

    private void submitReset() {
        String password = text(editNewPassword);
        String confirm = text(editConfirmPassword);
        
        if (password.length() < 6) {
            ToastUtils.showCustomToast(this, "Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            ToastUtils.showCustomToast(this, "Passwords do not match.");
            return;
        }

        // After verification, the session is already saved in SessionManager
        String accessToken = authRepository.getSessionManager().getAccessToken();
        
        authRepository.resetPasswordWithAccessToken(accessToken, password, new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                ToastUtils.showCustomToast(SupabasePasswordResetActivity.this, "Password updated successfully.");
                authRepository.signOutLocal(); // Force login with new password
                startActivity(new Intent(SupabasePasswordResetActivity.this, SupabaseAuthActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                ToastUtils.showCustomToast(SupabasePasswordResetActivity.this, message);
            }
        });
    }

    private static String text(EditText editText) {
        CharSequence value = editText.getText();
        return value == null ? "" : value.toString().trim();
    }
}