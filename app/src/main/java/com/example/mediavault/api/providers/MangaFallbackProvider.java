package com.example.mediavault.api.providers;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight manga fallback provider.
 * Provides generic chapter rows for chapter selector reliability.
 */
public class MangaFallbackProvider implements MediaProvider {
    private final String providerName;
    private final String searchUrlTemplate;
    private final int maxChapters;

    public MangaFallbackProvider(String providerName, String searchUrlTemplate, int maxChapters) {
        this.providerName = providerName;
        this.searchUrlTemplate = searchUrlTemplate;
        this.maxChapters = Math.max(100, maxChapters);
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.MANGA;
    }

    @Override
    public List<String> resolveMangaChapter(String title, int chapter) {
        // Fallback provider does not supply page images; keep returning empty.
        return new ArrayList<>();
    }

    @Override
    public List<ChapterInfo> fetchMangaChapters(String title) {
        List<ChapterInfo> chapters = new ArrayList<>();
        for (int i = 1; i <= maxChapters; i++) {
            chapters.add(new ChapterInfo("fallback-ch-" + i, i, "Chapter " + i, 0));
        }
        return chapters;
    }

    public String buildSearchUrl(String title) {
        String safeTitle = title == null ? "" : title;
        return String.format(searchUrlTemplate, encode(safeTitle));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value.replace(" ", "+");
        }
    }
}
