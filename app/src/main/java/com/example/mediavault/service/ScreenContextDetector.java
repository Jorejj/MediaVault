package com.example.mediavault.service;

import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects the type of screen currently displayed and extracts relevant information.
 */
public class ScreenContextDetector {
    private static final String TAG = "ScreenContextDetector";
    private static final Pattern CHAPTER_LINE_PATTERN = Pattern.compile("(?i).*\\b(chapter|ch\\.?|episode|ep\\.?)\\s*\\d+.*");
    private static final Pattern WEBNOVEL_DETAIL_SIGNAL_PATTERN = Pattern.compile("(?i).*(read\\s*now|add\\s*to\\s*library|contents|chapters?\\s+updated|by\\s+\\w+|novel\\s*[·•\\-]).*");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("(?i)^by\\s+(.+)$");
    private static final Pattern TOTAL_CHAPTERS_PATTERN = Pattern.compile("(?i)(\\d{1,3}(?:[\\,\\.]\\d{3})*|\\d{1,5})\\s+chapters?");
    private static final Pattern CHAPTER_PROGRESS_PATTERN = Pattern.compile("(?i)\\b(?:chapter|ch\\.?|episode|ep\\.?)\\s*(\\d+(?:[\\.\\-]\\d+)?)");
    private static final Pattern WEBNOVEL_ACTION_PATTERN = Pattern.compile("(?i).*(read\\s*now|start\\s*reading|continue\\s*reading|add\\s*to\\s*library|contents?).*");
    private static final Pattern WEBNOVEL_STATS_PATTERN = Pattern.compile("(?i).*(\\d{1,5}\\s+chapters?(?:\\s+updated)?|\\d+(?:[\\.,]\\d+)?\\s*[kmb]?\\s*(views?|votes?)|novel\\s*[·•\\-]).*");
    private static final Pattern CHAPTER_ROW_PREFIX_PATTERN = Pattern.compile("^\\s*\\d{1,5}(?:[\\.:\\-)]\\s*|\\s+).+");
    private static final Pattern DETAIL_NOISE_PATTERN = Pattern.compile("(?i).*(comments?|reviews?|views?|votes?|collections?|downloads?|rank\\s*#?\\d+).*");
    private static final Pattern AUTH_UI_PATTERN = Pattern.compile("(?i).*(log\\s*in|login|sign\\s*in|sign\\s*up|register|create\\s+account|continue\\s+with|google|facebook|apple\\s*id|phone\\s*number|verification\\s*code|otp|captcha).*");
    private static final Pattern GENERIC_UI_PHRASE_PATTERN = Pattern.compile("(?i).*(more options?|options|share|report|follow|bookmark|bookmarks|notification|notifications|discover|recommended|popular|latest|trending|all\\s+genres?|genres?|fanfic|movies?|series|tv\\s*shows?|media\\s*content|content\\s*hub).*");
    private static final Pattern DESCRIPTION_CANDIDATE_PATTERN = Pattern.compile("(?i).*[a-z]{3,}.*[\\.,!?].*");
    private static final Set<String> GENRE_CHIP_TERMS = new HashSet<>(Arrays.asList(
            "novel", "webnovel", "book", "genre", "genres", "fantasy", "action", "adventure",
            "romance", "drama", "comedy", "slice", "life", "isekai", "martial", "arts",
            "historical", "history", "sci", "fi", "science", "fiction", "horror", "mystery",
            "thriller", "magic", "supernatural", "system", "urban", "school", "xianxia", "wuxia",
            "fanfic", "fanfiction", "movie", "movies", "film", "films", "tv", "show", "shows",
            "series", "anime", "manga", "manhwa", "manhua", "comic", "comics", "webtoon",
            "media", "content", "novels", "chapter", "chapters", "latest", "popular", "trending"
    ));

    public enum ScreenType {
        BOOK_DETAIL,        // Book description/info page
        CHAPTER_READING,    // Active chapter reading
        LIBRARY_LIST,       // List of books
        SEARCH_RESULTS,     // Search results
        UNKNOWN
    }

