package com.example.mediavault.cloud;

import com.example.mediavault.BuildConfig;

public final class CloudConfig {
    private CloudConfig() {}

    public static String getSupabaseUrl() {
        return BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
    }

    public static String getSupabaseAnonKey() {
        return BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();
    }

    public static boolean isSupabaseConfigured() {
        return !getSupabaseUrl().isEmpty() && !getSupabaseAnonKey().isEmpty();
    }

    public static boolean isSupabaseEnabled() {
        return BuildConfig.SUPABASE_ENABLED && isSupabaseConfigured();
    }
}
