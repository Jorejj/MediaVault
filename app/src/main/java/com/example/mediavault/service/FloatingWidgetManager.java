package com.example.mediavault.service;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mediavault.AddMediaActivity;
import com.example.mediavault.AppExecutor;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.DescriptionActivity;
import com.example.mediavault.MainActivity;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advanced Floating Widget Manager with state machine, drag physics, and keyboard avoidance.
 */
public class FloatingWidgetManager {
    private static final String TAG = "FloatingWidget";
    private static final int MIN_TOP_MARGIN_DP = 24;
    private static final int PROMPT_TOP_OFFSET_DP = 96;
    private static final int PROMPT_SAFE_PADDING_DP = 24;
    private static final int MAGNETIC_SNAP_DISTANCE = 150; // pixels
    private static final int FADE_DELAY_MS = 4000;
    private static final float IDLE_ALPHA = 0.35f;
    private static final long IGNORE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final String SETTINGS_PREFS = "Settings";
    private static final String PREF_AUTO_TITLE_TRACKING_ENABLED = "auto_title_tracking_enabled";
    private static final String PREF_AUTO_PROGRESS_TRACKING_ENABLED = "auto_progress_tracking_enabled";
    
    private static FloatingWidgetManager instance;
    private final Context context;
    private final WindowManager windowManager;
    private final DatabaseHelper databaseHelper;
    
    // Views
    private View mainView;
    private View trashCanView;
    private View stateIdle;
    private View stateDetectedPrompt;
    private View stateEditPrompt;
    private View stateExpandedMenu;
    
    // State management
    private WidgetState currentState = WidgetState.IDLE;
    private String pendingTitle = null;
    private String pendingProgress = null;
    private int pendingMediaId = -1;
    private String pendingPackageName = null;
    private float pendingDetectedProgress = 0f;
    private String pendingDetectedAuthor = null;
    private int pendingDetectedTotalChapters = 0;
    private String pendingDetectedDescription = null;
    
    private String pendingDetectedMediaType = null;
    
    // Ignored titles with timestamps
    private final Map<String, Long> ignoredTitles = new HashMap<>();
    
    // Touch & drag
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long touchStartTime;
    private boolean isDragging = false;
    private WindowManager.LayoutParams mainParams;
    private WindowManager.LayoutParams trashParams;
    private ValueAnimator snapAnimator;
    
    // Auto-fade handler
    private final Handler fadeHandler = new Handler(Looper.getMainLooper());
    private Runnable fadeRunnable;
    
    // Keyboard avoidance
    private int originalY;
    private boolean keyboardVisible = false;
    private int lastIdleX = 50;
    private int lastIdleY = 200;
    
    // Clipboard listener
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    private boolean clipboardListenerRegistered = false;
    
    /**
     * Widget states
     */
    public enum WidgetState {
        IDLE,              // Compact anchor
        DETECTED_PROMPT,   // Show detected media with actions
        EDIT_PROMPT,       // Manual title editing
        EXPANDED_MENU      // Manual controls menu
    }
    