    public static class ScreenContext {
        public final ScreenType type;
        public final String extractedTitle;
        public final String additionalInfo;
        public final float extractedProgress;
        public final String author;
        public final int totalChapters;

        public ScreenContext(ScreenType type, String extractedTitle, String additionalInfo) {
            this(type, extractedTitle, additionalInfo, -1f, null, 0);
        }

        public ScreenContext(ScreenType type, String extractedTitle, String additionalInfo, float extractedProgress, String author, int totalChapters) {
            this.type = type;
            this.extractedTitle = extractedTitle;
            this.additionalInfo = additionalInfo;
            this.extractedProgress = extractedProgress;
            this.author = author;
            this.totalChapters = totalChapters;
        }
    }

    /**
     * Detect what type of screen the user is currently viewing.
     */
    public static ScreenContext detectContext(List<String> textNodes) {
        if (textNodes == null || textNodes.isEmpty()) {
            return new ScreenContext(ScreenType.UNKNOWN, null, null);
        }

        // Check for book detail indicators (prioritized when strong detail signals exist)
        if (isBookDetailScreen(textNodes)) {
            String title = extractTitleFromDetail(textNodes);
            String description = extractDescriptionFromDetail(textNodes, title);
            String author = extractAuthor(textNodes);
            int totalChapters = extractTotalChapters(textNodes);
            return new ScreenContext(
                    ScreenType.BOOK_DETAIL,
                    title,
                    (description != null && !description.trim().isEmpty()) ? description : "Book detail page",
                    -1f,
                    author,
                    totalChapters
            );
        }

        // Check for chapter reading patterns
        if (isChapterReadingScreen(textNodes)) {
            float progress = extractChapterProgress(textNodes);
            return new ScreenContext(ScreenType.CHAPTER_READING, null, "Chapter reading", progress, null, 0);
        }

        // Check for library list
        if (isLibraryListScreen(textNodes)) {
            return new ScreenContext(ScreenType.LIBRARY_LIST, null, "Library list");
        }

        return new ScreenContext(ScreenType.UNKNOWN, null, null);
    }

    /**
     * Detect if current screen is a book detail page.
     * Indicators: "Start Reading", "Continue Reading", "Add to Library", rating stars, description
     */
    private static boolean isBookDetailScreen(List<String> textNodes) {
        boolean hasActionButton = false;
        boolean hasCatalogSignals = false;
        boolean hasDescription = false;
        int longTextCount = 0;
        int chapterLikeCount = 0;
        int webNovelSignalCount = 0;
        int authSignalCount = 0;

        for (String text : textNodes) {
            String lower = text.toLowerCase(Locale.ROOT);
            
            // Check for action buttons typical of book detail pages
            if (WEBNOVEL_ACTION_PATTERN.matcher(text).matches()
                    || lower.matches(".*(start reading|continue reading|add to library|read now|begin reading).*")) {
                hasActionButton = true;
                webNovelSignalCount++;
            }

            if (WEBNOVEL_DETAIL_SIGNAL_PATTERN.matcher(text).matches()) {
                hasCatalogSignals = true;
                webNovelSignalCount++;
            }
            if (WEBNOVEL_STATS_PATTERN.matcher(text).matches()) {
                webNovelSignalCount++;
            }
            if (AUTH_UI_PATTERN.matcher(text).matches()) {
                authSignalCount++;
            }
             
            // Check for description-like text (50+ characters, no chapter numbers)
            if (CHAPTER_LINE_PATTERN.matcher(text).matches()) {
                chapterLikeCount++;
            }
            if (text.length() > 50 && !CHAPTER_LINE_PATTERN.matcher(text).matches()) {
                longTextCount++;
                if (longTextCount >= 2) {
                    hasDescription = true;
                }
            }
        }

        // Avoid treating chapter-reader pages as detail pages
        if (chapterLikeCount > 8 && !hasActionButton && webNovelSignalCount < 2) {
            return false;
        }
        if (authSignalCount >= 2 && webNovelSignalCount <= 1) {
            return false;
        }

        // Strong signal: explicit actions + catalog markers
        if (hasActionButton && (hasCatalogSignals || webNovelSignalCount >= 2)) {
            return true;
        }

        // WebNovel detail pages often expose metadata cards without long description blocks.
        if (webNovelSignalCount >= 3 && chapterLikeCount <= 6) {
            return true;
        }

        // Secondary signal: catalog/detail markers + descriptive block, but avoid chapter pages.
        return hasCatalogSignals
                && webNovelSignalCount >= 2
                && chapterLikeCount <= 6
                && (hasDescription || longTextCount >= 1);
    }

