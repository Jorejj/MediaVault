package com.example.mediavault.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.api.MediaMetadataProfile;
import com.example.mediavault.api.MediaSearchManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaMatcher {
    private static final String TAG = "MediaMatcher";
    private static final String SETTINGS_PREFS = "Settings";
    private static final String PREF_AUTO_TITLE_TRACKING_ENABLED = "auto_title_tracking_enabled";
    private static final String PREF_AUTO_PROGRESS_TRACKING_ENABLED = "auto_progress_tracking_enabled";

    /**
     * Wrapper for parsed media information.
     */
    public static class MediaMatch {
        public final String title;
        public final float progress;

        public MediaMatch(String title, float progress) {
            this.title = title;
            this.progress = progress;
        }
    }

    public static class DetectedMediaCandidate {
        public final String packageName;
        public final String rawTitle;
        public final float progress;
        public final float confidence;
        public final Map<String, String> externalIds;

        public DetectedMediaCandidate(String packageName, String rawTitle, float progress, float confidence, Map<String, String> externalIds) {
            this.packageName = packageName;
            this.rawTitle = rawTitle;
            this.progress = progress;
            this.confidence = confidence;
            this.externalIds = externalIds;
        }
    }

    // Prioritized Regex Waterfall (Patterns)
    
    // 0. WebNovel/Qidian Format: "123  Chapter 123: Title Goes Here"
    private static final Pattern WEBNOVEL_PATTERN = Pattern.compile("^(\\d+(?:[\\.\\-]\\d+)?)\\s+Chapter\\s+\\d+(?:[\\.\\-]\\d+)?\\s*:\\s*(.+?)\\s*$");
    
    // 1. Kotatsu/Manga Format: Vol. 2 Ch. 15.5 or Volume 1 Chapter 12
    private static final Pattern KOTATSU_PATTERN = Pattern.compile("(?i)^(.*?)\\s*\\bVol(?:ume)?\\.?\\s*\\d+\\s+Ch(?:apter)?\\.?\\s*(\\d+(?:[\\.\\-]\\d+)?)$");

    // 2. WEBTOON Format: [Title] - Ep. 45 or Ep. 120 - [Title]
    private static final Pattern WEBTOON_A_PATTERN = Pattern.compile("^(.*?)\\s+-\\s+Ep\\.\\s*(\\d+(?:[\\.\\-]\\d+)?)$");
    private static final Pattern WEBTOON_B_PATTERN = Pattern.compile("^Ep\\.\\s*(\\d+(?:[\\.\\-]\\d+)?)\\s+-\\s+(.*?)$");

    // 3. Browser Standard: Read [Title] Chapter 24.2 Online - [Site Name]
    private static final Pattern BROWSER_PATTERN = Pattern.compile("(?i)^Read\\s+(.*?)\\s+Chapter\\s+(\\d+(?:[\\.\\-]\\d+)?)\\s+Online.*");

    // 4. Tachiyomi/Mihon Standard: [Title] - Ch. 104
    private static final Pattern TACHIYOMI_PATTERN = Pattern.compile("^(.*?)\\s+-\\s+Ch\\.\\s*(\\d+(?:[\\.\\-]\\d+)?)$");

    // 5. Anime Standard: [Title] Episode 12 English Subbed
    private static final Pattern ANIME_PATTERN = Pattern.compile("(?i)^(.*?)\\s+Episode\\s+(\\d+(?:[\\.\\-]\\d+)?).*");

    // Fallback for generic patterns like "Chapter 123"
    private static final Pattern GENERIC_PROGRESS_PATTERN = Pattern.compile("(?i)(?:\\b(?:chapter|ch\\.?|episode|ep\\.?)\\s*)(\\d+(?:[\\.\\-]\\d+)?)");
    private static final Pattern BILIBILI_DURATION_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*/\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern BILIBILI_TIMECODE_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern BILIBILI_CONTROL_PATTERN = Pattern.compile("(?i).*(danmaku|弹幕|播放|暂停|倍速|清晰度|全屏|画质|subtitles?|captions?|fullscreen|quality|speed|pause|play).*");
    private static final Pattern BILIBILI_EPISODE_PATTERN = Pattern.compile("(?i).*(\\bep\\s*\\d+\\b|\\bepisode\\s*\\d+\\b|第\\s*\\d+\\s*[话集]).*");
    private static final Pattern BILIBILI_EP_PREFIX_PATTERN = Pattern.compile("(?i)(?:^|\\b)(?:ep(?:isode)?\\.?\\s*|e\\s*)(\\d{1,4})(?:\\b|$)");
    private static final Pattern BILIBILI_CN_EP_PATTERN = Pattern.compile("(?i)第\\s*(\\d{1,4})\\s*[话集]");
    private static final Pattern BILIBILI_METRIC_PATTERN = Pattern.compile("(?i)^\\d+(?:[\\.,]\\d+)?\\s*[kmbw万亿]$");
    private static final Pattern BILIBILI_SELECTED_PATTERN = Pattern.compile("(?i).*(selected|current|playing|watching|now\\s*playing|continue\\s*watching|已选|选中|当前|正在播放|在看|續看).*");
    private static final Pattern AUTH_NOISE_PATTERN = Pattern.compile("(?i).*(log\\s*in|login|sign\\s*in|sign\\s*up|register|create\\s+account|verification\\s*code|otp|captcha).*");
    private static final Pattern STATUS_SOURCE_LINE_PATTERN = Pattern.compile("(?i)^(?:ongoing|completed|hiatus|cancelled|canceled|dropped|publishing|finished)(?:\\s*[·•\\-]\\s*[\\p{L}\\p{M}\\d][\\p{L}\\p{M}\\d\\s\\.,'’:_&\\-]{1,60})?$");
    private static final Pattern DETAIL_TAB_PATTERN = Pattern.compile("(?i)^(?:in\\s+library|soon|tracking|webview|overview|details|chapters?|related|similar|description|reviews?)$");
    private static final Pattern AUTHOR_LIST_PATTERN = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M}'’\\-. ]{0,30}(?:,\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}'’\\-. ]{0,30}){1,4}$");
    private static final Pattern BILIBILI_TITLE_NOISE_PATTERN = Pattern.compile("(?i)^(?:license\\s*premium|premium|vip|vip\\s*exclusive|licensed|member(?:ship)?\\s*only|subscribe\\s*now|unlock\\s*all|watch\\s*with\\s*premium|trial\\s*member)$");
    private static final Pattern WEBNOVEL_TITLE_NOISE_PATTERN = Pattern.compile("(?i)^(?:novel\\s*fantasy|fantasy|author|authors?|status|ongoing|completed|chapters?\\s*updated|read\\s*now|contents?|book\\s*detail|book\\s*description|top\\s*up|power\\s*stone|energy\\s*stone|gift\\s*ranking)$");

    private final DatabaseHelper dbHelper;
    private final FloatingAssistantManager assistantManager;
    private final Context appContext;
    private final Map<Integer, Long> lastUpdateMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> titleConfidenceMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTitleDetectionMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> persistentTextMap = new ConcurrentHashMap<>(); // key: package|title -> count
    private final Map<String, String> packageTitleSession = new ConcurrentHashMap<>(); // packageName -> canonical book title
    private final Map<String, String> packageLastDetectedTitle = new ConcurrentHashMap<>(); // packageName -> last stable title
    private final Map<String, Float> packageLastProgress = new ConcurrentHashMap<>(); // packageName -> latest parsed progress
    private final Map<String, Long> packageLastScrollUpdate = new ConcurrentHashMap<>(); // packageName -> last synthetic update time
    private final Map<String, Long> packageLastNoTextSeen = new ConcurrentHashMap<>(); // packageName -> last no-text observation time
    private final Map<String, Long> dismissedTitleUntilMap = new ConcurrentHashMap<>();
    private final Map<String, Long> dismissedPackageUntilMap = new ConcurrentHashMap<>();
    
    private static final long DEBOUNCE_WINDOW_REGEX_MS = 60_000L;
    private static final long DEBOUNCE_WINDOW_SYNTHETIC_MS = 30_000L;
    private static final long TITLE_DISMISS_COOLDOWN_MS = 5 * 60_000L;
    private static final int CONFIDENCE_THRESHOLD = 3;
    private static final Set<String> TITLE_NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "novel", "webnovel", "book", "genre", "genres", "fantasy", "action", "adventure",
            "romance", "drama", "comedy", "slice", "life", "isekai", "martial", "arts",
            "historical", "history", "sci", "fi", "science", "fiction", "horror", "mystery",
            "thriller", "magic", "supernatural", "system", "urban", "school", "xianxia", "wuxia",
            "fanfic", "fanfiction", "movie", "movies", "film", "films", "tv", "show", "shows",
            "series", "anime", "manga", "manhwa", "manhua", "comic", "comics", "webtoon",
            "media", "content", "novels", "chapters", "chapter", "latest", "popular", "trending",
            "ongoing", "completed", "status", "author", "creator", "artist", "tracking", "webview",
            "library", "bookmark", "soon"
    ));

    public MediaMatcher(Context context) {
        this.appContext = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(appContext);
        this.assistantManager = FloatingAssistantManager.getInstance(appContext);
    }

    /**
     * Core parsing method utilizing the Regex Waterfall.
     * @param raw The raw string pulled from the screen.
     * @return MediaMatch object or null if no match found or parsing fails.
     */
    public static MediaMatch parseMedia(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String input = raw.trim();
        Matcher m;

        // 0. WebNovel/Qidian Format (Check first - most specific)
        m = WEBNOVEL_PATTERN.matcher(input);
        if (m.find()) {
            // Group 1 = chapter number, Group 2 = chapter title/body fragment.
            // Never promote chapter title to canonical media title.
            return createMatch(null, m.group(1));
        }

        // 1. Kotatsu/Manga
        m = KOTATSU_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(1), m.group(2));

        // 2. WEBTOON (A: Title first, B: Episode first)
        m = WEBTOON_A_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(1), m.group(2));
        m = WEBTOON_B_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(2), m.group(1));

        // 3. Browser Standard
        m = BROWSER_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(1), m.group(2));

        // 4. Tachiyomi/Mihon
        m = TACHIYOMI_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(1), m.group(2));

        // 5. Anime Standard
        m = ANIME_PATTERN.matcher(input);
        if (m.find()) return createMatch(m.group(1), m.group(2));

        // Fallback: Generic progress
        m = GENERIC_PROGRESS_PATTERN.matcher(input);
        if (m.find()) return createMatch(null, m.group(1));

        return null;
    }

    private static MediaMatch createMatch(String title, String progressStr) {
        try {
            // Support both '.' and '-' as decimal separators for fragmented chapters (e.g., 10-1 -> 10.1)
            String normalizedProgress = progressStr.replace('-', '.');
            float progress = Float.parseFloat(normalizedProgress);
            String cleanTitle = (title == null || title.trim().isEmpty()) ? null : title.trim();
            return new MediaMatch(cleanTitle, progress);
        } catch (NumberFormatException e) {
            // Requirement: Catch NumberFormatException and return null
            return null;
        }
    }

    public void matchAndProcess(List<String> textNodes, String packageName, ScreenContextDetector.ScreenContext context) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            Log.d(TAG, "matchAndProcess: processing " + textNodes.size() + " nodes");
            boolean autoProgressEnabled = isAutoProgressTrackingEnabled();
            boolean bilibiliPackage = isBilibiliPackage(packageName);
            boolean bilibiliPlayerState = !bilibiliPackage || isBilibiliPlayerState(textNodes, context);
            boolean strictMangaReaderPackage = isMangaReaderPackage(packageName);

            // Canonical title session from detected MEDIA_DETAIL pages
            String sessionTitle = packageTitleSession.get(packageName);
            if (!(bilibiliPackage && !bilibiliPlayerState)
                    && context != null
                    && context.type == ScreenContextDetector.ScreenType.MEDIA_DETAIL
                    && isLikelyTitleForPackage(packageName, context.extractedTitle)) {
                sessionTitle = context.extractedTitle.trim();
                packageTitleSession.put(packageName, sessionTitle);
                packageLastDetectedTitle.put(packageName, sessionTitle);
                Log.i(TAG, "Canonical title session set: " + sessionTitle + " (" + packageName + ")");
                enrichExistingMetadataFromContext(sessionTitle, context);
            }
            if (bilibiliPackage && !bilibiliPlayerState) {
                sessionTitle = null;
                packageTitleSession.remove(packageName);
                packageLastDetectedTitle.remove(packageName);
            }
            if (sessionTitle != null) {
                float contextProgress = (context != null && context.extractedProgress > 0f) ? context.extractedProgress : 0f;
                if (resolveExistingMediaId(sessionTitle, null) <= 0) {
                    handlePotentialNewTitle(packageName, sessionTitle, contextProgress);
                }
            }

            // Track persistent text (potential book title)
            if (!(bilibiliPackage && !bilibiliPlayerState) && !strictMangaReaderPackage) {
                for (int index = 0; index < textNodes.size(); index++) {
                    String node = textNodes.get(index);
                    String trimmed = node == null ? "" : node.trim();
                    // Only consider consistent canonical-title-like text.
                    if (trimmed.length() >= 3 && trimmed.length() <= 70 && isLikelyTitleForPackage(packageName, trimmed)) {
                        String persistentKey = buildPersistentKey(packageName, trimmed);
                        int seenCount = persistentTextMap.getOrDefault(persistentKey, 0) + 1;
                        persistentTextMap.put(persistentKey, seenCount);

                        // If text appears 3+ times, it's likely the book title
                        if (seenCount >= 3) {
                            String previousDetected = packageLastDetectedTitle.get(packageName);
                            if (!isLikelyTitleForPackage(packageName, previousDetected) || trimmed.length() > previousDetected.length()) {
                                packageLastDetectedTitle.put(packageName, trimmed);
                                Log.i(TAG, "Detected persistent book title: " + trimmed + " (" + packageName + ")");
                            }
                        }
                    }
                }
            }

            String fallbackDetectedTitle = packageLastDetectedTitle.get(packageName);
            if (sessionTitle == null && isLikelyTitleForPackage(packageName, fallbackDetectedTitle)) {
                sessionTitle = fallbackDetectedTitle;
                packageTitleSession.put(packageName, sessionTitle);
            }

            // Collect all potential matches from screen
            Map<String, Float> screenMatches = new java.util.HashMap<>();
            int matchCount = 0;
            for (String node : textNodes) {
                if (bilibiliPackage && !bilibiliPlayerState) {
                    continue;
                }
                MediaMatch match = parseMedia(node);
                if (match != null) {
                    String canonicalTitle = sessionTitle;
                    if (canonicalTitle == null && isLikelyTitleForPackage(packageName, match.title)) {
                        canonicalTitle = match.title.trim();
                    }

                    if (canonicalTitle != null) {
                        String key = canonicalTitle.toLowerCase(Locale.ROOT);
                        float existing = screenMatches.containsKey(key) ? screenMatches.get(key) : -1f;
                        float merged = Math.max(existing, match.progress);
                        screenMatches.put(key, merged);
                        packageLastProgress.put(packageName, merged);
                    }
                    matchCount++;
                }
            }

            // If immersive reader provides weak text signals, use context-extracted chapter progress.
            if (context != null
                    && context.type == ScreenContextDetector.ScreenType.CHAPTER_READING
                    && sessionTitle != null
                    && context.extractedProgress > 0f) {
                String key = sessionTitle.toLowerCase(Locale.ROOT);
                float existing = screenMatches.containsKey(key) ? screenMatches.get(key) : -1f;
                float merged = Math.max(existing, context.extractedProgress);
                screenMatches.put(key, merged);
                packageLastProgress.put(packageName, merged);
            }

            Log.d(TAG, "Found " + matchCount + " regex matches from " + textNodes.size() + " text nodes");
            if (matchCount > 0) {
                Log.d(TAG, "Detected canonical titles: " + screenMatches.keySet().toString());
                if (sessionTitle != null) {
                    Log.d(TAG, "Session title: " + sessionTitle);
                }
            } else if (matchCount == 0) {
                Log.d(TAG, "No matches found - sample texts: " + textNodes.subList(0, Math.min(5, textNodes.size())));
            }

            Cursor cursor = dbHelper.getAllMediaWithMetadata();
            if (cursor == null) return;

            Set<String> knownNormalizedTitles = new HashSet<>();
            try {
                int canonicalIndex = cursor.getColumnIndex("metadata_canonical_title");
                int altTitlesIndex = cursor.getColumnIndex("metadata_alt_titles_json");
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                    String dbTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                    String contentType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CONTENT_TYPE));
                    String mediaType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                    float currentProgress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                    float currentRating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));
                    int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));

                    String canonicalTitle = canonicalIndex >= 0 ? cursor.getString(canonicalIndex) : null;
                    String altTitlesJson = altTitlesIndex >= 0 ? cursor.getString(altTitlesIndex) : null;
                    List<String> titleCandidates = collectTitleCandidates(dbTitle, canonicalTitle, altTitlesJson);
                    String displayTitle = (canonicalTitle != null && !canonicalTitle.trim().isEmpty()) ? canonicalTitle.trim() : dbTitle;

                    for (String candidate : titleCandidates) {
                        String normalized = MediaMetadataProfile.normalizeTitle(candidate);
                        if (normalized != null) {
                            knownNormalizedTitles.add(normalized);
                        }
                    }

                    float foundProgress = extractProgress(textNodes, titleCandidates, currentProgress, packageName, sessionTitle);
                    if (bilibiliPackage) {
                        if (!bilibiliPlayerState) {
                            continue;
                        }
                        if (!isValidBilibiliProgress(foundProgress, currentProgress, total)) {
                            continue;
                        }
                    }
                      
                    if (autoProgressEnabled && foundProgress > currentProgress && foundProgress <= total) {
                        if (shouldUpdate(id, DEBOUNCE_WINDOW_REGEX_MS)) {
                            performUpdate(id, displayTitle, contentType, mediaType, foundProgress, currentProgress, total, currentRating);
                        }
                    }
                }
            } finally {
                cursor.close();
            }

            // Phase 1: New Title Detection
            for (Map.Entry<String, Float> entry : screenMatches.entrySet()) {
                String screenTitle = entry.getKey();
                String normalizedScreenTitle = MediaMetadataProfile.normalizeTitle(screenTitle);
                boolean alreadyInDb = matchesKnownTitle(normalizedScreenTitle, knownNormalizedTitles);

                if (!alreadyInDb) {
                    String titleToPrompt = (sessionTitle != null) ? sessionTitle
                            : (isLikelyTitleForPackage(packageName, fallbackDetectedTitle) ? fallbackDetectedTitle : screenTitle);
                    if (isLikelyTitleForPackage(packageName, titleToPrompt)) {
                        DetectedMediaCandidate candidate = new DetectedMediaCandidate(
                                packageName,
                                titleToPrompt,
                                entry.getValue(),
                                0.35f,
                                null
                        );
                        int resolvedMediaId = resolveExistingMediaId(candidate.rawTitle, candidate.externalIds);
                        if (resolvedMediaId > 0) {
                            recordAccessibilitySignal(resolvedMediaId, candidate);
                        } else {
                            handlePotentialNewTitle(packageName, titleToPrompt, entry.getValue());
                        }
                    }
                }
            }
        });
    }

    private void handlePotentialNewTitle(String packageName, String title, float progress) {
        if (!isAutoTitleTrackingEnabled()) {
            return;
        }
        if (!isLikelyTitleForPackage(packageName, title)) {
            return;
        }
        if (isTitlePromptSuppressed(packageName, title)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastSeen = lastTitleDetectionMap.get(title);
        
        // Reset confidence if too much time has passed
        if (lastSeen != null && (now - lastSeen) > 600000) { // 10 minutes
            titleConfidenceMap.put(title, 0);
        }
        
        int confidence = titleConfidenceMap.getOrDefault(title, 0) + 1;
        titleConfidenceMap.put(title, confidence);
        lastTitleDetectionMap.put(title, now);

        if (confidence == CONFIDENCE_THRESHOLD) {
            // Capitalize for UI
            String displayTitle = title.substring(0, 1).toUpperCase(Locale.ROOT) + title.substring(1);
            if (isFloatingAssistantEnabled()) {
                assistantManager.showNewTitlePrompt(displayTitle, progress, packageName);
            }
        }
    }

    public void dismissDetectedTitle(String title, String packageName, long cooldownMs) {
        long now = System.currentTimeMillis();
        long until = now + (cooldownMs > 0 ? cooldownMs : TITLE_DISMISS_COOLDOWN_MS);
        if (packageName != null && !packageName.trim().isEmpty()) {
            dismissedPackageUntilMap.put(packageName.trim().toLowerCase(Locale.US), until);
        }
        String key = normalizeSuppressionKey(title);
        if (key != null) {
            dismissedTitleUntilMap.put(key, until);
        }
        if (title != null) {
            titleConfidenceMap.put(title, 0);
            lastTitleDetectionMap.put(title, now);
        }
        Log.d(TAG, "Suppressed detected title prompt for cooldown: title=" + title + ", package=" + packageName);
    }

    private boolean isTitlePromptSuppressed(String packageName, String title) {
        long now = System.currentTimeMillis();

        if (packageName != null && !packageName.trim().isEmpty()) {
            String pkgKey = packageName.trim().toLowerCase(Locale.US);
            Long packageUntil = dismissedPackageUntilMap.get(pkgKey);
            if (packageUntil != null) {
                if (now < packageUntil) {
                    return true;
                }
                dismissedPackageUntilMap.remove(pkgKey);
            }
        }

        String key = normalizeSuppressionKey(title);
        if (key == null) {
            return false;
        }
        Long titleUntil = dismissedTitleUntilMap.get(key);
        if (titleUntil != null) {
            if (now < titleUntil) {
                return true;
            }
            dismissedTitleUntilMap.remove(key);
        }
        return false;
    }

    private String normalizeSuppressionKey(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        return MediaMetadataProfile.normalizeTitle(title);
    }

    private static List<String> collectTitleCandidates(String dbTitle, String canonicalTitle, String altTitlesJson) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (dbTitle != null && !dbTitle.trim().isEmpty()) {
            candidates.add(dbTitle.trim());
        }
        if (canonicalTitle != null && !canonicalTitle.trim().isEmpty()) {
            candidates.add(canonicalTitle.trim());
        }
        if (altTitlesJson != null && !altTitlesJson.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(altTitlesJson);
                for (int i = 0; i < array.length(); i++) {
                    String value = array.optString(i, null);
                    if (value != null && !value.trim().isEmpty()) {
                        candidates.add(value.trim());
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        return new java.util.ArrayList<>(candidates);
    }

    private static boolean matchesKnownTitle(String normalizedScreenTitle, Set<String> knownNormalizedTitles) {
        if (normalizedScreenTitle == null || knownNormalizedTitles == null || knownNormalizedTitles.isEmpty()) {
            return false;
        }
        for (String knownTitle : knownNormalizedTitles) {
            if (knownTitle == null || knownTitle.isEmpty()) {
                continue;
            }
            if (normalizedScreenTitle.equals(knownTitle)
                    || normalizedScreenTitle.contains(knownTitle)
                    || knownTitle.contains(normalizedScreenTitle)) {
                return true;
            }
        }
        return false;
    }

    public int resolveExistingMediaId(String title) {
        return resolveExistingMediaId(title, null);
    }

    public int resolveExistingMediaId(String title, Map<String, String> externalIds) {
        if (title == null || title.trim().isEmpty()) {
            return -1;
        }
        return dbHelper.findMediaIdByMetadataCandidate(title, externalIds);
    }

    public void recordAccessibilitySignal(int mediaId, DetectedMediaCandidate candidate) {
        if (mediaId <= 0 || candidate == null || candidate.rawTitle == null || candidate.rawTitle.trim().isEmpty()) {
            return;
        }
        AppExecutor.getInstance().diskIO().execute(() -> {
            MediaMetadataProfile profile = MediaMetadataProfile.create()
                    .withCanonicalTitle(candidate.rawTitle.trim())
                    .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(candidate.rawTitle))
                    .withProviderId(candidate.packageName)
                    .withProviderSlug("accessibility")
                    .withMetadataSource("accessibility")
                    .withProviderFeaturesJson(buildAccessibilityFeaturesJson(candidate.packageName))
                    .withMetadataConfidence(Math.max(0.2f, candidate.confidence))
                    .withMetadataPriority(10)
                    .stampNow()
                    .addTag("signal:accessibility");
            if (candidate.packageName != null && !candidate.packageName.trim().isEmpty()) {
                profile.addTag("package:" + candidate.packageName.trim().toLowerCase(Locale.US));
            }
            if (candidate.progress > 0f) {
                profile.addTag("observed_progress:" + String.format(Locale.US, "%.2f", candidate.progress));
            }
            if (candidate.externalIds != null && !candidate.externalIds.isEmpty()) {
                for (Map.Entry<String, String> entry : candidate.externalIds.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        profile.addExternalId(entry.getKey(), entry.getValue());
                    }
                }
            }
            dbHelper.mergeAndUpsertMetadata(mediaId, profile, "accessibility");
        });
    }

    private String buildAccessibilityFeaturesJson(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return "{\"signal\":\"accessibility\"}";
        }
        String safePackage = packageName.replace("\"", "");
        return "{\"signal\":\"accessibility\",\"package\":\"" + safePackage + "\"}";
    }

    /**
     * Logic for extracting progress from a list of text nodes based on a target title.
     */
    private static float extractProgress(List<String> textNodes, List<String> titleCandidates, float currentProgress, String packageName, String sessionTitle) {
        float bestMatch = -1f;
        boolean bilibiliPackage = isBilibiliPackage(packageName);
        List<String> normalizedCandidates = new java.util.ArrayList<>();
        if (titleCandidates != null) {
            for (String candidate : titleCandidates) {
                if (candidate != null && !candidate.trim().isEmpty()) {
                    normalizedCandidates.add(candidate.trim().toLowerCase(Locale.US));
                }
            }
        }
        
        // Check if the DB title is present in ANY node first.
        // This helps when title and progress are in separate nodes.
        boolean titleFoundInNodes = false;
        for (String node : textNodes) {
            if (containsAnyCandidate(node, normalizedCandidates)) {
                titleFoundInNodes = true;
                break;
            }
        }
        if (!titleFoundInNodes && sessionTitle != null && !sessionTitle.isEmpty() && !normalizedCandidates.isEmpty()) {
            String normalizedSession = sessionTitle.toLowerCase(Locale.US);
            for (String candidate : normalizedCandidates) {
                if (normalizedSession.contains(candidate) || candidate.contains(normalizedSession)) {
                    titleFoundInNodes = true;
                    break;
                }
            }
        }

        for (String node : textNodes) {
            MediaMatch match = parseMedia(node);
            
            if (match != null) {
                // If the pattern extracted a title, it must match our database title
                if (match.title != null) {
                    String normalizedMatchTitle = match.title.toLowerCase(Locale.US);
                    boolean matchedCandidate = false;
                    for (String candidate : normalizedCandidates) {
                        if (normalizedMatchTitle.contains(candidate) || candidate.contains(normalizedMatchTitle)) {
                            matchedCandidate = true;
                            break;
                        }
                    }
                    if (matchedCandidate) {
                        if (match.progress > currentProgress && (bestMatch == -1 || match.progress < bestMatch)) {
                            bestMatch = match.progress;
                        }
                    }
                } else {
                    // If no title was extracted (generic fallback), 
                    // we verify if the title was found in THIS node OR in ANY other node.
                    if (titleFoundInNodes || containsAnyCandidate(node, normalizedCandidates)) {
                        if (match.progress > currentProgress && (bestMatch == -1 || match.progress < bestMatch)) {
                            bestMatch = match.progress;
                        }
                    }
                }
            }
            
            // Special Case: Bare numbers (Kotatsu often shows just the chapter number in its own node)
            if (bestMatch == -1f && "org.koitharu.kotatsu".equals(packageName) && titleFoundInNodes) {
                String trimmed = node.trim();
                if (trimmed.matches("^\\d+(\\.\\d+)?$")) {
                    try {
                        float found = Float.parseFloat(trimmed);
                        if (found > currentProgress && found <= currentProgress + 5) {
                            bestMatch = found;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (bestMatch == -1f && bilibiliPackage && titleFoundInNodes) {
            float selected = extractBilibiliEpisodeCandidate(textNodes, true);
            if (selected > currentProgress) {
                bestMatch = selected;
            }
        }
        return bestMatch;
    }

    private static boolean containsAnyCandidate(String text, List<String> normalizedCandidates) {
        if (text == null || normalizedCandidates == null || normalizedCandidates.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.US);
        for (String candidate : normalizedCandidates) {
            if (candidate != null && !candidate.isEmpty() && lower.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String buildPersistentKey(String packageName, String title) {
        String pkg = packageName == null ? "unknown" : packageName.trim().toLowerCase(Locale.US);
        String value = title == null ? "" : title.trim().toLowerCase(Locale.US);
        return pkg + "|" + value;
    }

    private static boolean isLikelyBookTitle(String title) {
        if (title == null) return false;
        String t = title.trim();
        if (t.length() < 3 || t.length() > 120) return false;
        if (t.matches("^\\d{1,5}(?:[\\.:\\-)]\\s*|\\s+).+")) return false;
        if (t.split("\\s+").length > 12) return false;
        if (STATUS_SOURCE_LINE_PATTERN.matcher(t).matches()) return false;
        if (DETAIL_TAB_PATTERN.matcher(t).matches()) return false;
        if (AUTHOR_LIST_PATTERN.matcher(t).matches()) return false;
        if (looksLikeSingleNoiseToken(t)) return false;
        if (isLikelyChapterOrBodyText(t) || isLikelyUiText(t)) return false;
        return true;
    }

    public boolean isLikelyTitleForPackage(String packageName, String title) {
        if (!isLikelyBookTitle(title)) {
            return false;
        }
        return !isPackageSpecificNoiseTitle(packageName, title);
    }

    private static boolean isLikelyUiText(String text) {
        String t = text.toLowerCase(Locale.US);
        if (STATUS_SOURCE_LINE_PATTERN.matcher(text).matches()
                || DETAIL_TAB_PATTERN.matcher(text).matches()
                || AUTHOR_LIST_PATTERN.matcher(text).matches()) {
            return true;
        }
        if (t.matches(".*\\b(read now|continue reading|contents|content|media content|comments?|reviews?|add to library|library|home|search|ranking|explore|menu|settings|back|more options?|share|report|follow|bookmark|fanfic|movies?|series|tv shows?|genres?)\\b.*")) {
            return true;
        }
        return looksLikeGenreChip(t);
    }

    private static boolean isLikelyChapterOrBodyText(String text) {
        String t = text.toLowerCase(Locale.US);
        if (t.matches(".*\\b(chapter|ch\\.?|episode|ep\\.?)\\s*\\d+.*")) return true;
        if (t.matches(".*\\b(volume|vol\\.)\\s*\\d+.*")) return true;
        if (t.matches("^\\d{1,5}(?:[\\.:\\-)]\\s*|\\s+).+")) return true;
        // body paragraphs are poor canonical-title candidates
        return text.length() > 70 && (text.contains(".") || text.contains(","));
    }

    private static boolean looksLikeGenreChip(String text) {
        String normalized = text
                .replaceAll("[^a-z\\s\\-]", " ")
                .trim();
        if (normalized.isEmpty()) return false;
        String[] tokens = normalized.split("\\s+");
        if (tokens.length == 1) {
            return TITLE_NOISE_TOKENS.contains(tokens[0]);
        }
        if (tokens.length > 6) return false;
        int noiseCount = 0;
        for (String token : tokens) {
            if (TITLE_NOISE_TOKENS.contains(token)) {
                noiseCount++;
            }
        }
        return noiseCount >= 2 && noiseCount >= Math.max(2, (int) Math.ceil(tokens.length * 0.66f));
    }

    private static boolean looksLikeSingleNoiseToken(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.US).replaceAll("[^a-z]", "");
        return !normalized.isEmpty() && TITLE_NOISE_TOKENS.contains(normalized);
    }

    private static boolean isPackageSpecificNoiseTitle(String packageName, String title) {
        if (title == null) {
            return true;
        }
        String lower = title.trim().toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return true;
        }
        if (isBilibiliPackage(packageName)) {
            if (BILIBILI_TITLE_NOISE_PATTERN.matcher(lower).matches()) {
                return true;
            }
            if (lower.contains("premium")
                    && (lower.contains("license") || lower.contains("vip") || lower.contains("member"))) {
                return true;
            }
        }
        if (isWebNovelPackage(packageName)) {
            if (WEBNOVEL_TITLE_NOISE_PATTERN.matcher(lower).matches()) {
                return true;
            }
            if (lower.startsWith("by ")
                    || lower.startsWith("author:")
                    || lower.startsWith("authors:")
                    || lower.startsWith("genre:")) {
                return true;
            }
            if (looksLikeGenreChip(lower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBilibiliPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.US);
        return normalized.contains("bilibili")
                || normalized.contains("bstar")
                || normalized.contains("danmaku.bili");
    }

    private static boolean isMangaReaderPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.US);
        return normalized.contains("mihon")
                || normalized.contains("tachiyomi")
                || normalized.contains("aniyomi")
                || normalized.contains("kotatsu");
    }

    private static boolean isWebNovelPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.trim().toLowerCase(Locale.US);
        return normalized.contains("webnovel")
                || normalized.contains("qidian")
                || normalized.contains("novel");
    }

    private static boolean isBilibiliPlayerState(List<String> textNodes, ScreenContextDetector.ScreenContext context) {
        boolean hasDuration = false;
        boolean hasControl = false;
        boolean hasEpisode = false;
        int authSignals = 0;
        for (String node : textNodes) {
            if (node == null) {
                continue;
            }
            String value = node.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (BILIBILI_DURATION_PATTERN.matcher(value).find()) {
                hasDuration = true;
            }
            if (BILIBILI_CONTROL_PATTERN.matcher(value).matches()) {
                hasControl = true;
            }
            if (BILIBILI_EPISODE_PATTERN.matcher(value).matches()) {
                hasEpisode = true;
            }
            if (AUTH_NOISE_PATTERN.matcher(value).matches()) {
                authSignals++;
            }
        }
        if (authSignals >= 2 && !hasDuration) {
            return false;
        }
        return hasDuration && (hasControl || hasEpisode);
    }

    private static boolean isValidBilibiliProgress(float foundProgress, float currentProgress, int total) {
        if (foundProgress <= currentProgress || foundProgress > total) {
            return false;
        }
        float delta = foundProgress - currentProgress;
        float maxJump = currentProgress <= 1f ? 80f : 20f;
        if (delta > maxJump) {
            return false;
        }
        // BiliBili episode progress should usually be whole numbers.
        return Math.abs(foundProgress - Math.round(foundProgress)) <= 0.15f;
    }

    public void onClickSignal(String packageName, List<CharSequence> eventTexts) {
        if (eventTexts == null || eventTexts.isEmpty()) return;
        String sessionTitle = packageTitleSession.get(packageName);
        if (!isLikelyTitleForPackage(packageName, sessionTitle)) return;

        float currentProgress = packageLastProgress.getOrDefault(packageName, -1f);
        int total = Integer.MAX_VALUE;
        int resolvedId = resolveExistingMediaId(sessionTitle, null);
        if (resolvedId > 0) {
            Cursor cursor = dbHelper.getMediaById(resolvedId);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        if (currentProgress < 0f) {
                            currentProgress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                        }
                        total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        float best = -1f;
        boolean bilibiliPackage = isBilibiliPackage(packageName);
        for (CharSequence cs : eventTexts) {
            if (cs == null) continue;
            String raw = cs.toString();
            if (bilibiliPackage) {
                float explicitEpisode = extractBilibiliEpisodeFromText(raw, false);
                if (explicitEpisode > 0f
                        && (currentProgress < 0f || explicitEpisode > currentProgress)
                        && explicitEpisode <= total
                        && (best < 0f || explicitEpisode > best)) {
                    best = explicitEpisode;
                }
            }
            MediaMatch match = parseMedia(raw);
            if (match != null && match.progress > best) {
                best = match.progress;
            }
        }

        if (best > 0f) {
            packageLastProgress.put(packageName, best);
            applySyntheticProgressUpdate(packageName, sessionTitle, best, "click-signal");
        }
    }

    private static float extractBilibiliEpisodeCandidate(List<String> textNodes, boolean requireSelectedSignal) {
        if (textNodes == null || textNodes.isEmpty()) {
            return -1f;
        }
        float best = -1f;
        for (String node : textNodes) {
            float candidate = extractBilibiliEpisodeFromText(node, requireSelectedSignal);
            if (candidate > 0f && (best < 0f || candidate > best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static float extractBilibiliEpisodeFromText(String raw, boolean requireSelectedSignal) {
        if (raw == null) {
            return -1f;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return -1f;
        }
        if (BILIBILI_DURATION_PATTERN.matcher(text).find() || BILIBILI_TIMECODE_PATTERN.matcher(text).find()) {
            return -1f;
        }
        if (BILIBILI_METRIC_PATTERN.matcher(text).matches()) {
            return -1f;
        }
        String lower = text.toLowerCase(Locale.US);
        if (BILIBILI_CONTROL_PATTERN.matcher(lower).matches()
                || lower.contains("download")
                || lower.contains("share")
                || lower.contains("comment")) {
            return -1f;
        }
        boolean hasSelectedSignal = BILIBILI_SELECTED_PATTERN.matcher(text).matches();
        if (requireSelectedSignal && !hasSelectedSignal) {
            return -1f;
        }

        Matcher matcher = BILIBILI_EP_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return parseEpisodeNumber(matcher.group(1));
        }
        matcher = BILIBILI_CN_EP_PATTERN.matcher(text);
        if (matcher.find()) {
            return parseEpisodeNumber(matcher.group(1));
        }
        if (text.matches("^\\d{1,4}$")) {
            return parseEpisodeNumber(text);
        }
        return -1f;
    }

    private static float parseEpisodeNumber(String rawNumber) {
        if (rawNumber == null) {
            return -1f;
        }
        try {
            int value = Integer.parseInt(rawNumber.trim());
            if (value <= 0 || value > 5000) {
                return -1f;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return -1f;
        }
    }

    public void onNoTextWindow(String packageName) {
        if (packageName == null) return;
        packageLastNoTextSeen.put(packageName, System.currentTimeMillis());
    }

    public void onImmersiveScroll(String packageName, int fromIndex, int toIndex, int itemCount) {
        String sessionTitle = packageTitleSession.get(packageName);
        Float lastProgress = packageLastProgress.get(packageName);
        if (!isLikelyTitleForPackage(packageName, sessionTitle) || lastProgress == null || lastProgress <= 0f) return;

        if (toIndex <= fromIndex) return;

        long now = System.currentTimeMillis();
        Long lastUpdate = packageLastScrollUpdate.get(packageName);
        if (lastUpdate != null && (now - lastUpdate) < 45000) {
            return;
        }

        // Treat reaching the lower bound of a scrollable area as chapter completion signal.
        boolean atEnd = itemCount > 0 && toIndex >= itemCount - 1;
        Long noTextAt = packageLastNoTextSeen.get(packageName);
        boolean lowVisibilityReader = noTextAt != null && (now - noTextAt) < 15000;
        if (!atEnd && !lowVisibilityReader) return;

        float candidate = lastProgress + 1f;
        packageLastScrollUpdate.put(packageName, now);
        packageLastProgress.put(packageName, candidate);
        applySyntheticProgressUpdate(packageName, sessionTitle, candidate, "immersive-scroll");
    }

    private void applySyntheticProgressUpdate(String packageName, String sessionTitle, float candidateProgress, String source) {
        if (!isAutoProgressTrackingEnabled()) {
            return;
        }
        int resolvedId = resolveExistingMediaId(sessionTitle, null);
        if (resolvedId <= 0) {
            return;
        }
        Cursor cursor = dbHelper.getMediaById(resolvedId);
        if (cursor == null) return;
        try {
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String dbTitle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                float currentProgress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
                int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                String contentType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CONTENT_TYPE));
                String mediaType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEDIA_TYPE));
                float currentRating = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RATING));

                if (candidateProgress > currentProgress && candidateProgress <= total
                        && shouldUpdate(id, DEBOUNCE_WINDOW_SYNTHETIC_MS)) {
                    Log.i(TAG, "Synthetic progress update (" + source + "): " + dbTitle + " -> " + candidateProgress);
                    performUpdate(id, dbTitle, contentType, mediaType, candidateProgress, currentProgress, total, currentRating);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void enrichExistingMetadataFromContext(String sessionTitle, ScreenContextDetector.ScreenContext context) {
        if (context == null) return;
        if (context.author == null
                && context.totalChapters <= 0
                && !isUsefulContextDescription(context.additionalInfo)) {
            return;
        }

        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();
            Cursor cursor = dbHelper.getAllMedia();
            if (cursor == null) return;
            try {
                String target = sessionTitle.toLowerCase(Locale.ROOT);
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
                    String creator = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATOR));
                    int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DESCRIPTION));
                    if (title == null) continue;
                    String lower = title.toLowerCase(Locale.ROOT);
                    if (!(lower.contains(target) || target.contains(lower))) continue;

                    ContentValues values = new ContentValues();
                    if ((creator == null || creator.trim().isEmpty()) && context.author != null) {
                        values.put(DatabaseHelper.COL_CREATOR, context.author);
                    }
                    if ((total <= 1 || total == 999) && context.totalChapters > 1) {
                        values.put(DatabaseHelper.COL_TOTAL_COUNT, context.totalChapters);
                        values.put(DatabaseHelper.COL_UNIT, "Chapters");
                    }
                    if ((description == null || description.trim().isEmpty())
                            && isUsefulContextDescription(context.additionalInfo)) {
                        values.put(DatabaseHelper.COL_DESCRIPTION, context.additionalInfo.trim());
                    }
                    if (values.size() > 0) {
                        db.update(DatabaseHelper.TABLE_MEDIA, values, DatabaseHelper.COL_ID + "=?", new String[]{String.valueOf(id)});
                    }
                    MediaMetadataProfile contextProfile = MediaMetadataProfile.create()
                            .withCanonicalTitle(sessionTitle)
                            .withNormalizedTitle(MediaMetadataProfile.normalizeTitle(sessionTitle))
                            .withMetadataSource("accessibility")
                            .withMetadataConfidence(0.35f)
                            .withMetadataPriority(15)
                            .stampNow();
                    if (context.author != null && !context.author.trim().isEmpty()) {
                        contextProfile.addTag(context.author.trim());
                    }
                    if (context.totalChapters > 1) {
                        contextProfile.withTotalCount(context.totalChapters).withUnit("Chapters");
                    }
                    dbHelper.mergeAndUpsertMetadata(id, contextProfile, "accessibility");
                    break;
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Metadata enrichment from context failed for title: " + sessionTitle, e);
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    private boolean isUsefulContextDescription(String description) {
        if (description == null) {
            return false;
        }
        String normalized = description.trim();
        if (normalized.length() < 60) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.US);
        if ("book detail page".equals(lower)
                || "chapter reading".equals(lower)
                || "library list".equals(lower)) {
            return false;
        }
        return true;
    }

    private boolean shouldUpdate(int mediaId, long debounceWindowMs) {
        long now = System.currentTimeMillis();
        Long lastUpdate = lastUpdateMap.get(mediaId);
        if (lastUpdate == null || (now - lastUpdate) > debounceWindowMs) {
            lastUpdateMap.put(mediaId, now);
            return true;
        }
        return false;
    }

    private void performUpdate(int id, String title, String contentType, String mediaType, float next, float previous, int total, float currentRating) {
        if (!isAutoProgressTrackingEnabled()) {
            Log.d(TAG, "Auto progress tracking disabled, skipping update for " + title);
            return;
        }
        String displayTitle = dbHelper.getPreferredDisplayTitle(id);
        if (displayTitle == null || displayTitle.trim().isEmpty()) {
            displayTitle = title;
        }
        boolean isNowCompleted = next >= total;
        String status = isNowCompleted ? "Completed" : "Ongoing";
        
        boolean updated = dbHelper.updateProgress(id, next, status, currentRating);
        if (!updated) {
            Log.w(TAG, "Automated update failed for mediaId=" + id + ", skipping goals/widget.");
            return;
        }
        
        // Trigger Daily Goals Increment
        DailyGoalsManager manager = DailyGoalsManager.getInstance(dbHelper.getContext());
        String effectiveType = (mediaType != null && !mediaType.trim().isEmpty()) ? mediaType : contentType;
        if ("Manga".equalsIgnoreCase(effectiveType)
                || "Novel".equalsIgnoreCase(effectiveType)
                || "Book".equalsIgnoreCase(effectiveType)) {
            manager.incrementMangaProgress();
        } else if ("Anime".equalsIgnoreCase(effectiveType)
                || "Movie".equalsIgnoreCase(effectiveType)
                || "Series".equalsIgnoreCase(effectiveType)
                || "TV Show".equalsIgnoreCase(effectiveType)) {
            manager.incrementAnimeProgress();
        }

        Log.i(TAG, "Automated Update: " + displayTitle + " -> " + next + " (Previous: " + previous + ")");
        
        // Phase 3: Enhanced Completion Prompt
        if (isFloatingAssistantEnabled()) {
            if (isNowCompleted) {
                assistantManager.showCompletionOverlay(id, displayTitle);
            } else if (total > 0 && next >= total - 1) {
                // Near completion (last chapter/episode)
                assistantManager.showNearCompletionOverlay(id, displayTitle, next);
            } else {
                // Show the standard floating overlay
                assistantManager.showUpdate(id, displayTitle, next, previous);
            }
        }

        // Phase 3: Automatic total count verification for unknown totals
        if (total == 999 || total <= 0) {
            MediaSearchManager searchManager = new MediaSearchManager();
            searchManager.enrichMediaMetadata(dbHelper.getContext(), id, displayTitle, effectiveType);
        }
    }

    private boolean isAutoTitleTrackingEnabled() {
        SharedPreferences prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_TITLE_TRACKING_ENABLED, true);
    }

    private boolean isAutoProgressTrackingEnabled() {
        SharedPreferences prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_PROGRESS_TRACKING_ENABLED, true);
    }

    private boolean isFloatingAssistantEnabled() {
        SharedPreferences prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean("floating_assistant_enabled", false);
    }
}
