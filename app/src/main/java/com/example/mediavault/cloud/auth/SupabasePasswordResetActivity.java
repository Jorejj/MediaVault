package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.R;
import com.example.mediavault.utils.ThemeUtils;
import com.example.mediavault.widget.ToastUtils;

public class SupabasePasswordResetActivity extends AppCompatActivity {
    private SupabaseAuthRepository authRepository;
    private TextView textStatus;
    private EditText editNewPassword;
    private EditText editConfirmPassword;
    private String accessToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supabase_password_reset);

        authRepository = new SupabaseAuthRepository(this);
        textStatus = findViewById(R.id.text_reset_status);
        editNewPassword = findViewById(R.id.edit_reset_password);
        editConfirmPassword = findViewById(R.id.edit_reset_password_confirm);
        Button buttonSubmit = findViewById(R.id.btn_reset_submit);
        Button buttonBack = findViewById(R.id.btn_reset_back_to_login);

        accessToken = extractAccessToken(getIntent() == null ? null : getIntent().getData());
        if (TextUtils.isEmpty(accessToken)) {
            textStatus.setText(getString(R.string.auto_reset_link_invalid));
            buttonSubmit.setEnabled(false);
        } else {
            textStatus.setText(getString(R.string.auto_reset_link_valid));
        }

        buttonSubmit.setOnClickListener(v -> submitReset());
        buttonBack.setOnClickListener(v -> {
            startActivity(new Intent(this, SupabaseAuthActivity.class));
            finish();
        });
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
        authRepository.resetPasswordWithAccessToken(accessToken, password, new SupabaseAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                ToastUtils.showCustomToast(SupabasePasswordResetActivity.this, message);
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

    private static String extractAccessToken(Uri uri) {
        if (uri == null) return "";
        String queryToken = uri.getQueryParameter("access_token");
        if (!TextUtils.isEmpty(queryToken)) return queryToken;
        String fragment = uri.getFragment();
        if (TextUtils.isEmpty(fragment)) return "";
        String[] pairs = fragment.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "access_token".equals(kv[0])) {
                return kv[1];
            }
        }
        return "";
    }
}