    /**
     * Detect if current screen is actively reading a chapter.
     * Indicators: Chapter patterns, high text density, paragraph content
     */
    private static boolean isChapterReadingScreen(List<String> textNodes) {
        int chapterPatternCount = 0;
        int paragraphCount = 0;

        Pattern chapterPattern = Pattern.compile("(?i)(?:^|\\s)(?:chapter|ch\\.?|episode|ep\\.?)\\s*\\d+");

        for (String text : textNodes) {
            // Check for chapter title patterns
            if (chapterPattern.matcher(text).find()) {
                chapterPatternCount++;
            }
            
            // Check for paragraph-like content (30-200 chars, proper sentences)
            if (text.length() > 30 && text.length() < 200 && text.contains(" ")) {
                paragraphCount++;
            }
        }

        // Chapter reading has chapter patterns OR many paragraphs
        return chapterPatternCount > 0 || paragraphCount > 10;
    }

    /**
     * Detect if current screen is a library/list view.
     */
    private static boolean isLibraryListScreen(List<String> textNodes) {
        int titleLikeCount = 0;

        for (String text : textNodes) {
            // Count short titles (3-40 chars, capitalized)
            if (text.length() >= 3 && text.length() <= 40 && Character.isUpperCase(text.charAt(0))) {
                titleLikeCount++;
            }
        }

        // Library lists have many title-like elements
        return titleLikeCount > 5;
    }

