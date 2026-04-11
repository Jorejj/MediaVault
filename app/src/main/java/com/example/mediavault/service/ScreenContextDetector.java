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
    private static final Pattern WEBNOVEL_DETAIL_SIGNAL_PATTERN = Pattern.compile("(?i).*(read\\s*now|add\\s*to\\s*library|contents|chapters?\\s+updated|by\\s+\\w+|novel\\s*[·•\\-]|episodes?|synopsis|summary).*");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("(?i)^(?:by|author|creator|artist)\\s*[:\\-]?(.*)$");
    private static final Pattern TOTAL_CHAPTERS_PATTERN = Pattern.compile("(?i)(\\d{1,3}(?:[\\,\\.]\\d{3})*|\\d{1,5})\\s+(?:chapters?|episodes?)");
    private static final Pattern CHAPTER_PROGRESS_PATTERN = Pattern.compile("(?i)\\b(?:chapter|ch\\.?|episode|ep\\.?)\\s*(\\d+(?:[\\.\\-]\\d+)?)");
    private static final Pattern MEDIA_ACTION_PATTERN = Pattern.compile("(?i).*(read\\s*now|start\\s*reading|continue\\s*reading|add\\s*to\\s*library|watch\\s*now|play\\s*now|start\\s*watching|continue\\s*watching|add\\s*to\\s*watchlist|watchlist|subscribe|favorite|contents?|episodes?|chapters?|catalog|volumes?).*");
    private static final Pattern WEBNOVEL_STATS_PATTERN = Pattern.compile("(?i).*(\\d{1,5}\\s+(?:chapters?|episodes?)(?:\\s+updated)?|\\d+(?:[\\.,]\\d+)?\\s*[kmb]?\\s*(views?|votes?|likes?|reviews?)|novel\\s*[·•\\-]).*");
    private static final Pattern CHAPTER_ROW_PREFIX_PATTERN = Pattern.compile("^\\s*\\d{1,5}(?:[\\.:\\-)]\\s*|\\s+).+");
    private static final Pattern DETAIL_NOISE_PATTERN = Pattern.compile("(?i).*(comments?|reviews?|views?|votes?|collections?|downloads?|rank\\s*#?\\d+|ratings?|stars?|sub\\b|dub\\b|release\\s*date|status|type).*");
    private static final Pattern AUTH_UI_PATTERN = Pattern.compile("(?i).*(log\\s*in|login|sign\\s*in|sign\\s*up|register|create\\s+account|continue\\s+with|google|facebook|apple\\s*id|phone\\s*number|verification\\s*code|otp|captcha).*");
    private static final Pattern GENERIC_UI_PHRASE_PATTERN = Pattern.compile("(?i).*(more options?|options|share|report|follow|bookmark|bookmarks|notification|notifications|discover|recommended|popular|latest|trending|all\\s+genres?|genres?|fanfic|movies?|series|tv\\s*shows?|media\\s*content|content\\s*hub|home|explore|library|search|profile|settings).*");
    private static final Pattern STATUS_SOURCE_LINE_PATTERN = Pattern.compile("(?i)^(?:ongoing|completed|hiatus|cancelled|canceled|dropped|publishing|finished)(?:\\s*[·•\\-]\\s*[\\p{L}\\p{M}\\d][\\p{L}\\p{M}\\d\\s\\.,'’:_&\\-]{1,60})?$");
    private static final Pattern DETAIL_TAB_PATTERN = Pattern.compile("(?i)^(?:in\\s+library|soon|tracking|webview|overview|details|chapters?|related|similar|description|reviews?)$");
    private static final Pattern AUTHOR_LIST_PATTERN = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M}'’\\-. ]{0,30}(?:,\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}'’\\-. ]{0,30}){1,4}$");
    private static final Pattern DESCRIPTION_CANDIDATE_PATTERN = Pattern.compile("(?i).*[a-z]{3,}.*[\\.,!?].*");
    private static final Set<String> GENRE_CHIP_TERMS = new HashSet<>(Arrays.asList(
            "novel", "webnovel", "book", "genre", "genres", "fantasy", "action", "adventure",
            "romance", "drama", "comedy", "slice", "life", "isekai", "martial", "arts",
            "historical", "history", "sci", "fi", "science", "fiction", "horror", "mystery",
            "thriller", "magic", "supernatural", "system", "urban", "school", "xianxia", "wuxia",
            "fanfic", "fanfiction", "movie", "movies", "film", "films", "tv", "show", "shows",
            "series", "anime", "manga", "manhwa", "manhua", "comic", "comics", "webtoon",
            "media", "content", "novels", "chapter", "chapters", "latest", "popular", "trending",
            "ongoing", "completed", "status", "author", "creator", "artist", "tracking", "webview",
            "library", "bookmark", "soon"
    ));

    public enum ScreenType {
        MEDIA_DETAIL,       // Media description/info page
        CHAPTER_READING,    // Active chapter reading / video watching
        LIBRARY_LIST,       // List of books/media
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
        public final String detectedMediaType; // e.g., "Manga", "Anime", "Book"

        public ScreenContext(ScreenType type, String extractedTitle, String additionalInfo) {
            this(type, extractedTitle, additionalInfo, -1f, null, 0, null);
        }

        public ScreenContext(ScreenType type, String extractedTitle, String additionalInfo, float extractedProgress, String author, int totalChapters) {
            this(type, extractedTitle, additionalInfo, extractedProgress, author, totalChapters, null);
        }

        public ScreenContext(ScreenType type, String extractedTitle, String additionalInfo, float extractedProgress, String author, int totalChapters, String detectedMediaType) {
            this.type = type;
            this.extractedTitle = extractedTitle;
            this.additionalInfo = additionalInfo;
            this.extractedProgress = extractedProgress;
            this.author = author;
            this.totalChapters = totalChapters;
            this.detectedMediaType = detectedMediaType;
        }
    }

    /**
     * Detect what type of screen the user is currently viewing.
     */
    public static ScreenContext detectContext(List<String> textNodes) {
        if (textNodes == null || textNodes.isEmpty()) {
            return new ScreenContext(ScreenType.UNKNOWN, null, null);
        }

        String detectedMediaType = inferMediaType(textNodes);

        // Check for media detail indicators (prioritized when strong detail signals exist)
        if (isMediaDetailScreen(textNodes)) {
            String title = extractTitleFromDetail(textNodes);
            String description = extractDescriptionFromDetail(textNodes, title);
            String author = extractAuthor(textNodes);
            int totalChapters = extractTotalChapters(textNodes);
            return new ScreenContext(
                    ScreenType.MEDIA_DETAIL,
                    title,
                    (description != null && !description.trim().isEmpty()) ? description : "Media detail page",
                    -1f,
                    author,
                    totalChapters,
                    detectedMediaType
            );
        }

        // Check for reading/watching patterns
        if (isReadingOrWatchingScreen(textNodes)) {
            float progress = extractChapterProgress(textNodes);
            return new ScreenContext(ScreenType.CHAPTER_READING, null, "Currently active", progress, null, 0, detectedMediaType);
        }

        // Check for library list
        if (isLibraryListScreen(textNodes)) {
            return new ScreenContext(ScreenType.LIBRARY_LIST, null, "Library list", -1f, null, 0, detectedMediaType);
        }

        return new ScreenContext(ScreenType.UNKNOWN, null, null, -1f, null, 0, detectedMediaType);
    }

    private static String inferMediaType(List<String> textNodes) {
        int mangaScore = 0;
        int animeScore = 0;
        int novelScore = 0;

        for (String node : textNodes) {
            if (node == null) continue;
            String lower = node.toLowerCase(Locale.ROOT);
            
            if (lower.contains("manga") || lower.contains("manhwa") || lower.contains("manhua") || lower.contains("scanlation") || lower.contains("comic")) {
                mangaScore += 10;
            }
            if (lower.contains("anime")
                    || lower.matches(".*\\bepisode\\b.*")
                    || lower.matches(".*\\bsub(?:bed|title|titles)?\\b.*")
                    || lower.matches(".*\\bdub(?:bed)?\\b.*")
                    || lower.contains("bilibili")
                    || lower.contains("bstar")) {
                animeScore += 10;
            }
            if (lower.contains("novel") || lower.contains("light novel") || lower.contains("web novel")) {
                novelScore += 10;
            }
            if (lower.matches(".*\\bchapter\\b.*") || lower.matches(".*\\bch\\.\\s*\\d+.*")) {
                mangaScore += 2;
                novelScore += 2;
            }
            if (lower.contains("mangafire") || lower.contains("comix") || lower.contains("mangatoto")) {
                mangaScore += 20;
            }
            if (lower.contains("animekai") || lower.contains("aniwatch") || lower.contains("crunchyroll") || lower.contains("bilibili")) {
                animeScore += 20;
            }
        }

        if (mangaScore > animeScore && mangaScore > novelScore && mangaScore >= 10) return "Manga";
        if (animeScore > mangaScore && animeScore > novelScore && animeScore >= 10) return "Anime";
        if (novelScore > mangaScore && novelScore > animeScore && novelScore >= 10) return "Book";

        return null;
    }

    /**
     * Detect if current screen is a media detail page.
     */
    private static boolean isMediaDetailScreen(List<String> textNodes) {
        boolean hasActionButton = false;
        boolean hasCatalogSignals = false;
        boolean hasDescription = false;
        int longTextCount = 0;
        int chapterLikeCount = 0;
        int mediaSignalCount = 0;
        int authSignalCount = 0;

        for (String text : textNodes) {
            String lower = text.toLowerCase(Locale.ROOT);
            
            // Check for action buttons typical of detail pages
            if (MEDIA_ACTION_PATTERN.matcher(text).matches()
                    || lower.matches(".*(start reading|continue reading|add to library|read now|begin reading|watch now|play now|start watching|continue watching|add to watchlist).*")) {
                hasActionButton = true;
                mediaSignalCount++;
            }

            if (WEBNOVEL_DETAIL_SIGNAL_PATTERN.matcher(text).matches()) {
                hasCatalogSignals = true;
                mediaSignalCount++;
            }
            if (WEBNOVEL_STATS_PATTERN.matcher(text).matches()) {
                mediaSignalCount++;
            }
            if (AUTH_UI_PATTERN.matcher(text).matches()) {
                authSignalCount++;
            }
             
            // Check for description-like text
            if (CHAPTER_LINE_PATTERN.matcher(text).matches()) {
                chapterLikeCount++;
            }
            if (text.length() > 50 && !CHAPTER_LINE_PATTERN.matcher(text).matches() && !GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()) {
                longTextCount++;
                if (longTextCount >= 2) {
                    hasDescription = true;
                }
            }
        }

        // Avoid treating reading pages as detail pages
        if (chapterLikeCount > 10 && !hasActionButton && mediaSignalCount < 2) {
            return false;
        }
        if (authSignalCount >= 2 && mediaSignalCount <= 1) {
            return false;
        }

        // Strong signal: explicit actions + catalog markers
        if (hasActionButton && (hasCatalogSignals || mediaSignalCount >= 2)) {
            return true;
        }

        // Detail pages often expose metadata cards without long description blocks.
        if (mediaSignalCount >= 3 && chapterLikeCount <= 8) {
            return true;
        }

        return (hasCatalogSignals || mediaSignalCount >= 2)
                && chapterLikeCount <= 8
                && (hasDescription || longTextCount >= 1 || hasActionButton);
    }

    /**
     * Detect if current screen is actively reading or watching.
     */
    private static boolean isReadingOrWatchingScreen(List<String> textNodes) {
        int chapterPatternCount = 0;
        int paragraphCount = 0;

        Pattern chapterPattern = Pattern.compile("(?i)(?:^|\\s)(?:chapter|ch\\.?|episode|ep\\.?)\\s*\\d+");

        for (String text : textNodes) {
            if (chapterPattern.matcher(text).find()) {
                chapterPatternCount++;
            }
            if (text.length() > 30 && text.length() < 250 && text.contains(" ")) {
                paragraphCount++;
            }
        }

        return chapterPatternCount > 0 || paragraphCount > 12;
    }

    /**
     * Detect if current screen is a library/list view.
     */
    private static boolean isLibraryListScreen(List<String> textNodes) {
        int titleLikeCount = 0;

        for (String text : textNodes) {
            // Count short titles (3-50 chars, capitalized)
            if (text.length() >= 3 && text.length() <= 50 && Character.isUpperCase(text.charAt(0))) {
                titleLikeCount++;
            }
        }

        return titleLikeCount > 6;
    }

    /**
     * Extract the title from a detail page.
     */
    public static String extractTitleFromDetail(List<String> textNodes) {
        String bestCandidate = null;
        int bestScore = 0;
        int bestIndex = -1;

        // Common UI elements to filter out
        Pattern uiElementPattern = Pattern.compile("(?i)(library|search|settings|home|profile|back|menu|filter|sort|featured|explore|ranking|genres?|categories|contents|reviews?|comments|read\\s*now|continue\\s*reading|add\\s*to\\s*library|watch\\s*now|play\\s*now|chapters?\\s*updated|table\\s*of\\s*contents|more\\s*options?|share|report|follow|bookmark)");

        // Take top 70% of nodes
        int portionLimit = Math.max(1, Math.min((int) Math.ceil(textNodes.size() * 0.7f), 50));
        
        String authorAnchored = extractTitleNearAuthor(textNodes, uiElementPattern);
        if (authorAnchored != null) {
            return authorAnchored;
        }

        Map<String, Integer> frequency = new HashMap<>();
        for (int i = 0; i < Math.min(portionLimit, textNodes.size()); i++) {
            String text = textNodes.get(i) == null ? "" : textNodes.get(i).trim();
            if (isLikelyTitleCandidate(text, uiElementPattern)) {
                String key = text.toLowerCase(Locale.ROOT);
                frequency.put(key, frequency.getOrDefault(key, 0) + 1);
            }
        }

        for (int i = 0; i < Math.min(portionLimit, textNodes.size()); i++) {
            String text = textNodes.get(i).trim();

            if (!isLikelyTitleCandidate(text, uiElementPattern)) continue;

            int score = 0;

            // Length score
            if (text.length() >= 8 && text.length() <= 50) {
                score += 35; // One Piece is 9 chars, now gets higher base score
            } else if (text.length() >= 3 && text.length() <= 80) {
                score += 20;
            }

            // Capitalization score
            if (Character.isUpperCase(text.charAt(0))) {
                score += 15;
            }
            if (text.equals(text.toUpperCase(Locale.ROOT)) && text.length() > 3) {
                score += 10; // ALL CAPS titles are common in some apps
            }

            // Position score (Earlier is generally better)
            if (i < 15) {
                score += (15 - i) * 2;
            }

            // Word count score
            int wordCount = text.split("\\s+").length;
            if (wordCount >= 1 && wordCount <= 8) {
                score += 20;
            } else if (wordCount > 10) {
                score -= 20;
            }
            
            if (text.contains(":") || text.contains(" - ") || text.contains(" – ") || text.contains(" — ")) {
                score += 15; // Web browser titles often have " - Site Name"
            }

            // Anchors
            int from = Math.max(0, i - 3);
            int to = Math.min(textNodes.size() - 1, i + 3);
            for (int j = from; j <= to; j++) {
                if (j == i) continue;
                String nearby = textNodes.get(j).trim().toLowerCase(Locale.ROOT);
                if (nearby.startsWith("by ") || nearby.startsWith("author") || nearby.startsWith("creator")) {
                    score += 25;
                }
                if (MEDIA_ACTION_PATTERN.matcher(nearby).matches()) {
                    score += 20;
                }
            }

            // Penalties
            if (text.endsWith(".") && !text.matches(".*\\b(?:inc|ltd|corp)\\b.*")) {
                score -= 15;
            }
            if (text.matches("(?i).*(\\bchapter\\b|\\bepisode\\b|\\bvol(?:ume)?\\b).*")) {
                score -= 40;
            }
            if (CHAPTER_ROW_PREFIX_PATTERN.matcher(text).matches()) {
                score -= 30;
            }
            if (DETAIL_NOISE_PATTERN.matcher(text).matches()) {
                score -= 25;
            }
            if (Character.isDigit(text.charAt(0)) && text.length() < 10) {
                score -= 20; // Likely a rating or ep number
            }
            if (looksLikeGenreChip(text)) {
                score -= 35;
            }
            if (GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()) {
                score -= 45;
            }

            int repeats = frequency.getOrDefault(text.toLowerCase(Locale.ROOT), 0);
            if (repeats > 1) {
                score += (repeats - 1) * 15;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = text;
                bestIndex = i;
            }
        }

        if (bestScore < 25) {
            return null;
        }

        bestCandidate = combineAdjacentTitleLines(textNodes, bestIndex, bestCandidate, uiElementPattern);

        // Clean browser titles (e.g., "One Piece - Bilibili" -> "One Piece")
        if (bestCandidate != null && bestCandidate.contains(" - ")) {
            String[] parts = bestCandidate.split(" - ");
            if (parts.length > 1) {
                String potentialTitle = parts[0].trim();
                String siteName = parts[parts.length - 1].trim().toLowerCase(Locale.ROOT);
                if (siteName.contains("bilibili") || siteName.contains("chrome") || siteName.contains("google") || siteName.contains("youtube")) {
                    return potentialTitle;
                }
            }
        }

        return bestCandidate;
    }

    private static boolean isLikelyTitleCandidate(String text, Pattern uiElementPattern) {
        if (text == null) return false;
        if (text.length() < 3 || text.length() > 120) return false;
        String lower = text.toLowerCase(Locale.US);
        if (uiElementPattern.matcher(text).matches()) return false;
        if (GENERIC_UI_PHRASE_PATTERN.matcher(text).matches()) return false;
        if (STATUS_SOURCE_LINE_PATTERN.matcher(text).matches()) return false;
        if (DETAIL_TAB_PATTERN.matcher(text).matches()) return false;
        if (AUTHOR_LIST_PATTERN.matcher(text).matches()) return false;
        if (CHAPTER_LINE_PATTERN.matcher(text).matches()) return false;
        if (text.matches("(?i).*\\b(chapter list|volume \\d+|vol\\.\\s*\\d+).*")) return false;
        if (AUTH_UI_PATTERN.matcher(text).matches()) return false;
        if (text.matches("^\\d+(?:[\\.,]\\d+)?$")) return false;
        if (CHAPTER_ROW_PREFIX_PATTERN.matcher(text).matches()) return false;
        if (DETAIL_NOISE_PATTERN.matcher(text).matches()) return false;
        if (looksLikeSingleNoiseToken(lower)) return false;
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
        if (tokens.length == 1) {
            return GENRE_CHIP_TERMS.contains(tokens[0]);
        }
        if (tokens.length > 6) return false;
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
            String lower = node.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("by ")
                    || lower.startsWith("author")
                    || lower.startsWith("creator")
                    || lower.startsWith("artist")
                    || AUTHOR_LIST_PATTERN.matcher(node).matches())) {
                continue;
            }
            for (int back = 1; back <= 3; back++) {
                int idx = i - back;
                if (idx < 0) break;
                String candidate = textNodes.get(idx) == null ? "" : textNodes.get(idx).trim();
                if (isLikelyTitleCandidate(candidate, uiElementPattern)) {
                    return combineAdjacentTitleLines(textNodes, idx, candidate, uiElementPattern);
                }
            }
        }
        return null;
    }

    private static String combineAdjacentTitleLines(List<String> textNodes, int pivotIndex, String baseTitle, Pattern uiElementPattern) {
        if (baseTitle == null || pivotIndex < 0 || pivotIndex >= textNodes.size()) {
            return baseTitle;
        }
        String current = baseTitle.trim();

        int nextIndex = pivotIndex + 1;
        if (nextIndex < textNodes.size()) {
            String next = textNodes.get(nextIndex) == null ? "" : textNodes.get(nextIndex).trim();
            if (isLikelyTitleCandidate(next, uiElementPattern) && !AUTHOR_LIST_PATTERN.matcher(next).matches()) {
                String joined = (current + " " + next).replaceAll("\\s{2,}", " ").trim();
                if ((current.endsWith(":") || current.endsWith("-") || current.split("\\s+").length <= 5)
                        && joined.length() <= 100
                        && !STATUS_SOURCE_LINE_PATTERN.matcher(joined).matches()) {
                    return joined;
                }
            }
        }

        return current;
    }

    private static boolean looksLikeSingleNoiseToken(String lowerText) {
        if (lowerText == null) {
            return false;
        }
        String normalized = lowerText.replaceAll("[^a-z]", "");
        return !normalized.isEmpty() && GENRE_CHIP_TERMS.contains(normalized);
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
                    || MEDIA_ACTION_PATTERN.matcher(text).matches()
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
