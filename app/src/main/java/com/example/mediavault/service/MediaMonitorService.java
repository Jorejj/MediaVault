package com.example.mediavault.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.content.ContextCompat;
import com.example.mediavault.R;
import com.example.mediavault.utils.DefaultAppWhitelist;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

public class MediaMonitorService extends AccessibilityService {
    private static final String TAG = "MediaMonitorService";
    public static final String ACTION_MONITORED_APPS_CHANGED = "com.example.mediavault.MONITORED_APPS_CHANGED";
    public static final String ACTION_MANUAL_SCAN_REQUEST = "com.example.mediavault.MANUAL_SCAN_REQUEST";
    public static final String ACTION_WIDGET_SETTINGS_CHANGED = "com.example.mediavault.WIDGET_SETTINGS_CHANGED";
    public static final String ACTION_DISMISS_DETECTED_TITLE = "com.example.mediavault.DISMISS_DETECTED_TITLE";
    public static final String EXTRA_DETECTED_TITLE = "extra_detected_title";
    public static final String EXTRA_SOURCE_PACKAGE = "extra_source_package";
    private static final String PREFS_NAME = "monitored_apps_prefs";
    private static final String KEY_PACKAGE_SET = "monitored_packages";
    private static final String KEY_BILIBILI_DEFAULT_MIGRATED = "bilibili_default_migrated";
    private static final String KEY_YOUTUBE_DEFAULT_MIGRATED = "youtube_default_migrated";
    private static final String KEY_CORE_DEFAULTS_MIGRATED = "core_defaults_migrated";
    private static final String SETTINGS_PREFS = "Settings";
    private static final String KEY_TRACKING_PAUSED_UNTIL_MS = "tracking_paused_until_ms";
    private static final long MANUAL_PROMPT_WINDOW_MS = 5_000L;
    private static final Pattern MEET_OVERLAY_NOISE_PATTERN = Pattern.compile(
            "(?i).*(google\\s*meet|presenting|sharing\\s+your\\s+screen|return\\s+to\\s+call|stop\\s+sharing|tap\\s+to\\s+return|participants?|microphone|camera\\s+off|muted).*"
    );
    private static final Pattern OVERLAY_PARTICIPANT_BADGE_PATTERN = Pattern.compile("^\\+\\d{1,3}$");

    private static final Set<String> DEFAULT_MONITORED_PACKAGES = DefaultAppWhitelist.get();
    
    private static final int MSG_DEBOUNCE_SCAN = 100;
    private static final long DEBOUNCE_DELAY_MS = 800; 

    private final Handler debounceHandler = new Handler(Looper.getMainLooper(), msg -> {
        if (msg.what == MSG_DEBOUNCE_SCAN) {
            scanActiveWindow();
            return true;
        }
        return false;
    });

