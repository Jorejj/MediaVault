package com.example.mediavault.cloud.auth;

import android.content.Context;
import android.content.Intent;

public final class CloudAuthNavigator {
    private CloudAuthNavigator() {}

    public static void openForcedLogin(Context context) {
        Intent intent = new Intent(context, SupabaseAuthActivity.class);
        intent.putExtra(SupabaseAuthActivity.EXTRA_FORCE_LOGIN, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
