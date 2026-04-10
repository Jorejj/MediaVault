package com.example.mediavault.api.providers;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Ad-light search fallback provider.
 * Returns search URLs for playback fallbacks and generic table rows for selectors.
 */
public class SearchEngineFallbackProvider implements MediaProvider {
    private final String providerName;
    private final String searchUrlTemplate;
    private final EnumSet<MediaType> supportedTypes;
    private final int maxEpisodes;
    private final int maxChapters;

    public SearchEngineFallbackProvider(String providerName, String searchUrlTemplate, EnumSet<MediaType> supportedTypes) {
        this(providerName, searchUrlTemplate, supportedTypes, 260, 500);
    }

    public SearchEngineFallbackProvider(String providerName, String searchUrlTemplate, EnumSet<MediaType> supportedTypes, int maxEpisodes, int maxChapters) {
        this.providerName = providerName;
        this.searchUrlTemplate = searchUrlTemplate;
        this.supportedTypes = supportedTypes == null ? EnumSet.noneOf(MediaType.class) : EnumSet.copyOf(supportedTypes);
        this.maxEpisodes = Math.max(24, maxEpisodes);
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
        return type != null && supportedTypes.contains(type);
    }

    @Override
    public String resolveAnimeEpisode(String title, int episode) {
        return buildSearchUrl((safe(title) + " episode " + Math.max(1, episode) + " stream").trim());
    }

    @Override
    public List<EpisodeInfo> fetchAnimeEpisodes(String title) {
        List<EpisodeInfo> episodes = new ArrayList<>();
        for (int i = 1; i <= maxEpisodes; i++) {
            episodes.add(new EpisodeInfo("search-ep-" + i, i, "Episode " + i, null));
        }
        return episodes;
    }

    @Override
    public String resolveMovie(String title, int year, String tmdbId) {
        String suffix = year > 0 ? (" " + year) : "";
        return buildSearchUrl((safe(title) + suffix + " full movie").trim());
    }

    @Override
    public String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        return buildSearchUrl((safe(title) + " season " + Math.max(1, season) + " episode " + Math.max(1, episode)).trim());
    }

    @Override
    public List<String> resolveMangaChapter(String title, int chapter) {
        // The manga reader requires direct image pages; search providers cannot guarantee that.
        return new ArrayList<>();
    }

    @Override
    public List<ChapterInfo> fetchMangaChapters(String title) {
        List<ChapterInfo> chapters = new ArrayList<>();
        for (int i = 1; i <= maxChapters; i++) {
            chapters.add(new ChapterInfo("search-ch-" + i, i, "Chapter " + i, 0));
        }
        return chapters;
    }

    private String buildSearchUrl(String query) {
        String safeQuery = query == null ? "" : query;
        return String.format(searchUrlTemplate, encode(safeQuery));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value.replace(" ", "+");
        }
    }
}
