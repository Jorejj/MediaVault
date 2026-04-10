package com.example.mediavault.utils;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.core.app.NotificationCompat;

import com.example.mediavault.R;
import com.example.mediavault.service.MediaMonitorService;

import java.util.List;

/**
 * Helper class to check and manage accessibility service status.
 * Handles detection, user prompts, and deep links to settings.
 */
public class AccessibilityServiceHelper {
    private static final String TAG = "AccessibilityHelper";
    private static final String NOTIFICATION_CHANNEL_ID = "service_status_channel";
    private static final int NOTIFICATION_ID = 9001;

    /**
     * Check if MediaMonitor accessibility service is enabled
     */
    public static boolean isServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        String targetServiceName = MediaMonitorService.class.getName();
        String packageName = context.getPackageName();

        for (AccessibilityServiceInfo service : enabledServices) {
            String serviceId = service.getId();
            if (serviceId != null && serviceId.contains(packageName) && serviceId.contains("MediaMonitorService")) {
                Log.d(TAG, "Service is enabled: " + serviceId);
                return true;
            }
        }

        // Alternative check using Settings.Secure
        return isAccessibilityServiceEnabledAlternative(context);
    }

    /**
     * Alternative method using Settings.Secure
     */
    private static boolean isAccessibilityServiceEnabledAlternative(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (TextUtils.isEmpty(enabledServices)) return false;

        ComponentName expectedComponent = new ComponentName(context, MediaMonitorService.class);
        String expectedFlattened = expectedComponent.flattenToString();

        // Check both formats (with and without class name)
        return enabledServices.contains(expectedFlattened) || 
               enabledServices.contains(context.getPackageName() + "/");
    }

    /**
     * Check if overlay permission is granted
     */
    public static boolean isOverlayPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /**
     * Open accessibility settings directly
     */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Open overlay permission settings
     */
    public static void openOverlaySettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * Show notification reminding user to enable service
     */
    public static void showServiceDisabledNotification(Context context) {
        createNotificationChannel(context);

        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vault)
                .setContentTitle("MediaVault Tracker Disabled")
                .setContentText("Tap to enable the tracking service")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("The media tracking service was disabled. Tap here to re-enable it in Accessibility settings."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_vault, "Enable Now", pendingIntent);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    /**
     * Cancel the service disabled notification
     */
    public static void cancelServiceDisabledNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    /**
     * Create notification channel for service status
     */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Service Status",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications about tracking service status");

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Get a human-readable status message
     */
    public static String getStatusMessage(Context context) {
        boolean serviceEnabled = isServiceEnabled(context);
        boolean overlayEnabled = isOverlayPermissionGranted(context);

        if (serviceEnabled && overlayEnabled) {
            return "✅ All permissions granted - Tracking active";
        } else if (!serviceEnabled && !overlayEnabled) {
            return "❌ Accessibility and Overlay permissions needed";
        } else if (!serviceEnabled) {
            return "⚠️ Accessibility service disabled - Tap to enable";
        } else {
            return "⚠️ Overlay permission needed for floating widget";
        }
    }

    /**
     * Check all permissions and return detailed status
     */
    public static ServiceStatus getDetailedStatus(Context context) {
        return new ServiceStatus(
                isServiceEnabled(context),
                isOverlayPermissionGranted(context)
        );
    }

    /**
     * Status holder class
     */
    public static class ServiceStatus {
        public final boolean accessibilityEnabled;
        public final boolean overlayEnabled;
        public final boolean fullyOperational;

        public ServiceStatus(boolean accessibilityEnabled, boolean overlayEnabled) {
            this.accessibilityEnabled = accessibilityEnabled;
            this.overlayEnabled = overlayEnabled;
            this.fullyOperational = accessibilityEnabled && overlayEnabled;
        }
    }
}
