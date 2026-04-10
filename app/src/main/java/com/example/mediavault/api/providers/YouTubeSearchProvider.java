package com.example.mediavault.api.providers;

import java.net.URLEncoder;

/**
 * YouTube fallback provider for anime, movie, and TV resolution paths.
 * Returns YouTube search URLs that can be opened externally when direct providers fail.
 */
public class YouTubeSearchProvider implements MediaProvider {
    private static final String PROVIDER_NAME = "YouTube";
    private static final String SEARCH_BASE = "https://www.youtube.com/results?search_query=";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.ANIME || type == MediaType.MOVIE || type == MediaType.TV_SHOW;
    }

    @Override
    public String resolveAnimeEpisode(String title, int episode) {
        return SEARCH_BASE + encode((safe(title) + " episode " + episode + " full").trim());
    }

    @Override
    public String resolveMovie(String title, int year, String tmdbId) {
        String yearToken = year > 0 ? " " + year : "";
        return SEARCH_BASE + encode((safe(title) + yearToken + " full movie").trim());
    }

    @Override
    public String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        return SEARCH_BASE + encode((safe(title) + " season " + season + " episode " + episode + " full").trim());
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