    private Set<String> activePackages = new HashSet<>(DEFAULT_MONITORED_PACKAGES);
    private MediaMatcher matcher;
    private volatile String lastObservedPackage = "";
    private volatile long manualPromptRequestUntilMs = 0L;

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (ACTION_MONITORED_APPS_CHANGED.equals(intent.getAction())) {
                updateServiceInfo();
            } else if (ACTION_MANUAL_SCAN_REQUEST.equals(intent.getAction())) {
                manualPromptRequestUntilMs = System.currentTimeMillis() + MANUAL_PROMPT_WINDOW_MS;
                debounceHandler.removeMessages(MSG_DEBOUNCE_SCAN);
                debounceHandler.sendEmptyMessage(MSG_DEBOUNCE_SCAN);
                debounceHandler.sendEmptyMessageDelayed(MSG_DEBOUNCE_SCAN, 450);
            } else if (ACTION_WIDGET_SETTINGS_CHANGED.equals(intent.getAction())) {
                syncWidgetState();
            } else if (ACTION_DISMISS_DETECTED_TITLE.equals(intent.getAction())) {
                String dismissedTitle = intent.getStringExtra(EXTRA_DETECTED_TITLE);
                String sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE);
                if (matcher == null) {
                    matcher = new MediaMatcher(context.getApplicationContext());
                }
                matcher.dismissDetectedTitle(dismissedTitle, sourcePackage, 5 * 60_000L);
                manualPromptRequestUntilMs = 0L;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_MONITORED_APPS_CHANGED);
        filter.addAction(ACTION_MANUAL_SCAN_REQUEST);
        filter.addAction(ACTION_WIDGET_SETTINGS_CHANGED);
        filter.addAction(ACTION_DISMISS_DETECTED_TITLE);
        ContextCompat.registerReceiver(this, configReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(configReceiver);
        debounceHandler.removeCallbacksAndMessages(null);
        
        // Hide floating widget
        FloatingWidgetManager.getInstance(this).hide();
        FloatingAssistantManager.getInstance(this).destroyAll();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        updateServiceInfo();
        Log.i(TAG, "Service Connected: Monitoring " + activePackages.size() + " apps.");
        Log.d(TAG, "Active packages: " + activePackages.toString());
        
        // Verify the service info was applied
        AccessibilityServiceInfo verifyInfo = getServiceInfo();
        if (verifyInfo != null && verifyInfo.packageNames != null) {
            Log.d(TAG, "Service filter applied: " + Arrays.toString(verifyInfo.packageNames));
        } else {
            Log.w(TAG, "Service filter is NULL - listening to ALL apps");
        }
        
        syncWidgetState();
    }

    private void updateServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> updatedSet = prefs.getStringSet(KEY_PACKAGE_SET, DEFAULT_MONITORED_PACKAGES);
        
        // Ensure we have a fresh HashSet for O(1) lookups
        activePackages = updatedSet != null ? new HashSet<>(updatedSet) : new HashSet<>(DEFAULT_MONITORED_PACKAGES);
        if (!prefs.getBoolean(KEY_BILIBILI_DEFAULT_MIGRATED, false)) {
            activePackages.addAll(DefaultAppWhitelist.getBilibiliPackages());
            prefs.edit()
                    .putStringSet(KEY_PACKAGE_SET, new HashSet<>(activePackages))
                    .putBoolean(KEY_BILIBILI_DEFAULT_MIGRATED, true)
                    .apply();
        }
        if (!prefs.getBoolean(KEY_YOUTUBE_DEFAULT_MIGRATED, false)) {
            activePackages.addAll(DefaultAppWhitelist.getYoutubePackages());
            prefs.edit()
                    .putStringSet(KEY_PACKAGE_SET, new HashSet<>(activePackages))
                    .putBoolean(KEY_YOUTUBE_DEFAULT_MIGRATED, true)
                    .apply();
        }
        if (!prefs.getBoolean(KEY_CORE_DEFAULTS_MIGRATED, false)) {
            activePackages.addAll(DefaultAppWhitelist.getCoreRecommendedPackages());
            prefs.edit()
                    .putStringSet(KEY_PACKAGE_SET, new HashSet<>(activePackages))
                    .putBoolean(KEY_CORE_DEFAULTS_MIGRATED, true)
                    .apply();
        }

        if (activePackages.isEmpty()) {
            info.packageNames = new String[]{"com.example.mediavault.dummy"}; 
        } else {
            info.packageNames = activePackages.toArray(new String[0]);
        }

        setServiceInfo(info);
    }

    private void syncWidgetState() {
        SharedPreferences settingsPrefs = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean trackingEnabled = settingsPrefs.getBoolean("external_tracking_enabled", false);
        boolean assistantEnabled = settingsPrefs.getBoolean("floating_assistant_enabled", false);

        if (trackingEnabled && assistantEnabled) {
            FloatingWidgetManager.getInstance(this).show();
            FloatingAssistantManager.getInstance(this).ensureAnchorVisible();
        } else {
            FloatingWidgetManager.getInstance(this).hide();
            FloatingAssistantManager.getInstance(this).destroyAll();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();
        if (!packageName.isEmpty()) {
            lastObservedPackage = packageName;
        }
        
        Log.d(TAG, "Event received from: " + packageName + " type=" + event.getEventType());
        
        SharedPreferences settingsPrefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        if (!settingsPrefs.getBoolean("external_tracking_enabled", false)) {
            Log.d(TAG, "External tracking disabled in settings");
            return;
        }
        if (isTrackingPaused(settingsPrefs)) {
            return;
        }

        // 1. Package Routing
        if (activePackages == null || !activePackages.contains(packageName)) {
            Log.d(TAG, "Package not in whitelist: " + packageName);
            return;
        }

        int eventType = event.getEventType();
        if (matcher == null) matcher = new MediaMatcher(this);

        // 2. Immersive Mode Fallback: Immediate scan on state changes (UI overlays)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            debounceHandler.removeMessages(MSG_DEBOUNCE_SCAN);
            debounceHandler.sendEmptyMessage(MSG_DEBOUNCE_SCAN);
        } 
        // 3. Throttled Scan: Prevent regex storms during active scrolling
        else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (!debounceHandler.hasMessages(MSG_DEBOUNCE_SCAN)) {
                debounceHandler.sendEmptyMessageDelayed(MSG_DEBOUNCE_SCAN, DEBOUNCE_DELAY_MS);
            }
        } else if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            matcher.onImmersiveScroll(packageName, event.getFromIndex(), event.getToIndex(), event.getItemCount());
            if (!debounceHandler.hasMessages(MSG_DEBOUNCE_SCAN)) {
                debounceHandler.sendEmptyMessageDelayed(MSG_DEBOUNCE_SCAN, 350);
            }
        } else if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                || eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            matcher.onClickSignal(packageName, event.getText());
        }
    }

    private void scanActiveWindow() {
        boolean forcePrompt = System.currentTimeMillis() < manualPromptRequestUntilMs;
        SharedPreferences settingsPrefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        if (isTrackingPaused(settingsPrefs) && !forcePrompt) {
            return;
        }
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.d(TAG, "scanActiveWindow: rootNode is null");
            return;
        }

        String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";
        boolean packageMonitored = activePackages != null && activePackages.contains(packageName);
        if ((packageName.isEmpty() || !packageMonitored)
                && lastObservedPackage != null
                && !lastObservedPackage.isEmpty()
                && (forcePrompt || (activePackages != null && activePackages.contains(lastObservedPackage)))) {
            Log.d(TAG, "scanActiveWindow using fallback observed package: " + lastObservedPackage + " (root package was: " + packageName + ")");
            packageName = lastObservedPackage;
            packageMonitored = activePackages != null && activePackages.contains(packageName);
        }
        if (packageName.isEmpty() || (!packageMonitored && !forcePrompt)) {
            Log.d(TAG, "scanActiveWindow skipped, package not monitored: " + packageName);
            rootNode.recycle();
            return;
        }
        List<String> textNodes = new ArrayList<>();
        
        try {
            scrapeNodes(rootNode, textNodes, 0, packageName);
        } catch (Exception e) {
            Log.e(TAG, "DFS Traversal Error", e);
        } finally {
            rootNode.recycle();
        }

        Log.d(TAG, "Scanned " + packageName + ": found " + textNodes.size() + " text nodes");
        if (textNodes.size() > 0 && textNodes.size() <= 10) {
            Log.d(TAG, "Sample texts: " + textNodes.toString());
        }

        if (!textNodes.isEmpty()) {
            if (matcher == null) {
                matcher = new MediaMatcher(this);
            }
            // Detect screen context for new floating widget
            ScreenContextDetector.ScreenContext context = ScreenContextDetector.detectContext(textNodes);
            if (context.type == ScreenContextDetector.ScreenType.MEDIA_DETAIL
                    && (context.extractedTitle == null || context.extractedTitle.trim().isEmpty())) {
                String fallbackTitle = ScreenContextDetector.extractTitleFromDetail(textNodes);
                if (fallbackTitle != null && !fallbackTitle.trim().isEmpty()) {
                    context = new ScreenContextDetector.ScreenContext(
                            context.type,
                            fallbackTitle,
                            context.additionalInfo,
                            context.extractedProgress,
                            context.author,
                            context.totalChapters,
                            context.detectedMediaType
                    );
                }
            }
            if (context.extractedTitle != null
                    && !context.extractedTitle.trim().isEmpty()
                    && !matcher.isLikelyTitleForPackage(packageName, context.extractedTitle)) {
                Log.d(TAG, "Dropped noisy extracted title for " + packageName + ": " + context.extractedTitle);
                context = withSanitizedTitle(context, null);
            }
            if (forcePrompt && (context.extractedTitle == null || context.extractedTitle.trim().isEmpty())) {
                String fallbackTitle = ScreenContextDetector.extractTitleFromDetail(textNodes);
                if (fallbackTitle != null && !fallbackTitle.trim().isEmpty()) {
                    context = new ScreenContextDetector.ScreenContext(
                            ScreenContextDetector.ScreenType.MEDIA_DETAIL,
                            fallbackTitle,
                            "Manual scan title fallback",
                            context.extractedProgress,
                            context.author,
                            context.totalChapters,
                            context.detectedMediaType
                    );
                    if (!matcher.isLikelyTitleForPackage(packageName, context.extractedTitle)) {
                        context = withSanitizedTitle(context, null);
                    }
                }
            }
            boolean assistantEnabled = getSharedPreferences("Settings", MODE_PRIVATE)
                    .getBoolean("floating_assistant_enabled", false);
            FloatingAssistantManager assistantManager = FloatingAssistantManager.getInstance(this);
            
            if (assistantEnabled) {
                // Update old floating assistant (keep for compatibility)
                assistantManager.updateContext(context);
            }
            
            if (assistantEnabled && (context.type == ScreenContextDetector.ScreenType.MEDIA_DETAIL || forcePrompt)) {
                Log.i(TAG, "Book detail screen detected: " + context.extractedTitle);
                
                // Show detection prompt in new widget
                if (context.extractedTitle != null && !context.extractedTitle.isEmpty()) {
                    if (!matcher.isLikelyTitleForPackage(packageName, context.extractedTitle)) {
                        Log.d(TAG, "Suppressed prompt for noisy title candidate: " + context.extractedTitle);
                        context = withSanitizedTitle(context, null);
                    }
                }
                if (context.extractedTitle != null && !context.extractedTitle.isEmpty()) {
                    int resolvedMediaId = matcher.resolveExistingMediaId(context.extractedTitle);
                    if (resolvedMediaId > 0) {
                        matcher.recordAccessibilitySignal(
                                resolvedMediaId,
                                new MediaMatcher.DetectedMediaCandidate(
                                        packageName,
                                        context.extractedTitle,
                                        context.extractedProgress,
                                        0.35f,
                                        null
                                )
                        );
                    }
                    boolean shouldShowPrompt = forcePrompt || resolvedMediaId <= 0;
                    if (shouldShowPrompt) {
                        assistantManager.dismissTransientOverlays();
                        FloatingWidgetManager.getInstance(this).showDetectionPrompt(
                                context.extractedTitle,
                                null,
                                resolvedMediaId,
                                packageName,
                                context.extractedProgress,
                                context.author,
                                context.totalChapters,
                                context.additionalInfo,
                                context.detectedMediaType
                        );
                    }
                    manualPromptRequestUntilMs = 0L;
                }
            }
            
            // Continue with normal matching using detected screen context (canonical title/progress assistance)
            matcher.matchAndProcess(textNodes, packageName, context);
        } else {
            // Immersive/canvas readers may hide content from accessibility text extraction.
            if (matcher == null) matcher = new MediaMatcher(this);
            matcher.onNoTextWindow(packageName);
            Log.d(TAG, "No text nodes found - nothing to match");
        }
    }

    private ScreenContextDetector.ScreenContext withSanitizedTitle(
            ScreenContextDetector.ScreenContext context,
            String sanitizedTitle
    ) {
        return new ScreenContextDetector.ScreenContext(
                context.type,
                sanitizedTitle,
                context.additionalInfo,
                context.extractedProgress,
                context.author,
                context.totalChapters,
                context.detectedMediaType
        );
    }

    private void scrapeNodes(AccessibilityNodeInfo node, List<String> resultList, int depth, String activePackage) {
        if (node == null || depth > 50) return;

        String nodePackage = node.getPackageName() != null ? node.getPackageName().toString() : "";
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String value = text.toString();
            if (!shouldIgnoreNodeText(activePackage, nodePackage, value)) {
                resultList.add(value);
            }
        }
        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null && contentDescription.length() > 0) {
            String desc = contentDescription.toString();
            if ((text == null || !desc.equals(text.toString()))
                    && !shouldIgnoreNodeText(activePackage, nodePackage, desc)) {
                resultList.add(desc);
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    scrapeNodes(child, resultList, depth + 1, activePackage);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private boolean shouldIgnoreNodeText(String activePackage, String nodePackage, String rawText) {
        if (rawText == null) {
            return true;
        }
        String text = rawText.trim();
        if (text.isEmpty()) {
            return true;
        }

        String active = activePackage == null ? "" : activePackage.trim().toLowerCase(Locale.ROOT);
        String nodePkg = nodePackage == null ? "" : nodePackage.trim().toLowerCase(Locale.ROOT);
        boolean packageMismatch = !active.isEmpty()
                && !nodePkg.isEmpty()
                && !nodePkg.equals(active)
                && !nodePkg.startsWith(active + ".");

        // Ignore cross-app overlays (e.g., Google Meet PiP floating tile over BiliBili).
        if (packageMismatch) {
            return true;
        }

        if (active.contains("bilibili") || active.contains("bstar") || active.contains("danmaku.bili")) {
            String lower = text.toLowerCase(Locale.ROOT);
            if ("you".equals(lower)
                    || OVERLAY_PARTICIPANT_BADGE_PATTERN.matcher(text).matches()
                    || MEET_OVERLAY_NOISE_PATTERN.matcher(text).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted");
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FloatingAssistantManager.getInstance(this).onConfigurationChanged(newConfig);
    }

    private boolean isTrackingPaused(SharedPreferences settingsPrefs) {
        long pausedUntil = settingsPrefs.getLong(KEY_TRACKING_PAUSED_UNTIL_MS, 0L);
        if (pausedUntil <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= pausedUntil) {
            settingsPrefs.edit().putLong(KEY_TRACKING_PAUSED_UNTIL_MS, 0L).apply();
            return false;
        }
        return true;
    }
}
