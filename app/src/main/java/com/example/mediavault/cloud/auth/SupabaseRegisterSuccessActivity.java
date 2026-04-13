package com.example.mediavault.cloud.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.R;
import com.example.mediavault.utils.ThemeUtils;

public class SupabaseRegisterSuccessActivity extends AppCompatActivity {
    private boolean forceLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supabase_register_success);

        forceLogin = getIntent().getBooleanExtra(SupabaseAuthActivity.EXTRA_FORCE_LOGIN, false);

        Button buttonProceedLogin = findViewById(R.id.btn_proceed_login);
        buttonProceedLogin.setOnClickListener(v -> proceedToLogin());
    }

    private void proceedToLogin() {
        Intent intent = new Intent(this, SupabaseAuthActivity.class);
        intent.putExtra(SupabaseAuthActivity.EXTRA_FORCE_LOGIN, forceLogin);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        proceedToLogin();
    }
}