    private FloatingWidgetManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.databaseHelper = DatabaseHelper.getInstance(context);
        setupClipboardListener();
    }
    
    public static synchronized FloatingWidgetManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingWidgetManager(context);
        }
        return instance;
    }
    
    /**
     * Initialize and show the floating widget
     */
    public void show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fadeHandler.post(this::show);
            return;
        }
        if (mainView != null) {
            Log.w(TAG, "Widget already showing");
            return;
        }
        
        // Check overlay permission before proceeding
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot show widget: overlay permission not granted");
            return;
        }
        
        // Inflate main widget
        mainView = LayoutInflater.from(context).inflate(R.layout.layout_floating_assistant_v2, null);
        
        // Get state containers
        stateIdle = mainView.findViewById(R.id.state_idle);
        stateDetectedPrompt = mainView.findViewById(R.id.state_detected_prompt);
        stateEditPrompt = mainView.findViewById(R.id.state_edit_prompt);
        stateExpandedMenu = mainView.findViewById(R.id.state_expanded_menu);
        
        // Setup touch handling
        setupTouchHandling();
        
        // Setup button click listeners
        setupClickListeners();
        
        // Setup keyboard avoidance
        setupKeyboardAvoidance();
        
        // Create params and add to window
        mainParams = createBaseParams();
        mainParams.gravity = Gravity.TOP | Gravity.START;
        mainParams.x = 50;
        mainParams.y = 200;
        clampMainParams();
        originalY = mainParams.y;
        
        try {
            windowManager.addView(mainView, mainParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add main view", e);
            mainView = null;
            return;
        }
        
        // Inflate trash can (hidden by default)
        trashCanView = LayoutInflater.from(context).inflate(R.layout.layout_trash_can, null);
        trashParams = createBaseParams();
        trashParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        trashParams.y = 100;
        trashParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            windowManager.addView(trashCanView, trashParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add trash can view", e);
            trashCanView = null;
        }
        
        // Start in IDLE state
        updateState(WidgetState.IDLE);
        registerClipboardListener();
        
        Log.i(TAG, "Floating widget shown");
    }
    
    /**
     * Hide and cleanup the widget
     */
    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fadeHandler.post(this::hide);
            return;
        }
        if (mainView != null) {
            try {
                mainView.animate().cancel();
                windowManager.removeViewImmediate(mainView);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Main view not attached", e);
            }
        }
        if (trashCanView != null) {
            try {
                trashCanView.animate().cancel();
                windowManager.removeViewImmediate(trashCanView);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Trash view not attached", e);
            }
        }
        mainView = null;
        trashCanView = null;
        stateIdle = null;
        stateDetectedPrompt = null;
        stateEditPrompt = null;
        stateExpandedMenu = null;
        currentState = WidgetState.IDLE;
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        fadeHandler.removeCallbacksAndMessages(null);
        unregisterClipboardListener();
        Log.i(TAG, "Floating widget hidden");
    }

    public boolean isShowing() {
        return mainView != null;
    }

    public void showQuickActions() {
        if (mainView == null) return;
        updateState(WidgetState.EXPANDED_MENU);
    }

    public void dismissTransientUi() {
        if (mainView == null) return;
        if (currentState != WidgetState.IDLE) {
            updateState(WidgetState.IDLE);
        }
    }
    
    /**
     * Update widget state - safely toggles visibility
     */
    public void updateState(WidgetState newState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fadeHandler.post(() -> updateState(newState));
            return;
        }
        if (mainView == null) return;
        
        Log.d(TAG, "State transition: " + currentState + " -> " + newState);
        WidgetState previousState = currentState;
        currentState = newState;
        
        // Hide all states
        stateIdle.setVisibility(View.GONE);
        stateDetectedPrompt.setVisibility(View.GONE);
        stateEditPrompt.setVisibility(View.GONE);
        stateExpandedMenu.setVisibility(View.GONE);
        
        // Show appropriate state
        switch (newState) {
            case IDLE:
                stateIdle.setVisibility(View.VISIBLE);
                applyWindowInteractivity(false);
                resizeWidget(56, 56);
                if (previousState == WidgetState.DETECTED_PROMPT
                        || previousState == WidgetState.EDIT_PROMPT
                        || previousState == WidgetState.EXPANDED_MENU) {
                    restoreIdlePosition();
                }
                pendingTitle = null;
                pendingProgress = null;
                pendingMediaId = -1;
                pendingPackageName = null;
                pendingDetectedProgress = 0f;
                pendingDetectedAuthor = null;
                pendingDetectedTotalChapters = 0;
                pendingDetectedDescription = null;
                pendingDetectedMediaType = null;
                scheduleAutoFade();
                break;
                
            case DETECTED_PROMPT:
                if (previousState == WidgetState.IDLE) {
                    rememberIdlePosition();
                }
                stateDetectedPrompt.setVisibility(View.VISIBLE);
                applyWindowInteractivity(true);
                constrainPromptLayoutWidths();
                updateTrackingToggleLabels();
                resizeWidget(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
                cancelAutoFade();
                restoreVisibility();
                pinPromptTopCenter();
                break;
                
            case EDIT_PROMPT:
                if (previousState == WidgetState.IDLE) {
                    rememberIdlePosition();
                }
                stateEditPrompt.setVisibility(View.VISIBLE);
                applyWindowInteractivity(true);
                constrainPromptLayoutWidths();
                resizeWidget(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
                cancelAutoFade();
                restoreVisibility();
                pinPromptTopCenter();
                // Focus EditText
                EditText editText = mainView.findViewById(R.id.edit_title_input);
                if (editText != null && pendingTitle != null) {
                    editText.setText(pendingTitle);
                    editText.selectAll();
                    editText.requestFocus();
                    showKeyboard(editText);
                }
                break;
                
            case EXPANDED_MENU:
                if (previousState == WidgetState.IDLE) {
                    rememberIdlePosition();
                }
                stateExpandedMenu.setVisibility(View.VISIBLE);
                applyWindowInteractivity(true);
                constrainPromptLayoutWidths();
                updateTrackingToggleLabels();
                resizeWidget(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
                cancelAutoFade();
                restoreVisibility();
                pinPromptTopCenter();
                break;
        }
        mainView.requestLayout();
        mainView.invalidate();
    }
    
    /**
     * Show detection prompt with title
     */
    public void showDetectionPrompt(String title, String progress) {
        showDetectionPrompt(title, progress, -1, null, 0f, null, 0, null, null);
    }

    public void showDetectionPrompt(String title, String progress, int mediaId, String packageName, float detectedProgress) {
        showDetectionPrompt(title, progress, mediaId, packageName, detectedProgress, null, 0, null, null);
    }

    public void showDetectionPrompt(String title, String progress, int mediaId, String packageName, float detectedProgress, String detectedAuthor, int detectedTotalChapters) {
        showDetectionPrompt(title, progress, mediaId, packageName, detectedProgress, detectedAuthor, detectedTotalChapters, null, null);
    }

    public void showDetectionPrompt(
            String title,
            String progress,
            int mediaId,
            String packageName,
            float detectedProgress,
            String detectedAuthor,
            int detectedTotalChapters,
            String detectedDescription,
            String detectedMediaType
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final String nextTitle = title;
            final String nextProgress = progress;
            final int nextMediaId = mediaId;
            final String nextPackageName = packageName;
            final float nextDetectedProgress = detectedProgress;
            final String nextDetectedAuthor = detectedAuthor;
            final int nextDetectedTotalChapters = detectedTotalChapters;
            final String nextDetectedDescription = detectedDescription;
            final String nextDetectedMediaType = detectedMediaType;
            fadeHandler.post(() -> showDetectionPrompt(
                    nextTitle,
                    nextProgress,
                    nextMediaId,
                    nextPackageName,
                    nextDetectedProgress,
                    nextDetectedAuthor,
                    nextDetectedTotalChapters,
                    nextDetectedDescription,
                    nextDetectedMediaType
            ));
            return;
        }
        if (mainView == null) return;
        FloatingAssistantManager.getInstance(context).dismissTransientOverlays();
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        String normalizedTitle = title.trim();
        String ignoreKey = buildIgnoreKey(normalizedTitle, mediaId);
        
        // Check if this title is in ignore list and not expired
        Long ignoreTime = ignoredTitles.get(ignoreKey);
        if (ignoreTime != null && (System.currentTimeMillis() - ignoreTime) < IGNORE_TIMEOUT_MS) {
            Log.d(TAG, "Title is in ignore list: " + title);
            return;
        }
        
        // Clean up expired ignores
        cleanupExpiredIgnores();
        
        pendingTitle = normalizedTitle;
        pendingProgress = progress;
        pendingMediaId = mediaId;
        pendingPackageName = packageName;
        pendingDetectedProgress = detectedProgress;
        pendingDetectedAuthor = detectedAuthor;
        pendingDetectedTotalChapters = Math.max(0, detectedTotalChapters);
        pendingDetectedDescription = detectedDescription;
        pendingDetectedMediaType = detectedMediaType;
        String displayTitle = pendingTitle;
        if (pendingMediaId > 0) {
            String canonical = databaseHelper.getPreferredDisplayTitle(pendingMediaId);
            if (canonical != null && !canonical.trim().isEmpty()) {
                displayTitle = canonical.trim();
            }
        }
        
        TextView titleView = mainView.findViewById(R.id.detected_title);
        if (titleView != null) {
            titleView.setText(displayTitle);
        }
        Button btnYes = mainView.findViewById(R.id.btn_yes_add);
        if (btnYes != null) {
            btnYes.setText(pendingMediaId > 0 ? "Open" : "Add");
        }
        
        updateState(WidgetState.DETECTED_PROMPT);
        Log.i(TAG, "Detection prompt shown: " + title);
    }
    
    /**
     * Setup touch handling for drag and tap detection
     */
    private void setupTouchHandling() {
        if (stateIdle == null) return;
        // Restrict drag/tap capture to idle anchor only so prompt action buttons remain clickable.
        stateIdle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartTime = System.currentTimeMillis();
                    initialX = mainParams.x;
                    initialY = mainParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialTouchX;
                    float deltaY = event.getRawY() - initialTouchY;
                    
                    if (!isDragging && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
                        isDragging = true;
                        if (trashCanView != null) {
                            trashCanView.setVisibility(View.VISIBLE);
                            trashCanView.animate().alpha(1.0f).setDuration(200).start();
                        }
                    }
                    
                    if (isDragging) {
                        mainParams.x = (int) (initialX + deltaX);
                        mainParams.y = (int) (initialY + deltaY);
                        clampMainParams();
                        safeUpdateMainLayout();
                        
                        // Check magnetic snap to trash
                        if (trashCanView != null) {
                            checkMagneticSnap();
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    long touchDuration = System.currentTimeMillis() - touchStartTime;
                    float movement = (float) Math.sqrt(
                        Math.pow(event.getRawX() - initialTouchX, 2) + 
                        Math.pow(event.getRawY() - initialTouchY, 2)
                    );
                    
                    if (isDragging) {
                        // Check if released over trash
                        if (trashCanView != null && isOverTrashCan()) {
                            mainView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            hide(); // Remove widget
                            return true;
                        }
                        
                        // Hide trash can
                        if (trashCanView != null) {
                            trashCanView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                                if (trashCanView != null) {
                                    trashCanView.setVisibility(View.GONE);
                                }
                            }).start();
                        }
                        
                        isDragging = false;
                    } else if (touchDuration < 500 && movement < 10) {
                        // It's a tap
                        onWidgetTapped();
                    }
                    return true;
            }
            return false;
        });
    }
    
    /**
     * Setup all button click listeners
     */
    private void setupClickListeners() {
        // DETECTED_PROMPT buttons
        Button btnYes = mainView.findViewById(R.id.btn_yes_add);
        Button btnIgnore = mainView.findViewById(R.id.btn_ignore);
        if (btnYes != null) btnYes.setOnClickListener(v -> onYesAddClicked());
        if (btnIgnore != null) btnIgnore.setOnClickListener(v -> onIgnoreClicked());
        
        // EDIT_PROMPT buttons
        Button btnSearch = mainView.findViewById(R.id.btn_search);
        Button btnCancelEdit = mainView.findViewById(R.id.btn_cancel_edit);
        
        if (btnSearch != null) btnSearch.setOnClickListener(v -> onSearchClicked());
        if (btnCancelEdit != null) btnCancelEdit.setOnClickListener(v -> updateState(WidgetState.IDLE));
        
        // EXPANDED_MENU buttons
        Button menuForceScan = mainView.findViewById(R.id.menu_force_scan);
        Button menuDismissDetected = mainView.findViewById(R.id.menu_dismiss_detected);
        Button menuToggleTitle = mainView.findViewById(R.id.menu_toggle_title_tracking);
        Button menuToggleProgress = mainView.findViewById(R.id.menu_toggle_progress_tracking);
        Button menuOpenLibrary = mainView.findViewById(R.id.menu_open_library);
        Button menuClose = mainView.findViewById(R.id.menu_close);
        
        if (menuForceScan != null) menuForceScan.setOnClickListener(v -> onForceScanClicked(true));
        if (menuDismissDetected != null) menuDismissDetected.setOnClickListener(v -> onDismissDetectedMediaClicked());
        if (menuToggleTitle != null) menuToggleTitle.setOnClickListener(v -> toggleAutoTitleTracking());
        if (menuToggleProgress != null) menuToggleProgress.setOnClickListener(v -> toggleAutoProgressTracking());
        if (menuOpenLibrary != null) menuOpenLibrary.setOnClickListener(v -> onOpenLibraryClicked());
        if (menuClose != null) menuClose.setOnClickListener(v -> updateState(WidgetState.IDLE));
    }
    
    /**
     * Handle widget tap - show menu in IDLE state
     */
    private void onWidgetTapped() {
        if (currentState == WidgetState.IDLE) {
            updateState(WidgetState.EXPANDED_MENU);
        }
    }
    
    /**
     * Handle Yes/Add button.
     */
    private void onYesAddClicked() {
        if (pendingTitle == null) return;
        Log.d(TAG, "Detected prompt action: Add/Open clicked for " + pendingTitle + " (mediaId=" + pendingMediaId + ")");

        if (pendingMediaId > 0) {
            Intent intent = new Intent(context, DescriptionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(DescriptionActivity.EXTRA_MEDIA_ID, pendingMediaId);
            context.startActivity(intent);
            Toast.makeText(context, "Opened: " + pendingTitle, Toast.LENGTH_SHORT).show();
        } else {
            // Open AddMediaActivity with the detected title pre-filled
            // This ensures the user picks the correct result with accurate metadata
            Intent intent = new Intent(context, AddMediaActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("PREFILL_TITLE", pendingTitle);
            intent.putExtra("PREFILL_PROGRESS", pendingDetectedProgress);
            intent.putExtra("FROM_TRACKER", true);
            intent.putExtra("TRACKER_PACKAGE", pendingPackageName);
            String inferredType = inferTrackerTypeFromPackage(pendingPackageName);
            if (TextUtils.isEmpty(inferredType) && !TextUtils.isEmpty(pendingDetectedMediaType)) {
                inferredType = pendingDetectedMediaType;
            }
            if (!TextUtils.isEmpty(inferredType)) {
                intent.putExtra("PREFILL_TYPE_HINT", inferredType);
            }
            if (isWebNovelPackage(pendingPackageName)) {
                intent.putExtra("PREFILL_FORCE_MANUAL", true);
                intent.putExtra("PREFILL_SOURCE_URL", buildWebNovelSourceUrl(pendingTitle));
            }
            if (pendingDetectedAuthor != null && !pendingDetectedAuthor.trim().isEmpty()) {
                intent.putExtra("PREFILL_AUTHOR", pendingDetectedAuthor.trim());
            }
            if (pendingDetectedTotalChapters > 0) {
                intent.putExtra("PREFILL_TOTAL_COUNT", pendingDetectedTotalChapters);
            }
            if (isUsefulPrefillDescription(pendingDetectedDescription)) {
                intent.putExtra("PREFILL_DESCRIPTION", pendingDetectedDescription.trim());
            }
            context.startActivity(intent);
            Toast.makeText(context, "Search for: " + pendingTitle, Toast.LENGTH_SHORT).show();
        }
        updateState(WidgetState.IDLE);
    }
    
    /**
     * Handle Ignore button - add to blacklist
     */
    private void onIgnoreClicked() {
        Log.d(TAG, "Detected prompt action: Not Now clicked for " + pendingTitle + " (mediaId=" + pendingMediaId + ")");
        if (pendingTitle != null) {
            ignoredTitles.put(buildIgnoreKey(pendingTitle, pendingMediaId), System.currentTimeMillis());
            broadcastDetectedTitleDismissal();
            Log.d(TAG, "Added to ignore list: " + pendingTitle);
        }
        updateState(WidgetState.IDLE);
    }
    
    /**
     * Handle Search button - trigger resolver
     */
    private void onSearchClicked() {
        EditText editText = mainView.findViewById(R.id.edit_title_input);
        String searchQuery = editText.getText().toString().trim();
        
        if (searchQuery.isEmpty()) {
            Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Open AddMediaActivity with search query
        Intent intent = new Intent(context, AddMediaActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("PREFILL_TITLE", searchQuery);
        context.startActivity(intent);
        
        updateState(WidgetState.IDLE);
    }
    
    /**
     * Handle Force Scan button
     */
    private void onForceScanClicked(boolean collapseAfterAction) {
        Intent intent = new Intent(MediaMonitorService.ACTION_MANUAL_SCAN_REQUEST);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
        Toast.makeText(context, "Scanning current screen...", Toast.LENGTH_SHORT).show();
        if (collapseAfterAction) {
            updateState(WidgetState.IDLE);
        } else {
            updateTrackingToggleLabels();
        }
    }
    
    /**
     * Handle Open Library button
     */
    private void onOpenLibraryClicked() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        updateState(WidgetState.IDLE);
    }

    private void onDismissDetectedMediaClicked() {
        if (pendingTitle != null && !pendingTitle.trim().isEmpty()) {
            ignoredTitles.put(buildIgnoreKey(pendingTitle, pendingMediaId), System.currentTimeMillis());
            broadcastDetectedTitleDismissal();
            Toast.makeText(context, "Dismissed: " + pendingTitle, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "No detected title to dismiss", Toast.LENGTH_SHORT).show();
        }
        updateState(WidgetState.IDLE);
    }

    private void broadcastDetectedTitleDismissal() {
        Intent intent = new Intent(MediaMonitorService.ACTION_DISMISS_DETECTED_TITLE);
        intent.setPackage(context.getPackageName());
        intent.putExtra(MediaMonitorService.EXTRA_DETECTED_TITLE, pendingTitle);
        intent.putExtra(MediaMonitorService.EXTRA_SOURCE_PACKAGE, pendingPackageName);
        context.sendBroadcast(intent);
    }

    private boolean isWebNovelPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("webnovel")
                || normalized.contains("qidian")
                || normalized.contains("novel");
    }

    private String buildWebNovelSourceUrl(String title) {
        String safeTitle = Uri.encode(title == null ? "" : title.trim());
        return "https://www.webnovel.com/search?keywords=" + safeTitle;
    }

    private boolean isUsefulPrefillDescription(String description) {
        if (description == null) {
            return false;
        }
        String normalized = description.trim();
        if (normalized.length() < 30) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !lower.equals("book detail page")
                && !lower.equals("chapter reading")
                && !lower.equals("library list")
                && !lower.equals("manual scan title fallback")
                && !lower.contains("log in")
                && !lower.contains("sign in")
                && !lower.contains("create account");
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

    private void toggleAutoTitleTracking() {
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        boolean next = !prefs.getBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, true);
        prefs.edit().putBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, next).apply();
        updateTrackingToggleLabels();
        Toast.makeText(context, "Automatic title tracking " + (next ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
    }

    private void toggleAutoProgressTracking() {
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        boolean next = !prefs.getBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, true);
        prefs.edit().putBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, next).apply();
        updateTrackingToggleLabels();
        Toast.makeText(context, "Automatic media progress " + (next ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
    }

    private void updateTrackingToggleLabels() {
        if (mainView == null) return;
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        boolean titleEnabled = prefs.getBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, true);
        boolean progressEnabled = prefs.getBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, true);

        Button menuTitle = mainView.findViewById(R.id.menu_toggle_title_tracking);
        Button menuProgress = mainView.findViewById(R.id.menu_toggle_progress_tracking);

        String titleLabel = titleEnabled ? "Automatic title tracking: On" : "Automatic title tracking: Off";
        String progressLabel = progressEnabled ? "Automatic media progress: On" : "Automatic media progress: Off";

        if (menuTitle != null) menuTitle.setText(titleLabel);
        if (menuProgress != null) menuProgress.setText(progressLabel);
    }
    
    /**
     * Check if widget is within magnetic snap distance to trash can
     */
    private void checkMagneticSnap() {
        if (trashCanView == null || mainView == null) {
            return;
        }
        int[] trashLocation = new int[2];
        trashCanView.getLocationOnScreen(trashLocation);
        int trashCenterX = trashLocation[0] + trashCanView.getWidth() / 2;
        int trashCenterY = trashLocation[1] + trashCanView.getHeight() / 2;
        
        int[] widgetLocation = new int[2];
        mainView.getLocationOnScreen(widgetLocation);
        int widgetCenterX = widgetLocation[0] + mainView.getWidth() / 2;
        int widgetCenterY = widgetLocation[1] + mainView.getHeight() / 2;
        
        double distance = Math.sqrt(
            Math.pow(widgetCenterX - trashCenterX, 2) + 
            Math.pow(widgetCenterY - trashCenterY, 2)
        );
        
        if (distance < MAGNETIC_SNAP_DISTANCE) {
            // Magnetic snap!
            mainView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);

            if (snapAnimator != null) {
                snapAnimator.cancel();
            }
            final int startX = mainParams.x;
            final int startY = mainParams.y;
            final int targetX = trashCenterX - (mainView.getWidth() / 2);
            final int targetY = trashCenterY - (mainView.getHeight() / 2);

            snapAnimator = ValueAnimator.ofFloat(0f, 1f);
            snapAnimator.setDuration(150);
            snapAnimator.addUpdateListener(animation -> {
                float fraction = animation.getAnimatedFraction();
                mainParams.x = (int) (startX + (targetX - startX) * fraction);
                mainParams.y = (int) (startY + (targetY - startY) * fraction);
                clampMainParams();
                safeUpdateMainLayout();
            });
            snapAnimator.start();
            
            // Scale up trash can
            trashCanView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
        } else {
            // Reset trash can scale
            trashCanView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }
    }
    
    /**
     * Check if widget is over trash can
     */
    private boolean isOverTrashCan() {
        if (trashCanView == null || mainView == null) {
            return false;
        }
        int[] trashLocation = new int[2];
        trashCanView.getLocationOnScreen(trashLocation);
        int trashCenterX = trashLocation[0] + trashCanView.getWidth() / 2;
        int trashCenterY = trashLocation[1] + trashCanView.getHeight() / 2;
        
        int[] widgetLocation = new int[2];
        mainView.getLocationOnScreen(widgetLocation);
        int widgetCenterX = widgetLocation[0] + mainView.getWidth() / 2;
        int widgetCenterY = widgetLocation[1] + mainView.getHeight() / 2;
        
        double distance = Math.sqrt(
            Math.pow(widgetCenterX - trashCenterX, 2) + 
            Math.pow(widgetCenterY - trashCenterY, 2)
        );
        
        return distance < MAGNETIC_SNAP_DISTANCE;
    }
    
    /**
     * Schedule auto-fade in IDLE state
     */
    private void scheduleAutoFade() {
        cancelAutoFade();
        fadeRunnable = () -> {
            if (currentState == WidgetState.IDLE && mainView != null) {
                // Fade to semi-transparent
                mainView.animate()
                    .alpha(IDLE_ALPHA)
                    .setDuration(300)
                    .start();
            }
        };
        fadeHandler.postDelayed(fadeRunnable, FADE_DELAY_MS);
    }
    
    /**
     * Cancel auto-fade
     */
    private void cancelAutoFade() {
        if (fadeRunnable != null) {
            fadeHandler.removeCallbacks(fadeRunnable);
        }
    }
    
    /**
     * Restore visibility (cancel fade)
     */
    private void restoreVisibility() {
        if (mainView != null) {
            mainView.animate()
                .alpha(1.0f)
                .translationX(0)
                .translationY(0)
                .setDuration(200)
                .start();
        }
    }
    
    /**
     * Setup keyboard avoidance
     */
    private void setupKeyboardAvoidance() {
        mainView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastVisibleHeight = 0;
            
            @Override
            public void onGlobalLayout() {
                if (mainView == null) return;
                
                int visibleHeight = mainView.getRootView().getHeight();
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                int heightDiff = screenHeight - visibleHeight;
                
                // Keyboard is visible if height difference > 200px
                boolean nowVisible = heightDiff > 200;
                if (currentState != WidgetState.EDIT_PROMPT) {
                    if (keyboardVisible || mainView.getTranslationY() != 0f) {
                        keyboardVisible = false;
                        mainView.animate()
                                .translationY(0)
                                .setDuration(150)
                                .start();
                    }
                    return;
                }
                
                if (nowVisible != keyboardVisible) {
                    keyboardVisible = nowVisible;
                    
                    if (keyboardVisible) {
                        // Move widget above keyboard
                        int keyboardHeight = heightDiff;
                        int newY = screenHeight - keyboardHeight - mainView.getHeight() - 50;
                        
                        mainView.animate()
                            .translationY(newY - mainParams.y)
                            .setDuration(200)
                            .start();
                    } else {
                        // Restore original position
                        mainView.animate()
                            .translationY(0)
                            .setDuration(200)
                            .start();
                    }
                }
                
                lastVisibleHeight = visibleHeight;
            }
        });
    }
    
    /**
     * Setup clipboard listener for media URLs
     */
    private void setupClipboardListener() {
        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = () -> {
            if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = clipboardManager.getPrimaryClip().getItemAt(0).getText();
                if (clip != null) {
                    String text = clip.toString();
                    // Check if it's a media URL (basic detection)
                    if (text.contains("webnovel") || text.contains("manga") || text.contains("anime")) {
                        Log.d(TAG, "Detected media URL in clipboard: " + text);
                        // TODO: Parse URL and extract title
                        // For now, just show prompt with URL
                        showDetectionPrompt("Clipboard: " + text.substring(0, Math.min(50, text.length())), null);
                    }
                }
            }
        };
    }

    private void registerClipboardListener() {
        if (clipboardManager == null || clipboardListener == null || clipboardListenerRegistered) {
            return;
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        clipboardListenerRegistered = true;
    }

    private void unregisterClipboardListener() {
        if (clipboardManager == null || clipboardListener == null || !clipboardListenerRegistered) {
            return;
        }
        clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        clipboardListenerRegistered = false;
    }
    
    /**
     * Clean up expired ignore entries
     */
    private void cleanupExpiredIgnores() {
        long now = System.currentTimeMillis();
        Set<String> toRemove = new HashSet<>();
        
        for (Map.Entry<String, Long> entry : ignoredTitles.entrySet()) {
            if (now - entry.getValue() > IGNORE_TIMEOUT_MS) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String key : toRemove) {
            ignoredTitles.remove(key);
        }
    }

    private String buildIgnoreKey(String title, int mediaId) {
        if (mediaId > 0) {
            return "media:" + mediaId;
        }
        if (title == null) {
            return "title:unknown";
        }
        return "title:" + title.trim().toLowerCase(Locale.ROOT);
    }
    
    /**
     * Show success checkmark animation
     */
    private void showSuccessAnimation() {
        // TODO: Add checkmark animation
        Toast.makeText(context, "✓ Added successfully", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Resize widget
     */
    private void resizeWidget(int width, int height) {
        mainParams.width = width;
        mainParams.height = height;
        clampMainParams();
        safeUpdateMainLayout();
    }

    private void rememberIdlePosition() {
        if (mainParams == null) return;
        lastIdleX = mainParams.x;
        lastIdleY = mainParams.y;
    }

    private void restoreIdlePosition() {
        if (mainParams == null || mainView == null) return;
        mainParams.x = lastIdleX;
        mainParams.y = lastIdleY;
        clampMainParams();
        safeUpdateMainLayout();
    }

    private void pinPromptTopCenter() {
        if (mainView == null || mainParams == null) return;
        mainView.post(() -> {
            if (mainView == null || mainParams == null) return;
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int widgetWidth = getMeasuredWidth(mainView);
            int widgetHeight = getMeasuredHeight(mainView);
            int minTop = getSafeTopInsetPx();
            mainParams.x = Math.max(0, (screenWidth - Math.max(1, widgetWidth)) / 2);
            int preferredY = minTop + dpToPx(PROMPT_TOP_OFFSET_DP);
            int maxY = Math.max(minTop, screenHeight - Math.max(1, widgetHeight) - dpToPx(12));
            mainParams.y = Math.min(preferredY, maxY);
            clampMainParams();
            safeUpdateMainLayout();
        });
    }

    private void constrainPromptLayoutWidths() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int maxAllowed = Math.max(dpToPx(240), screenWidth - dpToPx(24));
        int promptWidth = Math.min(dpToPx(360), maxAllowed);
        int quickActionsWidth = Math.min(dpToPx(320), maxAllowed);
        int maxPromptHeight = Math.max(dpToPx(200), screenHeight - getSafeTopInsetPx() - dpToPx(24));
        int maxQuickActionsHeight = Math.max(dpToPx(180), screenHeight - getSafeTopInsetPx() - dpToPx(24));
        applyViewBounds(stateDetectedPrompt, promptWidth, maxPromptHeight);
        applyViewBounds(stateEditPrompt, promptWidth, maxPromptHeight);
        applyViewBounds(stateExpandedMenu, quickActionsWidth, maxQuickActionsHeight);
    }

    private void applyViewBounds(View target, int widthPx, int maxHeightPx) {
        if (target == null || widthPx <= 0) return;
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params.width = widthPx;
        }
        if (maxHeightPx > 0) {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.AT_MOST);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            target.measure(widthSpec, heightSpec);
            int measuredHeight = target.getMeasuredHeight();
            params.height = measuredHeight > maxHeightPx ? maxHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        target.setLayoutParams(params);
    }
    
    /**
     * Show keyboard
     */
    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            view.postDelayed(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT), 100);
        }
    }
    
    /**
     * Create base window params
     */
    private WindowManager.LayoutParams createBaseParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private void applyWindowInteractivity(boolean interactive) {
        if (mainView == null || mainParams == null) return;
        int desiredFlags = interactive
                ? WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (mainParams.flags == desiredFlags) {
            return;
        }
        mainParams.flags = desiredFlags;
        try {
            safeUpdateMainLayout();
        } catch (Exception e) {
            Log.w(TAG, "Failed to update overlay interactivity flags", e);
        }
    }
    
    /**
     * Handle configuration changes (rotation)
     */
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "Configuration changed");
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fadeHandler.post(() -> onConfigurationChanged(newConfig));
            return;
        }
        if (mainView == null || mainParams == null) return;
        if (currentState == WidgetState.IDLE) {
            clampMainParams();
            safeUpdateMainLayout();
            return;
        }
        constrainPromptLayoutWidths();
        pinPromptTopCenter();
    }

    private void clampMainParams() {
        if (mainParams == null) return;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int minTop = getSafeTopInsetPx();

        int widgetWidth = getMeasuredWidth(mainView);
        int widgetHeight = getMeasuredHeight(mainView);

        int maxX = Math.max(0, screenWidth - Math.max(1, widgetWidth));
        int maxY = Math.max(minTop, screenHeight - Math.max(1, widgetHeight));

        mainParams.x = Math.max(0, Math.min(mainParams.x, maxX));
        mainParams.y = Math.max(minTop, Math.min(mainParams.y, maxY));
    }

    private int getMeasuredWidth(View view) {
        if (view == null) return 0;
        if (view.getWidth() > 0) return view.getWidth();
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredWidth();
    }

    private int getMeasuredHeight(View view) {
        if (view == null) return 0;
        if (view.getHeight() > 0) return view.getHeight();
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
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
        return Math.max(minTop, statusBarHeight + dpToPx(PROMPT_SAFE_PADDING_DP));
    }

    private void safeUpdateMainLayout() {
        if (mainView == null || mainParams == null || !isViewAttached(mainView)) {
            return;
        }
        try {
            windowManager.updateViewLayout(mainView, mainParams);
        } catch (Exception e) {
            Log.w(TAG, "Failed to update main overlay layout", e);
        }
    }

    private boolean isViewAttached(View view) {
        return view != null && view.getParent() != null;
    }
}