    /**
     * Extract the book title from a detail page.
     * Strategy: Find the largest text element in the top portion that looks like a title.
     */
    public static String extractTitleFromDetail(List<String> textNodes) {
        String bestCandidate = null;
        int bestScore = 0;

        // Common UI elements to filter out
        Pattern uiElementPattern = Pattern.compile("(?i)(library|search|settings|home|profile|back|menu|filter|sort|featured|explore|ranking|genres?|categories|contents|reviews?|comments|read\\s*now|continue\\s*reading|add\\s*to\\s*library|chapters?\\s*updated|table\\s*of\\s*contents|more\\s*options?|share|report|follow|bookmark)");

        // Take top 65% of nodes; detail headers can appear lower in immersive layouts.
        int topPortionSize = Math.max(1, Math.min((int) Math.ceil(textNodes.size() * 0.65f), 45));
        String authorAnchored = extractTitleNearAuthor(textNodes, uiElementPattern);
        if (authorAnchored != null) {
            Log.d(TAG, "Extracted author-anchored title: '" + authorAnchored + "'");
            return authorAnchored;
        }

        Map<String, Integer> frequency = new HashMap<>();
        for (int i = 0; i < Math.min(topPortionSize, textNodes.size()); i++) {
            String text = textNodes.get(i) == null ? "" : textNodes.get(i).trim();
            if (isLikelyTitleCandidate(text, uiElementPattern)) {
                String key = text.toLowerCase(Locale.ROOT);
                frequency.put(key, frequency.getOrDefault(key, 0) + 1);
            }
        }

        for (int i = 0; i < Math.min(topPortionSize, textNodes.size()); i++) {
            String text = textNodes.get(i).trim();

            if (!isLikelyTitleCandidate(text, uiElementPattern)) continue;

            // Calculate score based on:
            // - Length (longer titles score higher, but not too long)
            // - Capitalization (proper titles are capitalized)
            // - Position (earlier in list = higher)
            int score = 0;

            // Length score (sweet spot: 10-50 characters)
            if (text.length() >= 10 && text.length() <= 50) {
                score += 30;
            } else if (text.length() >= 5 && text.length() <= 80) {
                score += 20;
            }

            // Capitalization score
            if (Character.isUpperCase(text.charAt(0))) {
                score += 15;
            }

            // Position score (earlier = better, but not first 2 which are often nav)
            if (i >= 2 && i < 10) {
                score += (10 - i) * 2;
            }

            // Word count score (titles usually 2-8 words)
            int wordCount = text.split("\\s+").length;
            if (wordCount >= 2 && wordCount <= 8) {
                score += 20;
            } else if (wordCount > 11) {
                score -= 18;
            }
            if (text.contains(":") || text.contains(" - ") || text.contains(" – ") || text.contains(" — ")) {
                score += 12;
            }

            // Nearby author/action labels are strong title anchors on WebNovel detail pages.
            int from = Math.max(0, i - 3);
            int to = Math.min(textNodes.size() - 1, i + 3);
            for (int j = from; j <= to; j++) {
                if (j == i) continue;
                String nearby = textNodes.get(j).trim().toLowerCase(Locale.ROOT);
                if (nearby.startsWith("by ")) {
                    score += 20;
                }
                if (nearby.matches(".*(read now|start reading|continue reading|add to library|chapters? updated|contents).*")) {
                    score += 18;
                }
            }

            // Penalize strings that look like body content/sentences
            if (text.endsWith(".") || text.contains(",")) {
                score -= 10;
            }
            if (text.matches("(?i).*(\\bchapter\\b|\\bepisode\\b|\\bvol(?:ume)?\\b).*")) {
                score -= 30;
            }
            if (CHAPTER_ROW_PREFIX_PATTERN.matcher(text).matches()) {
                score -= 25;
            }
            if (DETAIL_NOISE_PATTERN.matcher(text).matches()) {
                score -= 20;
            }
            if (Character.isDigit(text.charAt(0)) && text.length() > 5) {
                score -= 16;
            }
            if (looksLikeGenreChip(text)) {
                score -= 35;
            }
            if (GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()) {
                score -= 40;
            }

            int repeats = frequency.getOrDefault(text.toLowerCase(Locale.ROOT), 0);
            if (repeats > 1) {
                score += (repeats - 1) * 12;
            }

            // Contains common title words
            if (text.matches("(?i).*(wizard|hero|legend|chronicles|tales|story|saga|journey|quest).*")) {
                score += 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = text;
            }
        }

        if (bestCandidate != null) {
            Log.d(TAG, "Extracted title: '" + bestCandidate + "' (score: " + bestScore + ")");
        } else {
            Log.d(TAG, "No title candidate found");
        }

        // Require a minimum confidence score to avoid chapter body text becoming title.
        if (bestScore < 30) {
            return null;
        }

        return bestCandidate;
    }

    private static boolean isLikelyTitleCandidate(String text, Pattern uiElementPattern) {
        if (text == null) return false;
        if (text.length() < 3 || text.length() > 120) return false;
        if (uiElementPattern.matcher(text).matches()) return false;
        if (GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()) return false;
        if (CHAPTER_LINE_PATTERN.matcher(text).matches()) return false;
        if (text.matches("(?i).*\\b(chapter list|volume \\d+|vol\\.\\s*\\d+).*")) return false;
        if (AUTH_UI_PATTERN.matcher(text).matches()) return false;
        if (text.matches("^\\d+(?:[\\.,]\\d+)?$")) return false;
        if (CHAPTER_ROW_PREFIX_PATTERN.matcher(text).matches()) return false;
        if (DETAIL_NOISE_PATTERN.matcher(text).matches()) return false;
        if (text.split("\\s+").length > 12) return false;
        if (text.length() > 70 && text.matches(".*[\\.,!?].*")) return false;
        if (looksLikeGenreChip(text)) return false;
        return true;
    }

