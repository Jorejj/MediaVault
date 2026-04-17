package com.example.mediavault.cloud.auth;

import android.content.Context;

public final class CloudAccessGate {
    private CloudAccessGate() {}

    public static boolean requiresCloudLogin(Context context) {
        SupabaseSessionManager sessionManager = new SupabaseSessionManager(context);
        if (!sessionManager.shouldKeepSignedIn()) {
            sessionManager.clearSession();
            return true;
        }
        return !sessionManager.isLoggedIn();
    }
}
