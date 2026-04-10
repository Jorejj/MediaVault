package com.example.mediavault.api.providers;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight anime fallback provider.
 * Returns a web search URL for playback fallback and a generic episode table
 * to ensure selector screens remain usable even when APIs are unavailable.
 */
public class AnimeFallbackProvider implements MediaProvider {
    private final String providerName;
    private final String searchUrlTemplate;
    private final int maxEpisodes;

    public AnimeFallbackProvider(String providerName, String searchUrlTemplate, int maxEpisodes) {
        this.providerName = providerName;
        this.searchUrlTemplate = searchUrlTemplate;
        this.maxEpisodes = Math.max(24, maxEpisodes);
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
        return type == MediaType.ANIME;
    }

    @Override
    public String resolveAnimeEpisode(String title, int episode) {
        String safeTitle = title == null ? "" : title;
        String query = (safeTitle + " episode " + Math.max(1, episode)).trim();
        return String.format(searchUrlTemplate, encode(query));
    }

    @Override
    public List<EpisodeInfo> fetchAnimeEpisodes(String title) {
        List<EpisodeInfo> episodes = new ArrayList<>();
        for (int i = 1; i <= maxEpisodes; i++) {
            episodes.add(new EpisodeInfo("fallback-ep-" + i, i, "Episode " + i, null));
        }
        return episodes;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value.replace(" ", "+");
        }
    }
}
