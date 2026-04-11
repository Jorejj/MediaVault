package com.example.mediavault.service;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mediavault.AddMediaActivity;
import com.example.mediavault.AppExecutor;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.MainActivity;
import com.example.mediavault.R;
import com.example.mediavault.api.MediaSearchManager;
import com.example.mediavault.widget.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class FloatingAssistantManager {
    private static final String TAG = "FloatingAssistant";
    private static final int MIN_TOP_MARGIN_DP = 24;
    private static final String SETTINGS_PREFS = "Settings";
    private static final String PREF_AUTO_TITLE_TRACKING_ENABLED = "auto_title_tracking_enabled";
    private static final String PREF_AUTO_PROGRESS_TRACKING_ENABLED = "auto_progress_tracking_enabled";
    private static final long DISMISS_DETECTED_COOLDOWN_MS = 120000L;
    private final Context context;
    private final WindowManager windowManager;
    private View floatingView; // Legacy/Stand-alone cards
    private View anchorView;   // Persistent Anchor
    private View notificationPill; // Expanded Pill from Anchor
    private View contextMenu;  // Context-aware action menu
    
    private final DatabaseHelper dbHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideLegacyRunnable = this::hideSync;
    private final Runnable collapsePillRunnable = this::collapseNotificationPill;
    private final Runnable dismissContextMenuRunnable = this::dismissContextMenu;
    
    private boolean isLegacyAttached = false;
    private boolean isAnchorAttached = false;
    private boolean isPillExpanded = false;
    private boolean isContextMenuShowing = false;
    
    private ScreenContextDetector.ScreenContext currentContext = null;
    private ScreenContextDetector.ScreenType lastContextType = null; // Track context changes
    private String dismissedDetectedTitle = null;
    private long dismissedDetectedUntilMs = 0L;

    private static FloatingAssistantManager instance;

    public static synchronized FloatingAssistantManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingAssistantManager(context.getApplicationContext());
        }
        return instance;
    }

    private FloatingAssistantManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.dbHelper = DatabaseHelper.getInstance(this.context);
        
        // Auto-show anchor if enabled
        mainHandler.post(this::ensureAnchorVisible);
    }

    public void ensureAnchorVisible() {
        SharedPreferences settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        boolean anchorEnabled = settings.getBoolean("persistent_anchor_enabled", false);
        
        if (anchorEnabled && !isAnchorAttached) {
            showAnchor();
        } else if (!anchorEnabled && isAnchorAttached) {
            hideAnchor();
        }
    }

    private void showAnchor() {
        if (isAnchorAttached) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return;

        anchorView = LayoutInflater.from(context).inflate(R.layout.layout_floating_anchor, null);
        
        WindowManager.LayoutParams params = createBaseParams();
        
        // Restore last position
        SharedPreferences prefs = context.getSharedPreferences("AnchorPos", Context.MODE_PRIVATE);
        params.x = prefs.getInt("x", 20);
        params.y = prefs.getInt("y", 500);
        clampToScreen(params, anchorView);

        setupAnchorDrag(anchorView, params);

        try {
            windowManager.addView(anchorView, params);
            isAnchorAttached = true;
        } catch (Exception e) {
            Log.e("FloatingAssistant", "Failed to add anchor", e);
        }
    }

    private WindowManager.LayoutParams createBaseParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private void setupAnchorDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        
                        // If moved more than 10px, consider it a drag
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;
                            clampToScreen(params, anchorView);
                            if (isAnchorAttached) {
                                safeUpdateAnchorLayout(params, "drag");
                            }
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        long touchDuration = System.currentTimeMillis() - touchStartTime;
                        
                        if (!isDragging && touchDuration < 500) {
                            // It's a tap!
                            onAnchorTapped();
                        } else if (isDragging) {
                            // It's a drag - snap to edge
                            snapAnchorToEdge(params);
                            // Save position
                            context.getSharedPreferences("AnchorPos", Context.MODE_PRIVATE)
                                    .edit().putInt("x", params.x).putInt("y", params.y).apply();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void snapAnchorToEdge(WindowManager.LayoutParams params) {
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(size);
        clampToScreen(params, anchorView);
        int screenWidth = size.x;
        int targetX = (params.x + anchorView.getWidth() / 2 > screenWidth / 2) 
                      ? screenWidth - anchorView.getWidth() : 0;

        ValueAnimator animator = ValueAnimator.ofInt(params.x, targetX);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            params.x = (int) animation.getAnimatedValue();
            if (isAnchorAttached) {
                safeUpdateAnchorLayout(params, "snap");
            }
        });
        animator.start();
    }

    public void showUpdate(int mediaId, String title, float nextProgress, float prevProgress) {
        String displayTitle = resolveDisplayTitle(mediaId, title);
        if (!isAnchorAttached) {
            showLegacyUpdate(mediaId, displayTitle, nextProgress, prevProgress);
            return;
        }

        mainHandler.post(() -> {
            if (isWidgetPromptActive()) {
                Log.d(TAG, "Skipped progress pill while widget prompt is active");
                return;
            }
            pulseAnchor(0xFF3498DB); // Blue pulse
            AtomicBoolean undoConsumed = new AtomicBoolean(false);
            expandNotificationPill("📈 " + displayTitle + " progress updated: " + prevProgress + " -> " + nextProgress, R.drawable.ic_delete, v -> {
                if (!undoConsumed.compareAndSet(false, true)) {
                    return;
                }
                v.setEnabled(false);
                v.setAlpha(0.5f);
                AppExecutor.getInstance().diskIO().execute(() -> {
                    float currentRating = dbHelper.getMediaRating(mediaId);
                    dbHelper.updateProgress(mediaId, prevProgress, "Ongoing", currentRating);
                    mainHandler.post(() -> {
                        Toast.makeText(context, "Update Undone", Toast.LENGTH_SHORT).show();
                        collapseNotificationPill();
                    });
                });
            });
        });
    }

    public void showNewTitlePrompt(String title, float progress, String packageName) {
        FloatingWidgetManager widgetManager = FloatingWidgetManager.getInstance(context);
        if (widgetManager.isShowing()) {
            widgetManager.showDetectionPrompt(title, String.valueOf(progress), -1, packageName, progress);
            return;
        }
        if (!isAnchorAttached) {
            showLegacyNewTitlePrompt(title, progress);
            return;
        }

        mainHandler.post(() -> {
            pulseAnchor(0xFFE67E22); // Orange pulse
            expandNotificationPill("Add \"" + title + "\"?", R.drawable.ic_add, v -> {
                // Open AddMediaActivity with pre-filled title
                Intent intent = new Intent(context, AddMediaActivity.class);
                intent.putExtra("PREFILL_TITLE", title);
                intent.putExtra("PREFILL_PROGRESS", progress);
                intent.putExtra("FROM_TRACKER", true);
                intent.putExtra("TRACKER_PACKAGE", packageName);
                String inferredType = inferTrackerTypeFromPackage(packageName);
                if (inferredType != null && !inferredType.isEmpty()) {
                    intent.putExtra("PREFILL_TYPE_HINT", inferredType);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                collapseNotificationPill();
            });
        });
    }

    private String inferTrackerTypeFromPackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return "";
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("webnovel") || normalized.contains("qidian") || normalized.contains("novel")) {
            return "Book";
        }
        if (normalized.contains("kotatsu")
                || normalized.contains("mihon")
                || normalized.contains("tachiyomi")
                || normalized.contains("manga")
                || normalized.contains("manhwa")
                || normalized.contains("manhua")
                || normalized.contains("webtoon")) {
            return "Manga";
        }
        if (normalized.contains("bilibili")
                || normalized.contains("crunchyroll")
                || normalized.contains("aniwatch")
                || normalized.contains("anime")) {
            return "Anime";
        }
        return "";
    }

    private void pulseAnchor(int color) {
        if (!isAnchorAttached) return;
        View pulse = anchorView.findViewById(R.id.anchor_pulse);
        pulse.setVisibility(View.VISIBLE);
        pulse.getBackground().setTint(color);
        
        pulse.animate().scaleX(1.5f).scaleY(1.5f).alpha(0).setDuration(1000)
             .withEndAction(() -> {
                 pulse.setVisibility(View.GONE);
                 pulse.setScaleX(1f);
                 pulse.setScaleY(1f);
                 pulse.setAlpha(0.6f);
             }).start();
    }

    private void expandNotificationPill(String text, int actionIcon, View.OnClickListener action) {
        if (isWidgetPromptActive()) {
            Log.d(TAG, "Suppressing assistant pill while widget prompt is active");
            return;
        }
        mainHandler.removeCallbacks(collapsePillRunnable);
        if (isPillExpanded || notificationPill != null) {
            dismissNotificationPillImmediate();
        }
        FloatingWidgetManager.getInstance(context).dismissTransientUi();

        notificationPill = LayoutInflater.from(context).inflate(R.layout.layout_anchor_notification, null);
        TextView content = notificationPill.findViewById(R.id.text_notification_content);
        ImageButton btnAction = notificationPill.findViewById(R.id.btn_notification_action);
        
        content.setText(text);
        btnAction.setImageResource(actionIcon);
        btnAction.setOnClickListener(action);

        WindowManager.LayoutParams pillParams = createBaseParams();
        constrainOverlayWidth(pillParams, 360, 16);

        // Measure with constrained width before final placement
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int targetWidth = pillParams.width > 0 ? pillParams.width : screenWidth;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        notificationPill.measure(widthSpec, heightSpec);

        // Always show notifications at safe top-center.
        positionTopCenter(notificationPill, pillParams, 12);
        clampToScreen(pillParams, notificationPill);
        
        try {
            windowManager.addView(notificationPill, pillParams);
            isPillExpanded = true;

            notificationPill.setTranslationY(-12f);
            notificationPill.animate().alpha(1f).translationY(0f).setDuration(140).start();
            mainHandler.postDelayed(collapsePillRunnable, 6000);
            Log.d(TAG, "Notification pill shown: " + text);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification pill", e);
        }
    }

    private void collapseNotificationPill() {
        mainHandler.removeCallbacks(collapsePillRunnable);
        if (!isPillExpanded || notificationPill == null) {
            return;
        }
        View pillToRemove = notificationPill;
        notificationPill = null;
        isPillExpanded = false;
        removeNotificationPillImmediate(pillToRemove);
    }

    private void showLegacyUpdate(int mediaId, String title, float next, float prev) {
        mainHandler.post(() -> {
            if (isLegacyAttached) hideSync();
            floatingView = LayoutInflater.from(context).inflate(R.layout.layout_floating_tracker, null);
            TextView text = floatingView.findViewById(R.id.text_tracker_content);
            text.setText(title + " • " + next);
            ImageButton undo = floatingView.findViewById(R.id.btn_tracker_undo);
            AtomicBoolean undoConsumed = new AtomicBoolean(false);
            undo.setOnClickListener(v -> {
                 if (!undoConsumed.compareAndSet(false, true)) {
                     return;
                 }
                 v.setEnabled(false);
                 v.setAlpha(0.5f);
                 AppExecutor.getInstance().diskIO().execute(() -> {
                    dbHelper.updateProgress(mediaId, prev, "Ongoing", dbHelper.getMediaRating(mediaId));
                    mainHandler.post(this::hideSync);
                 });
            });
            attachLegacyView();
        });
    }

    private void showLegacyNewTitlePrompt(String title, float progress) {
        mainHandler.post(() -> {
            if (isLegacyAttached) hideSync();
            floatingView = LayoutInflater.from(context).inflate(R.layout.layout_floating_tracker, null);
            TextView text = floatingView.findViewById(R.id.text_tracker_content);
            text.setText("Add \"" + title + "\"?");
            ImageButton add = floatingView.findViewById(R.id.btn_tracker_undo);
            add.setImageResource(R.drawable.ic_add);
            add.setOnClickListener(v -> hideSync());
            attachLegacyView();
        });
    }

    private void attachLegacyView() {
        WindowManager.LayoutParams params = createBaseParams();
        params.x = 20; params.y = 200;
        clampToScreen(params, floatingView);
        try {
            windowManager.addView(floatingView, params);
            isLegacyAttached = true;
            mainHandler.postDelayed(hideLegacyRunnable, 8000);
        } catch (Exception e) {
            Log.w(TAG, "Failed to attach legacy assistant view", e);
        }
    }

    public void showCompletionOverlay(int id, String title) {
        String displayTitle = resolveDisplayTitle(id, title);
        if (isAnchorAttached) {
            if (isWidgetPromptActive()) {
                Log.d(TAG, "Skipped completion pill while widget prompt is active");
                return;
            }
            pulseAnchor(0xFF2ECC71); // Green pulse
            expandNotificationPill("🎉 " + displayTitle + " Completed!", R.drawable.ic_chart, v -> collapseNotificationPill());
        } else {
            showLegacyUpdate(id, displayTitle, 0, 0);
        }
    }

    public void showNearCompletionOverlay(int id, String title, float next) {
        String displayTitle = resolveDisplayTitle(id, title);
        if (isAnchorAttached) {
            if (isWidgetPromptActive()) {
                Log.d(TAG, "Skipped near-completion pill while widget prompt is active");
                return;
            }
            pulseAnchor(0xFF2ECC71);
            expandNotificationPill("Last Chapter of " + displayTitle + "!", R.drawable.ic_chart, v -> collapseNotificationPill());
        }
    }

    private String resolveDisplayTitle(int mediaId, String fallbackTitle) {
        String resolved = dbHelper.getPreferredDisplayTitle(mediaId);
        if (resolved != null && !resolved.trim().isEmpty()) {
            return resolved.trim();
        }
        return fallbackTitle;
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        if (isAnchorAttached && anchorView != null) {
            mainHandler.post(() -> {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) anchorView.getLayoutParams();
                params.x = 20; params.y = 200;
                clampToScreen(params, anchorView);
                safeUpdateAnchorLayout(params, "configuration-change");
                if (isPillExpanded || isContextMenuShowing) {
                    // Recreate transient overlays after rotation to avoid clipped remnants.
                    dismissTransientOverlays();
                }
            });
        }
    }

    public void hide() { mainHandler.post(this::hideSync); }

    private void hideSync() {
        if (isLegacyAttached && floatingView != null) {
            safeRemoveViewImmediate(floatingView, "legacy overlay");
            floatingView = null; isLegacyAttached = false;
        }
    }

    private void hideAnchor() {
        if (isAnchorAttached && anchorView != null) {
            safeRemoveViewImmediate(anchorView, "anchor overlay");
            anchorView = null; isAnchorAttached = false;
        }
    }

    /**
     * Cleanup method to be called when the app is destroyed.
     * Removes all floating windows to prevent "WIN DEATH" errors.
     */
    public void destroyAll() {
        mainHandler.post(() -> {
            // Remove all pending callbacks
            mainHandler.removeCallbacks(hideLegacyRunnable);
            mainHandler.removeCallbacks(collapsePillRunnable);
            dismissNotificationPillImmediate();
            
            // Dismiss context menu if showing
            if (isContextMenuShowing) {
                dismissContextMenu();
            }
            
            // Remove all views
            hideSync();
            hideAnchor();
            
            Log.d("FloatingAssistant", "All windows cleaned up");
        });
    }

    /**
     * Dismiss transient assistant surfaces that can obstruct widget prompts.
     */
    public void dismissTransientOverlays() {
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(collapsePillRunnable);
            dismissNotificationPillImmediate();
            if (isContextMenuShowing) {
                dismissContextMenu();
            }
        });
    }

    public void setAnchorSuppressed(boolean suppressed) {
        mainHandler.post(() -> {
            if (anchorView == null || !isAnchorAttached) {
                return;
            }
            anchorView.animate().cancel();
            anchorView.clearAnimation();
            anchorView.setAlpha(suppressed ? 0f : 1f);
            anchorView.setVisibility(suppressed ? View.GONE : View.VISIBLE);
        });
    }

    /**
     * Update the current screen context. Called by MediaMonitorService.
     */
    public void updateContext(ScreenContextDetector.ScreenContext context) {
        this.currentContext = context;
        
        // Update anchor appearance based on context
        if (anchorView != null && isAnchorAttached) {
            mainHandler.post(() -> updateAnchorAppearance(context));
        }
    }

    /**
     * Update anchor visual state based on context.
     */
    private void updateAnchorAppearance(ScreenContextDetector.ScreenContext context) {
        if (context == null || anchorView == null) return;
        
        View anchorIcon = anchorView.findViewById(R.id.anchor_icon);
        if (anchorIcon == null) return;
        
        // Detect context change
        boolean contextChanged = (lastContextType != context.type);
        lastContextType = context.type;
        
        // Color-code based on context type
        int color;
        switch (context.type) {
            case MEDIA_DETAIL:
                color = 0xFF2ECC71; // Green - book detected
                if (anchorIcon instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) anchorIcon).setColorFilter(color);
                }
                pulseAnchor(color);

                // Let FloatingWidgetManager be the single source for detection prompts.
                if (contextChanged
                        && isAutoTitleTrackingEnabled()
                        && context.extractedTitle != null
                        && !context.extractedTitle.isEmpty()
                        && !isDetectedTitleDismissed(context.extractedTitle)
                        && !FloatingWidgetManager.getInstance(this.context).isShowing()) {
                    expandNotificationPill(context.extractedTitle + " detected.", R.drawable.ic_add, v -> {
                        onAnchorTapped();
                        collapseNotificationPill();
                    });
                }
                break;
            case CHAPTER_READING:
                color = 0xFF3498DB; // Blue - tracking active
                if (anchorIcon instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) anchorIcon).setColorFilter(color);
                }
                break;
            case UNKNOWN:
            default:
                color = 0xFF3498DB; // Blue - default color
                if (anchorIcon instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) anchorIcon).setColorFilter(color);
                }
                break;
        }
    }

    /**
     * Handle anchor tap - show context menu if available.
     */
    private void onAnchorTapped() {
        Log.d("FloatingAssistant", "Anchor tapped!");

        FloatingWidgetManager widgetManager = FloatingWidgetManager.getInstance(context);
        if (widgetManager.isShowing()) {
            dismissTransientOverlays();
            widgetManager.showQuickActions();
            return;
        }
        
        if (currentContext != null
                && currentContext.type == ScreenContextDetector.ScreenType.MEDIA_DETAIL
                && !isDetectedTitleDismissed(currentContext.extractedTitle)) {
            // Book detail detected - show context menu with detected title
            Log.d("FloatingAssistant", "Showing menu with detected title: " + currentContext.extractedTitle);
            showContextMenu(currentContext.extractedTitle);
        } else {
            // No context detected - show menu with manual option
            Log.d("FloatingAssistant", "No context detected, showing fallback menu");
            showContextMenu(null);
        }
    }

    /**
     * Show the context-aware action menu.
     */
    private void showContextMenu(String detectedTitle) {
        if (isContextMenuShowing) {
            dismissContextMenu();
            return;
        }

        contextMenu = LayoutInflater.from(context).inflate(R.layout.layout_context_menu, null);
        
        // Keep a consistent quick-actions header while still showing the matched title.
        TextView titleView = contextMenu.findViewById(R.id.context_title);
        if (detectedTitle != null && !detectedTitle.trim().isEmpty()) {
            titleView.setText("Quick Actions • " + detectedTitle.trim());
        } else {
            titleView.setText(com.example.mediavault.R.string.auto_quick_actions);
            detectedTitle = null; // Ensure it's null for later checks
        }
        
        View dismissMediaBtn = contextMenu.findViewById(R.id.action_dismiss_media);
        View manualScanBtn = contextMenu.findViewById(R.id.action_manual_scan);
        TextView toggleTitleTrackingBtn = contextMenu.findViewById(R.id.action_toggle_title_tracking);
        TextView toggleProgressTrackingBtn = contextMenu.findViewById(R.id.action_toggle_progress_tracking);
        View exitBtn = contextMenu.findViewById(R.id.action_exit);
        final String finalTitle = detectedTitle;

        updateToggleButtonLabels(toggleTitleTrackingBtn, toggleProgressTrackingBtn);

        if (finalTitle == null || finalTitle.trim().isEmpty()) {
            dismissMediaBtn.setEnabled(false);
            dismissMediaBtn.setAlpha(0.5f);
        } else {
            dismissMediaBtn.setOnClickListener(v -> {
                dismissDetectedMedia(finalTitle);
                dismissContextMenu();
            });
        }

        manualScanBtn.setOnClickListener(v -> {
            requestManualScreenScan();
            dismissContextMenu();
        });

        toggleTitleTrackingBtn.setOnClickListener(v -> {
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
            boolean next = !isAutoTitleTrackingEnabled();
            prefs.edit().putBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, next).apply();
            updateToggleButtonLabels(toggleTitleTrackingBtn, toggleProgressTrackingBtn);
            Toast.makeText(context, "Automated title tracking " + (next ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        toggleProgressTrackingBtn.setOnClickListener(v -> {
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
            boolean next = !isAutoProgressTrackingEnabled();
            prefs.edit().putBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, next).apply();
            updateToggleButtonLabels(toggleTitleTrackingBtn, toggleProgressTrackingBtn);
            Toast.makeText(context, "Automated media progress " + (next ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        exitBtn.setOnClickListener(v -> dismissContextMenu());
        
        // Position context actions in a safe top-center zone.
        WindowManager.LayoutParams params = createBaseParams();
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;

        constrainOverlayWidth(params, 360, 16);
        positionTopCenter(contextMenu, params, 16);
        clampToScreen(params, contextMenu);

        try {
            windowManager.addView(contextMenu, params);
            isContextMenuShowing = true;
            
            // Auto-dismiss after 10 seconds
            mainHandler.removeCallbacks(dismissContextMenuRunnable);
            mainHandler.postDelayed(dismissContextMenuRunnable, 10000);
        } catch (Exception e) {
            Log.e("FloatingAssistant", "Failed to show context menu", e);
        }
    }

    /**
     * Dismiss the context menu.
     */
    private void dismissContextMenu() {
        mainHandler.removeCallbacks(dismissContextMenuRunnable);
        if (isContextMenuShowing && contextMenu != null) {
            View menuToRemove = contextMenu;
            contextMenu = null;
            isContextMenuShowing = false;
            safeRemoveViewImmediate(menuToRemove, "context menu");
        }
    }

    private void dismissNotificationPillImmediate() {
        mainHandler.removeCallbacks(collapsePillRunnable);
        if (notificationPill != null) {
            View pillToRemove = notificationPill;
            notificationPill = null;
            removeNotificationPillImmediate(pillToRemove);
        }
        isPillExpanded = false;
    }

    private void removeNotificationPillImmediate(View pill) {
        if (pill == null) {
            return;
        }
        try {
            pill.animate().cancel();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel notification pill animation", e);
        }
        safeRemoveViewImmediate(pill, "notification pill");
    }

    private void updateToggleButtonLabels(TextView titleTrackingBtn, TextView progressTrackingBtn) {
        if (titleTrackingBtn != null) {
            titleTrackingBtn.setText(
                    isAutoTitleTrackingEnabled()
                            ? "Automatic title tracking: On"
                            : "Automatic title tracking: Off"
            );
        }
        if (progressTrackingBtn != null) {
            progressTrackingBtn.setText(
                    isAutoProgressTrackingEnabled()
                            ? "Automatic media progress: On"
                            : "Automatic media progress: Off"
            );
        }
    }

    private void requestManualScreenScan() {
        Intent intent = new Intent(MediaMonitorService.ACTION_MANUAL_SCAN_REQUEST);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
        Toast.makeText(context, "Manual screen scan requested", Toast.LENGTH_SHORT).show();
    }

    private void dismissDetectedMedia(String title) {
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        dismissedDetectedTitle = title.trim().toLowerCase(Locale.ROOT);
        dismissedDetectedUntilMs = System.currentTimeMillis() + DISMISS_DETECTED_COOLDOWN_MS;
        currentContext = new ScreenContextDetector.ScreenContext(ScreenContextDetector.ScreenType.UNKNOWN, null, null);
        collapseNotificationPill();
        Toast.makeText(context, "Dismissed " + title, Toast.LENGTH_SHORT).show();
    }

    private boolean isDetectedTitleDismissed(String title) {
        if (title == null || dismissedDetectedTitle == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > dismissedDetectedUntilMs) {
            dismissedDetectedTitle = null;
            dismissedDetectedUntilMs = 0L;
            return false;
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(dismissedDetectedTitle) || dismissedDetectedTitle.contains(normalized);
    }

    private boolean isAutoTitleTrackingEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, true);
    }

    private boolean isAutoProgressTrackingEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, true);
    }

    /**
     * Quick add book with auto-detected settings.
     */
    private void quickAddBook(String title) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            // Auto-detect type based on title/context
            String type = "Novel"; // Default to Novel for webnovel flows
            String unit = "Chapters";
            String creator = "Unknown";
            int total = 999;
            String description = "Auto-detected from external app";

            if (currentContext != null) {
                if (currentContext.author != null && !currentContext.author.trim().isEmpty()) {
                    creator = currentContext.author.trim();
                }
                if (currentContext.totalChapters > 1) {
                    total = currentContext.totalChapters;
                }
                if (currentContext.additionalInfo != null) {
                    description = currentContext.additionalInfo;
                }
            }
            
            long id = dbHelper.addMedia(title, type, "Uncategorized", creator, total, unit, "N/A", null, description);
            
            if (id != -1) {
                com.example.mediavault.api.MediaMetadataProfile profile = com.example.mediavault.api.MediaMetadataProfile.create()
                        .withCanonicalTitle(title)
                        .withNormalizedTitle(com.example.mediavault.api.MediaMetadataProfile.normalizeTitle(title))
                        .withMetadataSource("accessibility")
                        .withMediaType(type)
                        .withTotalCount(total > 0 ? total : null)
                        .withUnit(unit)
                        .withMetadataConfidence(0.35f)
                        .withMetadataPriority(15)
                        .stampNow();
                if (creator != null && !creator.trim().isEmpty()) {
                    profile.addTag(creator.trim());
                }
                dbHelper.mergeAndUpsertMetadata((int) id, profile, "accessibility");

                // Enrich with metadata in background
                new MediaSearchManager().enrichMediaMetadata(context, (int) id, title, type);
                
                mainHandler.post(() -> {
                    Toast.makeText(context, "✓ Added " + title, Toast.LENGTH_SHORT).show();
                });
            } else {
                mainHandler.post(() -> {
                    Toast.makeText(context, "Failed to add book", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Open AddMediaActivity with pre-filled title.
     */
    private void openAddMediaActivity(String title) {
        Intent intent = new Intent(context, AddMediaActivity.class);
        intent.putExtra("PREFILL_TITLE", title);
        intent.putExtra("PREFILL_PROGRESS", 0f);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Check if book is already in library.
     */
    private boolean isBookInLibrary(String title) {
        android.database.Cursor cursor = dbHelper.getAllMedia();
        if (cursor == null) return false;
        
        try {
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            while (cursor.moveToNext()) {
                String dbTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                if (dbTitle.toLowerCase(Locale.ROOT).contains(lowerTitle) || lowerTitle.contains(dbTitle.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } finally {
            cursor.close();
        }
        
        return false;
    }

    private void safeUpdateAnchorLayout(WindowManager.LayoutParams params, String reason) {
        if (anchorView == null || !isAnchorAttached || !isViewAttached(anchorView)) {
            return;
        }
        try {
            windowManager.updateViewLayout(anchorView, params);
        } catch (Exception e) {
            Log.w(TAG, "Failed to update anchor layout (" + reason + ")", e);
        }
    }

    private void safeRemoveViewImmediate(View view, String surface) {
        if (view == null || !isViewAttached(view)) {
            return;
        }
        try {
            try {
                view.animate().cancel();
            } catch (Exception ignored) {
            }
            view.clearAnimation();
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            windowManager.removeViewImmediate(view);
        } catch (Exception e) {
            Log.w(TAG, "Failed to remove " + surface, e);
        }
    }

    private boolean isViewAttached(View view) {
        return view != null && view.getParent() != null;
    }

    private boolean isWidgetPromptActive() {
        FloatingWidgetManager widgetManager = FloatingWidgetManager.getInstance(context);
        return widgetManager.isPromptActive();
    }

    private void clampToScreen(WindowManager.LayoutParams params, View view) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int minTop = getSafeTopInsetPx();

        int viewWidth = getMeasuredWidth(view);
        int viewHeight = getMeasuredHeight(view);

        int maxX = Math.max(0, screenWidth - Math.max(1, viewWidth));
        int maxY = Math.max(minTop, screenHeight - Math.max(1, viewHeight));

        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(minTop, Math.min(params.y, maxY));
    }

    private void positionNearAnchor(View popupView, WindowManager.LayoutParams params, int gapDp) {
        if (anchorView == null) {
            params.x = 20;
            params.y = getSafeTopInsetPx() + dpToPx(16);
            return;
        }

        WindowManager.LayoutParams anchorParams = null;
        if (anchorView.getLayoutParams() instanceof WindowManager.LayoutParams) {
            anchorParams = (WindowManager.LayoutParams) anchorView.getLayoutParams();
        }

        if (anchorParams == null) {
            // Fallback to safe position
            params.x = 20;
            params.y = getSafeTopInsetPx() + dpToPx(16);
            return;
        }

        int anchorWidth = Math.max(1, getMeasuredWidth(anchorView));
        int anchorHeight = Math.max(1, getMeasuredHeight(anchorView));
        
        // CRITICAL: anchorParams.x and anchorParams.y are OFFSETS from TOP-LEFT
        // because gravity is TOP | START. We need to use the same coordinate system.
        int anchorX = anchorParams.x;
        int anchorY = anchorParams.y;

        int popupWidth = Math.max(1, getMeasuredWidth(popupView));
        int popupHeight = Math.max(1, getMeasuredHeight(popupView));

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int gap = dpToPx(gapDp);

        // Calculate position relative to anchor (both use TOP|START gravity offset system)
        int rightX = anchorX + anchorWidth + gap;
        int leftX = anchorX - popupWidth - gap;
        boolean canFitRight = rightX + popupWidth <= screenWidth;

        params.x = canFitRight ? rightX : Math.max(0, leftX);
        // Center vertically relative to anchor
        params.y = anchorY + ((anchorHeight - popupHeight) / 2);
        params.y = Math.max(getSafeTopInsetPx(), params.y);
    }

    private void positionTopCenter(View popupView, WindowManager.LayoutParams params, int topOffsetDp) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int viewWidth = params.width > 0 ? params.width : Math.max(1, getMeasuredWidth(popupView));
        params.x = Math.max(0, (screenWidth - viewWidth) / 2);
        params.y = getSafeTopInsetPx() + dpToPx(topOffsetDp);
    }

    private void constrainOverlayWidth(WindowManager.LayoutParams params, int maxWidthDp, int horizontalMarginDp) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int horizontalMarginPx = dpToPx(horizontalMarginDp);
        int maxAllowed = Math.max(dpToPx(220), screenWidth - (horizontalMarginPx * 2));
        int preferred = dpToPx(maxWidthDp);
        params.width = Math.min(preferred, maxAllowed);
    }

    private int getMeasuredWidth(View view) {
        if (view == null) return 0;
        if (view.getWidth() > 0) return view.getWidth();
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        return view.getMeasuredWidth();
    }

    private int getMeasuredHeight(View view) {
        if (view == null) return 0;
        if (view.getHeight() > 0) return view.getHeight();
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        return view.getMeasuredHeight();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private int getSafeTopInsetPx() {
        int minTop = dpToPx(MIN_TOP_MARGIN_DP);
        int statusBarHeight = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        return Math.max(minTop, statusBarHeight + dpToPx(8));
    }
}
