package com.example.mediavault.utils;

import java.util.Locale;

public final class ProgressValueUtils {
    private ProgressValueUtils() {}

    public static boolean isMinutesUnit(String unit) {
        return unit != null && "Minutes".equalsIgnoreCase(unit.trim());
    }

    public static float normalizeForUnit(float value, String unit) {
        float safe = Math.max(0f, value);
        if (isMinutesUnit(unit)) {
            return Math.round(safe * 10f) / 10f;
        }
        return (float) Math.floor(safe);
    }

    public static String formatForDisplay(float value, String unit) {
        float normalized = normalizeForUnit(value, unit);
        if (isMinutesUnit(unit)) {
            return String.format(Locale.getDefault(), "%.1f", normalized);
        }
        return String.valueOf((int) normalized);
    }
}