    private static boolean looksLikeGenreChip(String text) {
        if (text == null) return false;
        String normalized = text
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z\\s\\-]", " ")
                .trim();
        if (normalized.isEmpty()) return false;
        String[] tokens = normalized.split("\\s+");
        if (tokens.length < 2 || tokens.length > 6) return false;
        int noiseCount = 0;
        for (String token : tokens) {
            if (GENRE_CHIP_TERMS.contains(token)) {
                noiseCount++;
            }
        }
        return noiseCount >= 2 && noiseCount >= Math.max(2, (int) Math.ceil(tokens.length * 0.66f));
    }

    private static String extractTitleNearAuthor(List<String> textNodes, Pattern uiElementPattern) {
        for (int i = 0; i < textNodes.size(); i++) {
            String node = textNodes.get(i) == null ? "" : textNodes.get(i).trim();
            if (!node.toLowerCase(Locale.ROOT).startsWith("by ")) {
                continue;
            }
            for (int back = 1; back <= 3; back++) {
                int idx = i - back;
                if (idx < 0) break;
                String candidate = textNodes.get(idx) == null ? "" : textNodes.get(idx).trim();
                if (isLikelyTitleCandidate(candidate, uiElementPattern)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String extractAuthor(List<String> textNodes) {
        for (String text : textNodes) {
            java.util.regex.Matcher matcher = AUTHOR_PATTERN.matcher(text.trim());
            if (matcher.find()) {
                String author = matcher.group(1).trim();
                if (author.length() > 1 && author.length() < 60) {
                    return author;
                }
            }
        }
        return null;
    }

    private static int extractTotalChapters(List<String> textNodes) {
        int best = 0;
        for (String text : textNodes) {
            java.util.regex.Matcher matcher = TOTAL_CHAPTERS_PATTERN.matcher(text);
            while (matcher.find()) {
                try {
                    String raw = matcher.group(1);
                    String normalized = raw == null ? "" : raw.replaceAll("(?<=\\d)[,\\.](?=\\d{3}(?:\\D|$))", "");
                    normalized = normalized.replaceAll("[^\\d]", "");
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    int value = Integer.parseInt(normalized);
                    if (value > best && value <= 50000) {
                        best = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    private static float extractChapterProgress(List<String> textNodes) {
        float best = -1f;
        for (String text : textNodes) {
            java.util.regex.Matcher matcher = CHAPTER_PROGRESS_PATTERN.matcher(text);
            while (matcher.find()) {
                try {
                    String normalized = matcher.group(1).replace('-', '.');
                    float value = Float.parseFloat(normalized);
                    if (value > best && value <= 50000f) {
                        best = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    private static String extractDescriptionFromDetail(List<String> textNodes, String extractedTitle) {
        if (textNodes == null || textNodes.isEmpty()) {
            return null;
        }
        String normalizedTitle = extractedTitle == null ? "" : extractedTitle.trim().toLowerCase(Locale.US);
        String best = null;
        int bestScore = 0;
        for (String node : textNodes) {
            String text = node == null ? "" : node.trim();
            if (text.length() < 80 || text.length() > 650) {
                continue;
            }
            String lower = text.toLowerCase(Locale.US);
            if (!normalizedTitle.isEmpty() && (lower.equals(normalizedTitle) || lower.startsWith("by "))) {
                continue;
            }
            if (CHAPTER_LINE_PATTERN.matcher(text).matches()
                    || CHAPTER_ROW_PREFIX_PATTERN.matcher(text).matches()
                    || GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()
                    || DETAIL_NOISE_PATTERN.matcher(text).matches()
                    || AUTH_UI_PATTERN.matcher(text).matches()
                    || WEBNOVEL_ACTION_PATTERN.matcher(text).matches()
                    || WEBNOVEL_STATS_PATTERN.matcher(text).matches()) {
                continue;
            }
            if (!DESCRIPTION_CANDIDATE_PATTERN.matcher(text).matches()) {
                continue;
            }
            int wordCount = text.split("\\s+").length;
            if (wordCount < 12) {
                continue;
            }
            int score = Math.min(120, text.length()) + Math.min(40, wordCount);
            if (score > bestScore) {
                bestScore = score;
                best = text;
            }
        }
        if (best == null) {
            return null;
        }
        return best.replaceAll("\\s+", " ").trim();
    }
}
