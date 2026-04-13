package com.example.mediavault.cloud.auth;

import android.content.Context;

import com.example.mediavault.cloud.CloudConfig;

public final class CloudAccessGate {
    private CloudAccessGate() {}

    public static boolean requiresCloudLogin(Context context) {
        if (!CloudConfig.isSupabaseEnabled()) {
            return false;
        }
        SupabaseSessionManager sessionManager = new SupabaseSessionManager(context);
        return !sessionManager.isLoggedIn();
    }
}
